package com.ptcrys.blockoffensive.client.shop;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Fits weapon HUD artwork without stretching long rifles into square inventory icons. */
public final class ShopItemArtwork {
    private final Map<ResourceLocation, Size> sizes = new HashMap<>();
    private record Size(int textureWidth, int textureHeight, int left, int top, int width, int height) {
        private static final Size MISSING = new Size(0, 0, 0, 0, 0, 0);
    }

    public void clear() { sizes.clear(); }

    public static String texture(ItemStack stack, ResourceLocation configured) {
        if (configured != null) return configured.toString();
        if (GunCompatManager.isGun(stack)) {
            ResourceLocation hud = GunCompatManager.findProvider(stack).getGunHUDTexture(stack);
            if (hud != null) return hud.toString();
        }
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (item != null && item.getNamespace().equals("fpsmatch")) {
            return switch (item.getPath()) {
                case "flash_bomb", "smoke_shell", "grenade", "ct_incendiary_grenade", "t_incendiary_grenade" ->
                        "blockoffensive:textures/ui/cs/message/" + item.getPath() + ".png";
                default -> "";
            };
        }
        return "";
    }

    public boolean render(GuiGraphics graphics, String texture, int color, float x, float y, float width, float height) {
        if (texture == null || texture.isBlank()) return false;
        ResourceLocation resource = ResourceLocation.tryParse(texture);
        if (resource == null) return false;
        Size size = sizes.computeIfAbsent(resource, id -> {
            var asset = Minecraft.getInstance().getResourceManager().getResource(id);
            if (asset.isEmpty()) return Size.MISSING;
            try (var stream = asset.get().open(); var image = NativeImage.read(stream)) {
                int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
                for (int py = 0; py < image.getHeight(); py++) for (int px = 0; px < image.getWidth(); px++) {
                    if ((image.getPixelRGBA(px, py) >>> 24) < 16) continue;
                    left = Math.min(left, px); top = Math.min(top, py);
                    right = Math.max(right, px); bottom = Math.max(bottom, py);
                }
                return right < left ? Size.MISSING : new Size(image.getWidth(), image.getHeight(),
                        left, top, right - left + 1, bottom - top + 1);
            } catch (IOException exception) {
                return Size.MISSING;
            }
        });
        if (size.width == 0 || size.height == 0) return false;
        float fit = Math.min(width / size.width, height / size.height);
        float w = size.width * fit, h = size.height * fit;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + (width - w) / 2, y + (height - h) / 2, 0);
            graphics.pose().scale(w, h, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
            graphics.blit(resource, 0, 0, 1, 1, size.left, size.top, size.width, size.height, size.textureWidth, size.textureHeight);
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.pose().popPose();
        }
        return true;
    }
}
