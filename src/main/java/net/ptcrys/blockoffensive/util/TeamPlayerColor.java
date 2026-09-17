package net.ptcrys.blockoffensive.util;

import net.ptcrys.fpsmatch.util.RenderUtil;

public enum TeamPlayerColor {

    BLUE(RenderUtil.color(94, 150, 255), "#5E96FF"),
    YELLOW(RenderUtil.color(238, 228, 75), "#EEE44B"),
    PURPLE(RenderUtil.color(179, 107, 226), "#B36BE2"),
    GREEN(RenderUtil.color(7, 156, 130), "#079C82"),
    ORANGE(RenderUtil.color(255, 155, 56), "#FF9B38");

    private final int rgb;
    private final String hex;

    TeamPlayerColor(int rgb, String hex) {
        this.rgb = rgb;
        this.hex = hex;
    }

    public int getRGBA() {
        return rgb;
    }

    public String getHex() {
        return hex;
    }
}
