package com.ptcrys.blockoffensive.map;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ptcrys.blockoffensive.BOConfig;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.data.MvpReason;
import com.ptcrys.blockoffensive.event.CSGameMapEvent;
import com.ptcrys.blockoffensive.event.CSGamePlayerGetMvpEvent;
import com.ptcrys.blockoffensive.event.CSGameRoundEndEvent;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.item.BombDisposalKit;
import com.ptcrys.blockoffensive.item.CompositionC4;
import com.ptcrys.blockoffensive.map.team.capability.ColoredPlayerCapability;
import com.ptcrys.blockoffensive.mvp.CSMvpContribution;
import com.ptcrys.blockoffensive.mvp.CSMvpResult;
import com.ptcrys.blockoffensive.mvp.CSMvpScorer;
import com.ptcrys.blockoffensive.net.CSGameSettingsS2CPacket;
import com.ptcrys.blockoffensive.net.CSTabRemovalS2CPacket;
import com.ptcrys.blockoffensive.net.PxRagdollRemovalCompatS2CPacket;
import com.ptcrys.blockoffensive.net.bomb.BombDemolitionProgressS2CPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpHUDCloseS2CPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpMessageS2CPacket;
import com.ptcrys.blockoffensive.net.shop.ShopStatesS2CPacket;
import com.ptcrys.blockoffensive.net.spec.BombFuseS2CPacket;
import com.ptcrys.blockoffensive.sound.BOSoundRegister;
import com.ptcrys.blockoffensive.sound.MVPMusicManager;
import com.ptcrys.blockoffensive.spectator.BOSpecManager;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import com.ptcrys.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.*;
import com.ptcrys.fpsmatch.common.drop.DropType;
import com.ptcrys.fpsmatch.common.packet.FPSMSoundPlayS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMusicPlayS2CPacket;
import com.ptcrys.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.capability.CapabilityMap;
import com.ptcrys.fpsmatch.core.capability.map.MapCapability;
import com.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.data.Setting;
import com.ptcrys.fpsmatch.core.damage.DamageSourceCategory;
import com.ptcrys.fpsmatch.core.damage.MinecraftDamageSourceClassifier;
import com.ptcrys.fpsmatch.core.entity.BlastBombEntity;
import com.ptcrys.fpsmatch.core.map.BlastBombState;
import com.ptcrys.fpsmatch.core.map.DeathContext;
import com.ptcrys.fpsmatch.core.map.VoteObj;
import com.ptcrys.fpsmatch.core.match.RoundContext;
import com.ptcrys.fpsmatch.core.match.RoundLifecycle;
import com.ptcrys.fpsmatch.core.match.RoundPhase;
import com.ptcrys.fpsmatch.core.match.RoundResult;
import com.ptcrys.fpsmatch.core.music.MvpMusicManager;
import com.ptcrys.fpsmatch.core.persistence.FPSMDataManager;
import com.ptcrys.fpsmatch.core.shop.FPSMShop;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.core.team.TeamData;
import com.ptcrys.fpsmatch.util.FPSMUtil;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import com.ptcrys.blockoffensive.minimap.CSDeathMarkerCapture;
import com.ptcrys.blockoffensive.minimap.CSGameObjectiveTracker;
import com.ptcrys.blockoffensive.minimap.CSMapMinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerLedger;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;

/**
 * 反恐精英（CS）模式地图核心逻辑类
 * 管理回合制战斗、炸弹逻辑、商店系统、队伍经济、玩家装备等核心机制
 */
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CSGameMap extends CSMap{
    public static final String TYPE = "cs";
    /**
     * Codec序列化配置（用于地图数据保存/加载）
     */
    public static final Codec<CSGameMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // 基础地图数据
            Codec.STRING.fieldOf("mapName").forGetter(CSGameMap::getMapName),
            AreaData.CODEC.fieldOf("mapArea").forGetter(CSGameMap::getMapArea),
            ResourceLocation.CODEC.fieldOf("serverLevel").forGetter(map -> map.getServerLevel().dimension().location()),
            CapabilityMap.Wrapper.DATA_CODEC.fieldOf("capabilities").forGetter(csGameMap -> csGameMap.getCapabilityMap().getData().data()),
            // 队伍数据
            Codec.unboundedMap(
                    Codec.STRING,
                    CapabilityMap.Wrapper.CODEC
            ).fieldOf("teams").forGetter(csGameMap -> csGameMap.getMapTeams().getData())
    ).apply(instance, CSGameMap::new));

    public static void save(FPSMDataManager manager){
        FPSMCore.getInstance().getMapByClass(CSGameMap.class)
                .forEach((map -> {
                    map.saveConfig();
                    manager.saveData(map,map.getMapName(),false);
                }));
    }
    private static final List<Class<? extends MapCapability>> MAP_CAPABILITIES = List.of(DemolitionModeCapability.class, GameEndTeleportCapability.class, MinimapCapability.class);
    private static final List<Class<? extends TeamCapability>> TEAM_CAPABILITIES = List.of(PauseCapability.class, CompensationCapability.class, TeamSwitchRestrictionCapability.class, ShopCapability.class, StartKitsCapability.class, ColoredPlayerCapability.class);

    private Setting<Integer> winnerRound;
    private Setting<Integer> overtimeRound;
    private Setting<Integer> pauseTime;
    private Setting<Integer> winnerWaitingTime;
    private Setting<Integer> warmUpTime;
    private Setting<Integer> waitingTime;
    private Setting<Integer> roundTimeLimit;
    private Setting<Integer> startMoney;
    private Setting<Integer> defaultLoserEconomy;
    private Setting<Integer> defuseBonus;
    private Setting<Integer> compensationBase;
    private Setting<Integer> tDeathRewardPer;
    private Setting<Integer> closeShopTime;
    private final DeathMarkerLedger deathMarkerLedger = new DeathMarkerLedger();
    private final CSGameObjectiveTracker objectiveTracker = new CSGameObjectiveTracker();
    private Setting<Boolean> knifeSelection;
    private Setting<Integer> c4InstantKillRadius;

    private Setting<Integer> timeoutEconomy;
    private Setting<Integer> aceEconomy;
    private Setting<Integer> defuseBombEconomy;
    private Setting<Integer> detonateBombEconomy;

    private int currentPauseTime = 0;
    private int currentRoundTime = 0;
    private boolean isError = false;
    private boolean isPause = false;
    private boolean isWaiting = false;
    private boolean isWarmTime = false;
    private boolean isWaitingWinner = false;
    private boolean isShopLocked = false;
    private boolean isKnifeSelected = false;
    private boolean isOvertime = false;
    private int overCount = 0;
    private boolean isWaitingOverTimeVote = false;
    private boolean roundStarted = false;
    private WinnerReason lastWinnerReason = WinnerReason.ACED;
    private UUID lastBombPlanter;
    private UUID lastBombDefuser;
    private UUID pendingFinalKillAssist;

    private final Map<UUID,Integer> knifeCache = new HashMap<>();
    private final Map<UUID, Float> roundIncendiaryDamage = new HashMap<>();
    private final Map<UUID, Float> roundExplosiveDamage = new HashMap<>();
    /** Players who became sole living teammate while facing 2+ living enemies this round. */
    private final Set<UUID> roundClutchCandidates = new HashSet<>();

    /**
     * 构造函数：创建CS地图实例
     * @param serverLevel 服务器世界实例
     * @param mapName 地图名称
     * @param areaData 地图区域数据
     * @see #addTeam(TeamData) 初始化时自动添加CT和T阵营
     */
    public CSGameMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel,mapName,areaData, MAP_CAPABILITIES,TEAM_CAPABILITIES);
        this.registerCommands();
        CapabilityMap.getMapCapability(this, DemolitionModeCapability.class).ifPresent(cap -> cap.setDemolitionTeam(this.getT()));
        this.getT().setEnableRounds(true);
        this.getCT().setEnableRounds(true);
    }

    private CSGameMap(String mapName,AreaData areaData,ResourceLocation serverLevel, Map<String, JsonElement> capabilities, Map<String, CapabilityMap.Wrapper> teams) {
        this(FPSMCore.getInstance().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,serverLevel)), mapName, areaData);
        this.getCapabilityMap().write(capabilities);
        this.getMapTeams().writeData(teams);
    }

    private void registerCommands(){
        this.registerCommand("p", this::setPauseState);
        this.registerCommand("pause", this::setPauseState);
        this.registerCommand("unpause", this::startUnpauseVote);
        this.registerCommand("up", this::startUnpauseVote);
        this.registerCommand("agree", this::handleAgreeCommand);
        this.registerCommand("a", this::handleAgreeCommand);
        this.registerCommand("disagree", this::handleDisagreeCommand);
        this.registerCommand("da", this::handleDisagreeCommand);
        this.registerCommand("d", this::handleDropKnifeCommand);
        this.registerCommand("drop", this::handleDropKnifeCommand);

        this.registerCommand("debug_1",(p)->{
            if(!p.hasPermissions(2)) return;
            getCT().setScores(winnerRound.get() - 2);
            getT().setScores(winnerRound.get() - 1);
        });

        this.registerCommand("debug_2",CSCommand.withPermission(2,(p)-> {
            this.switchTeams();
        }));

        this.registerCommand("debug_3",CSCommand.withPermission(2,(p)-> {
            p.displayClientMessage(Component.literal("team: " + this.getMapTeams().getTeamByPlayer(p).map(t -> t.name).orElse("none")), false);
        }));

        this.registerCommand("debug_4",CSCommand.withPermission(2,(p)->{
            p.displayClientMessage(Component.literal("Victory Round: " + requiredScoreStr()),false);
            p.displayClientMessage(Component.literal("OverCount: " + overCount),false);
        }));
    }

    /**
     * 添加队伍并初始化商店系统
     * @param data 队伍数据
     * @see FPSMShop 每个队伍拥有独立商店实例
     */
    @Override
    public ServerTeam addTeam(TeamData data){
        ServerTeam team = super.addTeam(data);
        team.getCapabilityMap().get(ShopCapability.class).ifPresent(cap -> cap.initialize("cs", startMoney.get()));
        return team;
    }

    @Override
    public void setup() {
        winnerRound = this.addSetting("match", "winnerRound", 13); // 13回合
        overtimeRound = this.addSetting("match", "overtimeRound", 3); // 3回合
        pauseTime = this.addSetting("match", "pauseTime", 1200); // 60秒
        winnerWaitingTime = this.addSetting("match", "winnerWaitingTime", 160);
        warmUpTime = this.addSetting("match", "warmUpTime", 1200);
        waitingTime = this.addSetting("match", "waitingTime", 300);
        roundTimeLimit = this.addSetting("match", "roundTimeLimit", 2300);
        startMoney = this.addSetting("eco", "startEconomy", 800);
        defaultLoserEconomy = this.addSetting("eco", "defaultLoserEconomy", 1400);
        defuseBonus = this.addSetting("eco", "defuseEconomy", 600);
        compensationBase = this.addSetting("eco", "compensationBase", 500);
        tDeathRewardPer = this.addSetting("eco", "tDeathRewardPer", 50);
        closeShopTime = this.addSetting("eco", "closeShopTime", 200);
        knifeSelection = this.addSetting("match", "knifeSelection", false);
        c4InstantKillRadius = this.addSetting("bomb", "c4InstantKillRadius", 20);
        timeoutEconomy = this.addSetting("eco", "timeoutEconomy", 3250);
        aceEconomy = this.addSetting("eco", "aceEconomy", 3250);
        defuseBombEconomy = this.addSetting("eco", "defuseBombEconomy", 3500);
        detonateBombEconomy = this.addSetting("eco", "detonateBombEconomy", 3500);
        disableEnemyGlow();
    }

    @Override
    public Collection<Setting<?>> settings() {
        return List.of(
                displayName,
                iconTexture,
                backgroundTexture,
                allowJoinInProgress,
                autoStart,
                autoStartTime,
                readyStartEnabled,
                readyStartTime,
                minAssistDamageRatio,
                getTeammateGlowSetting(),
                hideEnemyNameTag,
                allowFriendlyFire,
                allowSpecAttach,
                magazineMode,
                knifeKillEconomy,
                smgKillEconomy,
                sniperKillEconomy,
                shotgunKillEconomy,
                defaultKillEconomy,
                ctLimit,
                tLimit,
                winnerRound,
                overtimeRound,
                pauseTime,
                winnerWaitingTime,
                warmUpTime,
                waitingTime,
                roundTimeLimit,
                startMoney,
                defaultLoserEconomy,
                defuseBonus,
                compensationBase,
                tDeathRewardPer,
                closeShopTime,
                knifeSelection,
                c4InstantKillRadius,
                timeoutEconomy,
                aceEconomy,
                defuseBombEconomy,
                detonateBombEconomy
        );
    }

    @Override
    public void configFromJson(JsonElement json) {
        super.configFromJson(json);
        disableEnemyGlow();
    }

    private void disableEnemyGlow() {
        getEnemyGlowSetting().set(false);
    }

    @Override
    public boolean getPlayerCanOpenShop(ShopCapability cap, ServerPlayer player) {
        if (isWaiting && !roundStarted) {
            return !isShopLocked;
        }
        return !isShopLocked && cap.getShopSafe().map(shop->shop.isInArea(player)).orElse(false);
    }

    @Override
    protected boolean shouldSyncShopInfoWithMapInfo() {
        return super.shouldSyncShopInfoWithMapInfo();
    }

    public int getNextRoundMinMoney(ServerTeam team){
        CompensationCapability compensation = getCompensation(team);
        return calculateNextRoundMinMoney(compensation == null ? null : compensation.getFactor());
    }

    public static int calculateNextRoundMinMoney(Integer compensationFactor) {
        return CSEconomyRules.calculateNextRoundMinMoney(compensationFactor);
    }

    public CompensationCapability getCompensation(ServerTeam team){
        return team.getCapabilityMap().get(CompensationCapability.class).orElse(null);
    }

    /**
     * 游戏主循环逻辑（每tick执行）
     * 管理暂停状态、回合时间、RoundLifecycle 胜利规则等核心流程
     * @see #startNewRound() 启动新回合
     */
    @Override
    public void tick() {
        super.tick();
        if (isStart && roundLifecycle != null) {
            syncPauseTimeWithLifecycle();
        }
    }

    private void syncPauseTimeWithLifecycle() {
        RoundPhase phase = roundLifecycle.phase();
        if (phase == RoundPhase.WAITING || phase == RoundPhase.ROUND_END_WAITING) {
            this.currentPauseTime = roundLifecycle.phaseElapsedTicks();
        }
    }

    @Override
    public MapTeams.JoinTeamResult join(String teamName, ServerPlayer player) {
        MapTeams.JoinTeamResult result = super.join(teamName, player);
        if(result.isSuccess() && allowSpecAttach.get() && teamName.equals("spectator")){
            BOSpecManager.requestAttachTeammate(player);
        }
        return result;
    }

    @Override
    public void leave(ServerPlayer player) {
        this.sendPacketToAllPlayer(new CSTabRemovalS2CPacket(player.getUUID()));
        super.leave(player);
    }

    @Override
    public String getGameType() {
        return TYPE;
    }


    /**
     * 开始新游戏（初始化所有玩家状态）
     * 核心优化：拆分职责、复用Setting配置、消除魔法值、简化嵌套逻辑
     * @see #giveBlastTeamBomb() 给爆破方分配C4
     */
    public boolean start() {
        if (!super.start()) {
            return false;
        }

        MapTeams mapTeams = getMapTeams();

        setTeamNameColors();
        if (this.isError) return false;

        resetGameCoreState();

        if (!this.setTeamSpawnPoints()) {
            this.reset();
            return false;
        }

        cleanupMap();
        initializeTeams(mapTeams);

        initializeAllJoinedPlayers(mapTeams);

        handleKnifeSelectionPhase();

        mapTeams.startNewRound();
        syncShopDataToClient();
        syncNormalRoundStartMessage();

        return true;
    }

    /**
     * 设置队伍名称颜色（CT蓝、T黄）
     */
    private void setTeamNameColors() {
        getT().getPlayerTeam().setColor(ChatFormatting.YELLOW);
        getCT().getPlayerTeam().setColor(ChatFormatting.BLUE);
    }


    /**
     * 重置游戏核心状态（加时、计数、等待状态等）
     */
    public void resetGameCoreState() {
        this.isOvertime = false;
        this.overCount = 0;
        this.isWaitingOverTimeVote = false;
        this.isStart = true;
        this.isWaiting = true;
        this.isWaitingWinner = false;
        this.currentPauseTime = 0;
        this.isShopLocked = false;
    }

    /**
     * 初始化队伍状态（分数、补偿因子、玩家数据重置）
     */
    private void initializeTeams(MapTeams mapTeams) {
        mapTeams.getNormalTeams().forEach(team -> {
            team.setScores(0);
            CapabilityMap<BaseTeam, TeamCapability> map = team.getCapabilityMap();
            map.get(CompensationCapability.class).ifPresentOrElse(cap->cap.setFactor(0),()-> FPSMatch.LOGGER.error("CSGameMap {} Compensation fail set to {}",this.getMapName(), 0));
            int money = startMoney.get();
            map.get(ShopCapability.class).ifPresentOrElse(cap-> cap.setStartMoney(money),()-> FPSMatch.LOGGER.error("CSGameMap {} {} Shop fail set start money {}",this.getMapName(),team.name, money));
            // 重置队伍内所有玩家数据
            team.getPlayers().forEach((uuid, data) -> data.reset());
        });
    }

    /**
     * 初始化所有已加入队伍的玩家状态
     */
    private void initializeAllJoinedPlayers(MapTeams mapTeams) {
        mapTeams.getJoinedPlayers().forEach(this::initializeSinglePlayer);
    }

    /**
     * 初始化单个玩家状态
     */
    private void initializeSinglePlayer(PlayerData data) {
        data.reset();
        data.getPlayer().ifPresent(player -> {
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(
                    MobEffects.SATURATION,
                    -1,
                    2,
                    false,
                    false,
                    false
            ));
            player.heal(player.getMaxHealth());
            player.setGameMode(GameType.ADVENTURE);
            this.clearInventory(player);
            this.teleportPlayerToReSpawnPoint(player);
        });
    }

    /**
     * 处理刀选阶段逻辑
     */
    private void handleKnifeSelectionPhase() {
        boolean isKnifeSelectionPhase = knifeSelection.get() && !isKnifeSelected;
        this.isShopLocked = isKnifeSelectionPhase;

        int shopCloseTime = this.closeShopTime.get();
        syncShopInfo(!isKnifeSelectionPhase, shopCloseTime);

        this.giveAllPlayersKits((type)-> !isKnifeSelectionPhase || type == DropType.THIRD_WEAPON);

        if (!isKnifeSelectionPhase) {
            this.giveBlastTeamBomb();
            ShopCapability.setPlayerMoney(this, this.startMoney.get());
        }
    }

    public boolean canRestTime(){
        return !this.isPause && !this.isWarmTime && !this.isWaiting && !this.isWaitingWinner;
    }

    public boolean checkPauseTime(){
        if(this.isPause && currentPauseTime < pauseTime.get()){
            this.currentPauseTime++;
        }else{
            if(this.isPause) {
                currentPauseTime = 0;
                if(this.getVote() != null && this.getVote().getVoteTitle().equals("unpause")){
                    this.setVote(null);
                }
                this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.cs.pause.done").withStyle(ChatFormatting.GOLD),false);
            }
            isPause = false;
        }
        return this.isPause;
    }

    public boolean checkWarmUpTime(){
        if(this.isWarmTime && currentPauseTime < warmUpTime.get()){
            this.currentPauseTime++;
        }else {
            if(this.canRestTime()) {
                currentPauseTime = 0;
            }
            isWarmTime = false;
        }
        return this.isWarmTime;
    }

    void checkPauseVote() {
        boolean b = false;
        Iterator<ServerTeam> teams = new ArrayList<>(this.getMapTeams().getNormalTeams()).iterator();
        while (teams.hasNext()) {
            ServerTeam team = teams.next();
            PauseCapability cap = team.getCapabilityMap().get(PauseCapability.class).orElse(null);
            if (cap != null) {
                if (!b) {
                    b = cap.needPause();
                    if (b) {
                        cap.setNeedPause(false);
                    }
                } else {
                    cap.resetPauseIfNeed();
                }
            }
            teams.remove();
        }

        if (b) {
            this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.cs.pause.now").withStyle(ChatFormatting.GOLD), false);
            this.isPause = true;
            this.currentPauseTime = 0;
            this.isWaiting = true;
        }
    }

    int getWaitingTimeTicks() {
        return waitingTime.get();
    }

    int getRoundTimeLimitTicks() {
        return roundTimeLimit.get();
    }

    int getWinnerWaitingTicks() {
        return winnerWaitingTime.get();
    }

    // ===== BaseRoundMap 生命周期回调 =====

    @Override
    protected RoundLifecycle<String, CSRoundResultReason> buildRoundLifecycle() {
        return lifecycleBuilder()
                .waitingTicks(getWaitingTimeTicks())
                .roundTicks(Integer.MAX_VALUE)
                .roundEndTicks(getWinnerWaitingTicks())
                .addRule(new CSBombExplodedRule())
                .addRule(new CSBombDefusedRule())
                .addRule(new CSEliminationRule())
                .addRule(new CSRoundTimeoutRule(getRoundTimeLimitTicks()))
                .build();
    }

    @Override
    protected RoundContext createRoundContext() {
        return new CSMapRoundContext(this);
    }

    @Override
    protected boolean shouldAdvanceRoundLifecycle() {
        return !checkPauseTime() && !checkWarmUpTime() && !isKnifeSelectingVote();
    }

    @Override
    protected void onRoundStart() {
        this.pendingFinalKillAssist = null;
        this.roundClutchCandidates.clear();
        this.roundStarted = true;
        this.isWaiting = false;
        this.onRoundStarted();
    }

    @Override
    protected void onRoundEnd(RoundResult<String, CSRoundResultReason> result) {
        this.getMapTeams().getNormalTeams().stream()
                .filter(team -> team.getFixedName().equals(result.winner()))
                .findFirst().ifPresent(winner -> this.roundVictory(winner, toLegacyReason(result.reason())));
    }

    @Override
    protected void onNextRoundRequested() {
        this.startNewRound();
    }

    @Override
    protected void onWaitingTick(RoundContext ctx) {
        int elapsed = this.roundLifecycle.phaseElapsedTicks();
        if (waitingTime.get() - elapsed <= 60 && elapsed % 20 == 0) {
            this.sendPacketToAllPlayer(new FPSMusicPlayS2CPacket(SoundEvents.NOTE_BLOCK_BELL.value().getLocation()));
        }
        this.checkPauseVote();
    }

    @Override
    protected void onRoundTick(RoundContext ctx) {
        if (this.blastState() != BlastBombState.NONE) {
            this.currentRoundTime = -1;
            return;
        }
        this.currentRoundTime = this.roundLifecycle.roundElapsedTicks() + 1;
        if (this.isClosedShop()) {
            this.isShopLocked = true;
            this.syncShopInfo(false, 0);
        }
    }

    private CSGameMap.WinnerReason toLegacyReason(CSRoundResultReason reason) {
        return switch (reason) {
            case TIME_OUT -> CSGameMap.WinnerReason.TIME_OUT;
            case ACED -> CSGameMap.WinnerReason.ACED;
            case DEFUSE_BOMB -> CSGameMap.WinnerReason.DEFUSE_BOMB;
            case DETONATE_BOMB -> CSGameMap.WinnerReason.DETONATE_BOMB;
        };
    }

    public boolean isClosedShop(){
        return (this.currentRoundTime >= this.closeShopTime.get() || this.currentRoundTime == -1 ) && !this.isShopLocked;
    }

    public int getShopCloseTime(){
        if(knifeSelection.get() && !isKnifeSelected) return 0;

        int closeTime = (this.closeShopTime.get() - this.currentRoundTime);
        if (closeTime < 0) return 0;
        if(this.isWaiting){
            closeTime += this.waitingTime.get() - this.currentPauseTime;
        }
        return closeTime / 20;
    }

    @Override
    public Team.Visibility nameTagVisibility() {
        return hideEnemyNameTag.get() ? Team.Visibility.HIDE_FOR_OTHER_TEAMS : Team.Visibility.ALWAYS;
    }

    @Override
    public boolean isError() {
        return isError;
    }

    @Override
    public boolean isPause() {
        return isPause;
    }

    @Override
    public boolean isWaiting() {
        return isWaiting;
    }

    @Override
    public boolean isWaitingWinner() {
        return isWaitingWinner;
    }

    @Override
    public boolean canGiveEconomy() {
        return this.isStart;
    }

    /**
     * 向所有玩家发送标题消息
     * @param title 主标题内容
     * @param subtitle 副标题内容（可选）
     * @see ClientboundSetTitleTextPacket Mojang网络协议包
     */
    public void sendAllPlayerTitle(Component title,@Nullable Component subtitle){
        this.getMapTeams().getJoinedPlayers().forEach((data -> data.getPlayer().ifPresent(player -> {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            if(subtitle != null){
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        })));
    }

    /**
     * 统一发送金钱奖励消息
     */
    private void sendMoneyRewardMessage(ServerPlayer player, int amount, String reasonDesc) {
        player.sendSystemMessage(Component.translatable(
                "blockoffensive.map.cs.reward.money",
                amount,
                reasonDesc
        ));
    }


    private void onRoundStarted(){
        this.sendNewRoundVoice();
    }

    /**
     * 处理回合胜利逻辑
     * @param winnerTeam 获胜队伍
     * @param reason 胜利原因（如炸弹拆除/爆炸）
     * @see #checkMatchPoint() 检查赛点状态
     * @see MVPMusicManager MVP音乐播放逻辑
     */
    void roundVictory(@NotNull ServerTeam winnerTeam, @NotNull WinnerReason reason) {
        if (handleKnifeRoundSpecialCase(winnerTeam)) {
            return;
        }

        if (isWaitingWinner) {
            return;
        }

        MapTeams mapTeams = getMapTeams();
        isWaitingWinner = true;
        lastWinnerReason = reason;

        MvpReason mvpReason = processMvpLogic(winnerTeam, reason, mapTeams);

        sendPacketToAllPlayer(new MvpMessageS2CPacket(mvpReason));
        sendMvpMusicPacket(mvpReason);
        MinecraftForge.EVENT_BUS.post(new CSGameRoundEndEvent(this, winnerTeam, reason));


        processRoundScoreAndOvertimeVote(winnerTeam);

        processEconomicRewards(winnerTeam, reason, mapTeams);
    }

    /**
     * 处理刀局特殊情况
     * @return 是否触发刀局逻辑（是则返回true，中断主流程）
     */
    private boolean handleKnifeRoundSpecialCase(@NotNull ServerTeam winnerTeam) {
        if (knifeSelection.get() && !isKnifeSelected) {
            knifeRoundVictory(winnerTeam);
            this.isWaitingWinner = true;
            return true;
        }
        return false;
    }

    /**
     * 刀局胜利处理
     */
    private void knifeRoundVictory(ServerTeam winnerTeam) {
        Component translation = Component.translatable("blockoffensive.cs.knife.selection");
        Component message = Component.translatable("blockoffensive.map.vote.message","System",translation);
        pause();
        this.isKnifeSelected = true;
        VoteObj knife = new VoteObj(
                "knife",
                message,
                20,
                0.6F,
                ()->{
                    this.switchTeams();
                    this.setUnPauseState();
                    this.start();
                },
                ()-> {
                    this.setUnPauseState();
                    this.start();
                },
                winnerTeam.getPlayerList()
        );
        this.startVote(knife);
    }

    public boolean isKnifeSelectingVote(){
        return this.getVote() != null && this.getVote().getVoteTitle().equals("knife");
    }

    public void recordBombPlanted(@NotNull ServerPlayer player) {
        this.lastBombPlanter = player.getUUID();
    }

    public void recordBombDefused(@NotNull ServerPlayer player) {
        this.lastBombDefuser = player.getUUID();
    }

    /**
     * 处理MVP相关逻辑（提取MVP数据、发布事件、播放MVP音乐）
     * @return 处理后的MVP原因（为空时返回默认实例）
     */
    @Override
    protected MapTeams.RawMVPData getRoundMvpPlayer(@NotNull ServerTeam winnerTeam, @NotNull MapTeams mapTeams) {
        return getCombatMvp(winnerTeam, mapTeams);
    }

    protected MapTeams.RawMVPData getRoundMvpPlayer(@NotNull ServerTeam winnerTeam, @NotNull MapTeams mapTeams, @NotNull WinnerReason reason) {
        CSMvpResult result = selectCsMvp(winnerTeam, reason);
        if (result == null) {
            return null;
        }
        awardRoundMvp(winnerTeam, result.uuid());
        return new MapTeams.RawMVPData(result.uuid(), result.reasonKey());
    }

    @Override
    protected MapTeams.RawMVPData getGameMvp(@NotNull BaseTeam winnerTeam, @NotNull MapTeams mapTeams) {
        return getCombatMvp((ServerTeam) winnerTeam, mapTeams);
    }

    protected MapTeams.RawMVPData getGameMvp(@NotNull BaseTeam winnerTeam, @NotNull MapTeams mapTeams, @NotNull WinnerReason reason) {
        return getRoundMvpPlayer((ServerTeam) winnerTeam, mapTeams, reason);
    }

    private MapTeams.RawMVPData getCombatMvp(@NotNull ServerTeam winnerTeam, @NotNull MapTeams mapTeams) {
        CSMvpResult result = selectCsMvp(winnerTeam, lastWinnerReason);
        if (result == null) {
            return null;
        }
        awardRoundMvp(winnerTeam, result.uuid());
        return new MapTeams.RawMVPData(result.uuid(), result.reasonKey());
    }

    private void awardRoundMvp(@NotNull ServerTeam winnerTeam, @NotNull UUID uuid) {
        PlayerData data = winnerTeam.getPlayers().get(uuid);
        if (data != null) {
            data.addMvpCount(1);
        }
    }

    private CSMvpResult selectCsMvp(@NotNull ServerTeam winnerTeam, @NotNull WinnerReason reason) {
        List<CSMvpContribution> contributions = winnerTeam.getPlayersData().stream()
                .map(data -> new CSMvpContribution(
                        data.getOwner(),
                        data.getTempKills(),
                        data.getTempAssists() + pendingFinalKillAssistBonus(data.getOwner()),
                        data.getTempDamage(),
                        data.getHeadshotKills(),
                        0,
                        0,
                        roundIncendiaryDamage.getOrDefault(data.getOwner(), 0.0F),
                        roundExplosiveDamage.getOrDefault(data.getOwner(), 0.0F),
                        reason == WinnerReason.DEFUSE_BOMB && data.getOwner().equals(lastBombDefuser),
                        reason == WinnerReason.DETONATE_BOMB && data.getOwner().equals(lastBombPlanter),
                        data.isLivingOnServer() && roundClutchCandidates.contains(data.getOwner())
                ))
                .toList();
        CSMvpResult result = CSMvpScorer.selectRoundMvp(contributions);
        pendingFinalKillAssist = null;
        return result;
    }

    private int pendingFinalKillAssistBonus(@NotNull UUID uuid) {
        return uuid.equals(pendingFinalKillAssist) ? 1 : 0;
    }

    private MapTeams.RawMVPData getObjectiveMvp(@NotNull ServerTeam winnerTeam, @NotNull WinnerReason reason) {
        return getRoundMvpPlayer(winnerTeam, getMapTeams(), reason);
    }



    /**
     * 处理比分更新和加时投票逻辑
     */
    private void processRoundScoreAndOvertimeVote(@NotNull ServerTeam winnerTeam) {
        int targetForOvertime = winnerRound.get() - 1;
        winnerTeam.setScores(winnerTeam.getScores() + 1);

        if (!isOvertime) {
            if (getT().getScores() == targetForOvertime && getCT().getScores() == targetForOvertime) {
                this.isWaitingOverTimeVote = true;
            }
        }
    }

    /**
     * 统一处理所有经济奖励（胜利方 + 失败方 + CT额外奖励）
     */
    private void processEconomicRewards(@NotNull ServerTeam winTeam, @NotNull WinnerReason reason, @NotNull MapTeams mapTeams) {
        List<ServerTeam> loseTeams = mapTeams.getNormalTeams(winTeam);

        // 检查连胜情况
        checkLoseStreaks(winTeam,loseTeams);

        //胜利方经济奖励
        processWinnerEconomicReward(winTeam, reason);

        //失败方经济奖励
        loseTeams.forEach(loserTeam -> processLoserEconomicReward(loserTeam, reason));

        //CT队伍额外奖励
        processCTTeamExtraReward();
    }

    /**
     * 处理胜利方经济奖励
     */
    private void processWinnerEconomicReward(@NotNull ServerTeam winTeam,WinnerReason reason) {
        winTeam.getPlayerList().forEach(uuid -> {
            int money = reason.getWinMoney(this);
            ShopCapability.getShop(winTeam).ifPresent(shop -> shop.addMoney(uuid,money));

            getPlayerByUUID(uuid).ifPresent(player ->
                    sendMoneyRewardMessage(player, money, reason.name())
            );
        });
    }

    /**
     * 处理失败方经济奖励
     */
    private void processLoserEconomicReward(@NotNull ServerTeam loserTeam, @NotNull WinnerReason reason) {
        int compensationFactor = getCompensation(loserTeam).getFactor();
        boolean isDefuseBonusApplicable = checkCanPlacingBombs(loserTeam.getFixedName())
                && reason == WinnerReason.DEFUSE_BOMB;

        // 基础经济 + 拆弹额外奖励
        int baseEconomy = defaultLoserEconomy.get() + (isDefuseBonusApplicable ? defuseBonus.get() : 0);
        // 总失败补偿 = 基础经济 + 连败补偿
        int totalLossCompensation = baseEconomy + (compensationBase.get() * compensationFactor);
        String rewardDesc = baseEconomy + " + " + compensationBase.get() + " * " + compensationFactor;

        loserTeam.getPlayerList().forEach(uuid -> {
            // 仅在符合条件时发放补偿
            boolean shouldGiveCompensation = shouldLoserGetCompensation(loserTeam, uuid, reason);
            int finalReward = shouldGiveCompensation ? totalLossCompensation : 0;
            String finalDesc = shouldGiveCompensation ? rewardDesc : "timeout living";

            ShopCapability.getShop(loserTeam).ifPresent(shop -> shop.addMoney(uuid,finalReward));

            // 发送奖励消息
            getPlayerByUUID(uuid).ifPresent(player ->
                    sendMoneyRewardMessage(player, finalReward, finalDesc)
            );
        });
    }

    /**
     * 检查失败方玩家是否符合补偿发放条件
     */
    private boolean shouldLoserGetCompensation(@NotNull ServerTeam loserTeam, UUID uuid, @NotNull WinnerReason reason) {
        // 非超时情况直接发放
        if (reason != WinnerReason.TIME_OUT) {
            return true;
        }

        // 超时情况：仅给死亡玩家发放
        return loserTeam.getPlayerData(uuid)
                .map(data -> !data.isLivingOnServer())
                .orElse(false); // 无玩家数据时不发放
    }

    private void processCTTeamExtraReward() {
        ServerTeam tTeam = getT();
        ServerTeam ctTeam = getCT();

        long deadTCount = tTeam.getPlayersData().stream()
                .filter(data -> !data.isLivingOnServer())
                .count();

        int extraReward = (int) deadTCount * tDeathRewardPer.get();
        if (extraReward <= 0) {
            return;
        }

        ctTeam.getPlayerList().forEach(uuid -> {
            ShopCapability.getShop(ctTeam).ifPresent(shop -> shop.addMoney(uuid, extraReward));
        });

        ctTeam.sendMessage(Component.translatable("blockoffensive.map.cs.reward.team", extraReward, deadTCount));
    }

    private MvpReason processMvpLogic(@NotNull ServerTeam winnerTeam, @NotNull WinnerReason reason, @NotNull MapTeams mapTeams) {
        MapTeams.RawMVPData rawMvpData = getRoundMvpPlayer(winnerTeam, mapTeams, reason);
        if (rawMvpData == null) {
            return buildEmptyMvpReason(winnerTeam);
        }

        String playerName = getPlayerName(rawMvpData.uuid());
        String reasonKey = normalizeReasonKey(rawMvpData.reason());
        String infoKey = resolveInfoKey(reasonKey);
        String musicName = getMvpMusicName(rawMvpData.uuid());

        MvpReason mvpReason = buildMvpReason(rawMvpData.uuid(), winnerTeam, playerName, reasonKey, infoKey, musicName);
        getPlayerByUUID(rawMvpData.uuid())
                .ifPresent(player -> MinecraftForge.EVENT_BUS.post(new CSGamePlayerGetMvpEvent(player, this, mvpReason)));
        return mvpReason;
    }

    private MvpReason buildEmptyMvpReason(@NotNull ServerTeam winnerTeam) {
        return new MvpReason.Builder(null)
                .setCtWinner(isCtWinner(winnerTeam))
                .setTeamName(Component.literal(winnerTeam.getFixedName()))
                .setMvpReason(Component.translatable("blockoffensive.mvp.combat"))
                .build();
    }

    private MvpReason buildMvpReason(UUID uuid, @NotNull ServerTeam winnerTeam, String playerName, String reasonKey, String infoKey, String musicName) {
        return new MvpReason.Builder(uuid)
                .setCtWinner(isCtWinner(winnerTeam))
                .setTeamName(Component.literal(winnerTeam.getFixedName()))
                .setPlayerName(Component.literal(playerName))
                .setMvpReason(Component.translatable(reasonKey))
                .setExtraInfo1(Component.translatable(infoKey, Component.literal(playerName)))
                .setExtraInfo2(Component.literal(musicName))
                .build();
    }

    private boolean isCtWinner(@NotNull ServerTeam winnerTeam) {
        return winnerTeam.getFixedName().equalsIgnoreCase(getCT().getFixedName());
    }

    private String getPlayerName(UUID uuid) {
        return getPlayerByUUID(uuid)
                .map(player -> player.getDisplayName().getString())
                .filter(name -> !name.isBlank())
                .orElse(uuid.toString());
    }

    private String normalizeReasonKey(String reasonKey) {
        if (reasonKey == null || reasonKey.isBlank()) {
            return "blockoffensive.mvp.combat";
        }
        return reasonKey;
    }

    private String resolveInfoKey(String reasonKey) {
        return switch (reasonKey) {
            case "blockoffensive.mvp.defuse" -> "blockoffensive.mvp.info.defuse";
            case "blockoffensive.mvp.detonate" -> "blockoffensive.mvp.info.detonate";
            case "blockoffensive.mvp.incendiary_damage" -> "blockoffensive.mvp.info.incendiary_damage";
            case "blockoffensive.mvp.explosive_damage" -> "blockoffensive.mvp.info.explosive_damage";
            case "blockoffensive.mvp.high_damage" -> "blockoffensive.mvp.info.high_damage";
            case "blockoffensive.mvp.survive_under_fire" -> "blockoffensive.mvp.info.survive_under_fire";
            default -> "blockoffensive.mvp.info.combat";
        };
    }

    private String getMvpMusicName(UUID uuid) {
        if (!MVPMusicManager.getInstance().playerHasMvpMusic(uuid.toString())) {
            return MvpMusicManager.getDefaultMvpMusicName();
        }
        return MVPMusicManager.getInstance().getMvpMusicName(uuid.toString());
    }

    private void sendMvpMusicPacket(MvpReason mvpReason) {
        if (mvpReason.uuid == null) {
            return;
        }
        ResourceLocation music;
        if (MVPMusicManager.getInstance().playerHasMvpMusic(mvpReason.uuid.toString())) {
            music = MVPMusicManager.getInstance().getMvpMusic(mvpReason.uuid.toString());
        } else {
            music = MvpMusicManager.getDefaultMvpMusic();
        }
        if (music != null) {
            sendPacketToAllPlayer(new FPSMusicPlayS2CPacket(music));
        }
    }


    private void checkLoseStreaks(ServerTeam winTeam, @NotNull List<ServerTeam> loseTeams) {
        winTeam.getCapabilityMap().get(CompensationCapability.class).ifPresentOrElse(cap-> cap.reduce(2),()->FPSMatch.LOGGER.error("Failed to reduce Compensation capability"));

        loseTeams.forEach(team -> team.getCapabilityMap().get(CompensationCapability.class).ifPresentOrElse(cap-> cap.add(1),()->FPSMatch.LOGGER.error("Failed to add Compensation capability")));
    }

    public void startNewRound() {
        boolean check = this.setTeamSpawnPoints();
        if(!check){
            this.reset();
        }else{
            this.isStart = true;
            this.isWaiting = true;
            this.isWaitingWinner = false;
            this.roundStarted = false;
            this.lastBombPlanter = null;
            this.lastBombDefuser = null;
            this.roundIncendiaryDamage.clear();
            this.roundExplosiveDamage.clear();
            this.roundClutchCandidates.clear();
            this.cleanupMap();
            this.sendRoundDamageMessage();
            this.getMapTeams().getJoinedPlayers().forEach((data -> data.getPlayer().ifPresentOrElse(player->{
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION,-1,2,false,false,false));
                this.teleportPlayerToReSpawnPoint(player);
            },()->{
                BulletproofArmorAttribute.removePlayer(data.getOwner());
            })));

            if(knifeSelection.get() && !isKnifeSelected){
                syncShopInfo(false,getShopCloseTime());
            }else {
                syncShopInfo(true,getShopCloseTime());
                this.giveBlastTeamBomb();
                this.checkMatchPoint();
            }
            this.getMapTeams().startNewRound();
            syncShopDataToClient();
            syncNormalRoundStartMessage();
            this.rebuildRoundLifecycle();
        }
    }

    public void checkMatchPoint(){
        int ctScore = this.getCT().getScores();
        int tScore = this.getT().getScores();
        if(this.isOvertime){
            int check = winnerRound.get() - 1 - 6 * this.overCount + 4;

            if(ctScore - check == 1 || tScore - check == 1){
                this.sendAllPlayerTitle(Component.translatable("blockoffensive.map.cs.match.point").withStyle(ChatFormatting.RED),null);
                this.sendPacketToAllPlayer(new FPSMSoundPlayS2CPacket(BOSoundRegister.MATCH_POINT.get().getLocation()));
            }
        }else{
            if(ctScore == winnerRound.get() - 1 || tScore == winnerRound.get() - 1){
                this.sendAllPlayerTitle(Component.translatable("blockoffensive.map.cs.match.point").withStyle(ChatFormatting.RED),null);
                this.sendPacketToAllPlayer(new FPSMSoundPlayS2CPacket(BOSoundRegister.MATCH_POINT.get().getLocation()));
            }
        }
    }

    private void syncNormalRoundStartMessage() {
        this.sendPacketToAllPlayer(new MvpHUDCloseS2CPacket());
        this.sendPacketToAllPlayer(new FPSMusicStopS2CPacket());
        this.sendPacketToAllPlayer(new BombDemolitionProgressS2CPacket(0));
        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> this.getPlayerByUUID(uuid).ifPresent(this::syncInventory)));
    }

    private void syncShopDataToClient() {
        CapabilityMap.getTeamCapability(this, ShopCapability.class)
                .forEach((team, opt) -> opt.ifPresent(ShopCapability::sync));
    }

    @Override
    public void victory() {
        super.victory();
        reset();
    }

    @Override
    public boolean victoryGoal() {
        if (this.isWaitingOverTimeVote || this.isDebug()) {
            return false;
        }

        ServerTeam winnerTeam = null;
        int requiredScore = calculateRequiredScore();

        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            if (team.getScores() >= requiredScore) {
                winnerTeam = team;
                break;
            }
        }

        if (winnerTeam == null) {
            return false;
        }

        handleVictory(winnerTeam);
        return true;
    }

    private int calculateRequiredScore() {
        int winner = winnerRound.get();
        if (!isOvertime) {
            return winner;
        }

        return winner + ((this.overCount + 1) * this.overtimeRound.get());
    }

    private String requiredScoreStr(){
        int winner = winnerRound.get();
        if (!isOvertime) {
            return "win score : " + winner;
        }

        return "win score = " + winner + "+ (" + this.overCount + "+1) " + "*" + this.overtimeRound + "=" + calculateRequiredScore();
    }

    private void handleVictory(@Nullable ServerTeam winnerTeam) {
        boolean isNull = winnerTeam == null;

        // 发送胜利消息
        ChatFormatting titleFormat = isNull ? ChatFormatting.GREEN : winnerTeam.name.equals("ct") ? ChatFormatting.DARK_AQUA : ChatFormatting.YELLOW;

        this.sendVictoryMessage(
                isNull ? Component.translatable("map.cs.message.victory.draw.head").withStyle(ChatFormatting.GOLD, ChatFormatting.GREEN) : Component.translatable("map.cs.message.victory.head", winnerTeam.name.toUpperCase(Locale.US)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Comparator.comparingDouble(PlayerData::getDamage).reversed()
        );

        // 发送胜利标题
        this.sendAllPlayerTitle(
                !isNull ? Component.translatable("blockoffensive.map.cs.winner.message",winnerTeam.name).withStyle(titleFormat) :
                        Component.translatable("blockoffensive.map.cs.winner.draw.message").withStyle(titleFormat),
                null
        );
    }

    public void startOvertimeVote() {
        Component translation = Component.translatable("blockoffensive.cs.overtime");
        Component message = Component.translatable("blockoffensive.map.vote.message","System",translation);
        VoteObj overtime = new VoteObj(
                "overtime",
                message,
                BOConfig.common.overtimeVoteSeconds.get(),
                BOConfig.common.overtimeVoteThreshold.get().floatValue(),
                this::startOvertime,
                ()->{
                    handleVictory(null);
                    reset();
                },
                this.getMapTeams().getJoinedUUID(),
                BOConfig.common.voteTimeoutPolicy.get(),
                BOConfig.common.voteAbstentionPolicy.get()
        );

        this.startVote(overtime);
    }

    public void startOvertime() {
        this.isOvertime = true;
        this.isWaitingOverTimeVote = false;

        int startMoney = BOConfig.common.overtimeStartMoney.get();
        this.getMapTeams().getNormalTeams().forEach(team-> {
            team.resetCapabilities();

            team.getPlayers().forEach((uuid, playerData) -> playerData.setLiving(false));

            team.getCapabilityMap().get(ShopCapability.class)
                    .flatMap(ShopCapability::getShopSafe).ifPresent(shop -> {
                        shop.setStartMoney(startMoney);
                        shop.resetPlayerData(true);
                    });
        });
        this.startNewRound();
    }

    public boolean cleanupMap() {
        if (!super.cleanupMap()) {
            return false;
        }
        this.objectiveTracker.roundReset();

        MapTeams mapTeams = getMapTeams();
        int ctScore = getCT().getScores();
        int tScore = getT().getScores();

        sendPhysicsRagdollRemovalPacket(PxRagdollRemovalCompatS2CPacket.ALL);
        cleanupSpecificEntities();
        notifySpectatorsOfBombFuse();

        boolean shouldSwitchTeams = handleOvertimeAndTeamSwitch(ctScore, tScore);

        resetMapBaseState();

        resetAllJoinedPlayersState(mapTeams, shouldSwitchTeams);

        knifeCache.clear();

        return true;
    }


    /**
     * 通知所有观察者玩家炸弹引信状态
     */
    private void notifySpectatorsOfBombFuse() {
        int initialFuse = 0;
        int maxFuseTime = BOConfig.common.fuseTime.get();
        BombFuseS2CPacket fusePacket = new BombFuseS2CPacket(initialFuse, maxFuseTime);

        getMapTeams().getSpecPlayers().forEach(pUUID ->
                FPSMCore.getInstance().getPlayerByUUID(pUUID)
                        .ifPresent(player -> BlockOffensive.INSTANCE.send(
                                PacketDistributor.PLAYER.with(() -> player),
                                fusePacket
                        ))
        );
    }

    private void pause(){
        isPause = true;
        currentPauseTime = 0;
        isWaiting = true;
    }

    /**
     * 处理加时赛投票与队伍切换逻辑
     * @return 是否需要切换队伍
     */
    private boolean handleOvertimeAndTeamSwitch(int ctScore, int tScore) {
        currentPauseTime = 0;

        // 计算关键分数阈值
        int scoreToTriggerOvertime = calculateScoreToTriggerOvertime();
        int maxNormalScore = winnerRound.get() - 1;

        if (!isOvertime) {
            return handleNormalTimeLogic(ctScore, tScore, maxNormalScore, scoreToTriggerOvertime);
        } else {
            return handleOvertimeLogic(ctScore, tScore, maxNormalScore);
        }
    }

    /**
     * 计算触发加时赛的分数阈值
     */
    private int calculateScoreToTriggerOvertime() {
        // 假设winnerRound是获胜所需轮数（如13），则触发加时赛的分数是winnerRound-1（如12）
        return winnerRound.get() - 1;
    }

    /**
     * 处理正常比赛时间的逻辑
     */
    private boolean handleNormalTimeLogic(int ctScore, int tScore, int maxNormalScore, int overtimeTriggerScore) {
        // 检查是否触发加时赛：双方都达到触发分数
        if (ctScore == overtimeTriggerScore && tScore == overtimeTriggerScore) {
            startOvertimeSequence();
            return false;
        }

        // 计算当前总局数
        int totalRoundsPlayed = getMapTeams().getNormalTeams().stream()
                .mapToInt(BaseTeam::getScores)
                .sum();

        // 是否达到换边条件：总局数达到换边阈值
        boolean shouldSwitchTeams = totalRoundsPlayed == maxNormalScore;

        if (shouldSwitchTeams) {
            isWaiting = true;
            switchTeams();
        }

        return shouldSwitchTeams;
    }

    /**
     * 开始加时赛序列。根据 {@link OvertimeMode} 决定 12-12 时的处理：
     * VOTE=发起加时投票；AUTO=直接进入加时；DISABLED=直接判平局。
     */
    private void startOvertimeSequence() {
        setBombEntity(null);
        currentRoundTime = 0;
        OvertimeMode mode = BOConfig.common.overtimeMode.get();
        switch (mode) {
            case AUTO -> startOvertime();
            case DISABLED -> {
                handleVictory(null);
                reset();
            }
            default -> startOvertimeVote();
        }
    }

    /**
     * 处理加时赛逻辑
     */
    private boolean handleOvertimeLogic(int ctScore, int tScore, int maxNormalScore) {
        int overtimeRound = this.overtimeRound.get();

        // 计算进入加时赛后的总局数
        int totalRoundsInOvertime = calculateTotalRoundsInOvertime(ctScore, tScore, maxNormalScore);

        // 检查是否需要换边：每overtimeRound局换一次
        boolean shouldSwitchTeams = shouldSwitchTeamsInOvertime(totalRoundsInOvertime, overtimeRound);

        if (shouldSwitchTeams) {
            isWaiting = true;
            switchTeams();

            // 检查是否应该增加加时赛轮次
            if (shouldIncreaseOvertimeCount(totalRoundsInOvertime, overtimeRound, ctScore, tScore, maxNormalScore)) {
                overCount++;
                // 加时段数上限保护：达到上限则判平局结束，防止无限加时（0=无限）
                int maxSegments = BOConfig.common.overtimeMaxSegments.get();
                if (maxSegments > 0 && overCount >= maxSegments) {
                    handleVictory(null);
                    reset();
                }
            }
        }

        return shouldSwitchTeams;
    }

    /**
     * 计算进入加时赛后的总局数
     */
    private int calculateTotalRoundsInOvertime(int ctScore, int tScore, int maxNormalScore) {
        // 总局数减去正常比赛最大可能分数（双方都达到触发加时赛的分数）
        return (ctScore + tScore) - (maxNormalScore * 2);
    }

    /**
     * 判断加时赛中是否需要换边
     */
    private boolean shouldSwitchTeamsInOvertime(int totalRoundsInOvertime, int overtimeRound) {
        return totalRoundsInOvertime != 0 && totalRoundsInOvertime % overtimeRound == 0;
    }

    /**
     * 判断是否应该增加加时赛计数
     */
    private boolean shouldIncreaseOvertimeCount(int totalRoundsInOvertime, int overtimeRound,
                                                int ctScore, int tScore, int maxNormalScore) {
        // 只有在完成一个加时赛段时才考虑增加计数
        int currentOverTimeRound = totalRoundsInOvertime - overCount * overtimeRound;

        if(currentOverTimeRound != overtimeRound * 2){
            return false;
        }

        // 计算当前加时赛段的分数上限
        int currentOvertimeScoreCap = calculateCurrentOvertimeScoreCap(maxNormalScore, overtimeRound);

        // 如果双方都未达到当前加时赛段的分数上限，则增加计数
        return ctScore < currentOvertimeScoreCap && tScore < currentOvertimeScoreCap;
    }

    /**
     * 计算当前加时赛段的分数上限
     * 公式解释：正常比赛最高分 + 已完成的加时赛局数 + (正常比赛最高分 + 1)
     * 例如：12 + (3 * 0) + (3 + 1) = 16
     * 表示第一个加时赛段需要达到16分才能获胜
     */
    private int calculateCurrentOvertimeScoreCap(int maxNormalScore, int overtimeRound) {
        return maxNormalScore + (overtimeRound * overCount) + (overtimeRound + 1);
    }

    /**
     * 重置地图基础状态（炸弹、回合时间、商店锁定）
     */
    public void resetMapBaseState() {
        setBombEntity(null);
        currentRoundTime = 0;
        isShopLocked = false;
    }

    /**
     * 重置所有已加入队伍的玩家状态
     */
    private void resetAllJoinedPlayersState(MapTeams mapTeams, boolean shouldSwitchTeams) {
        Component teamSwitchTitle = Component.translatable("blockoffensive.map.cs.team.switch")
                .withStyle(ChatFormatting.GREEN);

        mapTeams.getJoinedPlayers().forEach(data ->
                data.getPlayer().ifPresent(player -> processJoinedPlayer(data, player, shouldSwitchTeams, teamSwitchTitle))
        );
    }

    /**
     * 处理单个已加入队伍的玩家状态重置
     */
    private void processJoinedPlayer(PlayerData data, ServerPlayer player, boolean shouldSwitchTeams, Component teamSwitchTitle) {
        player.heal(player.getMaxHealth());
        player.setGameMode(GameType.ADVENTURE);

        if (shouldSwitchTeams) {
            clearInventory(player);
            givePlayerKits(player);
            sendPacketToJoinedPlayer(player, new ClientboundSetTitleTextPacket(teamSwitchTitle), true);
            return;
        }

        if (!data.isLiving()) {
            clearInventory(player);
            givePlayerKits(player);
        } else {
            FPSMUtil.resetAllGunAmmo(player);
        }

        ShopCapability.getPlayerShopData(this, player.getUUID())
                .ifPresent(shopData -> shopData.lockShopSlots(player));
    }


    @Override
    public void givePlayerKits(ServerPlayer player) {
        CapabilityMap.getTeamCapability(this,StartKitsCapability.class)
                .forEach((team, opt) -> {
                    if(team.hasPlayer(player.getUUID())){
                        opt.ifPresent(cap -> cap.givePlayerKits(player));
                    }
                });
    }

    /**
     * 为爆破方随机分配C4炸弹给一名在线玩家
     * @see CompositionC4 C4物品实体类
     * @see #cleanupMap() 回合结束清理残留C4
     */
    public void giveBlastTeamBomb() {
        this.getCapabilityMap().get(DemolitionModeCapability.class)
                .flatMap(cap -> this.getMapTeams().getTeamByFixedName(cap.getDemolitionTeam())).ifPresent((team) -> {
                    List<UUID> onlinePlayers = team.getOnlinePlayers();
                    if (onlinePlayers.isEmpty()) {
                        return;
                    }
                    // 清理队伍所有成员的C4
                    team.getPlayerList().forEach(uuid ->
                            clearInventory(uuid, itemStack -> itemStack.getItem() instanceof CompositionC4)
                    );

                    // 随机选择一名在线玩家
                    UUID selectedUuid = onlinePlayers.get(new Random().nextInt(onlinePlayers.size()));
                    this.getPlayerByUUID(selectedUuid).ifPresent(player -> {
                        // 添加C4并更新库存
                        player.getInventory().add(BOItemRegister.C4.get().getDefaultInstance());
                        team.sendMessage(Component.translatable("blockoffensive.map.cs.team.giveBomb", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
                        FPSMUtil.sortPlayerInventory(player);
                        this.syncInventory(player);
                        this.objectiveTracker.assignCarrier(
                                player.getUUID(),
                                player.level().getGameTime(),
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                player.getYRot(),
                                java.util.Optional.empty()
                        );
                    });
                });

    }

    public void setPauseState(ServerPlayer player){
        if(!this.isStart) return;
        this.getMapTeams().getTeamByPlayer(player).flatMap(team -> team.getCapabilityMap().get(PauseCapability.class)).ifPresent(cap -> {
            if (cap.canPause() && !this.isPause) {
                cap.addPause();
                if (!this.isWaiting) {
                    this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.cs.pause.nextRound.success").withStyle(ChatFormatting.GOLD), false);
                } else {
                    this.sendAllPlayerMessage(Component.translatable("blockoffensive.map.cs.pause.success").withStyle(ChatFormatting.GOLD), false);
                }
            } else {
                player.displayClientMessage(Component.translatable("blockoffensive.map.cs.pause.fail").withStyle(ChatFormatting.RED), false);
            }
        });
    }

    public void setUnPauseState(){
        this.isPause = false;
        this.currentPauseTime = 0;
    }

    private void startUnpauseVote(ServerPlayer serverPlayer) {
        if(this.getVote() == null){
            Component translation = Component.translatable("blockoffensive.cs.unpause");
            VoteObj unpause = new VoteObj(
                    "unpause",
                    Component.translatable("blockoffensive.map.vote.message",serverPlayer.getDisplayName(),translation),
                    15,
                    BOConfig.common.unpauseVoteThreshold.get().floatValue(),
                    this::setUnPauseState,
                    ()->{},
                    this.getMapTeams().getJoinedUUID(),
                    BOConfig.common.voteTimeoutPolicy.get(),
                    BOConfig.common.voteAbstentionPolicy.get()
            );
            this.startVote(unpause);
            this.getVote().processVote(serverPlayer,true);
        }else{
            Component translation = Component.translatable("blockoffensive.cs." + this.getVote().getVoteTitle());
            serverPlayer.displayClientMessage(Component.translatable("blockoffensive.map.vote.fail.alreadyHasVote", translation).withStyle(ChatFormatting.RED),false);
        }
    }

    private void handleDropKnifeCommand(ServerPlayer player) {
        List<ItemStack> list = FPSMUtil.searchInventoryForType(player.getInventory(),DropType.THIRD_WEAPON);
        int currentKnives = knifeCache.getOrDefault(player.getUUID(), 0);
        if(!list.isEmpty() && currentKnives < 5){
            knifeCache.put(player.getUUID(), currentKnives + 1);
            FPSMUtil.playerDropMatchItem(player,list.get(0).copy());
        }
    }

    @Override
    public void reset() {
        super.reset();
        MapTeams mapTeams = this.getMapTeams();
        this.isOvertime = false;
        this.isWaitingOverTimeVote = false;
        this.overCount = 0;
        this.isShopLocked = false;
        this.roundStarted = false;
        this.isError = false;
        this.isStart = false;
        this.isWaiting = false;
        this.isWaitingWinner = false;
        this.isWarmTime = false;
        this.isPause = false;
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isKnifeSelected = false;
        mapTeams.getJoinedPlayers().forEach(data-> {
            data.getPlayer().ifPresent(this::resetPlayerClientData);
            BulletproofArmorAttribute.removePlayer(data.getOwner());
        });
        mapTeams.reset();
    }

    /**
     * 切换两个队伍的阵营
     */
    @Override
    public void switchTeams() {
        // 换边前关闭商店，防止换边后显示错误阵营的商店界面
        syncShopInfo(false, 0);
        super.switchTeams();
        MinecraftForge.EVENT_BUS.post(new CSGameMapEvent.TeamSwitchEvent(this));
    }

    public boolean checkCanPlacingBombs(String team){
        return CapabilityMap.getMapCapability(this,DemolitionModeCapability.class)
                .map(cap -> cap.getDemolitionTeam().equals(team))
                .orElse(false);
    }

    public void setBombEntity(@Nullable BlastBombEntity bomb) {
        CapabilityMap.getMapCapability(this,DemolitionModeCapability.class)
                .ifPresent(cap -> cap.setBombEntity(bomb));
    }

    public BlastBombState blastState() {
        return CapabilityMap.getMapCapability(this,DemolitionModeCapability.class)
                .map(DemolitionModeCapability::blastState)
                .orElse(BlastBombState.NONE);
    }

    @Override
    public int getClientTime(){
        int time;
        if(this.isPause){
            time = pauseTime.get() - this.currentPauseTime;
        }else {
            if (this.isWaiting) {
                time = waitingTime.get() - this.currentPauseTime;
            } else if (this.isWarmTime) {
                time = warmUpTime.get() - this.currentPauseTime;
            } else if (this.isWaitingWinner) {
                time = winnerWaitingTime.get() - this.currentPauseTime;
            } else if(this.blastState() != BlastBombState.NONE){
                return -1;
            }else if (this.isStart) {
                time = roundTimeLimit.get() - this.currentRoundTime;
            }else {
                time = 0;
            }
        }
        return time;
    }

    /**
     * 同步游戏设置到客户端（比分/时间等）
     * @see CSGameSettingsS2CPacket
     */
    @Override
    public void syncToClient() {
        super.syncToClient();
        this.syncToClient(true);
    }

    @Override
    public boolean setTeamSpawnPoints(){
        for (ServerTeam team : this.getMapTeams().getNormalTeams()){
            Optional<SpawnPointCapability> capability = team.getCapabilityMap().get(SpawnPointCapability.class);
            if(capability.isPresent()){
                SpawnPointCapability cap = capability.get();
                if(!cap.randomSpawnPoints()){
                    return false;
                }
            }
        }
        return true;
    }

    public List<AreaData> getBombAreaData() {
        return this.getCapabilityMap().get(DemolitionModeCapability.class).map(DemolitionModeCapability::getBombAreaData).orElse(Collections.emptyList());
    }

    public boolean checkPlayerIsInBombArea(@NotNull Player player) {
        return this.getCapabilityMap().get(DemolitionModeCapability.class).map(cap->cap.checkPlayerIsInBombArea(player)).orElse(false);
    }

    public int getC4InstantKillRadius(){
        return c4InstantKillRadius.get();
    }

    @Override
    protected boolean shouldSendPhysicsDeathPacketInBaseDeathHandler(DeathContext context) {
        return !this.isStart || this.getMapTeams().getTeamByPlayer(context.getDeadPlayer()).isEmpty();
    }

    public DeathMarkerLedger deathMarkerLedger() {
        return deathMarkerLedger;
    }

    public CSGameObjectiveTracker objectiveTracker() {
        return objectiveTracker;
    }

    @Override
    public void handleDeath(DeathContext context){
        ServerPlayer dead = context.getDeadPlayer();
        // Capture authoritative death pose before spectator/camera transition.
        String teamId = this.getMapTeams().getTeamByPlayer(dead)
                .map(BaseTeam::getFixedName)
                .orElse("spectator");
        CSDeathMarkerCapture.captureFromWorldPose(
                deathMarkerLedger,
                dead.getUUID(),
                teamId,
                dead.getX(),
                dead.getY(),
                dead.getZ(),
                dead.getYRot(),
                context.getCreatedTick(),
                CSMapMinimapMarkerProvider.CS_DEATH_TTL_TICKS,
                java.util.Optional.empty()
        );
        pendingFinalKillAssist = calculatePendingFinalKillAssist(context);
        if(this.isStart){
            MapTeams teams = this.getMapTeams();
            teams.getTeamByPlayer(dead).ifPresent(deadPlayerTeam -> {
                sendPhysicsDeathPacket(dead);
                CapabilityMap.getTeamCapability(deadPlayerTeam, ShopCapability.class)
                        .flatMap(ShopCapability::getShopSafe).ifPresent(shop -> shop.getDefaultAndPutData(dead.getUUID()));

                this.sendPacketToJoinedPlayer(dead, new ShopStatesS2CPacket(false, 0, 0), true);
                ItemEntity droppedC4 = dropC4(dead);
                if (droppedC4 != null) {
                    this.objectiveTracker.carrierDisconnectedOrDied(
                            droppedC4.getId(),
                            droppedC4.getUUID(),
                            dead.level().getGameTime(),
                            droppedC4.getX(),
                            droppedC4.getY(),
                            droppedC4.getZ(),
                            droppedC4.getYRot(),
                            Optional.empty()
                    );
                }
                discardAmmo(dead.getUUID());
                int ik = dead.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof BombDisposalKit, -1, dead.inventoryMenu.getCraftSlots());
                if (ik > 0) {
                    dead.drop(new ItemStack(BOItemRegister.BOMB_DISPOSAL_KIT.get(), 1), false, false);
                }
                FPSMUtil.playerDeadDropWeapon(dead, true);
                dead.getInventory().clearContent();
                this.syncInventory(dead);

                dead.heal(dead.getMaxHealth());
                dead.setGameMode(GameType.SPECTATOR);
                dead.setRespawnPosition(dead.level().dimension(), dead.getOnPos().above(), 0, true, false);
                this.setBystander(dead);
            });
        }

        super.handleDeath(context);
        if (this.isStart && this.roundStarted) {
            updateClutchSurviveCandidates();
        }
    }

    /**
     * Mark clutch candidates: sole living teammate while 2+ enemies remain alive.
     * Called after living flags are updated by the base death handler.
     */
    private void updateClutchSurviveCandidates() {
        MapTeams teams = this.getMapTeams();
        for (ServerTeam team : teams.getNormalTeams()) {
            List<UUID> living = team.getLivingPlayers();
            if (living.size() != 1) {
                continue;
            }
            int enemyLiving = 0;
            for (ServerTeam other : teams.getNormalTeams()) {
                if (other.equals(team)) {
                    continue;
                }
                enemyLiving += other.getLivingPlayers().size();
            }
            if (enemyLiving >= 2) {
                roundClutchCandidates.add(living.get(0));
            }
        }
    }

    private UUID calculatePendingFinalKillAssist(@NotNull DeathContext context) {
        ServerPlayer attacker = context.getAttacker();
        if (attacker == null || attacker.getUUID().equals(context.getDeadPlayer().getUUID())) {
            return null;
        }
        return BOUtil.calculateAssistPlayer(this, context.getDeadPlayer(), this.getMinAssistDamageRatio())
                .map(PlayerData::getOwner)
                .filter(uuid -> !uuid.equals(attacker.getUUID()))
                .orElse(null);
    }

    @Override
    public void recordHurtData(ServerPlayer hurt, DamageSource source, float amount) {
        if (isC4Damage(source)) {
            return;
        }
        recordTypedRoundDamage(hurt, source, amount);
        super.recordHurtData(hurt, source, amount);
    }

    private void recordTypedRoundDamage(ServerPlayer hurt, DamageSource source, float amount) {
        getAttackerFromDamageSource(source).ifPresent(attacker -> {
            if (!isValidAttack(attacker, hurt) || getMapTeams().isSameTeam(attacker, hurt)) {
                return;
            }
            DamageSourceCategory category = MinecraftDamageSourceClassifier.classify(source);
            if (category == DamageSourceCategory.INCENDIARY || category == DamageSourceCategory.FIRE) {
                roundIncendiaryDamage.merge(attacker.getUUID(), amount, Float::sum);
            } else if (category == DamageSourceCategory.EXPLOSIVE) {
                roundExplosiveDamage.merge(attacker.getUUID(), amount, Float::sum);
            }
        });
    }

    private boolean isC4Damage(DamageSource source) {
        return source.getDirectEntity() instanceof CompositionC4Entity
                || source.getEntity() instanceof CompositionC4Entity;
    }

    public void discardAmmo(UUID uuid){
        this.getServerLevel().getEntitiesOfClass(EntityKineticBullet.class, mapArea.aabb())
                .stream()
                .filter(entityKineticBullet -> entityKineticBullet.getOwner() != null && entityKineticBullet.getOwner().getUUID().equals(uuid))
                .toList()
                .forEach(Entity::discard);
    }

    public int getDefuseBombEconomy() {
        return defuseBombEconomy.get();
    }

    public int getTimeoutEconomy() {
        return timeoutEconomy.get();
    }

    public int getDetonateBombEconomy() {
        return detonateBombEconomy.get();
    }

    public int getAceEconomy() {
        return aceEconomy.get();
    }

    public enum WinnerReason{
        TIME_OUT(CSGameMap::getTimeoutEconomy),
        ACED(CSGameMap::getAceEconomy),
        DEFUSE_BOMB(CSGameMap::getDefuseBombEconomy),
        DETONATE_BOMB(CSGameMap::getDetonateBombEconomy);
        private final Function<CSGameMap,Integer> winMoney;

        WinnerReason(Function<CSGameMap,Integer> winMoney) {
            this.winMoney = winMoney;
        }

        public int getWinMoney(CSGameMap map) {
            return winMoney.apply(map);
        }
    }

}
