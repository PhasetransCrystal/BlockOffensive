package net.ptcrys.blockoffensive.server.shop;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.mixin.ItemEntityPickupAccessor;
import net.ptcrys.blockoffensive.mixin.MatchDropPickupAccessor;
import net.ptcrys.fpsmatch.common.drop.DropType;
import net.ptcrys.fpsmatch.common.entity.MatchDropEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server authoritative validation for shop drop pickup requests.
 *
 * <p>
 * The client only supplies an entity id. The item, owner, phase and
 * inventory mutation are all read and performed on the server thread.
 * </p>
 */
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShopDropPickupService {

    public static final double DEFAULT_RADIUS = 8.0D;
    public static final int MAX_NEARBY_ENTRIES = 32;
    private static final long REQUEST_EXPIRY_TICKS = 100L;
    private static final int MAX_REQUESTS_PER_PLAYER = 128;
    private static final long LIST_REQUEST_INTERVAL_TICKS = 2L;

    /** Request ids are replay protection, not an authority token. */
    private static final Map<UUID, LinkedHashMap<UUID, Long>> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_LIST_REQUEST = new HashMap<>();

    private ShopDropPickupService() {}

    /**
     * Compatibility wrapper used by callers that only need the old result enum.
     */
    public static Result pickup(ServerPlayer player, UUID entityId, double radius,
                                Predicate<ItemStack> allowed, boolean purchasePhase) {
        return pickupDetailed(player, entityId, radius, allowed, purchasePhase).result();
    }

    /** Perform all checks and mutate the inventory atomically on the server thread. */
    public static Outcome pickupDetailed(ServerPlayer player, UUID entityId, double radius,
                                         Predicate<ItemStack> allowed, boolean purchasePhase) {
        FailureReason validation = validateRequest(player, entityId, radius, allowed, purchasePhase);
        if (validation != FailureReason.NONE) {
            return new Outcome(validation == FailureReason.INVENTORY_FULL ? Result.INVENTORY_FULL : Result.REJECTED, validation, ItemStack.EMPTY);
        }

        Entity entity = player.serverLevel().getEntity(entityId);
        ItemStack source = itemStack(entity);
        ItemStack authoritative = source.copy();
        // Match drops deliberately pick up one item at a time and enforce category limits.
        if (entity instanceof MatchDropEntity) authoritative.setCount(1);
        if (!hasCapacity(player.getInventory(), authoritative)) {
            return new Outcome(Result.INVENTORY_FULL, FailureReason.INVENTORY_FULL, ItemStack.EMPTY);
        }
        int before = source.getCount();
        // Preserve native pickup events, weapon/throwable limits, sounds and shop refund locks.
        if (entity instanceof MatchDropEntity match) match.playerTouch(player);
        else if (entity instanceof ItemEntity item) item.playerTouch(player);
        int taken = before - (entity.isRemoved() ? 0 : itemStack(entity).getCount());
        if (taken <= 0) return new Outcome(Result.REJECTED, FailureReason.INVALID_ITEM, ItemStack.EMPTY);
        authoritative.setCount(taken);
        if (itemStack(entity).isEmpty() && !entity.isRemoved()) entity.discard();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return new Outcome(Result.ACCEPTED, FailureReason.NONE, authoritative);
    }

    /**
     * Collect a bounded, server-authenticated snapshot for the shop UI.
     * Owner-restricted entities are omitted so the client cannot present an
     * action that the server will necessarily reject.
     */
    public static List<NearbyDrop> collectNearby(ServerPlayer player, double radius,
                                                 Predicate<ItemStack> allowed,
                                                 boolean purchasePhase) {
        if (player == null || !player.isAlive() || !purchasePhase || allowed == null) {
            return List.of();
        }
        double boundedRadius = boundedRadius(radius);
        if (boundedRadius <= 0.0D) {
            return List.of();
        }
        AABB bounds = player.getBoundingBox().inflate(boundedRadius);
        List<NearbyDrop> drops = new ArrayList<>();
        for (Entity entity : player.serverLevel().getEntitiesOfClass(Entity.class, bounds,
                item -> isCollectibleForList(player, item, allowed, boundedRadius))) {
            ItemStack stack = itemStack(entity).copy();
            drops.add(new NearbyDrop(entity.getUUID(), stack,
                    dropType(entity), entity.getX(), entity.getY(), entity.getZ(),
                    pickupDelay(entity)));
        }
        drops.sort(Comparator.comparingDouble(drop -> player.distanceToSqr(
                drop.x(), drop.y(), drop.z())));
        return drops.size() > MAX_NEARBY_ENTRIES ? List.copyOf(drops.subList(0, MAX_NEARBY_ENTRIES)) : List.copyOf(drops);
    }

    /** Rate-limit list refreshes independently from pickup request replay protection. */
    public static boolean acceptListRequest(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_LIST_REQUEST.put(player.getUUID(), now);
        return previous == null || now - previous >= LIST_REQUEST_INTERVAL_TICKS;
    }

    /** Return false for a replayed pickup request id. */
    public static boolean acceptRequest(ServerPlayer player, UUID requestId) {
        if (player == null || requestId == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        LinkedHashMap<UUID, Long> requests = REQUESTS.computeIfAbsent(
                player.getUUID(), ignored -> new LinkedHashMap<>());
        Iterator<Map.Entry<UUID, Long>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() > REQUEST_EXPIRY_TICKS) {
                iterator.remove();
            }
        }
        if (requests.containsKey(requestId)) {
            return false;
        }
        requests.put(requestId, now);
        while (requests.size() > MAX_REQUESTS_PER_PLAYER) {
            requests.remove(requests.keySet().iterator().next());
        }
        return true;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        REQUESTS.remove(playerId);
        LAST_LIST_REQUEST.remove(playerId);
    }

    private static FailureReason validateRequest(ServerPlayer player, UUID entityId, double radius,
                                                 Predicate<ItemStack> allowed, boolean purchasePhase) {
        if (player == null || !player.isAlive()) {
            return FailureReason.PLAYER_NOT_ALIVE;
        }
        if (!purchasePhase) {
            return FailureReason.NOT_PURCHASE_PHASE;
        }
        if (entityId == null || allowed == null) {
            return FailureReason.INVALID_REQUEST;
        }
        double boundedRadius = boundedRadius(radius);
        if (boundedRadius <= 0.0D) {
            return FailureReason.OUT_OF_RANGE;
        }
        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity == null || entity.isRemoved() || !entity.isAlive()) {
            return FailureReason.NOT_FOUND;
        }
        if (entity.level() != player.level() || !entity.level().dimension().equals(player.level().dimension())) {
            return FailureReason.WRONG_DIMENSION;
        }
        if (entity.distanceToSqr(player) > boundedRadius * boundedRadius) {
            return FailureReason.OUT_OF_RANGE;
        }
        if (pickupDelay(entity) > 0) {
            return FailureReason.PICKUP_DELAY;
        }
        UUID owner = ownerUuid(entity);
        if (owner != null && !owner.equals(player.getUUID())) {
            return FailureReason.OWNER_RESTRICTED;
        }
        ItemStack stack = itemStack(entity);
        if (stack.isEmpty() || !allowed.test(stack)) {
            return FailureReason.INVALID_ITEM;
        }
        if (entity instanceof MatchDropEntity match) {
            DropType type = match.getDropType();
            if (!type.inventoryMatch().test(player) || type == DropType.THROW && !type.canPickupThrowable(player, stack)) {
                return FailureReason.INVENTORY_FULL;
            }
        }
        return FailureReason.NONE;
    }

    private static boolean isCollectibleForList(ServerPlayer player, Entity entity,
                                                Predicate<ItemStack> allowed, double radius) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.level() != player.level()) {
            return false;
        }
        UUID owner = ownerUuid(entity);
        ItemStack stack = itemStack(entity);
        return (owner == null || owner.equals(player.getUUID())) && entity.distanceToSqr(player) <= radius * radius && pickupDelay(entity) <= 0 && !stack.isEmpty() && allowed.test(stack);
    }

    private static double boundedRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0.0D;
        }
        return Math.min(radius, DEFAULT_RADIUS);
    }

    private static boolean hasCapacity(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        int maxStack = stack.getMaxStackSize();
        for (ItemStack slot : inventory.items) {
            if (slot.isEmpty()) {
                remaining -= maxStack;
            } else if (ItemStack.isSameItemSameTags(slot, stack)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private static ItemStack itemStack(Entity entity) {
        if (entity instanceof MatchDropEntity match) return match.getItem();
        if (entity instanceof ItemEntity item) return item.getItem();
        return ItemStack.EMPTY;
    }

    private static DropType dropType(Entity entity) {
        return entity instanceof MatchDropEntity match ? match.getDropType() : DropType.getItemDropType(itemStack(entity));
    }

    private static int pickupDelay(Entity entity) {
        if (entity instanceof MatchDropEntity match) return ((MatchDropPickupAccessor) match).blockoffensive$pickupDelay();
        if (entity instanceof ItemEntity item) return ((ItemEntityPickupAccessor) item).blockoffensive$pickupDelay();
        return 0;
    }

    private static UUID ownerUuid(Entity entity) {
        return entity instanceof ItemEntity item ? ((ItemEntityPickupAccessor) item).blockoffensive$pickupTarget() : null;
    }

    public enum Result {
        ACCEPTED,
        INVENTORY_FULL,
        REJECTED
    }

    public enum FailureReason {
        NONE,
        PLAYER_NOT_ALIVE,
        NOT_PURCHASE_PHASE,
        INVALID_REQUEST,
        NOT_FOUND,
        WRONG_DIMENSION,
        OUT_OF_RANGE,
        PICKUP_DELAY,
        OWNER_RESTRICTED,
        INVALID_ITEM,
        INVENTORY_FULL,
        DUPLICATE_REQUEST
    }

    public record Outcome(Result result, FailureReason reason, ItemStack authoritativeStack) {

        public boolean accepted() {
            return result == Result.ACCEPTED;
        }
    }

    public record NearbyDrop(UUID entityId, ItemStack stack, DropType type,
                             double x, double y, double z, int pickupDelay) {}
}
