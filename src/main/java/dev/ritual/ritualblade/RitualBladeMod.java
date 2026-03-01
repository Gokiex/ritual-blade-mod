package dev.ritual.ritualblade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod entrypoint.
 */
public class RitualBladeMod implements ModInitializer {
    public static final String MOD_ID = "ritualblademod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RitualConfigHolder.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            TestRitualCommand.register(dispatcher)
        );

        ServerTickEvents.END_SERVER_TICK.register(RitualSequenceManager::tickServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RitualSequenceManager.clear());

        LOGGER.info("Ritual Blade Mod initialized");
    }
}
