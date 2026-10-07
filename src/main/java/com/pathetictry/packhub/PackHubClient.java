package com.pathetictry.packhub;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client side: registers /packs (and /pack) which open the Pack Hub toggle screen. */
public class PackHubClient implements ClientModInitializer {
    /** Set by the command, picked up at the end of the tick (after the chat screen has closed itself). */
    private static volatile boolean openRequested = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            for (String name : new String[] {"packs", "pack"}) {
                dispatcher.register(ClientCommands.literal(name).executes(context -> {
                    openRequested = true;
                    return 1;
                }));
            }
        });

        // Opening the screen straight from the command would be undone when the chat screen closes,
        // so wait until the end of the tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) {
                openRequested = false;
                PackHubScreen.open(new PackHubScreen(null));
            }
        });
    }
}
