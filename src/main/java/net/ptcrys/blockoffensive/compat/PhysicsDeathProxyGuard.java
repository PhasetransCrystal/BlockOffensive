package net.ptcrys.blockoffensive.compat;

/**
 * PhysicsMod 死亡兼容保护开关。
 * <p>
 * 仅在 BO 的代理死亡管线触发 PhysicsMod ragdoll 时开启，
 * 用于让 mixin 跳过 blockifyEntity 内对实体的 discard 步骤，
 * 避免客户端本地玩家实体被误移除。
 */
public final class PhysicsDeathProxyGuard {

    private static boolean active = false;

    private PhysicsDeathProxyGuard() {}

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
