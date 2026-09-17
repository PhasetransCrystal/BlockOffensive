package net.ptcrys.blockoffensive;

import net.ptcrys.blockoffensive.command.CSCommand;
import net.ptcrys.blockoffensive.compat.BOImpl;
import net.ptcrys.blockoffensive.compat.CSGrenadeCompat;
import net.ptcrys.blockoffensive.compat.PhysicsModCompat;
import net.ptcrys.blockoffensive.entity.BOEntityRegister;
import net.ptcrys.blockoffensive.intro.IntroSoundEvents;
import net.ptcrys.blockoffensive.item.BOItemRegister;
import net.ptcrys.blockoffensive.map.team.capability.ColoredPlayerCapability;
import net.ptcrys.blockoffensive.net.*;
import net.ptcrys.blockoffensive.net.spec.*;
import net.ptcrys.blockoffensive.sound.BOSoundRegister;
import net.ptcrys.blockoffensive.util.BOUtil;
import net.ptcrys.blockoffensive.util.ThrowableType;
import net.ptcrys.fpsmatch.common.item.FPSMItemRegister;
import net.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.ptcrys.fpsmatch.common.sound.FPSMSoundRegister;
import net.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(BlockOffensive.MODID)
public class BlockOffensive {

    public static final String MODID = "blockoffensive";
    private static final NetworkPacketRegister PACKET_REGISTER = new NetworkPacketRegister(
            ResourceLocation.tryBuild(MODID, "main"), BOPacketRegistration.PROTOCOL_VERSION);
    public static final SimpleChannel INSTANCE = PACKET_REGISTER.getChannel();

    @SuppressWarnings("removal")
    public BlockOffensive() {
        this(FMLJavaModLoadingContext.get());
    }

    public BlockOffensive(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        // InterModEnqueueEvent 是 MOD 生命周期事件，只会在 mod 事件总线上触发，
        // 必须通过 modEventBus.addListener 注册，而不是挂到游戏总线 MinecraftForge.EVENT_BUS。
        modEventBus.addListener(this::onEnqueue);
        MinecraftForge.EVENT_BUS.register(this);
        BOItemRegister.ITEMS.register(modEventBus);
        BOItemRegister.TABS.register(modEventBus);
        BOEntityRegister.ENTITY_TYPES.register(modEventBus);
        BOSoundRegister.SOUNDS.register(modEventBus);
        IntroSoundEvents.SOUND_EVENTS.register(modEventBus);
        context.registerConfig(ModConfig.Type.CLIENT, BOConfig.clientSpec);
        context.registerConfig(ModConfig.Type.COMMON, BOConfig.commonSpec);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CSCommand.onRegisterCommands(event);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        BOPacketRegistration.register(PACKET_REGISTER);

        event.enqueueWork(() -> {
            ColoredPlayerCapability.register();

            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.PISTOL, BOSoundRegister.WEAPON_PISTOL_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.RIFLE, BOSoundRegister.WEAPON_RIFLE_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SHOTGUN, BOSoundRegister.WEAPON_SHOTGUN_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SMG, BOSoundRegister.WEAPON_SMG_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SNIPER, BOSoundRegister.WEAPON_SNIPER_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.MG, BOSoundRegister.WEAPON_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.RPG, BOSoundRegister.WEAPON_PICKUP.get());

            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.PISTOL, BOSoundRegister.WEAPON_PISTOL_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SNIPER, BOSoundRegister.WEAPON_SNIPER_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.RIFLE, BOSoundRegister.WEAPON_RIFLE_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SMG, BOSoundRegister.WEAPON_SMG_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SHOTGUN, BOSoundRegister.WEAPON_SHOTGUN_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.MG, BOSoundRegister.WEAPON_HEAVY_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.RPG, BOSoundRegister.WEAPON_HEAVY_IMPACT.get());

            FPSMSoundRegister.registerKnifeDropSound(BOSoundRegister.WEAPON_KNIFE_IMPACT.get());
            FPSMSoundRegister.registerItemPickupSound(BOItemRegister.C4.get(), SoundEvents.EXPERIENCE_ORB_PICKUP);
            FPSMSoundRegister.registerItemDropSound(BOItemRegister.C4.get(), BOSoundRegister.WEAPON_C4_IMPACT.get());

            BOUtil.registerThrowable(ThrowableType.SMOKE, FPSMItemRegister.SMOKE_SHELL.get());
            BOUtil.registerThrowable(ThrowableType.GRENADE, FPSMItemRegister.GRENADE.get());
            BOUtil.registerThrowable(ThrowableType.INCENDIARY_GRENADE, FPSMItemRegister.T_INCENDIARY_GRENADE.get());
            BOUtil.registerThrowable(ThrowableType.INCENDIARY_GRENADE, FPSMItemRegister.CT_INCENDIARY_GRENADE.get());
            BOUtil.registerThrowable(ThrowableType.FLASH_BANG, FPSMItemRegister.FLASH_BOMB.get());

            // 兼容层注册（各模组兼容层在此统一注册）
            registerCompat();
        });
    }

    /**
     * 统一注册所有模组兼容层。
     * 由 {@code commonSetup} 在 enqueueWork 中调用，确保在主线程执行。
     */
    private static void registerCompat() {
        // 物理模组兼容
        if (BOImpl.isPhysicsModLoaded()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhysicsModCompat.init());
        }
        // CS Grenade 兼容
        if (FPSMImpl.findCounterStrikeGrenadesMod()) {
            CSGrenadeCompat.init();
        }
    }

    @SubscribeEvent
    public void onEnqueue(final InterModEnqueueEvent event) {
        event.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            try {
                if (FPSMImpl.findClothConfig()) {
                    Class<?> integration = Class.forName(
                            "net.ptcrys.blockoffensive.compat.BOMenuIntegration");
                    integration.getMethod("registerModsPage").invoke(null);
                } else {
                    Class<?> clothScreenClass = Class.forName(
                            "com.tacz.guns.client.gui.compat.ClothConfigScreen");
                    clothScreenClass.getMethod("registerNoClothConfigPage").invoke(null);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Optional client configuration integration is unavailable.
            }
        });
    }
}
