package net.ptcrys.blockoffensive.net.spec;

import net.ptcrys.blockoffensive.spectator.BOSpecManager;
import net.ptcrys.blockoffensive.spectator.DamagePosTracker;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class RequestKillCamFallbackC2SPacket {

    private final UUID killerId;

    public RequestKillCamFallbackC2SPacket(UUID killerId) {
        this.killerId = killerId;
    }

    public static void encode(RequestKillCamFallbackC2SPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.killerId);
    }

    public static RequestKillCamFallbackC2SPacket decode(FriendlyByteBuf buf) {
        return new RequestKillCamFallbackC2SPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ServerPlayer victim = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (victim == null) return;

            var mapOpt = FPSMCore.getInstance().getMapByPlayer(victim);
            if (mapOpt.isEmpty() || !victim.isSpectator()) return;

            Optional<ServerPlayer> recordedKiller = BOSpecManager.getRecordedKiller(victim.getUUID());
            if (recordedKiller.isEmpty() || !killerId.equals(recordedKiller.get().getUUID())) return;
            ServerPlayer killer = recordedKiller.get();
            var killerMap = FPSMCore.getInstance().getMapByPlayer(killer);
            if (killerMap.isEmpty() || killerMap.get() != mapOpt.get() || !BOSpecManager.matchesRecordedMap(victim.getUUID(), mapOpt.get())) return;

            Vec3 kEye = killer.getEyePosition(1.0F);
            Vec3 vEye = DamagePosTracker.consumeVictimEye(victim).orElseGet(() -> victim.getEyePosition(1.0F));

            ItemStack weapon = Optional.of(killer.getMainHandItem()).map(s -> {
                ItemStack c = s.copy();
                if (!c.isEmpty()) c.setCount(1);
                return c;
            }).orElse(ItemStack.EMPTY);

            BOSpecManager.sendKillCamAndAttach(victim, killer, weapon, kEye, vEye);
        });
        ctx.setPacketHandled(true);
    }
}
