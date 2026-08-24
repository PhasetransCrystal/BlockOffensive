package com.ptcrys.blockoffensive.net.bomb;

import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public record BombActionC2SPacket(boolean action) {

    public static void encode(BombActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.action);
    }

    public static BombActionC2SPacket decode(FriendlyByteBuf buf) {
        return new BombActionC2SPacket(
                buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        // 所有 map/team 查询都必须在 enqueueWork 内于服务端主线程执行，
        // 避免在 netty 线程读 FPSMCore 的非线程安全缓存而引发数据竞争。
        ctx.get().enqueueWork(() -> {
            if (sender == null) {
                ctx.get().setPacketHandled(true);
                return;
            }
            Optional<BaseMap> optional = FPSMCore.getInstance().getMapByPlayer(sender);
            if (optional.isEmpty()) {
                ctx.get().setPacketHandled(true);
                return;
            }
            BaseMap map = optional.get();
            ServerTeam team = map.getMapTeams().getTeamByPlayer(sender).orElse(null);
            if (team == null) {
                ctx.get().setPacketHandled(true);
                return;
            }

            if (map instanceof CSGameMap csGameMap && !csGameMap.checkCanPlacingBombs(team.getFixedName())) {
                List<? extends CompositionC4Entity> entities = sender.serverLevel().getEntities(EntityTypeTest.forClass(CompositionC4Entity.class),(t)->{
                    LivingEntity player = t.getDemolisher();
                    return player != null && player.getUUID().equals(sender.getUUID());
                });
                if(!action){
                    entities.forEach(CompositionC4Entity::resetDemolisher);
                }else{
                    HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(sender,(entity -> entity instanceof CompositionC4Entity),2);
                    if(hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof CompositionC4Entity c4){
                        c4.setDemolisher(sender);
                    }else{
                        entities.forEach(CompositionC4Entity::resetDemolisher);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
