package com.ptcrys.blockoffensive.util;

import com.ptcrys.blockoffensive.data.DeathMessage;
import com.ptcrys.blockoffensive.data.DeathMessageRules;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.map.team.capability.ColoredPlayerCapability;
import com.ptcrys.blockoffensive.net.DeathMessageS2CPacket;
import com.ptcrys.blockoffensive.sound.BOSoundRegister;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.ptcrys.fpsmatch.common.drop.ThrowableRegistry;
import com.ptcrys.fpsmatch.common.drop.ThrowableSubType;
import com.ptcrys.fpsmatch.common.packet.FPSMSoundPlayC2SPacket;
import com.ptcrys.fpsmatch.compat.CounterStrikeGrenadesCompat;
import com.ptcrys.fpsmatch.compat.LrtacticalCompat;
import com.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import com.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.ptcrys.fpsmatch.util.RenderUtil.color;

public class BOUtil {
    public static int CT_COLOR = color(182, 210, 240);
    public static int T_COLOR = color(253,217,141);

    /**
     * 秒数格式化为 MM:SS（分/秒两位补零），如 60 -> "01:00"。
     * 供 HUD 倒计时共用，避免各处重复实现同样的格式化。
     */
    public static String formatMinutesSeconds(int totalSeconds) {
        totalSeconds = Math.max(0, totalSeconds);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static final Map<Item, ThrowableType> throwables = new ConcurrentHashMap<>();

    public static void registerThrowable(ThrowableType type, Item item) {
        throwables.put(item, type);
    }

    @Nullable
    public static SoundEvent getVoiceByThrowType(ThrowableSubType subType){
        if(subType == null) return null;

        boolean isCT = FPSMClient.getGlobalData().isCurrentTeam("ct");

        if(ThrowableRegistry.GRENADE.equals(subType)){
            return isCT ? BOSoundRegister.THROWABLE_GRENADE_CT_THROW.get() : BOSoundRegister.THROWABLE_GRENADE_T_THROW.get();
        }

        if(ThrowableRegistry.MOLOTOV.equals(subType)){
            return isCT ? BOSoundRegister.THROWABLE_MOLOTOV_CT_THROW.get() : BOSoundRegister.THROWABLE_MOLOTOV_T_THROW.get();
        }

        if(ThrowableRegistry.SMOKE.equals(subType)){
            return isCT ? BOSoundRegister.THROWABLE_SMOKE_CT_THROW.get() : BOSoundRegister.THROWABLE_SMOKE_T_THROW.get();
        }

        if(ThrowableRegistry.FLASH_BANG.equals(subType)){
            return isCT ? BOSoundRegister.THROWABLE_FLASHBANG_CT_THROW.get() : BOSoundRegister.THROWABLE_FLASHBANG_T_THROW.get();
        }

        if(ThrowableRegistry.DECOY.equals(subType)){
            return isCT ? BOSoundRegister.THROWABLE_DECOY_CT_THROW.get() : BOSoundRegister.THROWABLE_DECOY_T_THROW.get();
        }

        return null;
    }

    public static ThrowableType getThrowableType(Item item) {
        if (item != null && throwables.containsKey(item)) {
            return throwables.get(item);
        } else {
            return ThrowableType.UNKNOWN;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static MutableComponent buildTeamChatMessage(MutableComponent message) {
        return buildTeamChatMessage(message, Component.empty());
    }

    public static int getTeamColor(UUID uuid){
        return FPSMClient.getGlobalData().getTeamByUUID(uuid)
                .map(team -> team.name.equals("ct") ? CT_COLOR :  T_COLOR).orElse(RenderUtil.WHITE);
    }

    public static int getColor(UUID uuid){
       return FPSMClient.getGlobalData().getTeamByUUID(uuid)
               .map(team-> team.getCapabilityMap().get(ColoredPlayerCapability.class)
                       .map(cap-> {
                           TeamPlayerColor color = cap.getColor(uuid);
                           return color == null ? RenderUtil.WHITE :  color.getRGBA();
                       }).orElse(RenderUtil.WHITE))
               .orElse(RenderUtil.WHITE);
    }

    public static MutableComponent buildTeamChatMessage(Player player, BaseTeam team, MutableComponent message, MutableComponent location, TextColor textColor){
        if (player == null) return message;

        MutableComponent head = Component.literal("[" + team.name.toUpperCase(Locale.US) + "]")
                .withStyle(Style.EMPTY.withColor(textColor));

        MutableComponent teamColor = Component.literal(" • ");

        team.getCapabilityMap().get(ColoredPlayerCapability.class).ifPresent(cap -> {
            TeamPlayerColor c = cap.getColor(player.getUUID());
            if (c != null) {
                teamColor.withStyle(Style.EMPTY.withColor(TextColor.parseColor(c.getHex())));
            }
        });

        MutableComponent playerName = ((MutableComponent) player.getName()).withStyle(Style.EMPTY.withColor(textColor));

        return head.append(teamColor)
                .append(playerName)
                .append(location.withStyle(ChatFormatting.GREEN))
                .append(Component.literal(": "))
                .append(message)
                .withStyle(ChatFormatting.BOLD);
    }

    @OnlyIn(Dist.CLIENT)
    public static MutableComponent buildTeamChatMessage(MutableComponent message, MutableComponent location) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return message;

        return FPSMClient.getGlobalData().getCurrentClientTeam().map(team -> {
            TextColor textColor = TextColor.parseColor(team.name.equals("ct") ? "#96C8FA" : "#EAC055");

            MutableComponent head = Component.literal("[" + team.name.toUpperCase(Locale.US) + "]")
                    .withStyle(Style.EMPTY.withColor(textColor));

            MutableComponent teamColor = Component.literal(" • ");

            team.getCapabilityMap().get(ColoredPlayerCapability.class).ifPresent(cap -> {
                TeamPlayerColor c = cap.getColor(player.getUUID());
                if (c != null) {
                    teamColor.withStyle(Style.EMPTY.withColor(TextColor.parseColor(c.getHex())));
                }
            });

            MutableComponent playerName = ((MutableComponent) player.getName()).withStyle(Style.EMPTY.withColor(textColor));

            return head.append(teamColor)
                    .append(playerName)
                    .append(location.withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(": "))
                    .append(message)
                    .withStyle(ChatFormatting.BOLD);

        }).orElse(message);
    }

    /**
     * 从击杀者和伤害源中解析导致死亡的物品栈
     *
     * @param attacker 击杀者
     * @param source   伤害源
     * @return 致死物品栈（非空，默认返回主手物品）
     */
    public static ItemStack getDeathItemStack(ServerPlayer attacker, DamageSource source) {
        if (FPSMImpl.findLrtacticalMod()) {
            ItemStack projectileItem = LrtacticalCompat.getProjectileItem(source);
            if (!projectileItem.isEmpty()) {
                return projectileItem;
            }
        }

        if (FPSMImpl.findCounterStrikeGrenadesMod()) {
            ItemStack grenade = CounterStrikeGrenadesCompat.getItemFromDamageSource(source);
            if (!grenade.isEmpty()) {
                return grenade;
            }
        }

        if(source.getEntity() instanceof CompositionC4Entity) {
            return BOItemRegister.C4.get().getDefaultInstance();
        }

        if(source.getDirectEntity() instanceof CompositionC4Entity){
            return BOItemRegister.C4.get().getDefaultInstance();
        }

        return attacker == null ? ItemStack.EMPTY : attacker.getMainHandItem();
    }

    /**
     * 计算助攻玩家（符合伤害阈值的首个助攻者）
     *
     * @param deadPlayer 死亡玩家
     * @return 助攻玩家数据（可能为空）
     */
    public static Optional<PlayerData> calculateAssistPlayer(BaseMap map, ServerPlayer deadPlayer, float minAssistDamageRatio) {
        MapTeams mapTeams = map.getMapTeams();

        if (mapTeams.getTeamByPlayer(deadPlayer).isEmpty()) return Optional.empty();

        Map<UUID, Float> hurtDataMap = mapTeams.getDamageMap().getOrDefault(deadPlayer.getUUID(), null);
        if (hurtDataMap == null || hurtDataMap.isEmpty()) {
            return Optional.empty();
        }

        float minAssistDamage = deadPlayer.getMaxHealth() * minAssistDamageRatio;
        return hurtDataMap.entrySet().stream()
                .filter(entry -> entry.getValue() > minAssistDamage)
                .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                .limit(1)
                .findAny()
                .flatMap(entry -> mapTeams.getTeamByPlayer(entry.getKey())
                        .flatMap(team -> team.getPlayerData(entry.getKey())));
    }

    public static boolean resolveNoScopeFlag(ItemStack weapon, boolean isScopedKill, boolean attackerIsDeadPlayer) {
        boolean isSniper = GunCompatManager.isGun(weapon)
                && GunCompatManager.findProvider(weapon).getGunTabType(weapon) == GunTabTypeEnum.SNIPER;
        return DeathMessageRules.resolveNoScopeFlag(isSniper, isScopedKill, attackerIsDeadPlayer);
    }


    /**
     * 构建死亡消息（包含击杀、助攻、爆头信息）
     *
     * @param map        地图
     * @param attacker   击杀者
     * @param deadPlayer 死亡玩家
     * @param deathItem  致死物品
     * @param isHeadShot 是否爆头
     * @return 死亡消息数据包
     */
    public static DeathMessageS2CPacket buildDeathMessagePacket(BaseMap map, ServerPlayer attacker, ServerPlayer deadPlayer,
                                                                ItemStack deathItem, boolean isHeadShot, boolean isPassWall,
                                                                boolean isPassSmoke, boolean isScopedKill, float minAssistDamageRatio) {

        DeathMessage.Builder builder = new DeathMessage.Builder(attacker, deadPlayer, deathItem);

        // Special kill flags come from DeathContext and must survive attacker fallback.
        builder.setHeadShot(isHeadShot);
        builder.setThroughWall(isPassWall);
        builder.setThroughSmoke(isPassSmoke);

        if(!attacker.is(deadPlayer)) {
            builder.setFlying(!attacker.equals(deadPlayer) && !attacker.onGround());
            builder.setNoScope(resolveNoScopeFlag(deathItem, isScopedKill, attacker.is(deadPlayer)));

            calculateAssistPlayer(map, deadPlayer, minAssistDamageRatio).ifPresent(assistData -> {
                if (!attacker.getUUID().equals(assistData.getOwner())) {
                    builder.setAssist(assistData.name(), assistData.getOwner());
                }
            });
        }

        return new DeathMessageS2CPacket(builder.build());
    }

    public static void buildGrenadeMessageAndSend(ItemStack itemStack) {
        ThrowableType type = BOUtil.getThrowableType(itemStack.getItem());
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        if(type != ThrowableType.UNKNOWN && data.isInNormalTeam()){
            data.getCurrentClientTeam().ifPresent(team->{
                team.sendMessage(BOUtil.buildTeamChatMessage(type.getChat()));
            });
        }

        SoundEvent sound = BOUtil.getVoiceByThrowType(ThrowableRegistry.getThrowableSubType(itemStack.getItem()));
        if(sound != null){
            FPSMatch.sendToServer(new FPSMSoundPlayC2SPacket(sound.getLocation(),true));
        }
    }
}
