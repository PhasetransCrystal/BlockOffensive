package com.ptcrys.blockoffensive;

import com.ptcrys.blockoffensive.minimap.BlockOffensiveMinimapExtension;

import com.ptcrys.blockoffensive.command.CSCommand;
import com.ptcrys.blockoffensive.command.BOCommandRegister;
import com.ptcrys.blockoffensive.compat.BOImpl;
import com.ptcrys.blockoffensive.compat.BOMenuIntegration;
import com.ptcrys.blockoffensive.compat.CSGrenadeCompat;
import com.ptcrys.blockoffensive.compat.PhysicsModCompat;
import com.ptcrys.blockoffensive.entity.BOEntityRegister;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.map.team.capability.ColoredPlayerCapability;
import com.ptcrys.blockoffensive.net.*;
import com.ptcrys.blockoffensive.net.bomb.BombActionC2SPacket;
import com.ptcrys.blockoffensive.net.bomb.BombActionS2CPacket;
import com.ptcrys.blockoffensive.net.bomb.BombDemolitionProgressS2CPacket;
import com.ptcrys.blockoffensive.net.dm.PlayerMoveC2SPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpHUDCloseS2CPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpMessageS2CPacket;
import com.ptcrys.blockoffensive.net.shop.ShopStatesS2CPacket;
import com.ptcrys.blockoffensive.net.spec.*;
import com.ptcrys.blockoffensive.net.vote.VoteCastC2SPacket;
import com.ptcrys.blockoffensive.net.vote.VoteSyncS2CPacket;
import com.ptcrys.blockoffensive.sound.BOSoundRegister;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.blockoffensive.util.ThrowableType;
import com.ptcrys.fpsmatch.common.item.FPSMItemRegister;
import com.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.ptcrys.fpsmatch.common.sound.FPSMSoundRegister;
import com.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import com.ptcrys.fpsmatch.compat.gun.GunTabTypeEnum;
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
    private static final String PROTOCOL_VERSION = "1.3.0";
    private static final NetworkPacketRegister PACKET_REGISTER = new NetworkPacketRegister(ResourceLocation.tryBuild(MODID, "main"),PROTOCOL_VERSION);
    public static final SimpleChannel INSTANCE = PACKET_REGISTER.getChannel();

    @SuppressWarnings("removal")
    public BlockOffensive() {
        this(FMLJavaModLoadingContext.get());
    }

    public BlockOffensive(FMLJavaModLoadingContext context)
    {
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
        context.registerConfig(ModConfig.Type.CLIENT, BOConfig.clientSpec);
        context.registerConfig(ModConfig.Type.COMMON, BOConfig.commonSpec);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BOCommandRegister.onRegisterCommands(event);
        CSCommand.onRegisterCommands(event);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        BlockOffensiveMinimapExtension.register();
        PACKET_REGISTER.registerPacket(BombActionC2SPacket.class);
        PACKET_REGISTER.registerPacket(BombActionS2CPacket.class);
        PACKET_REGISTER.registerPacket(BombDemolitionProgressS2CPacket.class);
        PACKET_REGISTER.registerPacket(MvpHUDCloseS2CPacket.class);
        PACKET_REGISTER.registerPacket(MvpMessageS2CPacket.class);
        PACKET_REGISTER.registerPacket(ShopStatesS2CPacket.class);
        PACKET_REGISTER.registerPacket(CSGameSettingsS2CPacket.class);
        PACKET_REGISTER.registerPacket(CSTabRemovalS2CPacket.class);
        PACKET_REGISTER.registerPacket(DeathMessageS2CPacket.class);
        PACKET_REGISTER.registerPacket(PxDeathCompatS2CPacket.class);
        PACKET_REGISTER.registerPacket(PxRagdollRemovalCompatS2CPacket.class);
        PACKET_REGISTER.registerPacket(CSGameWeaponDataS2CPacket.class);
        PACKET_REGISTER.registerPacket(BombFuseS2CPacket.class);
        PACKET_REGISTER.registerPacket(PlayerMoveC2SPacket.class);
        PACKET_REGISTER.registerPacket(KillCamS2CPacket.class);
        PACKET_REGISTER.registerPacket(RequestAttachTeammateC2SPacket.class);
        PACKET_REGISTER.registerPacket(RequestKillCamFallbackC2SPacket.class);
        PACKET_REGISTER.registerPacket(SwitchSpectateC2SPacket.class);
        PACKET_REGISTER.registerPacket(SpectatorRosterS2CPacket.class);
        PACKET_REGISTER.registerPacket(VoteSyncS2CPacket.class);
        PACKET_REGISTER.registerPacket(VoteCastC2SPacket.class);

        event.enqueueWork(() -> {
            ColoredPlayerCapability.register();

            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.PISTOL,BOSoundRegister.WEAPON_PISTOL_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.RIFLE,BOSoundRegister.WEAPON_RIFLE_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SHOTGUN,BOSoundRegister.WEAPON_SHOTGUN_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SMG,BOSoundRegister.WEAPON_SMG_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.SNIPER,BOSoundRegister.WEAPON_SNIPER_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.MG,BOSoundRegister.WEAPON_PICKUP.get());
            FPSMSoundRegister.registerGunPickupSound(GunTabTypeEnum.RPG,BOSoundRegister.WEAPON_PICKUP.get());

            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.PISTOL,BOSoundRegister.WEAPON_PISTOL_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SNIPER,BOSoundRegister.WEAPON_SNIPER_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.RIFLE,BOSoundRegister.WEAPON_RIFLE_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SMG,BOSoundRegister.WEAPON_SMG_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.SHOTGUN,BOSoundRegister.WEAPON_SHOTGUN_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.MG,BOSoundRegister.WEAPON_HEAVY_IMPACT.get());
            FPSMSoundRegister.registerGunDropSound(GunTabTypeEnum.RPG,BOSoundRegister.WEAPON_HEAVY_IMPACT.get());

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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PhysicsModCompat::init);
        }
        // CS Grenade 兼容
        if (FPSMImpl.findCounterStrikeGrenadesMod()) {
            CSGrenadeCompat.init();
        }
    }

    @SubscribeEvent
    public void onEnqueue(final InterModEnqueueEvent event) {
        event.enqueueWork(()->{
            if(FPSMImpl.findClothConfig()){
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> BOMenuIntegration::registerModsPage);
            }else{
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    try {
                        Class<?> clothScreenClass = Class.forName("com.tacz.guns.client.gui.compat.ClothConfigScreen");
                        clothScreenClass.getMethod("registerNoClothConfigPage").invoke(null);
                    } catch (Exception ignored) {
                        // TACZ 未加载，无需注册
                    }
                }
            }
        });
    }
}
