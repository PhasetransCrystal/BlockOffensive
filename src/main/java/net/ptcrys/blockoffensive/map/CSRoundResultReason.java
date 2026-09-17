package net.ptcrys.blockoffensive.map;

/**
 * CS 爆破模式回合胜负原因。
 * 独立为顶层枚举，避免测试和通用生命周期被 CSGameMap 的 Minecraft 依赖污染。
 */
public enum CSRoundResultReason {
    TIME_OUT,
    ACED,
    DEFUSE_BOMB,
    DETONATE_BOMB
}
