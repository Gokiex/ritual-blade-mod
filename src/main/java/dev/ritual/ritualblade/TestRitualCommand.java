package dev.ritual.ritualblade;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Registers /test command.
 */
public final class TestRitualCommand {
    private TestRitualCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("test")
            .requires(source -> source.hasPermission(2))
            .executes(context -> execute(context.getSource())));
    }

    private static int execute(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity executor)) {
            source.sendError(Text.literal("Only players can run this command."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        RitualConfig cfg = RitualConfigHolder.get();

        var maybeSite = RitualFinder.findClosestActivated(world, executor.getPosition(), cfg.searchRadius);
        if (maybeSite.isEmpty()) {
            source.sendError(Text.literal("No activated ritual table found in range."));
            return 0;
        }

        RitualSite site = maybeSite.get();
        if (RitualSequenceManager.isActive(world, site.tablePos())) {
            source.sendError(Text.literal("That ritual table is already active."));
            return 0;
        }

        RitualSequenceManager.start(world, site, executor.getUuid());
        source.sendFeedback(() -> Text.literal("Ritual started at " + site.tablePos().toShortString()
            + " with " + site.participants().size() + " participant(s)."), true);
        return 1;
    }
}
