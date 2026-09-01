package com.ptcrys.blockoffensive.net;

import com.ptcrys.blockoffensive.net.bomb.BombActionC2SPacket;
import com.ptcrys.blockoffensive.net.bomb.BombActionS2CPacket;
import com.ptcrys.blockoffensive.net.bomb.BombDemolitionProgressS2CPacket;
import com.ptcrys.blockoffensive.net.dm.PlayerMoveC2SPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpHUDCloseS2CPacket;
import com.ptcrys.blockoffensive.net.mvp.MvpMessageS2CPacket;
import com.ptcrys.blockoffensive.net.shop.ShopStatesS2CPacket;
import com.ptcrys.blockoffensive.net.spec.BombFuseS2CPacket;
import com.ptcrys.blockoffensive.net.spec.CSGameWeaponDataS2CPacket;
import com.ptcrys.blockoffensive.net.spec.KillCamS2CPacket;
import com.ptcrys.blockoffensive.net.spec.RequestAttachTeammateC2SPacket;
import com.ptcrys.blockoffensive.net.spec.RequestKillCamFallbackC2SPacket;
import com.ptcrys.blockoffensive.net.spec.SpectatorRosterS2CPacket;
import com.ptcrys.blockoffensive.net.spec.SwitchSpectateC2SPacket;
import com.ptcrys.blockoffensive.net.vote.VoteCastC2SPacket;
import com.ptcrys.blockoffensive.net.vote.VoteSyncS2CPacket;
import com.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.minecraftforge.network.NetworkDirection;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Single source of truth for the BO channel discriminator order. */
public final class BOPacketRegistration {
    public static final String PROTOCOL_VERSION = "1.4.1";

    public enum Direction {
        DEFAULT,
        PLAY_TO_CLIENT,
        PLAY_TO_SERVER
    }

    @FunctionalInterface
    public interface Registrar {
        void register(Class<?> packetClass, Direction direction);
    }

    private BOPacketRegistration() {
    }

    public static void register(NetworkPacketRegister register) {
        Objects.requireNonNull(register, "register");
        register((packetClass, direction) -> {
            switch (direction) {
                case DEFAULT -> register.registerPacket(packetClass);
                case PLAY_TO_CLIENT -> register.registerPacket(
                        packetClass, NetworkDirection.PLAY_TO_CLIENT);
                case PLAY_TO_SERVER -> register.registerPacket(
                        packetClass, NetworkDirection.PLAY_TO_SERVER);
            }
        });
    }

    /** Testable registration seam which deliberately preserves the legacy order. */
    public static void register(Registrar registrar) {
        Objects.requireNonNull(registrar, "registrar");
        Class<?>[] legacy = {
                BombActionC2SPacket.class,
                BombActionS2CPacket.class,
                BombDemolitionProgressS2CPacket.class,
                MvpHUDCloseS2CPacket.class,
                MvpMessageS2CPacket.class,
                ShopStatesS2CPacket.class,
                CSGameSettingsS2CPacket.class,
                CSTabRemovalS2CPacket.class,
                DeathMessageS2CPacket.class,
                PxDeathCompatS2CPacket.class,
                PxRagdollRemovalCompatS2CPacket.class,
                CSGameWeaponDataS2CPacket.class,
                BombFuseS2CPacket.class,
                PlayerMoveC2SPacket.class,
                KillCamS2CPacket.class,
                RequestAttachTeammateC2SPacket.class,
                RequestKillCamFallbackC2SPacket.class,
                SwitchSpectateC2SPacket.class,
                SpectatorRosterS2CPacket.class,
                VoteSyncS2CPacket.class,
                VoteCastC2SPacket.class
        };
        for (int discriminator = 0; discriminator < legacy.length; discriminator++) {
            Direction direction = discriminator == 2 || discriminator == 8
                    ? Direction.PLAY_TO_CLIENT
                    : Direction.DEFAULT;
            registrar.register(legacy[discriminator], direction);
        }
        registrar.register(
                com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneS2CPacket.class,
                Direction.PLAY_TO_CLIENT
        );
        registrar.register(
                com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAckC2SPacket.class,
                Direction.PLAY_TO_SERVER
        );
        registrar.register(
                com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContextC2SPacket.class,
                Direction.PLAY_TO_SERVER
        );
        // 本地功能数据包（Ping 标记 + MVP 音乐，顺序与 legacy 内联注册一致）
        registrar.register(
                com.ptcrys.blockoffensive.net.ping.PingC2SPacket.class,
                Direction.PLAY_TO_SERVER
        );
        registrar.register(
                com.ptcrys.blockoffensive.net.ping.PingS2CPacket.class,
                Direction.PLAY_TO_CLIENT
        );
        registrar.register(
                com.ptcrys.blockoffensive.net.mvp.MvpMusicUploadC2SPacket.class,
                Direction.PLAY_TO_SERVER
        );
        registrar.register(
                com.ptcrys.blockoffensive.net.mvp.MvpMusicChunkS2CPacket.class,
                Direction.PLAY_TO_CLIENT
        );
    }
}
