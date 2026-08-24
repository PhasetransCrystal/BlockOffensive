package com.ptcrys.blockoffensive.map;

import com.ptcrys.blockoffensive.BOConfig;
import com.ptcrys.blockoffensive.client.data.WeaponData;
import com.ptcrys.blockoffensive.command.BOTaczLiveFireDebugCommand;
import com.ptcrys.blockoffensive.compat.BOImpl;
import com.ptcrys.blockoffensive.compat.CSGrenadeCompat;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.item.CompositionC4;
import com.ptcrys.blockoffensive.net.CSGameSettingsS2CPacket;
import com.ptcrys.blockoffensive.net.DeathMessageS2CPacket;
import com.ptcrys.blockoffensive.net.PxDeathCompatS2CPacket;
import com.ptcrys.blockoffensive.net.PxRagdollRemovalCompatS2CPacket;
import com.ptcrys.blockoffensive.net.shop.ShopStatesS2CPacket;
import com.ptcrys.blockoffensive.net.spec.CSGameWeaponDataS2CPacket;
import com.ptcrys.blockoffensive.sound.BOSoundRegister;
import com.ptcrys.blockoffensive.spectator.BOSpecManager;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.common.capability.team.StartKitsCapability;
import com.ptcrys.fpsmatch.common.drop.DropType;
import com.ptcrys.fpsmatch.common.entity.MatchDropEntity;
import com.ptcrys.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.ptcrys.fpsmatch.compat.LrtacticalCompat;
import com.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.capability.CapabilityMap;
import com.ptcrys.fpsmatch.core.capability.map.MapCapability;
import com.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.data.Setting;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.map.BaseRoundMap;
import com.ptcrys.fpsmatch.core.map.DeathContext;
import com.ptcrys.fpsmatch.core.map.VoteObj;
import com.ptcrys.fpsmatch.core.shop.FPSMShop;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import com.ptcrys.fpsmatch.core.team.TeamData;
import com.ptcrys.fpsmatch.util.FPSMUtil;
import com.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class CSMap extends BaseRoundMap<String, CSRoundResultReason> {

    private static final Vector3f T_COLOR = new Vector3f(1, 0.75f, 0.25f);
    private static final Vector3f CT_COLOR = new Vector3f(0.25f, 0.55f, 1);

    private final Map<String, CSCommand> commands = new ConcurrentHashMap<>();
    protected final Setting<Boolean> allowFriendlyFire = this.addSetting("team", "allowFriendlyFire", false);
    protected final Setting<Boolean> allowSpecAttach = this.addSetting("player", "allowSpecAttach", true);

    /**
     * 弹夹模式：启用后枪械弹药以整弹夹为单位进行管理。
     * <ul>
     *   <li><b>武器获得时：</b>在 {@code fixGunItem} 之后，备弹量（dummy ammo）会被对齐为最大载弹量（maxAmmo）的整数倍（四舍五入），
     *       以确保备弹始终可以被最大载弹量整除，从而正确计算弹夹数量。</li>
     *   <li><b>换弹时：</b>通过 {@link com.tacz.guns.api.event.common.GunReloadEvent} 拦截换弹逻辑。
     *       换弹后当前弹夹内剩余弹药全部丢弃，备弹中扣除一个满弹夹的弹药量填充至当前弹夹。
     *       计算公式：弹夹数 = 备弹量 / 最大载弹量，新备弹量 = (弹夹数 - 1) * 最大载弹量。</li>
     * </ul>
     * 默认为 {@code false}，即保持 TACZ 原版弹药逻辑。
     */
    protected final Setting<Boolean> magazineMode = this.addSetting("player", "magazineMode", false);

    protected final Setting<Integer> knifeKillEconomy = this.addSetting("eco", "knifeKillEconomy", 1500);
    protected final Setting<Integer> smgKillEconomy = this.addSetting("eco", "smgKillEconomy", 600);
    protected final Setting<Integer> sniperKillEconomy = this.addSetting("eco", "sniperKillEconomy", 100);
    protected final Setting<Integer> shotgunKillEconomy = this.addSetting("eco", "shotgunKillEconomy", 900);
    protected final Setting<Integer> defaultKillEconomy = this.addSetting("eco", "defaultKillEconomy", 300);

    protected final Setting<Integer> ctLimit = this.addSetting("team", "ctLimit", 5);
    protected final Setting<Integer> tLimit = this.addSetting("team", "tLimit", 5);

    private final ServerTeam ctTeam;
    private final ServerTeam tTeam;

    protected VoteObj voteObj;

    private final Map<UUID, ShopStateSnapshot> lastShopStates = new HashMap<>();

    private final CSSpectatorRosterSync spectatorRosterSync = new CSSpectatorRosterSync();

    private record ShopStateSnapshot(boolean canOpenShop, int nextRoundMoney, int closeTime) {}

    public CSMap(ServerLevel serverLevel,
                 String mapName,
                 AreaData areaData,
                 List<Class<? extends MapCapability>> mapCapabilities,
                 List<Class<? extends TeamCapability>> teamCapabilities
    ) {
        this(serverLevel, mapName, areaData, mapCapabilities, teamCapabilities, teamCapabilities);
    }

    public CSMap(ServerLevel serverLevel,
                 String mapName,
                 AreaData areaData,
                 List<Class<? extends MapCapability>> capabilities ,
                 List<Class<? extends TeamCapability>> ctCapabilities,
                 List<Class<? extends TeamCapability>> tCapabilities
    ) {
        super(serverLevel, mapName, areaData, capabilities);
        this.setup();
        this.autoStart.set(true);
        this.autoStartTime.set(6000);
        this.loadConfig();
        this.ctTeam = this.addTeam(TeamData.of("ct",getCTLimit(), ctCapabilities));
        this.ctTeam.setColor(CT_COLOR);
        this.ctTeam.getPlayerTeam().setColor(ChatFormatting.BLUE);

        this.tTeam = this.addTeam(TeamData.of("t",getTLimit(), tCapabilities));
        this.tTeam.setColor(T_COLOR);
        this.tTeam.getPlayerTeam().setColor(ChatFormatting.YELLOW);
    }

    public int getCTLimit(){
        return this.ctLimit.get();
    }

    public int getTLimit(){
        return this.tLimit.get();
    }

    // Config
    public abstract void setup();

    //Shop
    public abstract boolean getPlayerCanOpenShop(ShopCapability cap, ServerPlayer player);

    @Override
    public boolean canUseShop(ShopCapability cap, ServerPlayer player) {
        return super.canUseShop(cap, player) && getPlayerCanOpenShop(cap, player);
    }

    public abstract int getNextRoundMinMoney(ServerTeam team);
    public abstract int getShopCloseTime();

    //Flags
    public abstract Team.Visibility nameTagVisibility();
    public boolean allowFriendlyFire(){
        return allowFriendlyFire.get();
    }

    public boolean isMagazineMode() {
        return magazineMode.get();
    }

    public abstract boolean isError();
    public abstract boolean isPause();
    public abstract boolean isWaiting();
    public abstract boolean isWaitingWinner();
    public abstract boolean canGiveEconomy();
    public abstract int getClientTime();

    @Override
    public void tick() {
        super.tick();
        this.voteLogic();
        this.rosterSyncLogic();
    }

    /**
     * 每秒同步一次观战者名单给观战者；仅在名单发生变化时才实际广播，降低带宽开销。
     */
    private void rosterSyncLogic() {
        spectatorRosterSync.tick(this);
    }

    @Override
    public boolean start(){
        if (!super.start()) {
            return false;
        }
        this.configureGameRules(this.getServerLevel());
        return true;
    }

    @Override
    protected boolean canAutoStart() {
        return !getCT().getOnlinePlayers().isEmpty() && !getT().getOnlinePlayers().isEmpty();
    }

    @Override
    protected boolean canReadyStart() {
        return allNormalOnlinePlayersReady();
    }

    @Override
    protected void startGameWithAnnouncement() {
        Component title = Component.translatable("blockoffensive.map.cs.auto.started")
                .withStyle(ChatFormatting.YELLOW);
        sendTitleToAllPlayers(title);
        start();
    }

    /**
     * 判断实体是否需要被清理
     */
    public boolean shouldDiscardEntity(Entity entity) {
        return entity instanceof ItemEntity
                || entity instanceof CompositionC4Entity
                || entity instanceof MatchDropEntity
                || entity instanceof EntityKineticBullet
                || (FPSMImpl.findCounterStrikeGrenadesMod() && CSGrenadeCompat.is(entity));
    }

    /**
     * 清理指定区域内的特定实体
     */
    public void cleanupSpecificEntities() {
        getServerLevel().getEntitiesOfClass(Entity.class, getMapArea().aabb())
                .stream()
                .filter(this::shouldDiscardEntity)
                .forEach(Entity::discard);
    }

    /**
     * 发送物理模组兼容包
     */
    public void sendPhysicsRagdollRemovalPacket(UUID uuid) {
        if (ModList.get().isLoaded("physicsmod")) {
            sendPacketToAllPlayer(new PxRagdollRemovalCompatS2CPacket(uuid));
        }
    }

    protected void sendPhysicsDeathPacket(ServerPlayer deadPlayer) {
        if (BOImpl.isPhysicsModLoaded()) {
            this.sendPacketToAllPlayer(new PxDeathCompatS2CPacket(deadPlayer.getId()));
        }
    }

    protected boolean shouldSendPhysicsDeathPacketInBaseDeathHandler(DeathContext context) {
        return true;
    }

    public void sendNewRoundVoice(){
        this.sendPacketToTeamPlayer(this.getCT(),new FPSMSoundPlayS2CPacket(BOSoundRegister.CT_ROUNDSTART.get().getLocation()),false);
        this.sendPacketToTeamPlayer(this.getT(),new FPSMSoundPlayS2CPacket(BOSoundRegister.T_ROUNDSTART.get().getLocation()),false);
    }

    public void sendRoundDamageMessage(){
        MapTeams mapTeams = this.getMapTeams();
        ServerTeam ctTeam = this.getCT();
        ServerTeam tTeam = this.getT();

        // 处理CT队伍玩家的伤害信息
        processTeamDamageInfo(ctTeam, tTeam, mapTeams);
        // 处理T队伍玩家的伤害信息
        processTeamDamageInfo(tTeam, ctTeam, mapTeams);
    }

    private void processTeamDamageInfo(ServerTeam current,ServerTeam target, MapTeams mapTeams) {
        List<PlayerData> currentPlayers = current.getPlayersData();
        List<PlayerData> targetPlayers = target.getPlayersData();

        Map<UUID, Float> remainHp = mapTeams.getRemainHealth();

        for (PlayerData c : currentPlayers) {
            Optional<ServerPlayer> playerOpt = c.getPlayer();
            UUID owner = c.getOwner();
            if (playerOpt.isEmpty()) continue;

            ServerPlayer player = playerOpt.get();
            Map<UUID, PlayerData.Damage> damageMap = c.getDamageData();

            for (PlayerData t : targetPlayers) {
                UUID targetId = t.getOwner();
                PlayerData.Damage damageData = damageMap.getOrDefault(targetId,new PlayerData.Damage());
                PlayerData.Damage receivedDamage = t.getDamageData().getOrDefault(owner,new PlayerData.Damage());
                // 获取目标玩家的名称
                Component targetName = mapTeams.getPlayerName(targetId);

                float remain = remainHp.getOrDefault(targetId, 0.0F);

                // 构建伤害信息消息
                Component damageMessage = buildDamageMessage(
                        damageData.count, damageData.damage,
                        receivedDamage.count,
                        receivedDamage.damage,
                        remain, targetName
                );

                // 发送给当前玩家
                player.sendSystemMessage(damageMessage);
            }
        }
    }

    private Component buildDamageMessage(int hitCount, float hitDamage,
                                         int receivedCount, float receivedDamage,
                                         float remainHp, Component targetName) {
        return Component.translatable("message.damage.hit")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(String.valueOf(hitCount))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("message.damage.times")
                        .withStyle(ChatFormatting.WHITE))
                .append(CommonComponents.SPACE)

                .append(Component.literal(String.format("%d", Math.round(hitDamage)))
                        .withStyle(ChatFormatting.GREEN))
                 .append(Component.translatable("message.damage.damage")
                        .withStyle(ChatFormatting.WHITE))
                .append(CommonComponents.SPACE)

                .append(Component.translatable("message.damage.received")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(receivedCount))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("message.damage.times")
                        .withStyle(ChatFormatting.WHITE))
                .append(CommonComponents.SPACE)

                .append(Component.literal(String.format("%d", Math.round(receivedDamage)))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("message.damage.damage")
                        .withStyle(ChatFormatting.WHITE))
                .append(CommonComponents.SPACE)

                .append(Component.translatable("message.damage.remain")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%d", Math.round(remainHp)))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("HP")
                        .withStyle(ChatFormatting.WHITE))
                .append(CommonComponents.SPACE)

                .append(targetName);
    }

    public void sendVictoryMessage(Component header, Comparator<PlayerData> comparator) {
        this.getMapTeams().startNewRound();
        // 获取并按指定比较器排序玩家数据
        List<PlayerData> players = this.getMapTeams()
                .getNormalTeams()
                .stream()
                .map(team -> team.getPlayers().values())
                .flatMap(Collection::stream)
                .sorted(comparator)
                .toList();

        // 构建胜利信息列表
        List<Component> messages = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            PlayerData data = players.get(i);

            ChatFormatting rowColor = (i % 2 == 0) ? ChatFormatting.DARK_AQUA : ChatFormatting.BLUE;

            Component message = Component.translatable(
                    "map.cs.message.victory.message",
                    i + 1,
                    data.name(),
                    data.getScores(),
                    data.getKills(),
                    data.getDeaths(),
                    data.getAssists(),
                    String.format("%.2f", data.getHeadshotRate()),
                    data.getDamage()
            ).withStyle(rowColor).withStyle(ChatFormatting.BOLD);

            messages.add(message);
        }

        // 发送胜利消息
        this.sendAllPlayerMessage(header, false);
        messages.forEach(message -> this.sendAllPlayerMessage(message, false));
    }


    // 通用标题发送工具（从CSGameMap迁移）
    protected void sendTitleToAllPlayers(Component title) {
        sendTitleToAllPlayers(title, Component.empty());
    }

    protected void sendTitleToAllPlayers(Component title, Component subtitle) {
        this.getMapTeams().getJoinedPlayers().forEach(data ->
                data.getPlayer().ifPresent(player -> {
                    player.connection.send(new ClientboundSetTitleTextPacket(title));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                })
        );
    }

    /**
     * 添加队伍并初始化商店系统
     * @param data 队伍数据
     * @see FPSMShop 每个队伍拥有独立商店实例
     */
    @Override
    public ServerTeam addTeam(TeamData data){
        ServerTeam team = super.addTeam(data);
        PlayerTeam playerTeam = team.getPlayerTeam();
        playerTeam.setNameTagVisibility(nameTagVisibility());
        playerTeam.setAllowFriendlyFire(allowFriendlyFire());
        playerTeam.setSeeFriendlyInvisibles(false);
        playerTeam.setDeathMessageVisibility(Team.Visibility.NEVER);
        return team;
    }

    /**
     * 切换两个队伍的阵营
     */
    public void switchTeams() {
        this.getMapTeams().switchAttackAndDefend(this, getT(), getCT());
    }

    public final VoteObj getVote() {
        return voteObj;
    }

    public final void setVote(VoteObj voteObj) {
        this.voteObj = voteObj;
    }

    public @NotNull ServerTeam getT(){
        return this.tTeam;
    }

    public @NotNull ServerTeam getCT(){
        return this.ctTeam;
    }

    public void registerCommand(String command, CSCommand handler) {
        commands.put(command, handler);
    }

    public void handleChatCommand(String rawText,ServerPlayer player){
        String command = rawText.toLowerCase(Locale.US);
        commands.forEach((k,v)->{
            if (command.equals(k) && rawText.length() == k.length()){
                v.run(player);
            }
        });
    }

    private void voteLogic() {
        if (this.getVote() != null) {
            long remainingBefore = this.voteObj.getRemainingTime();
            // 调用 tick 方法自动判断投票状态（会触发回调函数）
            boolean voteEnded = this.voteObj.tick();

            if (!voteEnded) {
                // 投票仍在进行中，显示剩余时间
                this.sendAllPlayerMessage(
                        Component.translatable("blockoffensive.map.vote.timer", this.voteObj.getRemainingTime())
                                .withStyle(ChatFormatting.DARK_AQUA),
                        true
                );
                // 每秒同步一次投票 HUD 状态
                if (this.voteObj.getRemainingTime() != remainingBefore) {
                    this.broadcastVoteSync(this.voteObj, 0);
                }
            } else {
                // 投票已结束，推送最终结果 + 反馈文本，随后清理
                int result = this.voteObj.getStatus() == VoteObj.VoteStatus.SUCCESS ? 1 : 2;
                this.broadcastVoteSync(this.voteObj, result);
                Component subject = Component.translatable("blockoffensive.cs." + this.voteObj.getVoteTitle());
                this.sendVoteMessage(false, Component.translatable(
                        result == 1 ? "blockoffensive.map.vote.success" : "blockoffensive.map.vote.fail", subject)
                        .withStyle(result == 1 ? ChatFormatting.GREEN : ChatFormatting.RED));
                this.voteObj = null;
            }
        }
    }

    /**
     * 向有资格投票者广播投票 HUD 状态。result: 0=进行中, 1=通过, 2=否决。
     */
    private void broadcastVoteSync(VoteObj vote, int result) {
        int agree = vote.getOnlineAgreeCount();
        int disagree = vote.getOnlineDisagreeCount();
        int eligible = vote.getEligiblePlayerCount();
        int notVoted = Math.max(0, eligible - vote.getOnlineVotedCount());
        boolean active = result == 0;
        com.ptcrys.blockoffensive.net.vote.VoteSyncS2CPacket packet =
                new com.ptcrys.blockoffensive.net.vote.VoteSyncS2CPacket(
                        active, vote.getVoteTitle(), (int) vote.getRemainingTime(),
                        agree, disagree, notVoted, eligible, vote.getRequiredPercent(), result);
        for (UUID uuid : vote.getEligiblePlayers()) {
            this.getPlayerByUUID(uuid).ifPresent(player ->
                    com.ptcrys.fpsmatch.FPSMatch.sendToPlayer(player, packet));
        }
    }

    public final void startVote(VoteObj voteObj){
        if(this.getVote() == null){
            this.setVote(voteObj);
            this.sendVoteMessage(false,
                    voteObj.getMessage(),
                    Component.translatable("blockoffensive.map.vote.help").withStyle(ChatFormatting.GREEN));
            this.broadcastVoteSync(voteObj, 0);
        }
    }

    public final void handleAgreeCommand(ServerPlayer serverPlayer){
        if(this.getVote() != null && this.getVote().processVote(serverPlayer, true)){
            this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.vote.agree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.GREEN),false);
            this.broadcastVoteSync(this.getVote(), 0);
        }
    }

    public final void handleDisagreeCommand(ServerPlayer serverPlayer) {
        if(this.getVote() != null && this.getVote().processVote(serverPlayer, false)){
            this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.vote.disagree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.RED),false);
            this.broadcastVoteSync(this.getVote(), 0);
        }
    }

    public final void sendVoteMessage(boolean actionBar,Component... messages){
        if(this.getVote() != null){
            for (UUID uuid : this.getVote().getEligiblePlayers()){
                this.getPlayerByUUID(uuid).ifPresent(player -> {
                    for (Component message : messages){
                        player.displayClientMessage(message,actionBar);
                    }
                });
            }
        }
    }

    /**
     * 配置游戏规则（基于BOConfig配置）
     */
    public void configureGameRules(ServerLevel serverLevel) {
        GameRules gameRules = serverLevel.getGameRules();
        gameRules.getRule(GameRules.RULE_KEEPINVENTORY).set(BOConfig.common.keepInventory.get(), null);
        gameRules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(BOConfig.common.immediateRespawn.get(), null);
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(BOConfig.common.daylightCycle.get(), null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(BOConfig.common.weatherCycle.get(), null);
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(BOConfig.common.mobSpawning.get(), null);
        gameRules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(BOConfig.common.naturalRegeneration.get(), null);

        if (BOConfig.common.hardDifficulty.get()) {
            serverLevel.getServer().setDifficulty(Difficulty.HARD, true);
        }
    }

    @Override
    public MapTeams.JoinTeamResult join(String teamName, ServerPlayer player) {
        MapTeams.JoinTeamResult result = super.join(teamName, player);
        if (!result.isSuccess()) {
            return result;
        }

        MapTeams mapTeams = this.getMapTeams();
        mapTeams.getTeamByPlayer(player).ifPresent(team -> {
            // 如果游戏已经开始，设置玩家为旁观者
            if(this.isStart){
                player.setGameMode(GameType.SPECTATOR);
                team.getPlayerData(player.getUUID()).ifPresent(data -> data.setLiving(false));
                setBystander(player);
            }
        });
        return result;
    }

    @Override
    public void leave(ServerPlayer player) {
        boolean wasInMap = checkGameHasPlayer(player) || checkSpecHasPlayer(player);
        super.leave(player);
        if (wasInMap && !checkGameHasPlayer(player) && !checkSpecHasPlayer(player)) {
            teleportPlayerToMatchEndPoint(player);
        }
    }

    public abstract boolean setTeamSpawnPoints();

    public Map<ServerTeam,List<SpawnPointData>> getAllSpawnPoints(){
        Map<ServerTeam,List<SpawnPointData>> allSpawnPoints = new HashMap<>();

        for (ServerTeam team : this.getMapTeams().getNormalTeams()){
            team.getCapabilityMap().get(SpawnPointCapability.class).ifPresent(cap-> allSpawnPoints.put(team,cap.getSpawnPointsData()));
        }

        return allSpawnPoints;
    }

    public void giveEco(ServerPlayer player, ServerPlayer attacker, ItemStack itemStack, boolean punish) {
        if(!canGiveEconomy()) return;
        if(itemStack.getItem() == BOItemRegister.C4.get()) return;

        ServerTeam killerTeam = this.getMapTeams().getTeamByPlayer(attacker).orElse(null);
        ServerTeam deadTeam = this.getMapTeams().getTeamByPlayer(player).orElse(null);
        if(killerTeam == null || deadTeam == null) {
            FPSMatch.LOGGER.error("CSGameMap {} -> killerTeam or deadTeam are null! : killer {} , dead {}",this.getMapName(),attacker.getDisplayName(),player.getDisplayName());
            return;
        }

        if(attacker.getUUID().equals(player.getUUID())){
            ServerTeam team = deadTeam.equals(getCT()) ? getT() : getCT();
            List<ServerPlayer> players = team.getOnline();
            if(players.isEmpty()) return;

            ServerPlayer p = players.get(getRandom().nextInt(0,players.size()));
            ShopCapability.getPlayerShopData(this,p.getUUID()).ifPresent(shopData -> {
                shopData.addMoney(300);
                p.displayClientMessage(Component.translatable("blockoffensive.kill.message.suicide",player.getDisplayName(), 300),false);
            });
            return;
        }

        if (!killerTeam.equals(deadTeam)){
            int reward = getRewardByItem(itemStack);
            ShopCapability.getShopByPlayer(attacker).ifPresent(shopData -> {
                shopData.addMoney(attacker,reward);
                attacker.displayClientMessage(Component.translatable("blockoffensive.kill.message.enemy",reward),false);
            });
        }else{
            if(punish){
                ShopCapability.getShopByPlayer(attacker).ifPresent(shopData -> {
                    shopData.reduceMoney(attacker,300);
                    attacker.displayClientMessage(Component.translatable("blockoffensive.kill.message.teammate",300),false);
                });
            }
        }
    }

    public void giveAllPlayersKits(){
        this.giveAllPlayersKits((type)-> true);
    }

    public void giveAllPlayersKits(Function<DropType, Boolean> checker) {
        CapabilityMap.getTeamCapability(this, StartKitsCapability.class)
                .forEach((team, opt) -> opt.ifPresent(cap -> team.getPlayersData().forEach(playerData -> playerData.getPlayer().ifPresent(player -> {
                    ArrayList<ItemStack> items = cap.getTeamKits();
                    player.getInventory().clearContent();
                    for (ItemStack item : items) {
                        ItemStack copy = item.copy();
                        DropType type = DropType.getItemDropType(copy);
                        if(!checker.apply(type)){
                            continue;
                        }
                        if(copy.getItem() instanceof ArmorItem armorItem){
                            player.setItemSlot(armorItem.getEquipmentSlot(),copy);
                        }else{
                            player.getInventory().add(FPSMUtil.fixGunItem(copy));
                        }
                    }
                    FPSMUtil.sortPlayerInventory(player);
                }))));
    }

    public abstract void givePlayerKits(ServerPlayer player);

    @Override
    public void handleRespawn(ServerPlayer player) {
        if (this instanceof CSDeathMatchMap deathMatchMap) {
            deathMatchMap.respawnPlayer(player);
            return;
        }
        this.getMapTeams().getPlayerData(player).ifPresent(data -> data.setLiving(false));
        player.setGameMode(GameType.SPECTATOR);
        this.setBystander(player);
    }

    /**
     * Force-drop C4 from a player inventory and retain the spawned ItemEntity identity
     * for minimap objective tracking. Returns null when the player held no C4.
     */
    @Nullable
    public static ItemEntity dropC4(ServerPlayer player) {
        int im = player.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof CompositionC4, -1, player.inventoryMenu.getCraftSlots());
        if (im > 0) {
            ItemEntity dropped = player.drop(new ItemStack(BOItemRegister.C4.get(), 1), false, false);
            player.getInventory().setChanged();
            return dropped;
        }
        return null;
    }

    public void setBystander(ServerPlayer player) {
        BOSpecManager.startSpectating(player);
    }

    public void resetPlayerClientData(ServerPlayer serverPlayer){
        FPSMatchStatsResetS2CPacket packet = new FPSMatchStatsResetS2CPacket();
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), packet);
    }

    public void sendAllPlayerMessage(Component message, boolean actionBar){
        this.getMapTeams().getJoinedPlayers().forEach(data -> data.getPlayer().ifPresent(player -> player.displayClientMessage(message,actionBar)));
    }

    public void teleportPlayerToMatchEndPoint(){
        this.getMapTeams().getJoinedPlayersWithSpec().forEach(uuid ->
                this.getPlayerByUUID(uuid).ifPresent(this::teleportPlayerToMatchEndPoint));
    }

    public void teleportPlayerToMatchEndPoint(ServerPlayer player){
        getCapabilityMap().get(GameEndTeleportCapability.class).ifPresent(cap -> {
            SpawnPointData data = cap.getPoint();
            if(data == null) return;
            teleportToPoint(player, data);
            player.setGameMode(FPSMConfig.common.autoAdventureMode.get() ? GameType.ADVENTURE : GameType.SURVIVAL);
            player.setRespawnPosition(null, null, 0, false, false);
        });
    }

    /**
     * 同步游戏设置到客户端（比分/时间等）
     * @see CSGameSettingsS2CPacket
     */
    public void syncToClient(boolean syncWeapon) {
        ServerTeam ct = this.getCT();
        ServerTeam t = this.getT();
        CSGameSettingsS2CPacket packet = new CSGameSettingsS2CPacket(
                ct.getScores(),t.getScores(),
                this.getClientTime(),
                CSGameSettingsS2CPacket.GameFlags.of(
                        this.isDebug(),
                        this.isStart,
                        this.isError(),
                        this.isPause(),
                        this.isWaiting(),
                        this.isWaitingWinner()
                )
        );

        this.sendPacketToAllPlayer(packet);

        if(isStart){
            if (shouldSyncShopInfoWithMapInfo()) {
                syncShopInfo();
            }

            if(syncWeapon){
                syncWeaponData();
            }
        }
    }

    protected boolean shouldSyncShopInfoWithMapInfo() {
        return true;
    }

    public void syncShopInfo(){
        for (ServerTeam team : this.getMapTeams().getNormalTeams()){
            ShopCapability cap = team.getCapabilityMap().get(ShopCapability.class).orElse(null);
            if(cap == null) continue;

            for (PlayerData data : team.getPlayersData()){
                data.getPlayer().ifPresent(player -> {
                    syncShopInfo(team,player,getPlayerCanOpenShop(cap,player),getShopCloseTime());
                });
            }
        }
    }

    public void syncShopInfo(boolean enable,int time){
        this.getMapTeams().getNormalTeams().forEach(team -> team.getPlayers().values().forEach(data -> data.getPlayer().ifPresent(player -> syncShopInfo(team,player,enable,time))));
    }

    public void syncShopInfo(ServerTeam team, ServerPlayer player, boolean enable, int closeTime){
        var packet = new ShopStatesS2CPacket(enable,getNextRoundMinMoney(team),closeTime);
        this.sendPacketToJoinedPlayer(player,packet,false);
    }

    public void syncWeaponData(){
        Map<UUID, WeaponData> weaponDataMap = new HashMap<>();
        Map<UUID, Map<String, List<ResourceLocation>>> itemIdMap = new HashMap<>();

        for (PlayerData data : this.getMapTeams().getJoinedPlayers()){
            Optional<ServerPlayer> optional = data.getPlayer();
            if(optional.isEmpty()) continue;
            ServerPlayer player = optional.get();
            Map<String, List<String>> weaponData = new HashMap<>();
            Map<String, List<ResourceLocation>> itemIds = new HashMap<>();

            List<List<ItemStack>> items = new ArrayList<>();
            items.add(player.getInventory().items);
            items.add(player.getInventory().armor);
            items.add(player.getInventory().offhand);
            for (List<ItemStack> itemStacks : items) {
                for(ItemStack itemStack : itemStacks){
                    if(itemStack.isEmpty()) continue;
                    for (DropType dropType : DropType.values()) {
                        if(dropType.itemMatch().test(itemStack)){
                            weaponData.computeIfAbsent(dropType.name(), k -> new ArrayList<>()).add(itemStack.getHoverName().getString());
                            ResourceLocation regId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(itemStack.getItem());
                            if (GunCompatManager.isGun(itemStack)) {
                                itemIds.computeIfAbsent(dropType.name(), k -> new ArrayList<>()).add(GunCompatManager.findProvider(itemStack).getGunId(itemStack));
                            } else {
                                itemIds.computeIfAbsent(dropType.name(), k -> new ArrayList<>()).add(regId);
                            }
                            break;
                        }
                    }
                }
            }
            // carried
            weaponData.computeIfAbsent("CARRIED", k -> new ArrayList<>()).add(player.getMainHandItem().getHoverName().getString());
            ItemStack mainHand = player.getMainHandItem();
            if (GunCompatManager.isGun(mainHand)) {
                itemIds.computeIfAbsent("CARRIED", k -> new ArrayList<>()).add(GunCompatManager.findProvider(mainHand).getGunId(mainHand));
            } else {
                ResourceLocation carriedId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(mainHand.getItem());
                itemIds.computeIfAbsent("CARRIED", k -> new ArrayList<>()).add(carriedId);
            }

            boolean hasHelmet;
            int durability;
            Optional<BulletproofArmorAttribute> attribute = BulletproofArmorAttribute.getInstance(player);
            if(attribute.isPresent()){
                hasHelmet = attribute.get().hasHelmet();
                durability = attribute.get().getDurability();
            }else{
                hasHelmet = false;
                durability = 0;
            }
            weaponDataMap.put(player.getUUID(),new WeaponData(weaponData,hasHelmet,durability));
            itemIdMap.put(player.getUUID(), itemIds);
        }

        CSGameWeaponDataS2CPacket weaponDataS2CPacket = new CSGameWeaponDataS2CPacket(weaponDataMap);
        this.sendPacketToSpecPlayer(weaponDataS2CPacket);
    }

    @Override
    public void reset() {
        super.reset();
        spectatorRosterSync.reset();
        this.cleanupMap();
        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> this.getPlayerByUUID(uuid).ifPresent(player->{
            this.getServerLevel().getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
            player.getInventory().clearContent();
            player.removeAllEffects();
        })));
        this.teleportPlayerToMatchEndPoint();
        this.sendPacketToAllPlayer(new FPSMatchStatsResetS2CPacket());
    }

    @Override
    public boolean reload(){
        if (!super.reload()) return false;
        reset();
        loadConfig();
        return true;
    }

    @Override
    public void handleDeath(DeathContext context) {
        super.handleDeath(context);

        if (!isStart) {
            return;
        }

        ServerPlayer deadPlayer = context.getDeadPlayer();
        ServerPlayer attacker = context.getAttacker();
        ItemStack deathItem = context.getDeathItem();

        if (shouldSendPhysicsDeathPacketInBaseDeathHandler(context)) {
            sendPhysicsDeathPacket(deadPlayer);
        }

        if (allowSpecAttach.get()) {
            if (attacker != null
                    && deadPlayer.isSpectator()
                    && !attacker.getUUID().equals(deadPlayer.getUUID())) {
                BOSpecManager.sendKillCamAndAttach(deadPlayer, attacker, deathItem);
            }
        }

        if (attacker != null) {
            giveEco(deadPlayer, attacker, deathItem, true);
        }else {
            //若无伤害源实体 将击杀者设置为自己
            context.setAttacker(deadPlayer);
            attacker = deadPlayer;
        }

        boolean passWall = context.isPassWall();
        boolean passSmoke = context.isPassSmoke();
        if (!FMLEnvironment.production && BOTaczLiveFireDebugCommand.isLiveFireTestMap(getMapName())) {
            BOTaczLiveFireDebugCommand.handleDeathContext(getMapName(), context);
        }

        DeathMessageS2CPacket killPacket = BOUtil.buildDeathMessagePacket(
                this,
                attacker,
                deadPlayer,
                deathItem,
                context.isHeadShot(),
                passWall,
                passSmoke,
                context.isScopedKill(),
                minAssistDamageRatio.get()
        );
        if (!FMLEnvironment.production && BOTaczLiveFireDebugCommand.isLiveFireTestMap(getMapName())) {
            BOTaczLiveFireDebugCommand.handleDeathMessage(getMapName(), killPacket.deathMessage());
        }
        sendPacketToAllPlayer(killPacket);
    }

    @Override
    public ItemStack resolveDeathItem(@Nullable ServerPlayer attacker, net.minecraft.world.damagesource.DamageSource source) {
        return BOUtil.getDeathItemStack(attacker, source);
    }

    public int getRewardByItem(ItemStack itemStack){
        if(FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(itemStack)){
            return knifeKillEconomy.get();
        }else{
            if(GunCompatManager.isGun(itemStack)){
                return gerRewardByGunId(GunCompatManager.findProvider(itemStack).getGunId(itemStack));
            }else{
                return defaultKillEconomy.get();
            }
        }
    }

    public int gerRewardByGunId(ResourceLocation gunId){
        Optional<GunTabTypeEnum> optional = FPSMUtil.getGunTypeByGunId(gunId);
        if(optional.isPresent()){
            switch(optional.get()){
                case SHOTGUN -> {
                    return shotgunKillEconomy.get();
                }
                case SMG -> {
                    return smgKillEconomy.get();
                }
                case SNIPER -> {
                    return sniperKillEconomy.get();
                }
                default -> {
                    return defaultKillEconomy.get();
                }
            }
        }else{
            return defaultKillEconomy.get();
        }
    }

    public void handleTeammateAttack(ServerPlayer attacker, ServerPlayer hurt) {
        MapTeams mapTeams = this.getMapTeams();
        mapTeams.getPlayerTeamAndData(attacker).ifPresent(pair->{
            ServerTeam team = pair.getFirst();
            PlayerData data = pair.getSecond();
            if(!data.isHurtTo(hurt.getUUID())){
                team.sendMessage(Component.translatable("blockoffensive.hurt.message.teammate",attacker.getDisplayName()));
            }
        });
    }

    @FunctionalInterface
    public interface CSCommand{

        void run(ServerPlayer player);

        /**
         * 创建一个需要特定权限等级的CSCommand。
         * @param level 所需权限等级（0-4）
         * @param action 命令执行的动作
         * @return 带权限检查的CSCommand实例
         */
        static CSCommand withPermission(int level, Consumer<ServerPlayer> action) {
            if (level < 0 || level > 4) {
                FPSMatch.LOGGER.error("CSCommand Permission level must be between 0 and 4");
                return action::accept;
            }
            return player -> {
                if(player.hasPermissions(level)) action.accept(player);
            };
        }
    }
}
