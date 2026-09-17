package net.ptcrys.blockoffensive.data;

import net.ptcrys.fpsmatch.common.effect.FPSMEffectRegister;
import net.ptcrys.fpsmatch.compat.CounterStrikeGrenadesCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class DeathMessage {

    private final Component killer;
    private final UUID killerUUID;
    private final Component assist;
    private final UUID assistUUID;
    private final Component dead;
    private final UUID deadUUID;
    private final ItemStack weapon;
    private final String arg;
    private final boolean isHeadShot;
    private final boolean isBlinded;
    private final boolean isThroughSmoke;
    private final boolean isThroughWall;
    private final boolean isNoScope;
    private final boolean isFlying;

    private final ResourceLocation itemRL;

    private DeathMessage(Builder builder) {
        this.killer = builder.killer;
        this.killerUUID = builder.killerUUID;
        this.assist = builder.assist;
        this.assistUUID = builder.assistUUID;
        this.dead = builder.dead;
        this.deadUUID = builder.deadUUID;
        this.weapon = builder.weapon;
        this.itemRL = ForgeRegistries.ITEMS.getKey(this.weapon.getItem());
        this.arg = builder.arg;
        this.isHeadShot = builder.isHeadShot;
        this.isBlinded = builder.isBlinded;
        this.isThroughSmoke = builder.isThroughSmoke;
        this.isThroughWall = builder.isThroughWall;
        this.isNoScope = builder.isNoScope;
        this.isFlying = builder.isFlying;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public static class Builder {

        private final Component killer;
        private final UUID killerUUID;
        private Component assist = null;
        private UUID assistUUID = null;
        private final Component dead;
        private final UUID deadUUID;
        private final ItemStack weapon;
        private String arg = "";
        private boolean isHeadShot = false;
        private boolean isBlinded = false;
        private boolean isThroughSmoke = false;
        private boolean isThroughWall = false;
        private boolean isNoScope = false;
        private boolean isFlying = false;

        public Builder(Player killer, Player dead, ItemStack weapon) {
            this.killer = killer.getDisplayName();
            this.killerUUID = killer.getUUID();
            this.dead = dead.getDisplayName();
            this.deadUUID = dead.getUUID();
            this.weapon = weapon;
            setBlinded(killer);
        }

        public Builder(Component killer, UUID killerUUID, Component dead, UUID deadUUID, ItemStack weapon) {
            this.killer = killer;
            this.killerUUID = killerUUID;
            this.dead = dead;
            this.deadUUID = deadUUID;
            this.weapon = weapon;
        }

        public void setBlinded(Player killer) {
            if (deadUUID.equals(this.killerUUID)) return;

            if (killer.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS.get()) || killer.hasEffect(MobEffects.BLINDNESS) || killer.hasEffect(MobEffects.DARKNESS)) {
                this.isBlinded = true;
            } else {
                if (FPSMImpl.findCounterStrikeGrenadesMod()) {
                    this.isBlinded = CounterStrikeGrenadesCompat.isPlayerFlashed(killer);
                }
            }
        }

        public Builder setAssist(Component assist, UUID assistUUID) {
            this.assist = assist;
            this.assistUUID = assistUUID;
            return this;
        }

        public Builder setAssist(Player assist) {
            this.assist = assist.getDisplayName();
            this.assistUUID = assist.getUUID();
            return this;
        }

        public Builder setArg(String arg) {
            this.arg = arg;
            return this;
        }

        public Builder setHeadShot(boolean headShot) {
            this.isHeadShot = headShot;
            return this;
        }

        public Builder setBlinded(boolean blinded) {
            this.isBlinded = blinded;
            return this;
        }

        public Builder setThroughSmoke(boolean throughSmoke) {
            this.isThroughSmoke = throughSmoke;
            return this;
        }

        public Builder setThroughWall(boolean throughWall) {
            this.isThroughWall = throughWall;
            return this;
        }

        public Builder setNoScope(boolean noScope) {
            this.isNoScope = noScope;
            return this;
        }

        public DeathMessage build() {
            return new DeathMessage(this);
        }

        public Builder setFlying(boolean flying) {
            isFlying = flying;
            return this;
        }
    }

    // Getters
    public Component getKiller() {
        return killer;
    }

    public UUID getKillerUUID() {
        return killerUUID;
    }

    public Component getDead() {
        return dead;
    }

    public UUID getDeadUUID() {
        return deadUUID;
    }

    public ItemStack getWeapon() {
        return weapon;
    }

    public String getArg() {
        return arg;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public boolean isBlinded() {
        return isBlinded;
    }

    public boolean isThroughSmoke() {
        return isThroughSmoke;
    }

    public boolean isThroughWall() {
        return isThroughWall;
    }

    public boolean isNoScope() {
        return isNoScope;
    }

    public ResourceLocation getItemRL() {
        return itemRL;
    }

    public Component getAssist() {
        return assist;
    }

    public UUID getAssistUUID() {
        return assistUUID;
    }

    public ResourceLocation getWeaponIcon() {
        if (GunCompatManager.isGun(weapon)) {
            return GunCompatManager.findProvider(weapon).getGunHUDTexture(weapon);
        }
        return null;
    }
}
