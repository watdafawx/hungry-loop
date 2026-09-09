package dev.mtop.hungryloop.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Adds a shiny Hungry Loop to the loot it's attached to (see
 * {@code data/hungryloop/loot_modifier/shiny_hungry_loop.json}). Shiny loops are loot-only and start
 * with several slots already unlocked.
 */
public class ShinyLoopModifier extends LootModifier {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HungryLoop.MODID);

    public static final Supplier<MapCodec<ShinyLoopModifier>> CODEC = REGISTER.register("shiny_loop",
            () -> RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst)
                    .and(Codec.intRange(LoopContents.MIN_BASE_SLOTS, LoopContents.MAX_SHINY_BASE_SLOTS)
                            .fieldOf("min_base").forGetter(m -> m.minBase))
                    .and(Codec.intRange(LoopContents.MIN_BASE_SLOTS, LoopContents.MAX_SHINY_BASE_SLOTS)
                            .fieldOf("max_base").forGetter(m -> m.maxBase))
                    .apply(inst, ShinyLoopModifier::new)));

    private final int minBase;
    private final int maxBase;

    protected ShinyLoopModifier(LootItemCondition[] conditions, int minBase, int maxBase) {
        super(conditions);
        this.minBase = minBase;
        this.maxBase = maxBase;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        int base = Mth.nextInt(context.getRandom(), Math.min(minBase, maxBase), Math.max(minBase, maxBase));

        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(),
                new LoopContents(base, 0, true, 0, List.of(), LoopContents.LOOP_MODE));

        generatedLoot.add(loop);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
