package com.ptcrys.blockoffensive.sound;

import com.ptcrys.blockoffensive.BlockOffensive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("all")
public class BOSoundRegister {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BlockOffensive.MODID);

    public static final RegistryObject<SoundEvent> BEEP = SOUNDS.register("beep", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "beep")));
    public static final RegistryObject<SoundEvent> PLANTING = SOUNDS.register("planting", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "planting")));
    public static final RegistryObject<SoundEvent> PLANTED = SOUNDS.register("planted", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "planted")));
    public static final RegistryObject<SoundEvent> DEFUSED = SOUNDS.register("defused", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "defused")));
    public static final RegistryObject<SoundEvent> CLICK = SOUNDS.register("buttons_click", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "buttons_click")));

    public static final RegistryObject<SoundEvent> VOICE_CT_WIN = SOUNDS.register("voice_ct_win", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "voice_ct_win")));
    public static final RegistryObject<SoundEvent> VOICE_T_WIN = SOUNDS.register("voice_t_win", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "voice_t_win")));

    public static final RegistryObject<SoundEvent> FLASH = SOUNDS.register("flash", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "flash")));
    public static final RegistryObject<SoundEvent> BOOM = SOUNDS.register("boom", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "boom")));

    // 武器掉落音效
    public static final RegistryObject<SoundEvent> WEAPON_KNIFE_IMPACT = SOUNDS.register("weapon_knife_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_knife_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_HEAVY_IMPACT = SOUNDS.register("weapon_heavy_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_heavy_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_PISTOL_IMPACT = SOUNDS.register("weapon_pistol_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_pistol_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_RIFLE_IMPACT = SOUNDS.register("weapon_rifle_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_rifle_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_SHOTGUN_IMPACT = SOUNDS.register("weapon_shotgun_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_shotgun_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_SMG_IMPACT = SOUNDS.register("weapon_smg_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_smg_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_SNIPER_IMPACT = SOUNDS.register("weapon_sniper_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_sniper_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_C4_IMPACT = SOUNDS.register("weapon_c4_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_c4_impact")));
    public static final RegistryObject<SoundEvent> WEAPON_C4BEEP_IMPACT = SOUNDS.register("weapon_c4beep_impact", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_c4beep_impact")));

    // 武器拾取音效
    public static final RegistryObject<SoundEvent> WEAPON_PICKUP = SOUNDS.register("weapon_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_AMMO_PICKUP = SOUNDS.register("weapon_ammo_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_ammo_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_GRENADE_PICKUP = SOUNDS.register("weapon_grenade_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_grenade_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_PISTOL_PICKUP = SOUNDS.register("weapon_pistol_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_pistol_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_QUIET_PICKUP = SOUNDS.register("weapon_quiet_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_quiet_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_RIFLE_PICKUP = SOUNDS.register("weapon_rifle_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_rifle_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_SHOTGUN_PICKUP = SOUNDS.register("weapon_shotgun_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_shotgun_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_SMG_PICKUP = SOUNDS.register("weapon_smg_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_smg_pickup")));
    public static final RegistryObject<SoundEvent> WEAPON_SNIPER_PICKUP = SOUNDS.register("weapon_sniper_pickup", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_sniper_pickup")));

    // 其他音效
    public static final RegistryObject<SoundEvent> ACTION_JUMP_SHOT = SOUNDS.register("action_jump_shot", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "action_jump_shot")));
    public static final RegistryObject<SoundEvent> MATCH_POINT = SOUNDS.register("match_point", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "match_point")));
    public static final RegistryObject<SoundEvent> WEAPON_C4_PRE_EXPLODE = SOUNDS.register("weapon_c4_pre_explode", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "weapon_c4_pre_explode")));
    // 语音音效
    public static final RegistryObject<SoundEvent> T_PLANTINGBOMB = SOUNDS.register("t_plantingbomb", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "t_plantingbomb")));

    // T方投掷物语音
    public static final RegistryObject<SoundEvent> THROWABLE_SMOKE_T_THROW = SOUNDS.register("throwable_smoke_t_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_smoke_t_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_MOLOTOV_T_THROW = SOUNDS.register("throwable_molotov_t_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_molotov_t_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_FLASHBANG_T_THROW = SOUNDS.register("throwable_flashbang_t_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_flashbang_t_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_DECOY_T_THROW = SOUNDS.register("throwable_decoy_t_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_decoy_t_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_GRENADE_T_THROW = SOUNDS.register("throwable_grenade_t_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_grenade_t_throw")));

    // T方回合开始
    public static final RegistryObject<SoundEvent> T_ROUNDSTART = SOUNDS.register("t_roundstart", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "t_roundstart")));

    // CT方投掷物语音
    public static final RegistryObject<SoundEvent> THROWABLE_SMOKE_CT_THROW = SOUNDS.register("throwable_smoke_ct_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_smoke_ct_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_MOLOTOV_CT_THROW = SOUNDS.register("throwable_molotov_ct_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_molotov_ct_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_FLASHBANG_CT_THROW = SOUNDS.register("throwable_flashbang_ct_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_flashbang_ct_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_DECOY_CT_THROW = SOUNDS.register("throwable_decoy_ct_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_decoy_ct_throw")));
    public static final RegistryObject<SoundEvent> THROWABLE_GRENADE_CT_THROW = SOUNDS.register("throwable_grenade_ct_throw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "throwable_grenade_ct_throw")));

    // CT方回合开始
    public static final RegistryObject<SoundEvent> CT_ROUNDSTART = SOUNDS.register("ct_roundstart", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, "ct_roundstart")));
}