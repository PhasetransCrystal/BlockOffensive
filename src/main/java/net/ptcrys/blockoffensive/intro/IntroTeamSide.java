package net.ptcrys.blockoffensive.intro;

import java.util.Locale;
import java.util.Optional;

public enum IntroTeamSide {

    CT("ct"),
    T("t");

    private final String id;

    IntroTeamSide(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<IntroTeamSide> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ct" -> Optional.of(CT);
            case "t" -> Optional.of(T);
            default -> Optional.empty();
        };
    }
}
