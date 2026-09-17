package net.ptcrys.blockoffensive.client.shop;

import net.ptcrys.fpsmatch.compat.LrtacticalCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/** Detached, render-only player. Never added to a level or ticked through game logic. */
public final class ShopPlayerPreview extends RemotePlayer {

    private final AbstractClientPlayer source;

    public ShopPlayerPreview(AbstractClientPlayer source) {
        // Separate identity also isolates gun/animation caches from the live player's UUID.
        super(source.clientLevel, new GameProfile(UUID.randomUUID(), source.getGameProfile().getName()));
        this.source = source;
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        return source.getSkinTextureLocation();
    }

    @Override
    public String getModelName() {
        return source.getModelName();
    }

    @Override
    public ResourceLocation getCloakTextureLocation() {
        return null;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return source.isModelPartShown(part);
    }

    @Override
    public HumanoidArm getMainArm() {
        return source.getMainArm();
    }

    public static ShopPreviewSelection.Category category(ItemStack stack) {
        if (stack.isEmpty()) return ShopPreviewSelection.Category.EMPTY;
        if (GunCompatManager.isGun(stack)) {
            return GunCompatManager.findProvider(stack).getGunTabType(stack) == GunTabTypeEnum.PISTOL ? ShopPreviewSelection.Category.PISTOL : ShopPreviewSelection.Category.PRIMARY;
        }
        if (stack.getItem() instanceof SwordItem || FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(stack)) {
            return ShopPreviewSelection.Category.KNIFE;
        }
        return ShopPreviewSelection.Category.UTILITY;
    }

    public void render(GuiGraphics graphics, ItemStack held, int x, int y, int scale) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack desired = slot == EquipmentSlot.MAINHAND ? held : slot == EquipmentSlot.OFFHAND ? ItemStack.EMPTY : source.getItemBySlot(slot);
            if (!ItemStack.matches(getItemBySlot(slot), desired)) setItemSlot(slot, desired.copy());
        }
        tickCount = source.tickCount;
        // TaCZ treats this as a third-person entity, even while the live camera is first person.
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, scale, 0.9f, 0, this);
    }

    /** Called after player animation, before sleeves and held-item layers are rendered. */
    public void applyPresentationPose(PlayerModel<?> model) {
        ItemStack stack = getMainHandItem();
        if (stack.isEmpty()) return;
        boolean right = getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart other = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        var kind = category(stack);
        // Keep weapon/item-provided poses (TaCZ third-person animation, C4, shields, etc.).
        boolean nativeGun = "tacz".equals(GunCompatManager.findProvider(stack).getModId());
        if (!nativeGun && IClientItemExtensions.of(stack).getArmPose(this, InteractionHand.MAIN_HAND, stack) == null) {
            if (kind == ShopPreviewSelection.Category.PRIMARY || kind == ShopPreviewSelection.Category.PISTOL) {
                main.xRot = -1.3f;
                main.yRot = -0.25f * side;
                other.xRot = -1.35f;
                other.yRot = 0.6f * side;
            } else {
                main.xRot = kind == ShopPreviewSelection.Category.KNIFE ? -1.05f : -0.9f;
                main.yRot = -0.18f * side;
                other.xRot = -0.25f;
            }
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
}
