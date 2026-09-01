package com.ptcrys.blockoffensive.entity;

import com.ptcrys.blockoffensive.BlockOffensive;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BOEntityRegister {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BlockOffensive.MODID);
    public static final RegistryObject<EntityType<CompositionC4Entity>> C4 =
            ENTITY_TYPES.register("c4", () -> EntityType.Builder.<CompositionC4Entity>of(CompositionC4Entity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build("c4"));

    /** 队友 Ping 标记实体（世界内光柱）。 */
    public static final RegistryObject<EntityType<PingMarkerEntity>> PING_MARKER =
            ENTITY_TYPES.register("ping_marker", () -> EntityType.Builder.<PingMarkerEntity>of(PingMarkerEntity::new, MobCategory.MISC)
                    .sized(0.01f, 0.01f).noSummon().noSave().build("ping_marker"));
}
