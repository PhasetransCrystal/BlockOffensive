package com.ptcrys.blockoffensive.util;

import com.ptcrys.fpsmatch.util.RenderUtil;

public enum TeamPlayerColor {
    BLUE(RenderUtil.color(216,130,44),"#D8822C"),
    YELLOW(RenderUtil.color(238,228,75),"#EEE44B"),
    PURPLE(RenderUtil.color(66,185,131),"#42B983"),
    GREEN(RenderUtil.color(7,156,130),"#079C82"),
    ORANGE(RenderUtil.color(145,203,234),"#91CBEA");

    private final int rgb;
    private final String hex;

    TeamPlayerColor(int rgb, String hex) {
        this.rgb = rgb;
        this.hex = hex;
    }

    public int getRGBA() {
        return rgb;
    }

    public String getHex(){
        return hex;
    }
}
