package net.ptcrys.blockoffensive.map;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import net.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import net.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import net.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import net.ptcrys.fpsmatch.common.capability.team.StartKitsCapability;
import net.ptcrys.fpsmatch.compat.LrtacticalCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.capability.CapabilityMap;
import net.ptcrys.fpsmatch.core.capability.map.MapCapability;
import net.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import net.ptcrys.fpsmatch.core.data.AreaData;
import net.ptcrys.fpsmatch.core.data.PlayerData;
import net.ptcrys.fpsmatch.core.data.Setting;
import net.ptcrys.fpsmatch.core.data.SpawnPointData;
import net.ptcrys.fpsmatch.core.map.DeathContext;
import net.ptcrys.fpsmatch.core.match.RoundLifecycle;
import net.ptcrys.fpsmatch.core.match.RoundResult;
import net.ptcrys.fpsmatch.core.persistence.FPSMDataManager;
import net.ptcrys.fpsmatch.core.team.MapTeams;
import net.ptcrys.fpsmatch.core.team.ServerTeam;
import net.ptcrys.fpsmatch.core.team.TeamData;
import net.ptcrys.fpsmatch.util.FPSMUtil;

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

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
                    CapabilityMap.Wrapper.CODEC).fieldOf("teams").forGetter(CSDeathMatchMap::getPersistentTeamData))
            .apply(instance, CSDeathMatchMap::new));

    public static final String TYPE = "csdm";
    private static final List<Class<? extends MapCapability>> MAP_CAPABILITIES = List.of(GameEndTeleportCapability.class);
    private static final List<Class<? extends TeamCapability>> TEAM_CAPABILITIES = List.of(ShopCapability.class, StartKitsCapability.class, SpawnPointCapability.class);

    // 死斗模式设置
    private Setting<Boolean> isTDM;
    private Setting<Integer> matchTimeLimit;
    private Setting<Integer> spawnProtectionTime;

    private static final int DEATHMATCH_SHOP_MONEY = -1;

    // 游戏状态
    private int currentMatchTime = 0;

    // 重生保护映射
    private final Map<UUID, DMPlayerData> playerData = new HashMap<>();
    private final List<SpawnPointData> spawnPoints = new ArrayList<>();

    private boolean isError = false;

    /**
     * 构造函数：创建CS死斗地图实例
     * 
     * @param serverLevel 服务器世界实例
     * @param mapName     地图名称
     * @param areaData    地图区域数据
     */
    public CSDeathMatchMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData, MAP_CAPABILITIES, TEAM_CAPABILITIES);
        applyDeathmatchTeamRules();
    }

    private CSDeathMatchMap(String mapName, AreaData areaData, ResourceLocation serverLevel, Map<String, JsonElement> capabilities, Map<String, CapabilityMap.Wrapper> teams) {
        this(FPSMCore.getInstance().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, serverLevel)), mapName, areaData);
        this.getCapabilityMap().write(capabilities);
        this.getMapTeams().writeData(filterPersistentTeamData(teams));
    }

    @Override
    public void syncToClient() {
        super.syncToClient();
        syncToClient(false);
    }

    @Override
    public void setup() {
        isTDM = this.addSetting(new DeathmatchModeSetting());
        matchTimeLimit = this.addSetting("match", "matchTimeLimit", 12000);
        spawnProtectionTime = this.addSetting("player", "spawnProtectionTime", 6);
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
                spawnProtectionTime);
    }

    @Override
    public ServerTeam addTeam(TeamData data) {
        ServerTeam team = super.addTeam(data);
        team.getPlayerTeam().setAllowFriendlyFire(!isTDM());
        team.getCapabilityMap().get(ShopCapability.class).ifPresent(cap -> cap.initialize("cs", DEATHMATCH_SHOP_MONEY));
        return team;
    }

    @Override
    public int getCTLimit() {
        return CSDMTeamSemantics.TEAM_CAPACITY;
    }

    @Override
    public int getTLimit() {
        return CSDMTeamSemantics.TEAM_CAPACITY;
    }

    @Override
    public String getGameType() {
        return TYPE;
    }

    @Override
    public MapTeams.JoinTeamResult join(String teamName, ServerPlayer player) {
        String targetTeamName = deathmatchTeamNames().contains(teamName) ? teamName : selectDeathmatchTeamName(player.getUUID()).orElse(null);
        if (targetTeamName == null) {
            return MapTeams.JoinTeamResult.of(MapTeams.JoinTeamResult.Status.NO_AVAILABLE_TEAM);
        }
        MapTeams.JoinTeamResult result = super.join(targetTeamName, player);
        if (result.isSuccess()) {
            getMapTeams().getTeamByPlayer(player).ifPresent(team -> {
                this.playerData.put(player.getUUID(), new DMPlayerData(player.getUUID()));
                if (isStart) {
                    respawnPlayer(player, true);
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
    public void leave(ServerPlayer player) {
        super.leave(player);
        this.playerData.remove(player.getUUID());
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
        this.applyDeathmatchTeamRules();

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
    public void victory() {
        this.sendVictoryMessage(
                Component.translatable("map.deathmatch.message.victory.head").withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD),
                Comparator.comparingInt(PlayerData::getScores).reversed());
        super.victory();
        this.reset();
    }

    @Override
    public void reset() {
        super.reset();
        this.isError = false;
        this.isStart = false;
        this.currentMatchTime = 0;
        this.getMapTeams().getJoinedPlayers().forEach(data -> data.getPlayer().ifPresent(this::resetPlayerClientData));
        this.getMapTeams().reset();
    }

    public void resetAllPlayerData() {
        this.playerData.values().forEach(DMPlayerData::reset);
    }

    private void initializePlayers(MapTeams mapTeams) {
        mapTeams.getJoinedPlayersMap().forEach(this::initializePlayer);
    }

    private void initializePlayer(ServerTeam team, List<PlayerData> players) {
        team.resetCapabilities();

        players.forEach(data -> {
            data.reset();
            data.getPlayer().ifPresent(player -> {
                player.removeAllEffects();
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                this.clearInventory(player);
                this.givePlayerKits(player);
                giveFullArmor(player);
                this.playerData.computeIfAbsent(player.getUUID(), DMPlayerData::new)
                        .respawn(this.getServerLevel().getGameTime());
            });
        });
    }

    @Override
    public void tick() {
        super.tick();
        applyDeathmatchTeamRules();
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
        respawnPlayer(player, false);
    }

    private void respawnPlayer(ServerPlayer player, boolean resetLoadout) {
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
        if (resetLoadout) {
            this.clearInventory(player);
            givePlayerKits(player);
        } else {
            FPSMUtil.getAllPlayerItems(player).forEach(FPSMUtil::fixGunItem);
        }
        giveFullArmor(player);

        this.getMapTeams().getTeamByPlayer(player).flatMap(ShopCapability::getShop).ifPresent(shop -> {
            shop.getDefaultAndPutData(player.getUUID(), false).lockShopSlots(player);
            shop.syncShopData(player);
        });

        // 给予重生保护（以服务器游戏时间计时，跟随游戏节奏而非墙钟）
        getDMPlayerData(player.getUUID()).ifPresent(d -> d.respawn(this.getServerLevel().getGameTime()));
    }

    private static void giveFullArmor(ServerPlayer player) {
        BulletproofArmorAttribute.addPlayer(player, new BulletproofArmorAttribute(true));
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
                });
    }

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

        if (spawnPoints.isEmpty()) return false;

        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : team.getOnline()) {
                SpawnPointData spawnPoint = getRandomSpawnPoint();
                if (spawnPoint != null) {
                    team.getPlayerData(player.getUUID()).ifPresent(playerData -> playerData.setSpawnPointsData(spawnPoint));
                    teleportToPoint(player, spawnPoint);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean getPlayerCanOpenShop(ShopCapability cap, ServerPlayer player) {
        return isStart && isInSpawnProtection(player.getUUID());
    }

    @Override
    public int getNextRoundMinMoney(ServerTeam team) {
        return -1;
    }

    @Override
    public int getShopCloseTime() {
        return spawnProtectionTime.get();
    }

    @Override
    public void syncToClient(boolean syncWeapon) {
        super.syncToClient(syncWeapon);
        syncShopInfo();
    }

    @Override
    public void syncShopInfo(ServerTeam team, ServerPlayer player, boolean enable, int closeTime) {
        ShopCapability.setPlayerMoney(this, player.getUUID(), DEATHMATCH_SHOP_MONEY);
        boolean canOpen = isStart && isInSpawnProtection(player.getUUID());
        super.syncShopInfo(team, player, canOpen, remainingSpawnProtectionSeconds(player.getUUID()));
    }

    private int remainingSpawnProtectionSeconds(UUID playerId) {
        return getDMPlayerData(playerId).map(data -> {
            long elapsed = this.getServerLevel().getGameTime() - data.lastProtectionTick;
            long remainingTicks = Math.max(0L, spawnProtectionTime.get() * 20L - elapsed);
            return (int) ((remainingTicks + 19L) / 20L);
        }).orElse(0);
    }

    @Override
    public void startNewRound() {
        this.start();
    }

    public Optional<DMPlayerData> getDMPlayerData(UUID uuid) {
        return Optional.ofNullable(playerData.getOrDefault(uuid, null));
    }

    /**
     * 检查玩家是否处于重生保护状态
     * 使用服务器游戏时间（tick）而非墙钟，避免服务器卡顿/暂停导致保护时长漂移
     */
    public boolean isInSpawnProtection(UUID uuid) {
        return this.getDMPlayerData(uuid)
                .map(d -> d.isSpawning() && this.getServerLevel().getGameTime() - d.lastProtectionTick < this.spawnProtectionTime.get() * 20L)
                .orElse(false);
    }

    /**
     * 处理玩家开枪事件，取消重生保护
     */
    public void handlePlayerFire(UUID uuid) {
        this.getDMPlayerData(uuid).ifPresent(DMPlayerData::setFired);
    }

    public void replenishAmmoReserve(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        if (!GunCompatManager.isGun(weapon)) return;
        var provider = GunCompatManager.findProvider(weapon);
        int reserve = provider.getMaxDummyAmmo(weapon);
        if (reserve > 0) {
            provider.setDummyAmmo(weapon, reserve);
        }
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

    public int getDeathmatchKillScore(ItemStack weapon) {
        if (FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(weapon)) {
            return 25;
        }
        if (GunCompatManager.isGun(weapon)) {
            ResourceLocation gunId = GunCompatManager.findProvider(weapon).getGunId(weapon);
            if (gunId != null) {
                return CSDMScoring.scoreForWeaponPath(gunId.getPath());
            }
        }
        return 10;
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
            applyDeathmatchTeamRules();
        }
    }

    private List<String> deathmatchTeamNames() {
        return CSDMTeamSemantics.teamPool();
    }

    private Optional<String> selectDeathmatchTeamName(UUID playerId) {
        List<String> teamNames = deathmatchTeamNames();
        Optional<ServerTeam> currentTeam = getMapTeams().getTeamByPlayer(playerId)
                .filter(team -> teamNames.contains(team.getName()));
        if (currentTeam.isPresent()) {
            return Optional.of(currentTeam.get().getName());
        }

        return getMapTeams().getNormalTeams().stream()
                .filter(team -> teamNames.contains(team.getName()))
                .filter(team -> team.getPlayerLimit() == -1 || team.getPlayerCount() < team.getPlayerLimit())
                .min(Comparator
                        .comparingInt(ServerTeam::getPlayerCount)
                        .thenComparing(ServerTeam::getName))
                .map(ServerTeam::getName);
    }

    private static Map<String, CapabilityMap.Wrapper> getPersistentTeamData(CSDeathMatchMap map) {
        return filterPersistentTeamData(map.getMapTeams().getData());
    }

    private static Map<String, CapabilityMap.Wrapper> filterPersistentTeamData(Map<String, CapabilityMap.Wrapper> source) {
        Set<String> supportedTeams = new HashSet<>(CSDMTeamSemantics.teamPool());
        supportedTeams.add(CSDMTeamSemantics.SPECTATOR);
        Map<String, CapabilityMap.Wrapper> filtered = new HashMap<>();
        source.forEach((name, data) -> {
            if (supportedTeams.contains(name)) {
                filtered.put(name, data);
            }
        });
        return filtered;
    }

    @Override
    public boolean areCombatTeammates(Player first, Player second, ServerTeam firstTeam, ServerTeam secondTeam) {
        return CSDMTeamSemantics.areTeammates(
                isTDM(),
                firstTeam.getName(),
                secondTeam.getName(),
                first.isSpectator(),
                second.isSpectator());
    }

    @Override
    public void creditAssist(PlayerData playerData) {
        super.creditAssist(playerData);
        playerData.addScore(5);
    }

    private void applyDeathmatchTeamRules() {
        boolean allowFriendlyFire = !isTDM();
        for (ServerTeam team : this.getMapTeams().getNormalTeams()) {
            team.getPlayerTeam().setAllowFriendlyFire(allowFriendlyFire);
            team.getPlayerTeam().setCollisionRule(Team.CollisionRule.NEVER);
        }
    }

    public static class DMPlayerData {

        UUID owner;
        boolean needRespawnProtection = false;
        long lastProtectionTick = 0;

        boolean isMoved = false;
        boolean isFired = false;

        private DMPlayerData(UUID owner) {
            this.owner = owner;
        }

        public boolean isSpawning() {
            return needRespawnProtection && !isFired && !isMoved;
        }

        public void setFired() {
            isFired = true;
            needRespawnProtection = false;
        }

        public void setMoved() {
            isMoved = true;
            needRespawnProtection = false;
        }

        public void reset() {
            isFired = false;
            isMoved = false;
            needRespawnProtection = false;
            lastProtectionTick = 0;
        }

        public void respawn(long gameTime) {
            reset();
            needRespawnProtection = true;
            lastProtectionTick = gameTime;
        }
    }
}
