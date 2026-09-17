package net.ptcrys.blockoffensive.intro;

import net.ptcrys.blockoffensive.BlockOffensive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class IntroDisplayWeaponResolver {

    public static final String RULE_ID = "cs2_display_pool";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation AUTO_GUN_ID = ResourceLocation.tryBuild(BlockOffensive.MODID, "auto_gun");
    private static final ResourceLocation T_DISPLAY_GUN = ResourceLocation.tryBuild("tacz", "ak47");
    private static final ResourceLocation CT_DISPLAY_GUN = ResourceLocation.tryBuild("tacz", "m4a1");

    private IntroDisplayWeaponResolver() {}

    public static Result items(IntroSequence sequence) {
        return items(sequence.side(), sequence.previewItemId(), sequence.players().size());
    }

    public static Result items(IntroTeamSide side, String itemId, int count) {
        ArrayList<ItemStack> items = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        int fallbacks = 0;
        for (int i = 0; i < count; i++) {
            Resolved resolved = item(side, itemId, i);
            items.add(resolved.stack().copy());
            ids.add(resolved.itemId());
            if (resolved.fallback()) {
                fallbacks++;
            }
        }
        return new Result(List.copyOf(items), fallbacks, List.copyOf(ids));
    }

    public static ItemStack itemStack(IntroTeamSide side, String itemId, int formationIndex) {
        return item(side, itemId, formationIndex).stack().copy();
    }

    private static Resolved item(IntroTeamSide side, String itemId, int formationIndex) {
        ResourceLocation requested = requestedId(side, itemId);
        if (isExplicitNonTaczItem(itemId, requested)) {
            Item item = ForgeRegistries.ITEMS.getValue(requested);
            if (item != null) {
                return new Resolved(new ItemStack(item), requested.toString(), false);
            }
        }
        ItemStack taczGun = buildTaczGun(requested);
        if (!taczGun.isEmpty()) {
            return new Resolved(taczGun, requested.toString(), false);
        }
        if (isExplicitItem(itemId)) {
            Item item = ForgeRegistries.ITEMS.getValue(requested);
            if (item != null) {
                return new Resolved(new ItemStack(item), requested.toString(), false);
            }
        }
        ItemStack registeredGun = findRegisteredGunItem();
        if (!registeredGun.isEmpty()) {
            LOGGER.warn("[BlockOffensive Halftime] introDisplayWeaponRule={} formationIndex={} requested={} fallback=registered_tacz_gun", RULE_ID, formationIndex, requested);
            return new Resolved(registeredGun, itemId(registeredGun), true);
        }
        LOGGER.warn("[BlockOffensive Halftime] introDisplayWeaponRule={} formationIndex={} requested={} fallback=minecraft:crossbow", RULE_ID, formationIndex, requested);
        return new Resolved(new ItemStack(Items.CROSSBOW), "minecraft:crossbow", true);
    }

    private static ResourceLocation requestedId(IntroTeamSide side, String itemId) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId == null || itemId.isBlank() ? AUTO_GUN_ID.toString() : itemId);
        if (parsed == null || AUTO_GUN_ID.equals(parsed)) {
            return side == IntroTeamSide.CT ? CT_DISPLAY_GUN : T_DISPLAY_GUN;
        }
        return parsed;
    }

    private static boolean isExplicitItem(String itemId) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId == null || itemId.isBlank() ? AUTO_GUN_ID.toString() : itemId);
        return parsed != null && !AUTO_GUN_ID.equals(parsed);
    }

    private static boolean isExplicitNonTaczItem(String itemId, ResourceLocation requested) {
        return isExplicitItem(itemId) && !"tacz".equals(requested.getNamespace());
    }

    private static ItemStack buildTaczGun(ResourceLocation gunId) {
        try {
            Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            Object builder = builderClass.getMethod("create").invoke(null);
            builder = builderClass.getMethod("setId", ResourceLocation.class).invoke(builder, gunId);
            builder = tryInvokeBuilder(builderClass, builder, "setAmmoCount", int.class, 30);
            builder = tryInvokeBuilder(builderClass, builder, "setAmmoInBarrel", boolean.class, true);
            ItemStack built = invokeGunBuild(builderClass, builder, "build");
            if (!built.isEmpty()) {
                return built;
            }
            return invokeGunBuild(builderClass, builder, "forceBuild");
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.debug("[BlockOffensive Halftime] TACZ GunItemBuilder unavailable for intro display weapon {}", gunId, ex);
            return ItemStack.EMPTY;
        }
    }

    private static Object tryInvokeBuilder(Class<?> builderClass, Object builder, String method, Class<?> parameterType, Object value) {
        try {
            return builderClass.getMethod(method, parameterType).invoke(builder, value);
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.debug("[BlockOffensive Halftime] TACZ GunItemBuilder method {} unavailable", method, ex);
            return builder;
        }
    }

    private static ItemStack invokeGunBuild(Class<?> builderClass, Object builder, String method) {
        try {
            Object stack = builderClass.getMethod(method).invoke(builder);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError ex) {
            LOGGER.debug("[BlockOffensive Halftime] TACZ GunItemBuilder {} failed", method, ex);
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack findRegisteredGunItem() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (isTaczGunItem(item)) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isTaczGunItem(Item item) {
        for (Class<?> type : item.getClass().getInterfaces()) {
            if ("com.tacz.guns.api.item.IGun".equals(type.getName())) {
                return true;
            }
        }
        Class<?> superClass = item.getClass().getSuperclass();
        while (superClass != null) {
            for (Class<?> type : superClass.getInterfaces()) {
                if ("com.tacz.guns.api.item.IGun".equals(type.getName())) {
                    return true;
                }
            }
            superClass = superClass.getSuperclass();
        }
        return false;
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    public record Result(List<ItemStack> items, int fallbackCount, List<String> itemIds) {

        public int nonEmptyCount() {
            int count = 0;
            for (ItemStack item : items) {
                if (item != null && !item.isEmpty()) {
                    count++;
                }
            }
            return count;
        }
    }

    private record Resolved(ItemStack stack, String itemId, boolean fallback) {}
}
