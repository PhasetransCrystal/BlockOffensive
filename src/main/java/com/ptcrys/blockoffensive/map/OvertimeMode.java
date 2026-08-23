package com.ptcrys.blockoffensive.map;

/**
 * 加时赛触发策略。
 * 
 *{@link #VOTE} —— 12-12 平局时发起加时投票（历史默认行为）：通过则进入加时，失败/超时判平局。
 * {@link #AUTO} —— 12-12 平局时无需投票，直接进入加时，避免流程中断（竞技推荐）。
 * {@link #DISABLED} —— 关闭加时：12-12 直接判平局结束，不投票不加时。
 * 
 */
public enum OvertimeMode {
    VOTE,
    AUTO,
    DISABLED
}
