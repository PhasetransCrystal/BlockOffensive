package com.ptcrys.blockoffensive.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class C4Renderer implements EntityRendererProvider<CompositionC4Entity> {

    @Override
    public @NotNull EntityRenderer<CompositionC4Entity> create(@NotNull Context pContext) {
        return new EntityRenderer<>(pContext) {
            ItemEntity item = null;
            ItemEntityRenderer itemRender = null;
            @Override
            public @NotNull ResourceLocation getTextureLocation(@NotNull CompositionC4Entity pEntity) {
                return InventoryMenu.BLOCK_ATLAS;
            }

            @Override
            public void render(@NotNull CompositionC4Entity pEntity, float pEntityYaw, float pPartialTicks, @NotNull PoseStack pPoseStack, @NotNull MultiBufferSource pBuffer, int pPackedLight) {
                pPoseStack.pushPose();
                if(item == null){
                    Item c4 = BOItemRegister.C4.get();  // 获取 C4 物品对象
                    item = new ItemEntity(pEntity.level(), pEntity.getX(), pEntity.getY(), pEntity.getZ(), new ItemStack(c4));
                    itemRender = new ItemEntityRenderer(pContext);
                }
                itemRender.render(item,pEntityYaw,0,pPoseStack,pBuffer,pPackedLight);
                pPoseStack.popPose();
                super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
            }

        };
    }
}