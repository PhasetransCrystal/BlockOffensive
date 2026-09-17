package net.ptcrys.blockoffensive.item;

import net.ptcrys.blockoffensive.entity.CompositionC4Entity;
import net.ptcrys.blockoffensive.event.CSGameMapEvent;
import net.ptcrys.blockoffensive.map.CSGameMap;
import net.ptcrys.blockoffensive.sound.BOSoundRegister;
import net.ptcrys.blockoffensive.util.BOUtil;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import net.ptcrys.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.item.BlastBombItem;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.map.BlastBombState;
import net.ptcrys.fpsmatch.core.team.ServerTeam;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = "blockoffensive", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CompositionC4 extends Item implements BlastBombItem {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());

        if (stack.getItem() instanceof CompositionC4) {
            event.setUseItem(Event.Result.ALLOW);
            event.setUseBlock(Event.Result.DENY);
        }
    }

    public CompositionC4(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {

            private static final HumanoidModel.ArmPose ITEM_C4 = HumanoidModel.ArmPose.create("ITEM_C4", true, (model, entity, arm) -> {
                float rotationAngle = (float) Math.toRadians(30);  // 将角度设置为 30 度（可以根据需要调整）
                // 右臂旋转
                if (arm == HumanoidArm.RIGHT) {
                    model.rightArm.xRot = -rotationAngle;  // 右臂旋转向中间
                    model.rightArm.yRot = -rotationAngle; // 右臂绕 Y 轴旋转
                }
                // 左臂旋转
                else {
                    model.leftArm.xRot = -rotationAngle;  // 左臂旋转向中间
                    model.leftArm.yRot = rotationAngle;   // 左臂绕 Y 轴旋转（相反方向）
                }
            });

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand, ItemStack itemStack) {
                if (!itemStack.isEmpty()) {
                    if (entityLiving.getUsedItemHand() == hand && entityLiving.getUseItemRemainingTicks() > 0) {
                        return ITEM_C4;
                    }
                }
                return HumanoidModel.ArmPose.EMPTY;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
                int i = arm == HumanoidArm.RIGHT ? 1 : -1;
                poseStack.translate(i * 0.36F, -0.52F, -0.72F);
                if (player.getUseItem() == itemInHand && player.isUsingItem()) {
                    poseStack.translate(0.0, -0.05, 0.0);
                }
                return true;
            }
        });
    }

    public void inventoryTick(@NotNull ItemStack pStack, @NotNull Level pLevel, @NotNull Entity pEntity, int pSlotId, boolean pIsSelected) {
        if (pLevel instanceof ServerLevel serverLevel && pEntity instanceof ServerPlayer player) {
            int i = player.getInventory().countItem(BOItemRegister.C4.get());
            if (i > 0) {
                double yawRad = Math.toRadians(player.getYRot());
                double distance = -0.5;
                double xOffset = -Math.sin(yawRad) * distance;
                double zOffset = Math.cos(yawRad) * distance;
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1, 0.1f, 0.1f), 1), player.getX() + xOffset, player.getY() + 1, player.getZ() + zOffset, 1, 0, 0, 0, 1);
            }
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        FPSMCore core = FPSMCore.getInstance();
        Optional<BaseMap> optional = core.getMapByPlayer(player);

        if (optional.isEmpty()) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.noMap"), true);
            return InteractionResultHolder.pass(stack);
        }
        BaseMap baseMap = optional.get();

        if (!(baseMap instanceof CSGameMap map)) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.noMap"), true);
            return InteractionResultHolder.pass(stack);
        }

        if (!baseMap.isStart()) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.map.notStart"), true);
            return InteractionResultHolder.pass(stack);
        }

        ServerTeam team = baseMap.getMapTeams().getTeamByPlayer(player).orElse(null);
        if (team == null) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.team.notInTeam"), true);
            return InteractionResultHolder.pass(stack);
        }

        boolean canPlace = map.checkCanPlacingBombs(team.getFixedName()) && map.blastState() == BlastBombState.NONE && player.onGround();
        boolean inBombArea = map.checkPlayerIsInBombArea(player);

        if (canPlace && inBombArea) {
            player.startUsingItem(hand);
            playClickSound(level, player, team);
            team.sendMessage(BOUtil.buildTeamChatMessage(player, team, Component.translatable("blockoffensive.place.message.c4"), Component.empty(), TextColor.parseColor(team.name.equals("ct") ? "#96C8FA" : "#EAC055")));
            return InteractionResultHolder.consume(stack);
        }

        if (!canPlace) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail"), true);
        } else if (map.getBombAreaData().isEmpty()) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.noArea"), true);
        } else {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.notInArea"), true);
        }
        return InteractionResultHolder.pass(stack);
    }

    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player == null) return InteractionResult.PASS;

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        InteractionResultHolder<ItemStack> result = this.use(level, player, context.getHand());

        if (result.getResult() == InteractionResult.CONSUME) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void playClickSound(Level level, LivingEntity entity, ServerTeam team) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                BOSoundRegister.CLICK.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
        team.getOnline().forEach(player -> {
            FPSMatch.sendToPlayer(player, new FPSMSoundPlayS2CPacket(BOSoundRegister.T_PLANTINGBOMB.get().getLocation()));
        });
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity,
                          @NotNull ItemStack stack, int remainingTicks) {
        if (!(level.isClientSide())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !entity.getUUID().equals(mc.player.getUUID())) return;
        // 禁用移动控制
        disableMovementKeys(mc);

        if (remainingTicks != 80 && remainingTicks % 8 == 0) {
            level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), BOSoundRegister.CLICK.get(), SoundSource.PLAYERS, 3.0F, 1.0F, false);
        }
    }

    private void disableMovementKeys(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
                                              @NotNull LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return stack;

        FPSMCore core = FPSMCore.getInstance();
        Optional<BaseMap> optional = core.getMapByPlayer(player);

        if (optional.isEmpty()) return stack;
        BaseMap baseMap = optional.get();

        if (!(baseMap instanceof CSGameMap map)) return stack;

        if (!map.checkPlayerIsInBombArea(player)) {
            player.displayClientMessage(Component.translatable("blockoffensive.item.c4.use.fail.notInArea"), true);
            return stack;
        }

        // 放置C4实体
        CompositionC4Entity c4 = new CompositionC4Entity(
                level, player.getX(), player.getY() + 0.25, player.getZ(), player, map);
        level.addFreshEntity(c4);
        map.recordBombPlanted(player);

        // 播放放置音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                BOSoundRegister.PLANTED.get(), SoundSource.PLAYERS, 3.0F, 1.0F);

        // 经济奖励
        baseMap.getMapTeams().getTeamByPlayer(player)
                .flatMap(team -> team.getCapabilityMap().get(ShopCapability.class)
                        .flatMap(ShopCapability::getShopSafe))
                .ifPresent(shop -> {
                    shop.getPlayerShopData(player.getUUID()).addMoney(300);
                });

        // 通知所有玩家
        Component message = Component.translatable("blockoffensive.item.c4.planted").withStyle(ChatFormatting.RED);
        baseMap.getMapTeams().getJoinedPlayers().forEach(data -> data.getPlayer().ifPresent(p -> p.displayClientMessage(message, false)));

        map.getMapTeams().getTeamByPlayer(player).ifPresent(team -> {
            MinecraftForge.EVENT_BUS.post(new CSGameMapEvent.PlayerEvent.PlacedC4Event(map, team, player, c4));
        });

        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 80;
    }
}
