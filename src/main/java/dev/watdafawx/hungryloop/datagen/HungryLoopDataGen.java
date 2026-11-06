package dev.watdafawx.hungryloop.datagen;

import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.ModItems;
import dev.watdafawx.hungryloop.loot.ShinyLoopModifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.minecraft.data.tags.ItemTagsProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code build.bat runData} writes these into {@code src/generated/resources} (a resource source
 * dir). Recipe, loot modifier, the {@code curios:loop} tag and the item model come from here; lang
 * and the Curios slot/entity JSON stay hand-written in {@code src/main/resources}.
 */
@EventBusSubscriber(modid = HungryLoop.MODID)
public final class HungryLoopDataGen {

    /** Structure chests the shiny loop can turn up in, and its chance per chest. */
    static final float SHINY_CHANCE = 0.06f;
    static final List<String> SHINY_TABLES = List.of(
            "simple_dungeon", "abandoned_mineshaft", "jungle_temple", "desert_pyramid",
            "stronghold_corridor", "stronghold_crossing", "woodland_mansion", "nether_bridge",
            "bastion_treasure", "bastion_other", "ancient_city", "end_city_treasure",
            "pillager_outpost", "shipwreck_treasure", "buried_treasure");

    private HungryLoopDataGen() {}

    @SubscribeEvent
    static void gather(GatherDataEvent event) {
        var gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        gen.addProvider(event.includeServer(), new Recipes(out, lookup));
        gen.addProvider(event.includeServer(), new LootModifiers(out, lookup));
        var blockTags = gen.addProvider(event.includeServer(), new NoBlockTags(out, lookup, files));
        gen.addProvider(event.includeServer(), new Tags(out, lookup, blockTags.contentsGetter(), files));
        gen.addProvider(event.includeClient(), new ItemModels(out, files));
    }

    static final class Recipes extends RecipeProvider {
        Recipes(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup) {
            super(out, lookup);
        }

        @Override
        protected void buildRecipes(RecipeOutput output) {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.HUNGRY_LOOP.get())
                    .group(HungryLoop.MODID)
                    .pattern("CPC").pattern("PAP").pattern("CPC")
                    .define('C', Items.COPPER_INGOT)
                    .define('P', Items.ENDER_PEARL)
                    .define('A', Items.AMETHYST_SHARD)
                    .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                    .save(output);
        }
    }

    static final class LootModifiers extends GlobalLootModifierProvider {
        LootModifiers(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup) {
            super(out, lookup, HungryLoop.MODID);
        }

        @Override
        protected void start() {
            add("shiny_hungry_loop", ShinyLoopModifier.of(SHINY_CHANCE,
                    SHINY_TABLES.stream().map(t -> ResourceLocation.withDefaultNamespace("chests/" + t)).toList()));
        }
    }

    /** ItemTagsProvider needs a block-tag source; we have no blocks. */
    static final class NoBlockTags extends BlockTagsProvider {
        NoBlockTags(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(out, lookup, HungryLoop.MODID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {}
    }

    static final class Tags extends ItemTagsProvider {
        static final TagKey<Item> CURIOS_LOOP = ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", "loop"));

        Tags(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup,
             CompletableFuture<TagLookup<Block>> blocks, ExistingFileHelper files) {
            super(out, lookup, blocks, HungryLoop.MODID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(CURIOS_LOOP).add(ModItems.HUNGRY_LOOP.get());
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput out, ExistingFileHelper files) {
            super(out, HungryLoop.MODID, files);
        }

        @Override
        protected void registerModels() {
            basicItem(ModItems.HUNGRY_LOOP.get());
        }
    }
}
