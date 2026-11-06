package dev.watdafawx.hungryloop.integration.accessories;

import io.wispforest.accessories.client.gui.AccessoriesScreenBase;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.server.ScreenOpen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client-side Accessories bits for the loop screen's overlay ({@code client.LoopOverlay}). Only
 * reached behind {@code ModList.isLoaded("accessories")}.
 */
public final class AccessoriesClientHooks {

    private AccessoriesClientHooks() {}

    public static boolean isAccessoriesScreen(Screen screen) {
        return screen instanceof AccessoriesScreenBase<?>;
    }

    /** Reopen the Accessories screen the loop was opened from, same variant, same target entity. */
    public static void reopen(Screen parent) {
        if (parent instanceof AccessoriesScreenBase<?> base) {
            var menu = base.getMenu();
            AccessoriesNetworking.sendToServer(ScreenOpen.of(menu.targetEntity(), menu.menuVariant()));
        }
    }
}
