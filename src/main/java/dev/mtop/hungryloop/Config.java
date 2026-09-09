package dev.mtop.hungryloop;

import net.neoforged.neoforge.common.ModConfigSpec;

/** COMMON config - values the server decides and syncs to clients. */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SLOTS_PER_STAR = BUILDER
            .comment("Slots added to a loop's cap per Nether Star fed to it.")
            .defineInRange("slotsPerStar", 2, 1, 12);

    public static final ModConfigSpec.IntValue ABSOLUTE_MAX_SLOTS = BUILDER
            .comment("Hard ceiling on a loop's total slots, however many stars are fed.",
                    "The default cap without stars is 12.")
            .defineInRange("absoluteMaxSlots", 20, 12, 54);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
