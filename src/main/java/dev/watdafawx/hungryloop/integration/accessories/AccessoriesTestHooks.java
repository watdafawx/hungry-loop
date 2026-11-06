package dev.watdafawx.hungryloop.integration.accessories;

import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.client.gui.AccessoriesScreenBase;
import io.wispforest.accessories.menu.AccessoriesInternalSlot;
import io.wispforest.accessories.menu.AccessoriesMenuVariant;
import io.wispforest.accessories.menu.variants.AccessoriesExperimentalMenu;
import io.wispforest.accessories.menu.variants.AccessoriesMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only hooks {@code client.SelfTest} uses to drive and inspect Accessories' screens without
 * importing Accessories types itself (it must load in packs without Accessories). Only reached
 * behind a {@code ModList.isLoaded("accessories")} check.
 */
public final class AccessoriesTestHooks {

    private AccessoriesTestHooks() {}

    public static void open(ServerPlayer player, boolean experimental) {
        Accessories.openAccessoriesMenu(player,
                experimental ? AccessoriesMenuVariant.EXPERIMENTAL_V1 : AccessoriesMenuVariant.ORIGINAL, null);
    }

    public static boolean isScreen(Screen screen, boolean experimental) {
        return screen instanceof AccessoriesScreenBase<?> base
                && (experimental ? base.getMenu() instanceof AccessoriesExperimentalMenu
                                 : base.getMenu() instanceof AccessoriesMenu);
    }

    /** A menu slot of the loop's own slot type - empty or holding a loop, as asked. */
    public static Slot loopSlot(AbstractContainerMenu menu, boolean empty) {
        for (Slot slot : menu.slots) {
            if (slot instanceof AccessoriesInternalSlot s && !s.isCosmetic && s.slotName().endsWith("loop")
                    && s.getItem().isEmpty() == empty) {
                return s;
            }
        }
        return null;
    }

    /** How many menu rows currently belong to a loop mirror - proves a check had something to hide. */
    public static int mirrorRows(AbstractContainerMenu menu) {
        int n = 0;
        for (Slot slot : menu.slots) {
            if (slot instanceof AccessoriesInternalSlot s && !s.isCosmetic && AccessoriesMirrors.isMirrorRow(s)) n++;
        }
        return n;
    }

    /**
     * Ghost-slot checks for an open Accessories menu: no mirror row may be usable/drawn, and the
     * shown rows must have no gaps. Reads only final slot state, not how the mixins got there.
     */
    public static List<String> layoutFails(AbstractContainerMenu menu) {
        List<String> fails = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot instanceof AccessoriesInternalSlot s && AccessoriesMirrors.isMirrorRow(s) && s.isActive()) {
                fails.add("VISIBLE mirror row " + s.slotName() + ":" + s.getContainerSlot() + (s.isCosmetic ? " (cosmetic)" : "")
                        + " at " + s.x + "," + s.y);
            }
        }
        if (menu instanceof AccessoriesMenu original) {
            // one column, rows 18px apart from y=16 (scrolled to the top on open)
            List<Integer> ys = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (slot instanceof AccessoriesInternalSlot s && !s.isCosmetic && s.isActive()) ys.add(s.y);
            }
            ys.sort(Integer::compare);
            for (int i = 0; i < ys.size(); i++) {
                if (ys.get(i) != 16 + 18 * i) {
                    fails.add("GAP in original screen column: row " + i + " at y=" + ys.get(i) + " (expected " + (16 + 18 * i) + ")");
                    break;
                }
            }
            int expectRows = Math.min(original.totalSlots, 8);
            if (ys.size() != expectRows) fails.add("original screen shows " + ys.size() + " rows, expected " + expectRows);
        }
        if (menu instanceof AccessoriesExperimentalMenu experimental) {
            var shown = experimental.getVisibleAccessoriesSlots();
            for (Slot slot : shown) {
                if (slot instanceof AccessoriesBasedSlot s && AccessoriesMirrors.isMirrorRow(s)) {
                    fails.add("experimental screen lays out mirror row " + s.slotName() + ":" + s.getContainerSlot());
                }
            }
        }
        return fails;
    }
}
