package net.ptcrys.blockoffensive.client.spec;

public final class SpecHudAPI {

    private static IStyleProvider STYLE = IStyleProvider.DEFAULT;

    public static void setStyleProvider(IStyleProvider p) {
        STYLE = p == null ? IStyleProvider.DEFAULT : p;
    }

    public static IStyleProvider style() {
        return STYLE;
    }

    private SpecHudAPI() {}
}
