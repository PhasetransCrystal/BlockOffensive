package net.ptcrys.blockoffensive.intro.client;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.intro.IntroMotionProfile;
import net.ptcrys.blockoffensive.intro.IntroPhase;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IntroPlayerAnimatorBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PLAYER_ANIMATOR_MODID = "playeranimator";
    private static final ResourceLocation LAYER_ID = ResourceLocation.tryBuild(BlockOffensive.MODID, "halftime_intro_layer");
    private static final ResourceLocation WALKOUT_ANIMATION_ID = ResourceLocation.tryBuild(BlockOffensive.MODID, "intro_walkout");
    private static final ResourceLocation SWITCH_ANIMATION_ID = ResourceLocation.tryBuild(BlockOffensive.MODID, "intro_switch");
    private static final List<ResourceLocation> CINEMATIC_SUPPRESSED_LAYERS = List.of(
            ResourceLocation.tryBuild("tacz", "lower_animation"),
            ResourceLocation.tryBuild("tacz", "loop_upper_animation"),
            ResourceLocation.tryBuild("tacz", "once_upper_animation"),
            ResourceLocation.tryBuild("tacz", "rotation"));

    private static boolean initialized;
    private static boolean available;
    private static Method getAnimationMethod;
    private static Method getAssociatedDataMethod;
    private static Method dataGetMethod;
    private static Method replaceAnimationWithFadeMethod;
    private static Method modifierLayerGetAnimationMethod;
    private static Method standardFadeInMethod;
    private static Constructor<?> keyframeAnimationPlayerCtor;
    private static Constructor<?> keyframeAnimationPlayerAtTickCtor;
    private static Method keyframeAnimationPlayerTickMethod;
    private static Method keyframeAnimationPlayerCurrentTickMethod;
    private static Method keyframeAnimationPlayerIsActiveMethod;
    private static Constructor<?> modifierLayerCtor;
    private static Object easeInOutSine;
    private static Object layerFactoryProxy;
    private static final Set<ResourceLocation> MISSING_ANIMATION_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> BEND_PROBE_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, Boolean> BEND_RUNTIME_VERIFIED = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveIntroAnimation> ACTIVE_ANIMATIONS = new ConcurrentHashMap<>();

    private IntroPlayerAnimatorBridge() {}

    public static void registerFactoryIfPresent() {
        if (!ensureReady()) {
            LOGGER.info("[BlockOffensive Halftime] playerAnimator bridge unavailable loadedPlayerAnimator={} loadedBendyLib={}",
                    ModList.get().isLoaded(PLAYER_ANIMATOR_MODID), isBendyLibLoaded());
            return;
        }
        try {
            Class<?> playerAnimationFactoryClass = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory");
            Field holderField = playerAnimationFactoryClass.getField("ANIMATION_DATA_FACTORY");
            Object holder = holderField.get(null);
            Method registerFactory = holder.getClass().getMethod("registerFactory", ResourceLocation.class, int.class, playerAnimationFactoryClass);
            registerFactory.invoke(holder, LAYER_ID, 120, layerFactoryProxy);
            LOGGER.info("[BlockOffensive Halftime] playerAnimator bridge ready layer={} animations=[{},{}] loadedBendyLib={}",
                    LAYER_ID, WALKOUT_ANIMATION_ID, SWITCH_ANIMATION_ID, isBendyLibLoaded());
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator factory registration skipped", e);
            available = false;
        }
    }

    public static boolean play(Player player) {
        return play(player, IntroPhase.SWITCH);
    }

    public static boolean play(Player player, IntroPhase phase) {
        return play(player, phase, -1);
    }

    public static boolean play(Player player, IntroPhase phase, int formationIndex) {
        if (!ensureReady() || !(player instanceof AbstractClientPlayer clientPlayer)) {
            return false;
        }
        ResourceLocation animationId = animationIdForPhase(phase);
        try {
            Object animation = getAnimationMethod.invoke(null, animationId);
            if (animation == null) {
                if (MISSING_ANIMATION_LOGGED.add(animationId)) {
                    LOGGER.warn("[BlockOffensive Halftime] playerAnimator animation {} is not registered; using cinematic movement fallback", animationId);
                }
                return false;
            }
            Object layer = layerFor(clientPlayer);
            if (layer == null) {
                return false;
            }
            suppressExternalCinematicLayers(clientPlayer);
            Object fade = standardFadeInMethod.invoke(null, 6, easeInOutSine);
            int targetTick = IntroMotionProfile.animationTick(phase, formationIndex, 0, IntroMotionProfile.AUTHORED_STRIDE_TICKS);
            Object animationPlayer = newAnimationPlayer(animation, targetTick);
            probeBendRuntime(animationId, animation, animationPlayer);
            replaceAnimationWithFadeMethod.invoke(layer, fade, animationPlayer);
            ACTIVE_ANIMATIONS.put(player.getUUID(), new ActiveIntroAnimation(animationId, phase, formationIndex, animation, animationPlayer));
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator play skipped", e);
            return false;
        }
    }

    public static SyncResult sync(Player player, IntroPhase phase, int formationIndex, int movementTick, int durationTicks) {
        int targetTick = IntroMotionProfile.animationTick(phase, formationIndex, movementTick, durationTicks);
        if (!ensureReady() || !(player instanceof AbstractClientPlayer clientPlayer)) {
            return SyncResult.unavailable(targetTick);
        }
        ResourceLocation animationId = animationIdForPhase(phase);
        try {
            Object animation = getAnimationMethod.invoke(null, animationId);
            if (animation == null) {
                if (MISSING_ANIMATION_LOGGED.add(animationId)) {
                    LOGGER.warn("[BlockOffensive Halftime] playerAnimator animation {} is not registered; sync unavailable", animationId);
                }
                return SyncResult.unavailable(targetTick);
            }
            Object layer = layerFor(clientPlayer);
            if (layer == null) {
                return SyncResult.unavailable(targetTick);
            }
            suppressExternalCinematicLayers(clientPlayer);
            ActiveIntroAnimation active = ACTIVE_ANIMATIONS.get(player.getUUID());
            if (active == null || !active.matches(animationId, phase, formationIndex) || !isPlayerActive(active.animationPlayer())) {
                Object animationPlayer = replaceAnimationAtTick(player.getUUID(), layer, animationId, phase, formationIndex, animation, targetTick, 0);
                int currentTick = currentTick(animationPlayer);
                return new SyncResult(true, targetTick, currentTick, 0, 1, 0);
            }
            int currentTick = currentTick(active.animationPlayer());
            int observedDrift = currentTick - targetTick;
            if (currentTick > targetTick + 2) {
                Object animationPlayer = replaceAnimationAtTick(player.getUUID(), layer, animationId, phase, formationIndex, animation, targetTick, 0);
                int rebuiltTick = currentTick(animationPlayer);
                LOGGER.debug("[BlockOffensive Halftime] playerAnimator sync rebuilt animation={} player={} targetTick={} previousTick={} rebuiltTick={}",
                        animationId, player.getUUID(), targetTick, currentTick, rebuiltTick);
                return new SyncResult(true, targetTick, rebuiltTick, observedDrift, 1, 0);
            }
            int catchupTicks = 0;
            while (currentTick < targetTick && catchupTicks < 8) {
                keyframeAnimationPlayerTickMethod.invoke(active.animationPlayer());
                currentTick = currentTick(active.animationPlayer());
                catchupTicks++;
            }
            if (currentTick < targetTick) {
                Object animationPlayer = replaceAnimationAtTick(player.getUUID(), layer, animationId, phase, formationIndex, animation, targetTick, 0);
                int rebuiltTick = currentTick(animationPlayer);
                LOGGER.debug("[BlockOffensive Halftime] playerAnimator sync catchup rebuilt animation={} player={} targetTick={} previousTick={} rebuiltTick={} catchupTicks={}",
                        animationId, player.getUUID(), targetTick, currentTick, rebuiltTick, catchupTicks);
                return new SyncResult(true, targetTick, rebuiltTick, observedDrift, 1, catchupTicks);
            }
            return new SyncResult(true, targetTick, currentTick, observedDrift, 0, catchupTicks);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator sync skipped", e);
            return SyncResult.unavailable(targetTick);
        }
    }

    public static int maintainCinematicIsolation(Player player) {
        if (!ensureReady() || !(player instanceof AbstractClientPlayer clientPlayer)) {
            return 0;
        }
        try {
            return suppressExternalCinematicLayers(clientPlayer);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator cinematic isolation skipped", e);
            return 0;
        }
    }

    public static ResourceLocation animationIdForPhase(IntroPhase phase) {
        return phase == IntroPhase.SWITCH ? SWITCH_ANIMATION_ID : WALKOUT_ANIMATION_ID;
    }

    public static void stop(Player player) {
        ACTIVE_ANIMATIONS.remove(player.getUUID());
        if (!ensureReady() || !(player instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        try {
            Object layer = layerFor(clientPlayer);
            if (layer == null) {
                return;
            }
            Object fade = standardFadeInMethod.invoke(null, 6, easeInOutSine);
            replaceAnimationWithFadeMethod.invoke(layer, fade, null);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator stop skipped", e);
        }
    }

    private static Object replaceAnimationAtTick(UUID playerId, Object layer, ResourceLocation animationId, IntroPhase phase, int formationIndex, Object animation, int targetTick, int fadeTicks) throws ReflectiveOperationException {
        Object animationPlayer = newAnimationPlayer(animation, targetTick);
        Object fade = standardFadeInMethod.invoke(null, fadeTicks, easeInOutSine);
        replaceAnimationWithFadeMethod.invoke(layer, fade, animationPlayer);
        ACTIVE_ANIMATIONS.put(playerId, new ActiveIntroAnimation(animationId, phase, formationIndex, animation, animationPlayer));
        return animationPlayer;
    }

    private static Object newAnimationPlayer(Object animation, int targetTick) throws ReflectiveOperationException {
        return keyframeAnimationPlayerAtTickCtor.newInstance(animation, clampTick(targetTick));
    }

    private static int clampTick(int targetTick) {
        return Math.max(0, Math.min(IntroMotionProfile.MAX_ANIMATION_TICK, targetTick));
    }

    private static int currentTick(Object animationPlayer) throws ReflectiveOperationException {
        Object value = keyframeAnimationPlayerCurrentTickMethod.invoke(animationPlayer);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean isPlayerActive(Object animationPlayer) throws ReflectiveOperationException {
        Object value = keyframeAnimationPlayerIsActiveMethod.invoke(animationPlayer);
        return value instanceof Boolean bool && bool;
    }

    private static Object layerFor(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object data = getAssociatedDataMethod.invoke(null, player);
        return data == null ? null : dataGetMethod.invoke(data, LAYER_ID);
    }

    private static int suppressExternalCinematicLayers(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object data = getAssociatedDataMethod.invoke(null, player);
        if (data == null) {
            return 0;
        }
        int cleared = 0;
        for (ResourceLocation layerId : CINEMATIC_SUPPRESSED_LAYERS) {
            Object layer = dataGetMethod.invoke(data, layerId);
            Object animation = layer == null ? null : modifierLayerGetAnimationMethod.invoke(layer);
            if (animation != null) {
                Object fade = standardFadeInMethod.invoke(null, 0, easeInOutSine);
                replaceAnimationWithFadeMethod.invoke(layer, fade, null);
                cleared++;
            }
        }
        if (cleared > 0) {
            LOGGER.debug("[BlockOffensive Halftime] suppressed {} external playerAnimator layers for {}", cleared, player.getGameProfile().getName());
        }
        return cleared;
    }

    private static boolean ensureReady() {
        if (initialized) {
            return available;
        }
        initialized = true;
        if (!ModList.get().isLoaded(PLAYER_ANIMATOR_MODID)) {
            available = false;
            return false;
        }
        try {
            Class<?> playerAnimationFactoryClass = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory");
            Class<?> registryClass = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry");
            Class<?> accessClass = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
            Class<?> associatedDataClass = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess$PlayerAssociatedAnimationData");
            Class<?> modifierLayerClass = Class.forName("dev.kosmx.playerAnim.api.layered.ModifierLayer");
            Class<?> iAnimationClass = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
            Class<?> keyframeAnimationClass = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
            Class<?> keyframeAnimationPlayerClass = Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer");
            Class<?> abstractFadeModifierClass = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier");
            Class<?> easeClass = Class.forName("dev.kosmx.playerAnim.core.util.Ease");

            getAnimationMethod = registryClass.getMethod("getAnimation", ResourceLocation.class);
            getAssociatedDataMethod = accessClass.getMethod("getPlayerAssociatedData", AbstractClientPlayer.class);
            dataGetMethod = associatedDataClass.getMethod("get", ResourceLocation.class);
            replaceAnimationWithFadeMethod = modifierLayerClass.getMethod("replaceAnimationWithFade", abstractFadeModifierClass, iAnimationClass);
            modifierLayerGetAnimationMethod = modifierLayerClass.getMethod("getAnimation");
            standardFadeInMethod = abstractFadeModifierClass.getMethod("standardFadeIn", int.class, easeClass);
            keyframeAnimationPlayerCtor = keyframeAnimationPlayerClass.getConstructor(keyframeAnimationClass);
            keyframeAnimationPlayerAtTickCtor = keyframeAnimationPlayerClass.getConstructor(keyframeAnimationClass, int.class);
            keyframeAnimationPlayerTickMethod = keyframeAnimationPlayerClass.getMethod("tick");
            keyframeAnimationPlayerCurrentTickMethod = keyframeAnimationPlayerClass.getMethod("getCurrentTick");
            keyframeAnimationPlayerIsActiveMethod = keyframeAnimationPlayerClass.getMethod("isActive");
            modifierLayerCtor = modifierLayerClass.getConstructor();
            easeInOutSine = Enum.valueOf(easeClass.asSubclass(Enum.class), "INOUTSINE");
            layerFactoryProxy = Proxy.newProxyInstance(
                    playerAnimationFactoryClass.getClassLoader(),
                    new Class<?>[] { playerAnimationFactoryClass },
                    (proxy, method, args) -> "invoke".equals(method.getName()) ? modifierLayerCtor.newInstance() : null);
            available = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("[BlockOffensive Halftime] playerAnimator bridge unavailable", e);
            available = false;
            return false;
        }
    }

    public static boolean isBendyLibLoaded() {
        return ModList.get().isLoaded("bendylib");
    }

    public static boolean isPlayerAnimatorLoaded() {
        return ModList.get().isLoaded(PLAYER_ANIMATOR_MODID);
    }

    public static boolean isBendRuntimeVerified() {
        return isBendRuntimeVerified(WALKOUT_ANIMATION_ID);
    }

    public static boolean isBendRuntimeVerified(IntroPhase phase) {
        return isBendRuntimeVerified(animationIdForPhase(phase));
    }

    public static boolean isBendRuntimeVerified(ResourceLocation animationId) {
        return Boolean.TRUE.equals(BEND_RUNTIME_VERIFIED.get(animationId));
    }

    public static boolean verifyRuntimeBendConsumption() {
        return verifyRuntimeBendConsumption(IntroPhase.SWITCH);
    }

    public static boolean verifyRuntimeBendConsumption(IntroPhase phase) {
        ResourceLocation animationId = animationIdForPhase(phase);
        if (!ensureReady()) {
            return false;
        }
        try {
            Object animation = getAnimationMethod.invoke(null, animationId);
            if (animation == null) {
                logBendProbeFailure(animationId, "animation-missing", null);
                return false;
            }
            Object animationPlayer = keyframeAnimationPlayerCtor.newInstance(animation);
            probeBendRuntime(animationId, animation, animationPlayer);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logBendProbeFailure(animationId, "reflection-runtime", e);
        }
        return isBendRuntimeVerified(animationId);
    }

    private static void probeBendRuntime(ResourceLocation animationId, Object animation, Object animationPlayer) {
        if (!BEND_PROBE_LOGGED.add(animationId)) {
            return;
        }
        try {
            BendProbe parsed = inspectParsedBends(animation);
            BendProbe runtime = inspectRuntimeBends(animationPlayer);
            Object probePlayer = keyframeAnimationPlayerCtor.newInstance(animation);
            BendProbe runtimeTick0 = inspectRuntimeBends(probePlayer);
            Method tick = probePlayer.getClass().getMethod("tick");
            for (int i = 0; i < 7; i++) {
                tick.invoke(probePlayer);
            }
            BendProbe runtimeTick7 = inspectRuntimeBends(probePlayer);
            // Arms are intentionally left to TACZ's third-person gun hold pose during the intro.
            // Torso and both legs still prove bendy-lib consumes the cinematic locomotion keys.
            boolean bendRuntimeVerified = parsed.bendParts >= 3 && parsed.directionParts >= 3 && Math.max(runtimeTick0.bendParts, runtimeTick7.bendParts) >= 3;
            BEND_RUNTIME_VERIFIED.put(animationId, bendRuntimeVerified);
            LOGGER.info("[BlockOffensive Halftime] playerAnimator bend parser check animation={} parsedBendParts={} parsedDirectionParts={} runtimeBendParts={} runtimeDirectionParts={} tick0BendParts={} tick0DirectionParts={} tick7BendParts={} tick7DirectionParts={} bendConsumed={} sampleTick0={} sampleTick7={}",
                    animationId, parsed.bendParts, parsed.directionParts, runtime.bendParts, runtime.directionParts,
                    runtimeTick0.bendParts, runtimeTick0.directionParts, runtimeTick7.bendParts, runtimeTick7.directionParts,
                    bendRuntimeVerified, runtimeTick0.sample, runtimeTick7.sample);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BEND_RUNTIME_VERIFIED.put(animationId, false);
            LOGGER.warn("[BlockOffensive Halftime] playerAnimator bend parser check failed for {}; limb bend will fall back to whole-part rotation", animationId, e);
        }
    }

    private static void logBendProbeFailure(ResourceLocation animationId, String reason, Throwable throwable) {
        if (!BEND_PROBE_LOGGED.add(animationId)) {
            return;
        }
        BEND_RUNTIME_VERIFIED.put(animationId, false);
        if (throwable == null) {
            LOGGER.warn("[BlockOffensive Halftime] playerAnimator bend parser check failed for {} reason={}; limb bend will fall back to whole-part rotation", animationId, reason);
        } else {
            LOGGER.warn("[BlockOffensive Halftime] playerAnimator bend parser check failed for {} reason={}; limb bend will fall back to whole-part rotation", animationId, reason, throwable);
        }
    }

    private static BendProbe inspectParsedBends(Object animation) throws ReflectiveOperationException {
        Method getPart = animation.getClass().getMethod("getPart", String.class);
        int bendParts = 0;
        int directionParts = 0;
        for (String partName : bendablePartNames()) {
            Object part = getPart.invoke(animation, partName);
            if (part == null) {
                continue;
            }
            if (stateHasKeyframes(part, "bend")) {
                bendParts++;
            }
            if (stateHasKeyframes(part, "bendDirection")) {
                directionParts++;
            }
        }
        return new BendProbe(bendParts, directionParts, "");
    }

    private static boolean stateHasKeyframes(Object stateCollection, String fieldName) throws ReflectiveOperationException {
        Field field = stateCollection.getClass().getField(fieldName);
        Object state = field.get(stateCollection);
        if (state == null) {
            return false;
        }
        Method length = state.getClass().getMethod("length");
        return (int) length.invoke(state) > 0;
    }

    private static BendProbe inspectRuntimeBends(Object animationPlayer) throws ReflectiveOperationException {
        Method getPart = animationPlayer.getClass().getMethod("getPart", String.class);
        Class<?> pairClass = Class.forName("dev.kosmx.playerAnim.core.util.Pair");
        Constructor<?> pairCtor = pairClass.getConstructor(Object.class, Object.class);
        Method getLeft = pairClass.getMethod("getLeft");
        Method getRight = pairClass.getMethod("getRight");
        int bendParts = 0;
        int directionParts = 0;
        StringBuilder sample = new StringBuilder();
        for (String partName : bendablePartNames()) {
            Object part = getPart.invoke(animationPlayer, partName);
            if (part == null) {
                continue;
            }
            Object fallback = pairCtor.newInstance(0.0f, 0.0f);
            Object bend = part.getClass().getMethod("getBend", pairClass).invoke(part, fallback);
            float direction = ((Float) getLeft.invoke(bend)).floatValue();
            float amount = ((Float) getRight.invoke(bend)).floatValue();
            if (!sample.isEmpty()) {
                sample.append(';');
            }
            sample.append(partName).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.3f/%.3f", direction, amount));
            if (Math.abs(amount) > 0.001f) {
                bendParts++;
            }
            if (Math.abs(direction) > 0.001f) {
                directionParts++;
            }
        }
        return new BendProbe(bendParts, directionParts, sample.toString());
    }

    private static String[] bendablePartNames() {
        return new String[] { "rightArm", "leftArm", "rightLeg", "leftLeg", "torso" };
    }

    public record SyncResult(boolean available, int targetTick, int currentTick, int drift, int rebuilds, int catchupTicks) {

        static SyncResult unavailable(int targetTick) {
            return new SyncResult(false, targetTick, 0, 0, 0, 0);
        }
    }

    private record ActiveIntroAnimation(ResourceLocation animationId, IntroPhase phase, int formationIndex, Object animation, Object animationPlayer) {

        boolean matches(ResourceLocation animationId, IntroPhase phase, int formationIndex) {
            return this.animationId.equals(animationId) && this.phase == phase && this.formationIndex == formationIndex;
        }
    }

    private record BendProbe(int bendParts, int directionParts, String sample) {}
}
