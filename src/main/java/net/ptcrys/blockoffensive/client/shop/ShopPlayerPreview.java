package net.ptcrys.blockoffensive.client.shop;

import net.ptcrys.fpsmatch.compat.LrtacticalCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/** Detached, render-only player. Never added to a level or ticked through game logic. */
public final class ShopPlayerPreview extends RemotePlayer {

    private final AbstractClientPlayer source;

    public ShopPlayerPreview(AbstractClientPlayer source) {
        // Separate identity also isolates gun/animation caches from the live player's UUID.
        super(source.clientLevel, new GameProfile(UUID.randomUUID(), source.getGameProfile().getName()));
        this.source = source;
        clearPlayerAnimatorLayers();
    }

    /** PlayerAnimator is optional; remove its factory-created layers from this static, unticked preview only. */
    private void clearPlayerAnimatorLayers() {
        try {
            Class<?> playerInterface = Class.forName("dev.kosmx.playerAnim.api.IPlayer");
            Method getAnimationStack = playerInterface.getMethod("getAnimationStack");
            Object animationStack = getAnimationStack.invoke(this);
            Field layersField = animationStack.getClass().getDeclaredField("layers");
            layersField.setAccessible(true);
            Object layers = layersField.get(animationStack);
            if (layers instanceof Collection<?> collection) {
                collection.clear();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // PlayerAnimator is optional, and its internals may differ between supported versions.
        }
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
    public void applyPresentationPose(PlayerModel<?> model, float age) {
        ItemStack stack = getMainHandItem();
        if (stack.isEmpty()) return;
        boolean right = getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart other = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        var kind = category(stack);
        boolean gun = kind == ShopPreviewSelection.Category.PRIMARY || kind == ShopPreviewSelection.Category.PISTOL;
        HumanoidModel.ArmPose itemPose = IClientItemExtensions.of(stack)
                .getArmPose(this, InteractionHand.MAIN_HAND, stack);
        // Generic ITEM/EMPTY poses carry no useful weapon animation. TaCZ's preview is
        // intentionally detached from its live animation manager, so provide a stable stance.
        boolean usePreviewPose = itemPose == null || itemPose == HumanoidModel.ArmPose.EMPTY || (gun && itemPose == HumanoidModel.ArmPose.ITEM);
        if (usePreviewPose && gun) {
            float idle = (float) Math.sin(age * 0.08F) * 0.025F;
            main.xRot = -1.3F + idle;
            main.yRot = -0.25F * side;
            main.zRot = 0.035F * side;
            other.xRot = -1.35F + idle * 0.7F;
            other.yRot = 0.6F * side;
            other.zRot = -0.025F * side;
        } else if (usePreviewPose) {
            main.xRot = kind == ShopPreviewSelection.Category.KNIFE ? -1.05F : -0.9F;
            main.yRot = -0.18F * side;
            main.zRot = 0;
            other.xRot = -0.25F;
            other.yRot = 0;
            other.zRot = 0;
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
}
