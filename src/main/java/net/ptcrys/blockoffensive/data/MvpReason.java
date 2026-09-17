package net.ptcrys.blockoffensive.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MvpReason {

    public final UUID uuid;
    private boolean ctWinner;
    private MutableComponent teamName;
    private MutableComponent playerName;
    private MutableComponent mvpReason;
    private MutableComponent extraInfo1;
    private MutableComponent extraInfo2;

    private MvpReason(Builder builder) {
        this.uuid = builder.uuid;
        this.ctWinner = builder.ctWinner;
        this.teamName = builder.teamName;
        this.playerName = builder.playerName == null ? Component.empty() : builder.playerName;
        this.mvpReason = builder.mvpReason == null ? Component.empty() : builder.mvpReason;
        this.extraInfo1 = builder.extraInfo1 == null ? Component.empty() : builder.extraInfo1;
        this.extraInfo2 = builder.extraInfo2 == null ? Component.empty() : builder.extraInfo2;
    }

    public boolean isCtWinner() {
        return ctWinner;
    }

    public void setCtWinner(boolean ctWinner) {
        this.ctWinner = ctWinner;
    }

    public MutableComponent getTeamName() {
        return teamName;
    }

    public void setTeamName(MutableComponent teamName) {
        this.teamName = teamName;
    }

    public MutableComponent getPlayerName() {
        return playerName;
    }

    public void setPlayerName(MutableComponent playerName) {
        this.playerName = playerName;
    }

    public MutableComponent getMvpReason() {
        return mvpReason;
    }

    public void setMvpReason(MutableComponent mvpReason) {
        this.mvpReason = mvpReason;
    }

    public MutableComponent getExtraInfo1() {
        return extraInfo1;
    }

    public void setExtraInfo1(MutableComponent extraInfo1) {
        this.extraInfo1 = extraInfo1;
    }

    public MutableComponent getExtraInfo2() {
        return extraInfo2;
    }

    public void setExtraInfo2(MutableComponent extraInfo2) {
        this.extraInfo2 = extraInfo2;
    }

    public static class Builder {

        public final UUID uuid;
        boolean ctWinner;
        MutableComponent teamName;
        MutableComponent playerName;
        MutableComponent mvpReason;
        @Nullable
        MutableComponent extraInfo1;
        @Nullable
        MutableComponent extraInfo2;

        public Builder(UUID uuid) {
            this.uuid = uuid;
        }

        public Builder setTeamName(MutableComponent teamName) {
            this.teamName = teamName;
            return this;
        }

        public Builder setCtWinner(boolean ctWinner) {
            this.ctWinner = ctWinner;
            return this;
        }

        public Builder setPlayerName(MutableComponent playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder setMvpReason(MutableComponent mvpReason) {
            this.mvpReason = mvpReason;
            return this;
        }

        public Builder setExtraInfo1(@Nullable MutableComponent extraInfo1) {
            this.extraInfo1 = extraInfo1;
            return this;
        }

        public Builder setExtraInfo2(@Nullable MutableComponent extraInfo2) {
            this.extraInfo2 = extraInfo2;
            return this;
        }

        public MvpReason build() {
            return new MvpReason(this);
        }
    }
}
