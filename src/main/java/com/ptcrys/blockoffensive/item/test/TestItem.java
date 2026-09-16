package com.ptcrys.blockoffensive.item.test;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class TestItem extends Item {
    public TestItem(Properties pProperties) {
        super(pProperties);
    }
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, @NotNull Player pPlayer, @NotNull InteractionHand pUsedHand) {
        if(pLevel.isClientSide){
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> ClientAccess::openShop);
        }
        return super.use(pLevel,pPlayer,pUsedHand);
    }

    private static final class ClientAccess {
        private static void openShop() {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    com.ptcrys.blockoffensive.client.screen.CSGameShopScreen.getInstance());
        }
    }
}
