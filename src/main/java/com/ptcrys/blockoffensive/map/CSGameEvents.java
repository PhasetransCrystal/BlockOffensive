package com.ptcrys.blockoffensive.map;

import com.ptcrys.blockoffensive.event.CSGameMapEvent;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.item.BombDisposalKit;
import com.ptcrys.blockoffensive.item.CompositionC4;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.common.event.FPSMapEvent;
import com.ptcrys.fpsmatch.common.event.PlayerObtainItemEvent;
import com.ptcrys.fpsmatch.common.event.FPSMGunShootEvent;
import com.ptcrys.fpsmatch.common.event.FPSMGunReloadEvent;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CSGameEvents {
    private static final Map<UUID, PendingMagazineReload> pendingMagazineReloads = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        PendingMagazineReload pending = pendingMagazineReloads.get(player.getUUID());
        if (pending == null) return;
        if (!player.getMainHandItem().equals(pending.stack()) && !player.getOffhandItem().equals(pending.stack())) {
            cancelMagazineReload(player.getUUID());
            return;
        }
        if (player.tickCount - pending.startedTick() >= 20) {
            commitMagazineReload(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(FPSMapEvent.PlayerEvent.HurtEvent event) {
        BaseMap map = event.getMap();

        if(!(map instanceof CSMap cs)) return;
        Optional<ServerPlayer> opt = event.getMap().getAttackerFromDamageSource(event.getSource());
        boolean isTeammate = opt.map(attacker -> cs.getMapTeams().isSameTeam(event.getPlayer(), attacker)).orElse(false);

        if (cs instanceof CSDeathMatchMap dm){
            if(dm.isInSpawnProtection(event.getPlayer().getUUID())){
                event.setCanceled(true);
            }else{
               if(dm.isTDM() && isTeammate){
                   event.setCanceled(true);
               }
            }
        }else{
            if(isTeammate){
                if (isC4Kill(event.getSource())) {
                    return;
                }
                if(cs.allowFriendlyFire()){
                    event.setAmount(event.getAmount() * 0.3F);
                    cs.handleTeammateAttack(opt.get(),event.getPlayer());
                }else{
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKillRecord(FPSMapEvent.PlayerEvent.KillRecordEvent event) {
        if (event.getMap() instanceof CSMap && isC4Kill(event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTeamKillPenalty(FPSMapEvent.PlayerEvent.KillEvent event) {
        if (!(event.getMap() instanceof CSMap cs)) return;

        ServerPlayer killer = event.getPlayer();
        ServerPlayer dead = event.getDead();
        boolean teammateKill = cs.getMapTeams().isSameTeam(killer, dead) && !isC4Kill(event.getSource());
        if (cs instanceof CSDeathMatchMap dm) {
            if (dm.isTDM() && teammateKill) {
                return;
            }

            // CS2 死斗计分：击杀 +100，爆头击杀额外 +50（合计 150）
            // 注：TDM 友伤已在 HurtEvent 屏蔽、FFA 各玩家独立队伍，teammateKill 此处实际不可达
            cs.getMapTeams().getPlayerData(killer).ifPresent(data ->
                    data.addScore(event.isHeadshot() ? 150 : 100)
            );
            return;
        }

        if (teammateKill) {
            cs.getMapTeams().getPlayerData(killer).ifPresent(PlayerData::removeKill);
        }
    }

    private static boolean isC4Kill(DamageSource source) {
        return source.getDirectEntity() instanceof CompositionC4Entity;
    }

    @SubscribeEvent
    public static void onPlayerShoot(FPSMGunShootEvent event) {
        if (event.getShooter().level().isClientSide()) return;

        if(event.getShooter() instanceof Player player) {
            FPSMCore.getInstance().getMapByPlayer(player)
                    .map(map->{
                        if(map instanceof CSDeathMatchMap dm){
                            return dm;
                        }
                        return null;
                    }).ifPresent(dm-> dm.handlePlayerFire(player.getUUID()));
        }
    }

    // 在登出时自动清理身上的C4和物品
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(FPSMapEvent.PlayerEvent.LoggedOutEvent event){
        if(event.getMap() instanceof CSMap){
            ServerPlayer player = event.getPlayer();
            ItemEntity dropped = CSMap.dropC4(player);
            player.getInventory().clearContent();
            BulletproofArmorAttribute.removePlayer(player);
            event.setCanceled(true);
        }
    }

    //处理地图命令
    @SubscribeEvent
    public static void onChat(FPSMapEvent.PlayerEvent.ChatEvent event){
        if(event.getMap() instanceof CSMap csGameMap){
            String[] m = event.getMessage().split("\\.");
            if(m.length > 1){
                csGameMap.handleChatCommand(m[1],event.getPlayer());
            }
        }
    }

    //控制地图物品掉落
    @SubscribeEvent
    public static void onPlayerDropItem(FPSMapEvent.PlayerEvent.TossItemEvent event){
        ServerPlayer player = event.getPlayer();
        ItemStack itemStack = event.getItemEntity().getItem();
        BaseMap map = event.getMap();
        if (map instanceof CSMap cs){
            if( cs instanceof CSDeathMatchMap){
                event.setCanceled(true);
            }

            if(itemStack.getItem() instanceof BombDisposalKit){
                event.setCanceled(true);
                event.getPlayer().getInventory().add(new ItemStack(BOItemRegister.BOMB_DISPOSAL_KIT.get(),1));
            }


            if(!event.isCanceled()){
                FPSMUtil.sortPlayerInventory(player);
            }
        }
    }

    /**
     * 队伍换边事件处理 - 移除所有玩家的防弹衣属性
     */
    @SubscribeEvent
    public static void onTeamSwitch(CSGameMapEvent.TeamSwitchEvent event) {
        event.getMap().getMapTeams().getJoinedPlayers().forEach(data ->
            data.getPlayer().ifPresent(BulletproofArmorAttribute::removePlayer)
        );
    }

    @SubscribeEvent
    public static void onGunReload(FPSMGunReloadEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        FPSMCore.getInstance().getMapByPlayer(player)
                .filter(map -> map instanceof CSGameMap)
                .map(map -> (CSGameMap) map)
                .filter(CSGameMap::isMagazineMode)
                .ifPresent(cs -> {
                    ItemStack stack = event.getGunItemStack();
                    if (stack != null && GunCompatManager.isGun(stack)) {
                        beginMagazineReload(player, stack);
                    }
                });
    }

    @SubscribeEvent
    public static void onPlayerObtainItem(PlayerObtainItemEvent event) {
        if (!(event.getMap() instanceof CSGameMap cs) || !cs.isMagazineMode()) return;
        ItemStack stack = event.getItemStack();
        if (GunCompatManager.isGun(stack)) {
            applyMagazineObtainAmmo(stack);
        }
    }

    private static void applyMagazineReload(ItemStack stack) {
        int dummyAmmo = GunCompatManager.findProvider(stack).getDummyAmmo(stack);
        int maxAmmo = GunCompatManager.findProvider(stack).getGunData(stack)
                .map(data -> data.getAmmoAmount())
                .orElse(0);
        if (maxAmmo <= 0 || dummyAmmo < maxAmmo) return;

        int magazineCount = dummyAmmo / maxAmmo;
        GunCompatManager.findProvider(stack).setDummyAmmo(stack, (magazineCount - 1) * maxAmmo);
    }

    private static void beginMagazineReload(ServerPlayer player, ItemStack stack) {
        int before = GunCompatManager.findProvider(stack).getDummyAmmo(stack);
        pendingMagazineReloads.put(player.getUUID(), new PendingMagazineReload(stack.copy(), before, player.tickCount));
    }

    public static void commitMagazineReload(UUID playerId) {
        PendingMagazineReload pending = pendingMagazineReloads.remove(playerId);
        if (pending != null) applyMagazineReload(pending.stack());
    }

    public static void cancelMagazineReload(UUID playerId) {
        pendingMagazineReloads.remove(playerId);
    }

    private record PendingMagazineReload(ItemStack stack, int previousAmmo, int startedTick) {
    }

    private static void applyMagazineObtainAmmo(ItemStack stack) {
        int dummyAmmo = GunCompatManager.findProvider(stack).getDummyAmmo(stack);
        if (dummyAmmo <= 0) return;

        int maxAmmo = GunCompatManager.findProvider(stack).getGunData(stack)
                .map(data -> data.getAmmoAmount())
                .orElse(0);
        if (maxAmmo <= 0) return;

        int magazineCount = Math.round((float) dummyAmmo / maxAmmo);
        GunCompatManager.findProvider(stack).setDummyAmmo(stack, magazineCount * maxAmmo);
    }


    @SubscribeEvent
    public static void onPlacedC4(CSGameMapEvent.PlayerEvent.PlacedC4Event event) {
    }

}
