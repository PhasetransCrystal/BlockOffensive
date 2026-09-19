package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.blockoffensive.client.shop.*;
import net.ptcrys.blockoffensive.map.shop.ItemType;
import net.ptcrys.fpsmatch.common.client.FPSMClient;
import net.ptcrys.fpsmatch.common.client.music.FPSClientMusicManager;
import net.ptcrys.fpsmatch.common.client.screen.modernui.ModernScreen;
import net.ptcrys.fpsmatch.common.client.shop.ClientShopSlot;
import net.ptcrys.fpsmatch.common.client.shop.ShopActionResultListener;
import net.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.ptcrys.fpsmatch.common.packet.shop.ShopActionC2SPacket;
import net.ptcrys.fpsmatch.common.packet.shop.ShopActionResultS2CPacket;
import net.ptcrys.fpsmatch.common.sound.FPSMSoundRegister;
import net.ptcrys.fpsmatch.compat.LrtacticalCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import net.ptcrys.fpsmatch.core.shop.ShopAction;
import net.ptcrys.fpsmatch.util.FPSMUtil;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Native Modern UI shop; Minecraft renders only item icons and the live player preview. */
public final class CSGameShopScreen extends ModernScreen {

    private static final String[] TOP_NAME_KEYS = { "blockoffensive.shop.title.equipment", "blockoffensive.shop.title.pistol", "blockoffensive.shop.title.mid_rank", "blockoffensive.shop.title.rifle", "blockoffensive.shop.title.throwable" };
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
    private static CSGameShopScreen INSTANCE;
    private final Map<Long, PendingShopAction> pendingActions = new HashMap<>();
    private final ShopActionProgress<SlotRef> actionProgress = new ShopActionProgress<>();
    private AutoCloseable resultSubscription;
    private ShopPlayerPreview playerPreview;
    private final ShopItemArtwork itemArtwork = new ShopItemArtwork();
    private final ShopStyle shopStyle = new ShopStyle();
    private volatile int teamAccent = ShopStyle.T_ACCENT;
    private SlotRef hoveredSlot;
    private boolean preferInventoryPreview;
    private PendingShopAction feedbackAction;
    private boolean feedbackFailed;
    private int feedbackTicks, dropRefreshTicks, actionTick, dropPage;
    private int keyboardCategory = -1;
    private long keyboardCategoryStarted, keyboardSlotStarted;
    private SlotRef keyboardSelectedSlot;
    private boolean keyboardSelectionUnavailable;
    private static final long KEYBOARD_FEEDBACK_MS = 700;
    // Reference-space foot anchor and model scale; leave room for held weapons and the footer.
    private static final int PREVIEW_X = 1560;
    private static final int PREVIEW_FEET_Y = 995;
    private static final int PREVIEW_SCALE = 385;

    private CSGameShopScreen() {
        super(Component.translatable("blockoffensive.shop.title"), null);
    }

    public static synchronized CSGameShopScreen getInstance() {
        if (INSTANCE == null) INSTANCE = new CSGameShopScreen();
        return INSTANCE;
    }

    @Override
    protected int designWidth() {
        return 1920;
    }

    @Override
    protected int designHeight() {
        return 1080;
    }

    @Override
    protected float nativeTextSize() {
        return 16;
    }

    @Override
    protected void styleView(icyllis.modernui.view.View view, Node node, float scale, boolean changed) {
        shopStyle.apply(view, node, scale, changed, teamAccent);
    }

    @Override
    protected boolean renderItemArtwork(GuiGraphics graphics, String key, ItemStack stack,
                                        String texture, float x, float y, float w, float h) {
        int color = teamAccent;
        if (key.startsWith("icon.")) {
            String[] parts = key.split("\\.");
            ClientShopSlot slot = FPSMClient.getGlobalData().getSlotData(parts[1], Integer.parseInt(parts[2]));
            if (!canBuy(slot)) color = 0xFF555555;
        }
        return itemArtwork.render(graphics, texture, color, x, y, w, h);
    }

    @Override
    public void init() {
        closeResultSubscription();
        itemArtwork.clear();
        keyboardCategory = -1;
        keyboardSelectedSlot = null;
        hoveredSlot = null;
        dropPage = 0;
        preferInventoryPreview = false;
        if (minecraft != null && minecraft.player != null) playerPreview = new ShopPlayerPreview(minecraft.player);
        super.init();
        resultSubscription = ShopActionResultListener.install(this::handleShopActionResult);
        dropRefreshTicks = 0;
        ShopDropClientState.requestRefresh();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xA5101010);
        graphics.fillGradient(0, 0, width, height, 0x10000000, 0x48000000);
        if (playerPreview == null || minecraft == null || minecraft.player == null) return;
        var inventory = new ArrayList<ItemStack>(minecraft.player.getInventory().items);
        inventory.addAll(minecraft.player.getInventory().offhand);
        ItemStack hovered = hoveredSlot == null || preferInventoryPreview ? null :
                FPSMClient.getGlobalData().getSlotData(hoveredSlot.type().name(), hoveredSlot.index()).itemStack();
        ItemStack held = ShopPreviewSelection.choose(inventory, hovered, ItemStack.EMPTY, ShopPlayerPreview::category);
        float scale = Math.min(width / 1920f, height / 1080f);
        float ox = (width - 1920 * scale) / 2, oy = (height - 1080 * scale) / 2;
        playerPreview.render(graphics, held, Math.round(ox + PREVIEW_X * scale),
                Math.round(oy + PREVIEW_FEET_Y * scale), Math.max(1, Math.round(PREVIEW_SCALE * scale)));
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static int displayMoney() {
        return CSClientData.getMoney() < 0 ? 16000 : CSClientData.getMoney();
    }

    private static String clockText() {
        int seconds = Math.max(0, CSClientData.shopCloseTime);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static boolean canBuy(ClientShopSlot slot) {
        return canRequestBuy(slot) && (CSClientData.getMoney() < 0 || CSClientData.getMoney() >= slot.cost());
    }

    /**
     * Checks only the client-known shop state. The server remains authoritative for
     * the balance because the client's money packet may still be in flight.
     */
    private static boolean canRequestBuy(ClientShopSlot slot) {
        return CSClientData.canOpenShop && !slot.itemStack().isEmpty() && !slot.isLocked();
    }

    @Override
    protected List<Node> content() {
        // Resolve on the Minecraft thread on every refresh, including reopening and side swaps.
        teamAccent = FPSMClient.getGlobalData().isCurrentTeam("ct") ? ShopStyle.CT_ACCENT : ShopStyle.T_ACCENT;
        // Reference coordinates are physical 1920 x 1080 pixels, independent of GUI scale.
        List<Node> nodes = new ArrayList<>();
        nodes.add(canvas("header", List.of(title("money", "$" + displayMoney()).at(40, 3, 245, 35),
                text("time", tr("blockoffensive.shop.title.cooldown", clockText())).at(325, 3, 310, 35),
                text("next", tr("blockoffensive.shop.title.min.money", "$" + String.format(Locale.ROOT, "%,d", CSClientData.getNextRoundMinMoney()))).at(650, 3, 285, 35)))
                .surface().at(252, 180, 950, 41));
        nodes.add(canvas("shop.surface", List.of()).surface().at(252, 225, 950, 552));
        int[] positions = { 267, 438, 609, 818, 1046 }, widths = { 141, 141, 179, 198, 141 };
        for (ItemType type : ItemType.values()) {
            int c = type.ordinal(), cw = widths[c];
            nodes.add(text("category.number." + type, Integer.toString(c + 1)).selected(highlightedKeyboardCategory() == c).at(positions[c], 231, 20, 29));
            nodes.add(text("category.name." + type, tr(TOP_NAME_KEYS[c])).selected(highlightedKeyboardCategory() == c).at(positions[c] + 22, 229, cw - 35, 35));
            for (int index = 0; index < type.slotCount(); index++) {
                final int slotIndex = index;
                SlotRef ref = new SlotRef(type, index);
                ClientShopSlot slot = FPSMClient.getGlobalData().getSlotData(type.name(), index);
                boolean populated = !slot.itemStack().isEmpty(), busy = actionProgress.isBusy(ref);
                // A plain frame reserves the slot without any content, focus or input handlers.
                if (!populated) {
                    nodes.add(canvas("slot.empty." + type + "." + index, List.of())
                            .at(positions[c], 277 + index * 100, cw, 90));
                    continue;
                }
                List<Node> children = new ArrayList<>();
                if (populated) {
                    children.add((canBuy(slot) ? accent("number", Integer.toString(index + 1)) : muted("number", Integer.toString(index + 1))).at(11, 4, 16, 22));
                    children.add((canBuy(slot) ? accent("name", slot.name()) : muted("name", slot.name())).hint(slot.name()).at(29, 4, cw - 40, 20));
                    children.add(item("icon." + type + "." + index, slot.itemStack(), ShopItemArtwork.texture(slot.itemStack(), slot.texture())).at(18, 28, cw - 36, 38));
                    String feedback = busy ? " …" : feedbackAction != null && feedbackAction.type() == type && feedbackAction.index() == index ? (feedbackFailed ? " !" : " ✓") : "";
                    children.add((canBuy(slot) ? accent("price", "$" + slot.cost() + feedback) : muted("price", "$" + slot.cost() + feedback)).at(36, 66, cw - 47, 20));
                    if (slot.canReturn()) children.add(iconButton("refund", "rotate-cw", tr("blockoffensive.shop.refund", slot.name()), CSClientData.canOpenShop && !busy, () -> sendShopAction(type, slotIndex, ShopAction.RETURN))
                            .hint(tr("blockoffensive.shop.refund", slot.name())).at(5, 63, 27, 25));
                    if (slot.boughtCount() > 0) children.add(accent("owned", "•".repeat(Math.min(5, slot.boughtCount()))).at(34, 77, cw - 68, 12));
                }
                nodes.add(actionCanvas("slot." + type + "." + index, populated ? slot.name() + " $" + slot.cost() : "", populated, event -> {
                    if (event.equals("hover")) {
                        hoveredSlot = ref;
                        preferInventoryPreview = false;
                    } else if (event.equals("hoverExit")) {
                        if (ref.equals(hoveredSlot)) hoveredSlot = null;
                    } else sendShopAction(type, slotIndex, event.equals("secondary") ? ShopAction.RETURN : ShopAction.BUY);
                }, children).hint(populated && !canBuy(slot) ? tr("blockoffensive.shop.unavailable") : "")
                        .selected(ref.equals(keyboardSelectedSlot) && keyboardSelectionStrength() > 0)
                        .at(positions[c], 277 + index * 100, cw, 90));
            }
        }
        nodes.add(canvas("drops.surface", List.of()).surface().at(252, 787, 950, 216));
        var drops = ShopDropClientState.nearby();
        int pages = Math.max(1, (drops.size() + 9) / 10);
        dropPage = Math.min(dropPage, pages - 1);
        int index = 0;
        for (var drop : drops.stream().skip(dropPage * 10L).limit(10).toList()) {
            List<Node> children = List.of(text("name", drop.stack().getHoverName().getString()).at(5, 2, 160, 23),
                    item("drop.icon." + drop.entityId(), drop.stack(), ShopItemArtwork.texture(drop.stack(), null)).at(18, 25, 134, 36), text("count", "×" + drop.stack().getCount()).at(5, 60, 160, 20));
            nodes.add(actionCanvas("drop." + drop.entityId(), tr("blockoffensive.shop.pick_up", drop.stack().getHoverName()), true,
                    event -> { if (!event.startsWith("hover")) ShopDropClientState.requestPickup(drop.entityId()); }, children)
                    .at(267 + index % 5 * 184, 803 + index / 5 * 94, 170, 84));
            index++;
        }
        if (drops.isEmpty()) nodes.add(text("drops.empty", tr("blockoffensive.shop.dropped_weapons")).at(267, 807, 920, 166));
        if (pages > 1) nodes.add(row("pages", button("previous", "‹", dropPage > 0, () -> dropPage--), text("page", (dropPage + 1) + " / " + pages),
                button("next", "›", dropPage + 1 < pages, () -> dropPage++)).at(668, 982, 190, 21));
        nodes.add(canvas("footer.rule", List.of()).at(503, 1019, 910, 1));
        nodes.add(text("controls", tr("blockoffensive.shop.controls.select")).at(503, 1031, 430, 42));
        nodes.add(button("refundAll", tr("blockoffensive.shop.controls.refund"), CSClientData.canOpenShop, actionProgress::requestRefundAll).at(958, 1031, 230, 42));
        nodes.add(button("close", tr("blockoffensive.shop.controls.back", net.ptcrys.blockoffensive.client.key.OpenShopKey.OPEN_SHOP_KEY.getTranslatedKeyMessage()), true, this::onClose).at(1208, 1031, 205, 42));
        return List.of(canvas("purchase", nodes).fill());
    }

    @Override
    public void tick() {
        actionTick++;
        actionProgress.reconcile(ref -> FPSMClient.getGlobalData().getSlotData(ref.type().name(), ref.index()).boughtCount());
        for (var retry : actionProgress.retries(actionTick)) {
            PendingShopAction pending = pendingActions.get(retry.id());
            if (pending != null) transmitShopAction(retry.id(), pending);
        }
        for (long expired : actionProgress.expire(actionTick)) {
            pendingActions.remove(expired);
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("blockoffensive.shop.request_timeout"), true);
        }
        pendingActions.entrySet().removeIf(entry -> actionTick - entry.getValue().sentTick() >= 120);
        if (!CSClientData.canOpenShop) actionProgress.cancelRefundAll();
        List<SlotRef> slots = new ArrayList<>();
        for (ItemType type : ItemType.values()) for (int index = 0; index < type.slotCount(); index++) slots.add(new SlotRef(type, index));
        SlotRef refund = actionProgress.nextRefund(slots, ref -> FPSMClient.getGlobalData().getSlotData(ref.type().name(), ref.index()).canReturn());
        if (refund != null) sendShopAction(refund.type(), refund.index(), ShopAction.RETURN);
        if (feedbackTicks > 0 && --feedbackTicks == 0) feedbackAction = null;
        if (++dropRefreshTicks >= 10) {
            dropRefreshTicks = 0;
            ShopDropClientState.requestRefresh();
        }
        refresh();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || net.ptcrys.blockoffensive.client.key.OpenShopKey.OPEN_SHOP_KEY.matches(key, scanCode)) {
            onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            keyboardCategory = -1;
            keyboardSelectedSlot = null;
            actionProgress.requestRefundAll();
            refresh();
            return true;
        }
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_5) {
            int number = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            if (keyboardCategory < 0) {
                keyboardCategory = number;
                keyboardCategoryStarted = Util.getMillis();
                keyboardSelectedSlot = null;
            } else {
                ItemType type = ItemType.values()[keyboardCategory];
                ClientShopSlot slot = FPSMClient.getGlobalData().getSlotData(type.name(), number);
                keyboardSelectedSlot = null;
                if (!slot.itemStack().isEmpty()) {
                    keyboardSelectedSlot = new SlotRef(type, number);
                    keyboardSlotStarted = Util.getMillis();
                    keyboardSelectionUnavailable = !canBuy(slot);
                    sendShopAction(type, number, ShopAction.BUY);
                }
                keyboardCategory = -1;
            }
            refresh();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private float keyboardSelectionStrength() {
        return keyboardSelectedSlot == null ? 0 : Math.max(0f,
                1f - (Util.getMillis() - keyboardSlotStarted) / (float) KEYBOARD_FEEDBACK_MS);
    }

    private int highlightedKeyboardCategory() {
        if (keyboardCategory >= 0) return keyboardCategory;
        return keyboardSelectionStrength() > 0 ? keyboardSelectedSlot.type().ordinal() : -1;
    }

    @Override
    public void onClose() {
        if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        closeResultSubscription();
        pendingActions.clear();
        actionProgress.clear();
        ShopDropClientState.clear();
        playerPreview = null;
        hoveredSlot = null;
        feedbackAction = null;
        super.removed();
    }

    private void closeResultSubscription() {
        if (resultSubscription != null) {
            try {
                resultSubscription.close();
            } catch (Exception ignored) {}
            resultSubscription = null;
        }
    }

    private void sendShopAction(ItemType type, int index, ShopAction action) {
        ClientShopSlot slot = FPSMClient.getGlobalData().getSlotData(type.name(), index);
        if (!CSClientData.canOpenShop || slot.itemStack().isEmpty()) return;
        // Do not reject a purchase solely from the cached client balance. A money
        // update can arrive after the shop screen was built; the server validates
        // the current balance and sends the authoritative result.
        if (action == ShopAction.BUY && !canRequestBuy(slot)) return;
        if (action == ShopAction.RETURN && !slot.canReturn()) return;
        SlotRef ref = new SlotRef(type, index);
        if (actionProgress.isBusy(ref)) return;
        long id = NEXT_REQUEST_ID.incrementAndGet();
        PendingShopAction pending = new PendingShopAction(type, index, action, actionTick);
        pendingActions.put(id, pending);
        actionProgress.start(id, ref, slot.boughtCount(), action == ShopAction.BUY ? 1 : -1, actionTick);
        transmitShopAction(id, pending);
    }

    private void transmitShopAction(long id, PendingShopAction pending) {
        NetworkPacketRegister.getChannelFromCache(ShopActionC2SPacket.class).sendToServer(new ShopActionC2SPacket(
                id, FPSMClient.getGlobalData().getCurrentMap(), pending.type(), pending.index(), pending.action()));
    }

    private void handleShopActionResult(ShopActionResultS2CPacket packet) {
        PendingShopAction pending = pendingActions.get(packet.requestId());
        if (pending == null || !pending.matches(packet.type(), packet.index(), packet.action())) return;
        pendingActions.remove(packet.requestId());
        feedbackAction = pending;
        feedbackFailed = !packet.result().accepted();
        if (packet.result().accepted()) {
            feedbackTicks = 10;
            // Only an acknowledged transaction switches back to the live inventory priority.
            // A new hovered card re-enables inspection, including while purchase packets settle.
            preferInventoryPreview = true;
            if (packet.action() == ShopAction.BUY) playPurchaseSound(pending.type(), pending.index());
        } else if (Minecraft.getInstance().player != null) {
            actionProgress.rejected(packet.requestId());
            feedbackTicks = 8;
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("blockoffensive.shop.result." + packet.result().code().name().toLowerCase(Locale.ROOT)), true);
        }
    }

    private static void playPurchaseSound(ItemType type, int index) {
        ItemStack stack = FPSMClient.getGlobalData().getSlotData(type.name(), index).itemStack();
        if (GunCompatManager.isGun(stack)) {
            FPSMUtil.getGunTypeByGunId(GunCompatManager.findProvider(stack).getGunId(stack))
                    .ifPresent(gun -> FPSClientMusicManager.playSound(FPSMSoundRegister.getGunDropSound(gun)));
            return;
        }
        SoundEvent sound = FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(stack.getItem()) ? FPSMSoundRegister.getKnifeDropSound() : FPSMSoundRegister.getItemDropSound(stack.getItem());
        FPSClientMusicManager.playSound(sound);
    }

    private record SlotRef(ItemType type, int index) {}

    private record PendingShopAction(ItemType type, int index, ShopAction action, int sentTick) {

        boolean matches(String t, int i, ShopAction a) {
            return type.name().equals(t) && index == i && action == a;
        }
    }
}
