package com.ptcrys.blockoffensive.map;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.common.capability.team.StartKitsCapability;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.capability.CapabilityMap;
import com.ptcrys.fpsmatch.core.capability.map.MapCapability;
import com.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.data.Setting;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.map.DeathContext;
import com.ptcrys.fpsmatch.core.match.RoundLifecycle;
import com.ptcrys.fpsmatch.core.match.RoundResult;
import com.ptcrys.fpsmatch.core.persistence.FPSMDataManager;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import com.ptcrys.fpsmatch.core.team.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Team;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CSDeathMatchMap extends CSMap {
    /**
     * Codec序列化配置（用于地图数据保存/加载）
     */
    public static final Codec<CSDeathMatchMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // 基础地图数据
            Codec.STRING.fieldOf("mapName").forGetter(CSDeathMatchMap::getMapName),
            AreaData.CODEC.fieldOf("mapArea").forGetter(CSDeathMatchMap::getMapArea),
            ResourceLocation.CODEC.fieldOf("serverLevel").forGetter(map -> map.getServerLevel().dimension().location()),
            CapabilityMap.Wrapper.DATA_CODEC.fieldOf("capabilities").forGetter(csGameMap -> csGameMap.getCapabilityMap().getData().data()),
            // 队伍数据
            Codec.unboundedMap(
                    Codec.STRING,
                    CapabilityMap.Wrapper.CODEC
            ).fieldOf("teams").forGetter(CSDeathMatchMap::getPersistentTeamData)
    ).apply(instance, CSDeathMatchMap::new));

    public static final String TYPE = "csdm";
    private static final List<Class<? extends MapCapability>> MAP_CAPABILITIES = List.of(GameEndTeleportCapability.class);
    private static final List<Class<? extends TeamCapability>> TEAM_CAPABILITIES = List.of(ShopCapability.class, StartKitsCapability.class, SpawnPointCapability.class);
    
    // 死斗模式设置
    private Setting<Boolean> isTDM;
    private Setting<Integer> matchTimeLimit;
    private Setting<Integer> spawnProtectionTime;
    private boolean deathmatchTeamsInitialized;

    private static final String LEGACY_PLAYER_TEAM_PREFIX = "player_";
    private static final int DEATHMATCH_SHOP_MONEY = 16000;
    private static final int DEATHMATCH_SHOP_CLOSE_TIME = Integer.MAX_VALUE / 20;
    private int nextTeamIndex = 6; // 下一个动态队伍编号
    
    // 游戏状态
    private int currentMatchTime = 0;

    // 重生保护映射
    private final Map<UUID, DMPlayerData> playerData = new HashMap<>();
    private final List<SpawnPointData> spawnPoints = new ArrayList<>();

    private boolean isError = false;

    /**
     * 构造函数：创建CS死斗地图实例
     * @param serverLevel 服务器世界实例
     * @param mapName 地图名称
     * @param areaData 地图区域数据
     */
    public CSDeathMatchMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData, MAP_CAPABILITIES, TEAM_CAPABILITIES);
        ensureDeathmatchTeams();
        deathmatchTeamsInitialized = true;
    }

    private CSDeathMatchMap(String mapName, AreaData areaData, ResourceLocation serverLevel, Map<String, JsonElement> capabilities, Map<String, CapabilityMap.Wrapper> teams) {
        this(FPSMCore.getInstance().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,serverLevel)), mapName, areaData);
        this.getCapabilityMap().write(capabilities);
        this.getMapTeams().writeData(teams);
        // 恢复nextTeamIndex，避免与已有的动态队伍编号冲突
        restoreNextTeamIndex();
    }

    @Override
    public void syncToClient() {
        super.syncToClient();
        syncToClient(false);
    }

    @Override
    public void setup() {
        isTDM = this.addSetting(new DeathmatchModeSetting());
        matchTimeLimit = this.addSetting("match", "matchTimeLimit", 18000);
        // 重生保护时长（秒）：参照 CS2 死斗约 3~5 秒
        spawnProtectionTime = this.addSetting("player", "spawnProtectionTime", 5);
        // 死斗模式默认关闭敌方发光
        getEnemyGlowSetting().set(false);
    }

    @Override
    public Collection<Setting<?>> settings() {
        return List.of(
                isTDM,
                displayName,
                iconTexture,
                backgroundTexture,
                allowJoinInProgress,
                autoStart,
                autoStartTime,
                readyStartEnabled,
                readyStartTime,
                minAssistDamageRatio,
                getEnemyGlowSetting(),
                allowSpecAttach,
                magazineMode,
                matchTimeLimit,
                spawnProtectionTime
        );
    }

    @Override
    public ServerTeam addTeam(TeamData data){
        ServerTeam team = super.addTeam(data);
        team.getPlayerTeam().setAllowFriendlyFire(!isTDM());
        team.getCapabilityMap().get(ShopCapability.class).ifPresent(cap -> cap.initialize("cs", 16000));
        return team;
    }

    @Override
    public String getGameType() {
        return TYPE;
    }

    @Override
    public MapTeams.JoinTeamResult join(String teamName, ServerPlayer player){
        ensureDeathmatchTeams();
        String targetTeamName = deathmatchTeamNames().contains(teamName) ? teamName
                : selectDeathmatchTeamName(player.getUUID()).orElse(null);
        if (targetTeamName == null) {
            return MapTeams.JoinTeamResult.of(MapTeams.JoinTeamResult.Status.NO_AVAILABLE_TEAM);
        }
        MapTeams.JoinTeamResult result = super.join(targetTeamName, player);
        if (result.isSuccess()) {
            getMapTeams().getTeamByPlayer(player).ifPresent(team -> {
                this.playerData.put(player.getUUID(), new DMPlayerData(player.getUUID()));
                if(isStart){
                    respawnPlayer(player);
                }
            });
        }
        return result;
    }

    @Override
    public MapTeams.JoinTeamResult join(ServerPlayer player) {
        return join(null, player);
    }

    @Override
    public void leave(ServerPlayer player){
        // 先获取玩家所在的数字队伍
        Optional<ServerTeam> dmTeam = getMapTeams().getTeamByPlayer(player)
                .filter(team -> isDeathmatchTeamName(team.getName()));

        super.leave(player);
        this.playerData.remove(player.getUUID());

        // 离开后检查该数字队伍是否为空，空则销毁
        dmTeam.ifPresent(this::cleanupEmptyDeathmatchTeam);
    }
    
    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        
        MapTeams mapTeams = getMapTeams();

        this.isStart = true;
        this.currentMatchTime = matchTimeLimit.get();
        this.resetAllPlayerData();
        this.applyDeathmatchFriendlyFireRule();
        
        if (!this.setTeamSpawnPoints()) {
            this.reset();
            return false;
        }
        
        cleanupMap();
        mapTeams.startNewRound();
        mapTeams.resetLivingPlayers();

        initializePlayers(mapTeams);
        
        return true;
    }

    @Override
    protected boolean canAutoStart() {
        return !this.getMapTeams().getOnline().isEmpty();
    }

    @Override
    public void recordHurtData(ServerPlayer hurt, DamageSource source, float amount) {
        if (isTDM()) {
            super.recordHurtData(hurt, source, amount);
            return;
        }

        getAttackerFromDamageSource(source).ifPresent(attacker -> {
            if (!isValidAttack(attacker, hurt)) return;
            getMapTeams().addHurtData(attacker, hurt, amount);
        });
    }

    @Override
    public boolean cleanupMap() {
        if (!super.cleanupMap()) {
            return false;
        }

        this.cleanupSpecificEntities();

        return true;
    }

    @Override
    public void victory(){
        this.sendVictoryMessage(
                Component.translatable("map.deathmatch.message.victory.head").withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD),
                Comparator.comparingInt(PlayerData::getScores).reversed());
        super.victory();
        this.reset();
    }

    @Override
    public void reset(){
        super.reset();
        this.isError = false;
        this.isStart = false;
        this.currentMatchTime = 0;
        this.nextTeamIndex = 6;
        this.getMapTeams().getJoinedPlayers().forEach(data-> data.getPlayer().ifPresent(this::resetPlayerClientData));
        cleanupLegacyPlayerTeams();
        cleanupDynamicDeathmatchTeams();
        this.getMapTeams().reset();
    }

    public void resetAllPlayerData(){
        this.playerData.values().forEach(DMPlayerData::reset);
    }

    private void initializePlayers(MapTeams mapTeams) {
        mapTeams.getJoinedPlayersMap().forEach(this::initializePlayer);
    }
    
    private void initializePlayer(ServerTeam team,List<PlayerData> players) {
        team.resetCapabilities();

        players.forEach(data -> {
            data.reset();
            data.getPlayer().ifPresent(player -> {
                player.removeAllEffects();
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                this.clearInventory(player);
                this.givePlayerKits(player);
        });
    });
    }

    @Override
    public void tick() {
        super.tick();
        applyDeathmatchFriendlyFireRule();
    }

    @Override
    public boolean victoryGoal() {
        return false;
    }

    @Override
    protected RoundLifecycle<String, CSRoundResultReason> buildRoundLifecycle() {
        int limit = matchTimeLimit.get();
        return lifecycleBuilder()
                .waitingTicks(0)
                .roundTicks(limit)
                .roundEndTicks(0)
                .timeoutResult(() -> new RoundResult<>("match_end", CSRoundResultReason.TIME_OUT))
                .onRoundTick(ctx -> this.currentMatchTime = Math.max(0, limit - this.roundLifecycle.roundElapsedTicks()))
                .build();
    }

    @Override
    protected void onRoundEnd(RoundResult<String, CSRoundResultReason> result) {
        this.victory();
    }
    
    @Override
    public void handleDeath(DeathContext context) {
        super.handleDeath(context);
        // 立即重生玩家
        respawnPlayer(context.getDeadPlayer());
    }

    public void respawnPlayer(ServerPlayer player) {
        // 对局未在进行（已结束/重置）时不重生，避免胜利结算瞬间死亡被传回出生点
        if (!this.isStart) {
            return;
        }

        // 重置玩家状态
        player.heal(player.getMaxHealth());
        player.removeAllEffects();
        player.setGameMode(GameType.ADVENTURE);
        
        // 随机选择重生点
        SpawnPointData spawnPoint = getRandomSpawnPoint();
        if (spawnPoint != null) {
            teleportToPoint(player, spawnPoint);
        }

        this.getMapTeams().getPlayerData(player).ifPresent(data -> data.setLiving(true));
        givePlayerKits(player);
        
        // 给予重生保护（以服务器游戏时间计时，跟随游戏节奏而非墙钟）
        getDMPlayerData(player.getUUID()).ifPresent(d -> d.respawn(this.getServerLevel().getGameTime()));
    }
    
    public SpawnPointData getRandomSpawnPoint() {
        if (spawnPoints.isEmpty()) {
            return null;
        }
        
        Map<SpawnPointData, Double> weightMap = new HashMap<>();
        for (SpawnPointData spawnPoint : spawnPoints) {
            double weight = calculateSpawnPointWeight(spawnPoint);
            weightMap.put(spawnPoint, weight);
        }
        
        return selectWeightedRandomSpawnPoint(weightMap);
    }
    
    public double calculateSpawnPointWeight(SpawnPointData spawnPoint) {
        double weight = 1.0;
        List<ServerPlayer> onlinePlayers = this.getMapTeams().getOnline();
        
        for (Player player : onlinePlayers) {
            if (player.isSpectator() || player.isDeadOrDying()) {
                continue;
            }
            
            double distance = player.distanceToSqr(spawnPoint.getX(), spawnPoint.getY(), spawnPoint.getZ());
            weight += Math.min(distance, 4096.0) / 4096.0;
        }
        
        return weight;
    }

    public SpawnPointData selectWeightedRandomSpawnPoint(Map<SpawnPointData, Double> weightMap) {
        double totalWeight = weightMap.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = this.getRandom().nextDouble() * totalWeight;
        
        double currentWeight = 0.0;
        for (Map.Entry<SpawnPointData, Double> entry : weightMap.entrySet()) {
            currentWeight += entry.getValue();
            if (currentWeight >= randomValue) {
                return entry.getKey();
            }
        }

        return weightMap.keySet().iterator().next();
    }
    
    @Override
    public void givePlayerKits(ServerPlayer player) {
        CapabilityMap.getTeamCapability(this, StartKitsCapability.class)
                .forEach((team, opt) -> {
                    if (team.hasPlayer(player.getUUID())) {
                        opt.ifPresent(cap -> cap.givePlayerKits(player));
                    }
                });    }
    
    @Override
    public Team.Visibility nameTagVisibility() {
        return Team.Visibility.ALWAYS;
    }
    
    @Override
    public boolean isError() {
        return this.isError;
    }
    
    @Override
    public boolean isPause() {
        return false;
    }
    
    @Override
    public boolean isWaiting() {
        return false;
    }
    
    @Override
    public boolean isWaitingWinner() {
        return false;
    }
    
    @Override
    public boolean canGiveEconomy() {
        return false;
    }
    
    @Override
    public int getClientTime() {
        return this.currentMatchTime;
    }
    
    @Override
    public boolean setTeamSpawnPoints() {
        spawnPoints.clear();
        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            Optional<SpawnPointCapability> spawnCapOpt = team.getCapabilityMap().get(SpawnPointCapability.class);
            spawnCapOpt.ifPresent(cap -> spawnPoints.addAll(cap.getSpawnPointsData()));
        }

        if(spawnPoints.isEmpty()) return false;

        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : team.getOnline()) {
                SpawnPointData spawnPoint = getRandomSpawnPoint();
                if (spawnPoint != null) {
                    team.getPlayerData(player.getUUID()).ifPresent(playerData -> playerData.setSpawnPointsData(spawnPoint));
                    teleportToPoint(player, spawnPoint);
                }else{
                    return false;
                }
            }
        }
        return true;
    }
    
    @Override
    public boolean getPlayerCanOpenShop(ShopCapability cap, ServerPlayer player) {
        return true;
    }
    
    @Override
    public int getNextRoundMinMoney(ServerTeam team) {
        return -1;
    }
    
    @Override
    public int getShopCloseTime() {
        return DEATHMATCH_SHOP_CLOSE_TIME;
    }

    @Override
    public void syncToClient(boolean syncWeapon) {
        super.syncToClient(syncWeapon);
        syncShopInfo();
    }

    @Override
    public void syncShopInfo(ServerTeam team, ServerPlayer player, boolean enable, int closeTime) {
        ShopCapability.setPlayerMoney(this, player.getUUID(), DEATHMATCH_SHOP_MONEY);
        super.syncShopInfo(team, player, true, DEATHMATCH_SHOP_CLOSE_TIME);
    }
    
    @Override
    public void startNewRound() {
        this.start();
    }

    public Optional<DMPlayerData> getDMPlayerData(UUID uuid){
        return Optional.ofNullable(playerData.getOrDefault(uuid, null));
    }

    /**
     * 检查玩家是否处于重生保护状态
     * 使用服务器游戏时间（tick）而非墙钟，避免服务器卡顿/暂停导致保护时长漂移
     */
    public boolean isInSpawnProtection(UUID uuid) {
        return this.getDMPlayerData(uuid)
                .map(d -> d.isSpawning()
                        && this.getServerLevel().getGameTime() - d.lastProtectionTick < this.spawnProtectionTime.get() * 20L)
                .orElse(false);
    }
    
    /**
     * 处理玩家开枪事件，取消重生保护
     */
    public void handlePlayerFire(UUID uuid) {
        this.getDMPlayerData(uuid).ifPresent(DMPlayerData::setFired);
    }
    
    /**
     * 处理玩家移动事件，取消重生保护
     */
    public void handlePlayerMove(UUID uuid) {
        this.getDMPlayerData(uuid).ifPresent(DMPlayerData::setMoved);
    }
    
    /**
     * 写入地图数据到数据管理器
     */
    public static void save(FPSMDataManager manager) {
        FPSMCore.getInstance().getMapByClass(CSDeathMatchMap.class)
                .forEach((map -> {
                    map.saveConfig();
                    manager.saveData(map, map.getMapName(), false);
                }));
    }

    public boolean isTDM() {
        return isTDM != null && Boolean.TRUE.equals(isTDM.get());
    }

    private static Boolean parseDeathmatchMode(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean: " + value);
    }

    /**
     * Team limits are immutable in the FPSMatch team model. A mode switch is
     * therefore only valid before a normal player has joined; otherwise the
     * setting must remain unchanged instead of leaving a half-reconfigured map.
     */
    private final class DeathmatchModeSetting extends Setting<Boolean> {
        private DeathmatchModeSetting() {
            super("team", "isTDM", Codec.BOOL, false, CSDeathMatchMap::parseDeathmatchMode);
        }

        private boolean canApply(Boolean value) {
            return Objects.equals(get(), value) || getMapTeams().getJoinedPlayers().isEmpty();
        }

        @Override
        public Boolean set(Boolean value) {
            if (!canApply(value)) {
                return get();
            }
            boolean changed = !Objects.equals(get(), value);
            Boolean result = super.set(value);
            if (changed) {
                reconcileAfterModeChange();
            }
            return result;
        }

        @Override
        public boolean parse(String value) {
            final Boolean parsed;
            try {
                parsed = parseDeathmatchMode(value);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            if (!canApply(parsed)) {
                return false;
            }
            boolean changed = !Objects.equals(get(), parsed);
            boolean accepted = super.parse(value);
            if (accepted && changed) {
                reconcileAfterModeChange();
            }
            return accepted;
        }

        @Override
        public void fromJson(JsonElement json) {
            if (!getMapTeams().getJoinedPlayers().isEmpty()) {
                try {
                    if (!Objects.equals(get(), json.getAsBoolean())) {
                        return;
                    }
                } catch (RuntimeException ignored) {
                    return;
                }
            }
            boolean changed = true;
            try {
                changed = !Objects.equals(get(), json.getAsBoolean());
            } catch (RuntimeException ignored) {
                // Let Setting#fromJson report malformed values during initialization.
            }
            super.fromJson(json);
            if (changed) {
                reconcileAfterModeChange();
            }
        }

        @Override
        public void reset() {
            if (canApply(getDefaultValue())) {
                boolean changed = !Objects.equals(get(), getDefaultValue());
                super.reset();
                if (changed) {
                    reconcileAfterModeChange();
                }
            }
        }

        private void reconcileAfterModeChange() {
            if (deathmatchTeamsInitialized) {
                ensureDeathmatchTeams();
            }
        }
    }

    private List<String> deathmatchTeamNames() {
        return isTDM() ? CSDMTeamSemantics.tdmTeamPool() : CSDMTeamSemantics.ffaBaseTeamPool();
    }

    private boolean isDeathmatchTeamName(String teamName) {
        if (deathmatchTeamNames().contains(teamName)) {
            return true;
        }
        // 动态创建的数字队伍也是死斗队伍
        try {
            int num = Integer.parseInt(teamName);
            return num > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void ensureDeathmatchTeams() {
        for (String teamName : deathmatchTeamNames()) {
            Optional<ServerTeam> current = getMapTeams().getTeamByName(teamName);
            Optional<CapabilityMap.Wrapper> preservedData = Optional.empty();
            if (current.filter(team -> team.getPlayerLimit() != CSDMTeamSemantics.teamCapacity(isTDM())
                    && team.isEmpty()).isPresent()) {
                ServerTeam team = current.orElseThrow();
                preservedData = Optional.of(team.getCapabilityMap().getData());
                getMapTeams().delTeam(team.getPlayerTeam());
                current = Optional.empty();
            }
            if (current.isEmpty()) {
                ServerTeam replacement = addTeam(TeamData.of(teamName, CSDMTeamSemantics.teamCapacity(isTDM()), TEAM_CAPABILITIES));
                if (preservedData.isPresent()) {
                    replacement.getCapabilityMap().write(preservedData.orElseThrow());
                } else {
                    copyDeathmatchTeamTemplate(replacement);
                }
            }
        }
        applyDeathmatchFriendlyFireRule();
    }

    private Optional<String> selectDeathmatchTeamName(UUID playerId) {
        List<String> teamNames = deathmatchTeamNames();
        Optional<ServerTeam> currentTeam = getMapTeams().getTeamByPlayer(playerId)
                .filter(team -> teamNames.contains(team.getName()));
        if (currentTeam.isPresent()) {
            return Optional.of(currentTeam.get().getName());
        }

        // 查找所有数字队伍中人数最少的
        Optional<ServerTeam> bestTeam = getMapTeams().getNormalTeams().stream()
                .filter(team -> isTDM() ? teamNames.contains(team.getName()) : isDeathmatchTeamName(team.getName()))
                .filter(team -> team.getPlayerCount() < CSDMTeamSemantics.teamCapacity(isTDM()))
                .filter(team -> team.getPlayerLimit() == -1 || team.getPlayerCount() < team.getPlayerLimit())
                .min(Comparator
                        .comparingInt(ServerTeam::getPlayerCount)
                        .thenComparingInt(team -> Integer.parseInt(team.getName())));

        if (bestTeam.isPresent()) {
            return Optional.of(bestTeam.get().getName());
        }

        if (isTDM()) {
            return Optional.empty();
        }

        // 所有队伍都满了，创建一个新的数字队伍
        return Optional.of(createNewDeathmatchTeam());
    }

    /**
     * 创建一个新的数字队伍并返回其名称
     */
    private String createNewDeathmatchTeam() {
        String newName = String.valueOf(nextTeamIndex);
        // 确保名称不与现有队伍冲突
        while (getMapTeams().getTeamByName(newName).isPresent()) {
            nextTeamIndex++;
            newName = String.valueOf(nextTeamIndex);
        }
        ServerTeam team = addTeam(TeamData.of(newName, CSDMTeamSemantics.teamCapacity(isTDM()), TEAM_CAPABILITIES));
        copyDeathmatchTeamTemplate(team);
        applyDeathmatchFriendlyFireRule();
        nextTeamIndex++;
        return newName;
    }

    /**
     * 清理空的数字队伍（非初始5个队伍的动态队伍会被销毁）
     */
    private void cleanupEmptyDeathmatchTeam(ServerTeam team) {
        if (!isDeathmatchTeamName(team.getName())) {
            return;
        }
        if (!team.isEmpty()) {
            return;
        }
        // 初始5个队伍保留不销毁，只销毁动态创建的队伍
        if (deathmatchTeamNames().contains(team.getName())) {
            return;
        }
        getMapTeams().delTeam(team.getPlayerTeam());
    }

    private void copyDeathmatchTeamTemplate(ServerTeam team) {
        ServerTeam template = getCT();
        team.getCapabilityMap().write(template.getCapabilityMap().getData());
        team.getCapabilityMap().get(ShopCapability.class).ifPresent(cap -> cap.initialize("cs", 16000));
        team.getPlayerTeam().setAllowFriendlyFire(true);
    }

    private void cleanupLegacyPlayerTeams() {
        getMapTeams().getNormalTeams().stream()
                .filter(team -> team.getName().startsWith(LEGACY_PLAYER_TEAM_PREFIX))
                .filter(ServerTeam::isEmpty)
                .map(ServerTeam::getPlayerTeam)
                .toList()
                .forEach(getMapTeams()::delTeam);
    }

    /**
     * 清理动态创建的数字队伍（重置时调用）
     */
    private void cleanupDynamicDeathmatchTeams() {
        getMapTeams().getNormalTeams().stream()
                .filter(team -> isDeathmatchTeamName(team.getName()))
                .filter(team -> !deathmatchTeamNames().contains(team.getName()))
                .map(ServerTeam::getPlayerTeam)
                .toList()
                .forEach(getMapTeams()::delTeam);
    }

    /**
     * 从现有队伍中恢复nextTeamIndex，确保新创建的队伍不会与已有队伍冲突
     */
    private void restoreNextTeamIndex() {
        int maxIndex = 5; // 初始队伍最大为5
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            if (isDeathmatchTeamName(team.getName())) {
                try {
                    int num = Integer.parseInt(team.getName());
                    if (num > maxIndex) {
                        maxIndex = num;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        this.nextTeamIndex = maxIndex + 1;
    }

    private static Map<String, CapabilityMap.Wrapper> getPersistentTeamData(CSDeathMatchMap map) {
        Map<String, CapabilityMap.Wrapper> data = new HashMap<>(map.getMapTeams().getData());
        data.keySet().removeIf(name -> name.startsWith(LEGACY_PLAYER_TEAM_PREFIX));
        return data;
    }

    private void applyDeathmatchFriendlyFireRule() {
        boolean allowFriendlyFire = !isTDM();
        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            team.getPlayerTeam().setAllowFriendlyFire(allowFriendlyFire);
        }
    }

    public static class DMPlayerData{
        UUID owner;
        boolean needRespawnProtection = false;
        long lastProtectionTick = 0;

        boolean isMoved = false;
        boolean isFired = false;

        private DMPlayerData(UUID owner){
            this.owner = owner;
        }

        public boolean isSpawning(){
            return needRespawnProtection && !isFired && !isMoved;
        }

        public void setFired(){
            isFired = true;
            needRespawnProtection = false;
        }

        public void setMoved(){
            isMoved = true;
            needRespawnProtection = false;
        }

        public void reset(){
            isFired = false;
            isMoved = false;
            needRespawnProtection = false;
            lastProtectionTick = 0;
        }

        public void respawn(long gameTime){
            reset();
            needRespawnProtection = true;
            lastProtectionTick = gameTime;
        }
    }
}
