package dev.mtop.hungryloop.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mtop.hungryloop.Config;
import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Adds a shiny Hungry Loop to a configured set of chest loot tables (see
 * {@code data/hungryloop/loot_modifier/shiny_hungry_loop.json}). Shiny loops are loot-only and start
 * with several slots already unlocked.
 *
 * <p>The target tables and roll chance are codec fields rather than loot conditions - simpler than
 * chaining {@code any_of} over a dozen {@code loot_table_id} conditions in JSON.
 */
public class ShinyLoopModifier extends LootModifier {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HungryLoop.MODID);

    public static final Supplier<MapCodec<ShinyLoopModifier>> CODEC = REGISTER.register("shiny_loop",
            () -> RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst)
                    .and(Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(m -> m.chance))
                    .and(ResourceLocation.CODEC.listOf().fieldOf("tables").forGetter(m -> List.copyOf(m.tables)))
                    .apply(inst, ShinyLoopModifier::new)));

    private final float chance;
    private final Set<ResourceLocation> tables;

    protected ShinyLoopModifier(LootItemCondition[] conditions, float chance, List<ResourceLocation> tables) {
        super(conditions);
        this.chance = chance;
        this.tables = Set.copyOf(tables);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!tables.contains(context.getQueriedLootTableId())) return generatedLoot;
        if (context.getRandom().nextFloat() >= chance) return generatedLoot;

        int base = Config.startSlots(true);

        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(),
                new LoopContents(base, 0, 0, true, 0, List.of(), LoopContents.ALL_RENDER, true));

        generatedLoot.add(loop);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
