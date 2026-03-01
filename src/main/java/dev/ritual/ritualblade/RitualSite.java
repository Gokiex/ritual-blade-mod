package dev.ritual.ritualblade;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Computed ritual site state for command-time activation checks.
 */
public record RitualSite(BlockPos tablePos, List<BlockPos> dustPositions, List<ServerPlayerEntity> participants) {
}
