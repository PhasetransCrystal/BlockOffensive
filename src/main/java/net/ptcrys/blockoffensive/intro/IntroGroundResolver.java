package net.ptcrys.blockoffensive.intro;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class IntroGroundResolver {

    private static final double FOOT_HALF_WIDTH = 0.31;
    private static final double PLAYER_HEIGHT = 1.80;
    private static final double GROUND_EPSILON = 0.001;
    private static final int SCAN_UP_BLOCKS = 3;
    private static final int SCAN_DOWN_BLOCKS = 9;

    private IntroGroundResolver() {}

    public static Result resolve(Level level, Vec3 planned) {
        if (level == null || planned == null) {
            return Result.fallback(planned == null ? Vec3.ZERO : planned, true, "missing-level-or-position");
        }

        int minBlockX = Mth.floor(planned.x - FOOT_HALF_WIDTH);
        int maxBlockX = Mth.floor(planned.x + FOOT_HALF_WIDTH);
        int minBlockZ = Mth.floor(planned.z - FOOT_HALF_WIDTH);
        int maxBlockZ = Mth.floor(planned.z + FOOT_HALF_WIDTH);
        int startY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(planned.y) + SCAN_UP_BLOCKS);
        int endY = Math.max(level.getMinBuildHeight(), Mth.floor(planned.y) - SCAN_DOWN_BLOCKS);
        boolean sawUnsafeSurface = false;
        for (int y = startY; y >= endY; y--) {
            double highestFeetY = Double.NEGATIVE_INFINITY;
            for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
                for (int blockZ = minBlockZ; blockZ <= maxBlockZ; blockZ++) {
                    BlockPos pos = new BlockPos(blockX, y, blockZ);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    Double feetY = highestSupportingSurface(shape, pos, planned.x, planned.z);
                    if (feetY != null) {
                        highestFeetY = Math.max(highestFeetY, feetY);
                    }
                }
            }
            if (highestFeetY == Double.NEGATIVE_INFINITY) {
                continue;
            }
            Vec3 grounded = new Vec3(planned.x, highestFeetY, planned.z);
            if (hasStandingSpace(level, grounded)) {
                return new Result(grounded, false, false, grounded.y - planned.y, "ok");
            }
            sawUnsafeSurface = true;
        }
        return Result.fallback(planned, true, sawUnsafeSurface ? "unsafe-headroom" : "no-collision-surface");
    }

    private static Double highestSupportingSurface(VoxelShape shape, BlockPos pos, double worldX, double worldZ) {
        double localX = worldX - pos.getX();
        double localZ = worldZ - pos.getZ();
        double minX = localX - FOOT_HALF_WIDTH;
        double maxX = localX + FOOT_HALF_WIDTH;
        double minZ = localZ - FOOT_HALF_WIDTH;
        double maxZ = localZ + FOOT_HALF_WIDTH;
        double highest = Double.NEGATIVE_INFINITY;
        List<AABB> boxes = shape.toAabbs();
        for (AABB box : boxes) {
            if (box.maxX <= minX || box.minX >= maxX || box.maxZ <= minZ || box.minZ >= maxZ) {
                continue;
            }
            highest = Math.max(highest, pos.getY() + box.maxY);
        }
        return highest == Double.NEGATIVE_INFINITY ? null : highest;
    }

    private static boolean hasStandingSpace(Level level, Vec3 feet) {
        AABB body = new AABB(
                feet.x - FOOT_HALF_WIDTH,
                feet.y + GROUND_EPSILON,
                feet.z - FOOT_HALF_WIDTH,
                feet.x + FOOT_HALF_WIDTH,
                feet.y + PLAYER_HEIGHT,
                feet.z + FOOT_HALF_WIDTH);
        return level.noCollision(body);
    }

    public record Result(Vec3 position, boolean fallback, boolean unsafe, double deltaY, String reason) {

        static Result fallback(Vec3 position, boolean unsafe, String reason) {
            return new Result(position, true, unsafe, 0.0, reason);
        }
    }
}
