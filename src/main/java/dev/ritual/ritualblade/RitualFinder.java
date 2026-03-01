package dev.ritual.ritualblade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Finds nearby activated ritual tables.
 */
public final class RitualFinder {
    private RitualFinder() {
    }

    public static Optional<RitualSite> findClosestActivated(ServerWorld world, Vec3d origin, int radius) {
        BlockPos center = BlockPos.ofFloored(origin);
        int sq = radius * radius;
        List<RitualSite> matches = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterateOutwards(center, radius, radius, radius)) {
            if (center.getSquaredDistance(pos) > sq) {
                continue;
            }
            if (!world.getBlockState(pos).isOf(Blocks.ENCHANTING_TABLE)) {
                continue;
            }

            RitualSite site = evaluateSite(world, pos);
            if (site != null && !site.participants().isEmpty()) {
                matches.add(site);
            }
        }

        return matches.stream()
            .min(Comparator.comparingDouble(site -> site.tablePos().getSquaredDistance(origin.x, origin.y, origin.z)));
    }

    private static RitualSite evaluateSite(ServerWorld world, BlockPos tablePos) {
        BlockPos c = tablePos.down();
        List<BlockPos> dust = List.of(c.north(), c.south(), c.east(), c.west());

        for (BlockPos p : dust) {
            if (!world.getBlockState(p).isOf(Blocks.REDSTONE_WIRE)) {
                return null;
            }
        }

        Box participantScan = new Box(c).expand(2.0);
        List<ServerPlayerEntity> nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity.class, participantScan,
            player -> true);
        List<ServerPlayerEntity> participants = new ArrayList<>();

        for (ServerPlayerEntity player : nearbyPlayers) {
            BlockPos feet = player.getBlockPos();
            if (dust.contains(feet)) {
                participants.add(player);
            }
        }

        return new RitualSite(tablePos, dust, participants);
    }
}
