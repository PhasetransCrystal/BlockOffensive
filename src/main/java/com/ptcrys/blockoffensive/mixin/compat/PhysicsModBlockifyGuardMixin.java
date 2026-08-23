package com.ptcrys.blockoffensive.mixin.compat;

import com.ptcrys.blockoffensive.compat.PhysicsDeathProxyGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 PhysicsMod 在代理死亡场景下直接 discard 本地实体导致的客户端断控问题。
 *
 * <p>当 {@link PhysicsDeathProxyGuard#isActive()} 为 true 时，跳过 blockifyEntity
 * 中的实体移除与本地死亡动画计时字段写入，仅保留布娃娃相关逻辑。</p>
 */
@Pseudo
@Mixin(targets = "net.diebuddies.physics.PhysicsMod", remap = false)
@Deprecated
public class PhysicsModBlockifyGuardMixin {

    @Redirect(
            method = "blockifyEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"
            )
    )
    private static void blockoffensive$guardEntityDiscard(LivingEntity entity, Entity.RemovalReason reason) {
        if (PhysicsDeathProxyGuard.isActive()) {
            return;
        }
        entity.remove(reason);
    }

    @Redirect(
            method = "blockifyEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;deathTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private static void blockoffensive$guardDeathTimeWrite(LivingEntity entity, int value) {
        if (PhysicsDeathProxyGuard.isActive()) {
            return;
        }
        entity.deathTime = value;
    }

    @Redirect(
            method = "blockifyEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private static void blockoffensive$guardHurtTimeWrite(LivingEntity entity, int value) {
        if (PhysicsDeathProxyGuard.isActive()) {
            return;
        }
        entity.hurtTime = value;
    }
}
