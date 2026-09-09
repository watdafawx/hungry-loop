package dev.mtop.hungryloop;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SERVER config - lives in {@code <world>/serverconfig/hungryloop-server.toml}, syncs to clients,
 * and reloads live when the file changes or on {@code /reload}. Slot maths reads these every time,
 * so bumping a value takes effect on existing loops immediately.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue NORMAL_MAX_SLOTS = BUILDER
            .comment("Slot cap for a crafted (normal) loop, before Nether Stars.")
            .defineInRange("normalMaxSlots", 12, 4, 54);

    public static final ModConfigSpec.IntValue SHINY_MAX_SLOTS = BUILDER
            .comment("Slot cap for a shiny (loot-found) loop, before Nether Stars.")
            .defineInRange("shinyMaxSlots", 20, 4, 54);

    public static final ModConfigSpec.IntValue SLOTS_PER_STAR = BUILDER
            .comment("Slots added to a loop's cap per Nether Star fed to it.")
            .defineInRange("slotsPerStar", 2, 1, 12);

    public static final ModConfigSpec.IntValue ABSOLUTE_MAX_SLOTS = BUILDER
            .comment("Hard ceiling on a loop's total slots, however many stars are fed.")
            .defineInRange("absoluteMaxSlots", 24, 4, 54);

    public static final ModConfigSpec.IntValue LOOP_SLOTS = BUILDER
            .comment("How many dedicated Curios slots the player gets for wearing Hungry Loops.")
            .defineInRange("loopSlots", 2, 0, 12);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /** Effective cap for a loop, or the built-in fallback if the config isn't loaded yet. */
    public static int slotCap(boolean shiny, int capBonus) {
        try {
            int typeMax = shiny ? SHINY_MAX_SLOTS.get() : NORMAL_MAX_SLOTS.get();
            return Math.min(typeMax + capBonus, ABSOLUTE_MAX_SLOTS.get());
        } catch (IllegalStateException notLoadedYet) {
            return (shiny ? 20 : 12) + capBonus;
        }
    }

    public static int slotsPerStar() {
        try {
            return SLOTS_PER_STAR.get();
        } catch (IllegalStateException notLoadedYet) {
            return 2;
        }
    }

    public static int absoluteMaxSlots() {
        try {
            return ABSOLUTE_MAX_SLOTS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 24;
        }
    }

    public static int loopSlots() {
        try {
            return LOOP_SLOTS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 2;
        }
    }
}
