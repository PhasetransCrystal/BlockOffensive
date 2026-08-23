package com.ptcrys.blockoffensive.mvp;

import java.util.UUID;

public record CSMvpResult(
        UUID uuid,
        String reasonKey,
        String infoKey,
        int score
) {
}
