package dev.watdafawx.hungryloop.client;

import dev.watdafawx.hungryloop.Config;
import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.component.LoopContents;
import dev.watdafawx.hungryloop.component.ModDataComponents;
import dev.watdafawx.hungryloop.curio.ContentForwarding;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.integration.apotheosis.ApothLoot;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import dev.watdafawx.hungryloop.ModItems;
import dev.watdafawx.hungryloop.menu.HungryLoopMenu;
import dev.watdafawx.hungryloop.net.OpenLoopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.ModList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.client.CPacketPage;
import dev.watdafawx.hungryloop.integration.accessories.AccessoriesTestHooks;
import dev.watdafawx.hungryloop.integration.artifacts.ArtifactsTestHooks;
import dev.watdafawx.hungryloop.integration.relics.RelicsTestHooks;
import dev.watdafawx.hungryloop.integration.relics.RelicsUnlock;
import top.theillusivec4.curios.common.network.client.CPacketToggleRender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Dev-only, singleplayer-only regression suite for the loop/Curios sync bugs.
 *
 * <p>In singleplayer the integrated server lives in the same JVM, so after every step this snapshots
 * the client <em>and</em> the server and diffs them slot by slot. A ghost item is exactly a client
 * slot that disagrees with the server and stays that way, so it is detected directly. Screenshots are
 * still taken per step ({@code run/screenshots/hungryloop-selftest-*.png}) for what only eyes can
 * judge.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@code build.bat runSelftest} - unattended full suite. Deletes and recreates a flat scratch
 *   world ({@value #WORLD}), provisions loops with chosen contents per scenario, writes
 *   {@code run/hungryloop-selftest.txt}, quits. Never touches a real save.</li>
 *   <li>{@code /hlselftest} in any world with a loop worn and a free second loop slot - the
 *   non-destructive move scenario only (moves the loop around and back).</li>
 * </ul>
 * Never registered in production.
 */
@EventBusSubscriber(modid = HungryLoop.MODID, value = Dist.CLIENT)
public final class SelfTest {

    private static final String WORLD = "hungryloop-selftest";
    private static final boolean AUTO = !System.getProperty("hungryloop.selftest", "").isEmpty();
    private static final String LOOP = "loop";
    private static final String GRANT_TAG = "hl_slot_";
    private static final int SETTLE = 40;
    private static final int RECHECK = 20;

    private static final Deque<BooleanSupplier> STEPS = new ArrayDeque<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static boolean running;
    private static boolean worldRequested;
    private static boolean autoStarted;
    private static int failures;
    private static int shot;

    // expectations for the generic check, set per scenario
    private static int baseLoops;
    private static int baseMirrors;
    private static int baseVisible;
    private static int expectLoopAt;

    private SelfTest() {}

    // --- entry points ----------------------------------------------------------------------------

    @SubscribeEvent
    static void onRegisterCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("hlselftest").executes(ctx -> {
            startManual();
            return 1;
        }));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (FMLEnvironment.production) return;
        Minecraft mc = Minecraft.getInstance();

        if (AUTO) {
            mc.options.pauseOnLostFocus = false; // an unattended run has no focused window
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                createScratchWorld(mc);
            }
            if (!autoStarted && mc.player != null && mc.getSingleplayerServer() != null) {
                autoStarted = true;
                startSuite();
            }
        }

        if (running && mc.player == null) {
            fail("session", "left the world mid-run (disconnect or kick) - see latest.log");
            running = false;
            writeReport();
            if (AUTO) mc.stop();
            return;
        }

        while (running && !STEPS.isEmpty()) {
            // Take the step off before running it: steps queue follow-ups at the front, so removing
            // "the first element" afterwards would drop the follow-up instead of the step itself.
            BooleanSupplier step = STEPS.pollFirst();
            if (!step.getAsBoolean()) {
                STEPS.addFirst(step); // still waiting
                return;
            }
        }
    }

    private static void createScratchWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                HungryLoop.LOG.error("hungryloop-selftest: couldn't delete old scratch world", e);
            }
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    private static void startManual() {
        if (running) return;
        reset("manual (non-destructive moves only)");
        openCurios();
        settle();
        STEPS.add(() -> {
            Slot loop = findLoopSlot();
            if (loop == null || otherLoopSlot() < 0) {
                fail("precondition", "wear a loop in a loop slot and keep a second loop slot free");
                finish();
                return true;
            }
            capture(loop.getSlotIndex(), -1);
            queueFront(movesScenario(loop.getSlotIndex(), false));
            return true;
        });
    }

    private static void startSuite() {
        reset("suite in scratch world " + WORLD);
        waitTicks(STEPS, 60);
        STEPS.add(() -> {
            onServer(SelfTest::logMultiTypeItems);
            List<Item> items = onServer(p -> pickItems(p, 6));
            if (items.size() < 6) {
                fail("precondition", "need 6 curio items with a real slot type, found " + items.size()
                        + " (are the optional curio mods missing?)");
                finish();
                return true;
            }
            List<BooleanSupplier> suite = new ArrayList<>();
            if (ACCESSORIES) {
                // Accessories' compat layer replaces the Curios screen, which every Curios scenario
                // drives - run the Accessories screen scenarios instead.
                for (boolean experimental : new boolean[]{false, true}) {
                    suite.addAll(accessoriesOpenScenario(items, experimental));
                    suite.addAll(accessoriesEquipWhileOpenScenario(items, experimental));
                    suite.addAll(accessoriesClickScenario(items, experimental));
                }
                suite.addAll(abilityScenarios());
                if (wants("overflow")) suite.addAll(overflowScenario(items));
                if (wants("overlay")) {
                    suite.addAll(overlayScenario("overlay-inventory", SelfTest::openInventoryInto, s -> s instanceof InventoryScreen));
                    suite.addAll(overlayScenario("overlay-accessories", seq -> openAccessoriesInto(seq, false),
                            s -> AccessoriesTestHooks.isScreen(s, false)));
                }
                // Screen-free: item worn directly vs in the loop, every attribute compared. Under the
                // compat layer slot names are Accessories' (head->hat, feet->shoes, curio->any, ...)
                // and wildcards no longer list "curio" - this is what proves affixes still fire.
                if (wants("parity")) {
                    List<Item> parityItems = new ArrayList<>(items);
                    parityItems.addAll(onServer(SelfTest::multiTypeItems));
                    for (Item item : parityItems) suite.addAll(parityScenario(item));
                }
                suite.add(() -> {
                    finish();
                    return true;
                });
                queueFront(suite);
                return true;
            }
            if (wants("order")) suite.addAll(loopSlotOrderScenario(items));
            if (wants("idle")) {
                suite.addAll(idleChurnScenario(items, true));
                suite.addAll(idleChurnScenario(items, false));
            }
            if (wants("open")) suite.addAll(equipWhileOpenScenario(items));
            if (wants("reopen")) {
                suite.addAll(reopenScenario("reopen-same", items.get(0), true));
                suite.addAll(reopenScenario("reopen-mixed", null, false));
            }
            if (wants("moves")) suite.addAll(provisionedMoves(items));
            suite.addAll(abilityScenarios());
            if (wants("scales")) suite.addAll(scalesScenario(items));
            if (wants("arms")) suite.addAll(armsScenario());
            if (wants("peers")) {
                suite.addAll(peersScenario("artifacts"));
                suite.addAll(peersScenario("relics"));
            }
            if (wants("body")) {
                suite.addAll(bodyScenario("body-copper", items, false));
                suite.addAll(bodyScenario("body-shiny", items, true));
            }
            if (wants("look")) {
                suite.addAll(lookScenario("look-copper", items, false));
                suite.addAll(lookScenario("look-shiny", items, true));
            }
            if (wants("overlay")) {
                suite.addAll(overlayScenario("overlay-inventory", SelfTest::openInventoryInto, s -> s instanceof InventoryScreen));
                suite.addAll(overlayScenario("overlay-curios", SelfTest::openCuriosInto, s -> s instanceof CuriosScreen));
            }
            if (wants("nested")) suite.addAll(nestedLoopScenario(items));
            if (wants("twoloops")) suite.addAll(twoLoopsScenario(items));
            if (wants("overflow")) suite.addAll(overflowScenario(items));
            if (wants("parity")) {
                List<Item> parityItems = new ArrayList<>(items);
                parityItems.addAll(onServer(SelfTest::multiTypeItems));
                for (Item item : parityItems) suite.addAll(parityScenario(item));
            }
            List<Item> wildcards = onServer(SelfTest::multiTypeItems);
            if (wants("swap") && !wildcards.isEmpty()) suite.addAll(rightClickSwapScenario(wildcards.get(0), items.get(1), items.get(2)));
            suite.add(() -> {
                finish();
                return true;
            });
            queueFront(suite);
            return true;
        });
    }

    /**
     * Scenario filter: {@code -Dhungryloop.selftest=auto} (or {@code all}) runs everything; otherwise
     * a comma list of group names runs just those - order, idle, open, moves, nested, twoloops,
     * overflow, parity, swap. From gradle: {@code runSelftest -Pscenarios=idle,moves}.
     */
    private static boolean wants(String group) {
        String value = System.getProperty("hungryloop.selftest", "auto").trim();
        if (value.equals("auto") || value.equals("all")) return true;
        for (String token : value.split(",")) if (token.trim().equals(group)) return true;
        return false;
    }

    private static void reset(String mode) {
        running = true;
        failures = 0;
        shot = 0;
        REPORT.clear();
        STEPS.clear();
        log("start: " + mode);
    }

    // --- scenarios -------------------------------------------------------------------------------

    private static List<BooleanSupplier> provisionedMoves(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("moves", List.of(loopOf(4, items.get(0), items.get(1), items.get(2)))));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            capture(0, 3);
            queueFront(movesScenario(0, true));
            return true;
        });
        return seq;
    }

    /** Moves the loop back and forth between two loop slots: same-tick and carried, then home. */
    private static List<BooleanSupplier> movesScenario(int home, boolean provisioned) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(checkStep("moves-baseline"));
        for (int i = 0; i < 3; i++) {
            seq.add(moveStep("move-same-tick-" + i, 0));
            seq.add(moveStep("move-carry-" + i, 10));
        }
        seq.add(() -> {
            if (expectLoopAt != home) queueFront(moveTo("move-return-home", 10, home));
            return true;
        });
        if (!provisioned) {
            seq.add(() -> {
                finish();
                return true;
            });
        }
        return seq;
    }

    /**
     * The Curios screen is already open when the loop is equipped, and again when it comes off: the
     * granted slots and mirrors must appear and disappear without the screen being reopened.
     */
    private static List<BooleanSupplier> equipWhileOpenScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("equip-while-open", List.of(), true));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            capture(-1, 0);
            queueFront(List.of(checkStep("open-no-loop")));
            return true;
        });
        seq.add(provision("equip-while-open (equip)", List.of(loopOf(4, items.get(0), items.get(1), items.get(2))), false));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            baseLoops = 1;
            baseMirrors = 3; // visible stays the no-loop baseline: mirrors must be hidden in place
            expectLoopAt = 0;
            queueFront(List.of(checkStep("equipped-while-open")));
            return true;
        });
        seq.add(provision("equip-while-open (unequip)", List.of(), false));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            baseLoops = 0;
            baseMirrors = 0;
            expectLoopAt = -1;
            queueFront(List.of(checkStep("unequipped-while-open")));
            return true;
        });
        return seq;
    }

    /**
     * The player's own path, as reported: with the Curios screen open, click a loop from the
     * inventory into the loop slot, close, and reopen. Every page of the reopened panel must be a
     * hole-free grid of usable slots - no dead cells where the granted (mirror) slots sit.
     * {@code same} fills the loop with four of one item (several grants in one type), otherwise four
     * different types. Run it with a small {@code maxSlotsPerPage} too: multi-page panels are where
     * packs with many slot types hit the ghosts.
     */
    private static List<BooleanSupplier> reopenScenario(String name, Item same, boolean useSame) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision(name, List.of()));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            onServer(p -> {
                List<Item> picked = pickItems(p, 4);
                p.getInventory().setItem(9, useSame ? loopOf(8, same, same, same, same) : loopOf(8, picked.toArray(Item[]::new)));
                return null;
            });
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            click(playerSlotHolding(ModItems.HUNGRY_LOOP.get()));
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            click(curioSlot(LOOP, 0));
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            Minecraft.getInstance().player.closeContainer();
            return true;
        });
        waitTicks(seq, SETTLE);
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            int mirrors = onServer(p -> Snap.of(p).mirrors);
            if (mirrors != 4) fails.add("expected 4 mirrors after equip, server has " + mirrors + " (test didn't equip the loop)");
            fails.addAll(ghostDiff());
            fails.addAll(onServer(p -> Snap.of(p).invariantFails));
            log("    client curios (* = mirror): " + Snap.of(Minecraft.getInstance().player).curios);
            int pages = Minecraft.getInstance().player.containerMenu instanceof CuriosContainer cc ? cc.totalPages : 1;
            log("    panel pages: " + pages);
            List<BooleanSupplier> perPage = new ArrayList<>();
            for (int page = 0; page < pages; page++) {
                int pg = page;
                if (page > 0) {
                    perPage.add(() -> {
                        PacketDistributor.sendToServer(new CPacketPage(Minecraft.getInstance().player.containerMenu.containerId, true));
                        return true;
                    });
                    perPage.add(waitUntil(() -> Minecraft.getInstance().player.containerMenu instanceof CuriosContainer cc
                            && cc.currentPage == pg, 60, "page " + pg + " never shown"));
                    waitTicks(perPage, 10);
                }
                perPage.add(() -> {
                    List<String> pf = new ArrayList<>(deadDrawnSlots());
                    pf.addAll(gridHoles());
                    pf.forEach(f -> fails.add("page " + pg + ": " + f));
                    screenshot(name + "-p" + pg);
                    return true;
                });
            }
            perPage.add(() -> {
                record(name, fails);
                return true;
            });
            queueFront(perPage);
            return true;
        });
        return seq;
    }

    // --- scales: the loop screen must fit and stay inside its panel at every GUI scale ----------

    /**
     * Opens a 20-slot shiny loop (the widest ring) at each GUI scale for two window sizes - the
     * minimum Minecraft allows (854x480) and 1080p - and requires the panel inside the screen, and
     * every ring slot, eye, the feed button and the inventory inside the panel. Screenshots each one,
     * since "readable" is for eyes.
     */
    private static List<BooleanSupplier> scalesScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(7, 0, 0, true, 0,
                items.subList(0, 5).stream().map(ItemStack::new).toList(), LoopContents.ALL_RENDER, true));
        seq.add(provision("scales", List.of(loop)));
        waitTicks(seq, SETTLE);
        int[][] windows = {{854, 480}, {1920, 1080}};
        for (int[] win : windows) {
            for (int scale = 1; scale <= 4; scale++) {
                int sc = scale;
                String name = "scale-" + win[0] + "x" + win[1] + "-gui" + scale;
                seq.add(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), win[0], win[1]);
                    mc.options.guiScale().set(sc);
                    mc.resizeDisplay();
                    PacketDistributor.sendToServer(new OpenLoopPayload());
                    return true;
                });
                seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof HungryLoopScreen, 100, "loop screen never opened"));
                waitTicks(seq, 10);
                seq.add(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.resizeDisplay();
                    List<String> fails = new ArrayList<>();
                    if (mc.screen instanceof HungryLoopScreen screen) {
                        int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
                        int effective = (int) mc.getWindow().getGuiScale();
                        log("    " + name + ": window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                                + ", effective gui scale " + effective + ", gui " + gw + "x" + gh);
                        fails.addAll(screen.layoutProblems(gw, gh));
                    } else {
                        fails.add("loop screen not open");
                    }
                    record(name, fails);
                    return true;
                });
                waitTicks(seq, 4); // let the resized frame render before grabbing it
                seq.add(() -> {
                    screenshot(name);
                    return true;
                });
                waitTicks(seq, 4);
                seq.add(() -> {
                    Minecraft.getInstance().player.closeContainer();
                    return true;
                });
                waitTicks(seq, 5);
            }
        }
        seq.add(() -> {
            Minecraft mc = Minecraft.getInstance();
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 854, 480);
            mc.options.guiScale().set(0);
            mc.resizeDisplay();
            return true;
        });
        return seq;
    }

    // --- body: the worn loop on the player, and the animated sprites in the hotbar --------------

    private static List<BooleanSupplier> bodyScenario(String name, List<Item> items, boolean shiny) {
        List<BooleanSupplier> seq = new ArrayList<>();
        ItemStack worn = new ItemStack(ModItems.HUNGRY_LOOP.get());
        worn.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(4, 0, 0, shiny, 0,
                List.of(), LoopContents.ALL_RENDER, true));
        seq.add(provision(name, List.of(worn, worn.copy()))); // two loops: one coils each arm
        seq.add(() -> {
            onServer(p -> {
                for (int i = 0; i < 3; i++) p.getInventory().setItem(i, worn.copy()); // hotbar sprites
                p.getInventory().selected = 5; // empty hand, so the held item doesn't hide the ring
                p.setYRot(180);
                p.setXRot(0);
                return null;
            });
            Minecraft mc = Minecraft.getInstance();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.getInventory().selected = 5;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(5));
            mc.player.setYRot(180);
            mc.player.setXRot(0);
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            screenshot(name + "-a");
            return true;
        });
        waitTicks(seq, 7);
        seq.add(() -> {
            screenshot(name + "-b");
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            screenshot(name + "-firstperson");
            record(name, List.of());
            return true;
        });
        return seq;
    }

    // --- peers: how other mods' worn gear looks, for reference -------------------------------------

    /** Arm-worn items first (hands, bracelet, ring), then a few body ones, from one mod's namespace. */
    private static List<Item> peerItems(ServerPlayer p, String namespace) {
        List<String> order = List.of("hands", "bracelet", "ring", "necklace", "back", "belt", "head");
        Map<String, Item> byType = new LinkedHashMap<>();
        BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(namespace)).sorted().forEach(id -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (CuriosApi.getCurio(stack).isEmpty()) return;
            byType.putIfAbsent(ContentForwarding.resolveSlot(stack, p), item);
        });
        List<Item> out = new ArrayList<>();
        for (String type : order) if (byType.containsKey(type)) out.add(byType.get(type));
        return out;
    }

    private static List<BooleanSupplier> peersScenario(String namespace) {
        List<BooleanSupplier> seq = new ArrayList<>();
        if (!ModList.get().isLoaded(namespace)) return seq;
        seq.add(provision("peers-" + namespace, List.of()));
        seq.add(() -> {
            onServer(p -> {
                List<Item> items = peerItems(p, namespace);
                log("    wearing: " + items.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).getPath()).toList());
                wearDirectly(p, items);
                p.setYRot(180);
                return null;
            });
            Minecraft mc = Minecraft.getInstance();
            mc.player.getInventory().selected = 5;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(5));
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setYRot(180);
            mc.player.setXRot(0);
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            screenshot("peers-" + namespace + "-front");
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            screenshot("peers-" + namespace + "-back");
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            Minecraft.getInstance().player.setXRot(10);
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            screenshot("peers-" + namespace + "-firstperson");
            record("peers-" + namespace, List.of());
            return true;
        });
        return seq;
    }

    // --- arms: the first-person coil through arm animations (Punchy's, when installed) -----------

    /**
     * Two loops worn, empty hand, first person. Captures the arm idle, mid-punch (a few frames into
     * a swing), sprinting, and walking, so the coil can be checked against whatever pose the arm
     * renderer - vanilla or Punchy - draws the arm in.
     */
    private static List<BooleanSupplier> armsScenario() {
        List<BooleanSupplier> seq = new ArrayList<>();
        ItemStack worn = new ItemStack(ModItems.HUNGRY_LOOP.get());
        worn.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(4, 0, 0, false, 0,
                List.of(), LoopContents.ALL_RENDER, true));
        seq.add(provision("arms", List.of(worn, worn.copy())));
        seq.add(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.getInventory().selected = 5;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(5));
            mc.player.setXRot(10);
            log("    punchy loaded: " + ModList.get().isLoaded("punchy"));
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            screenshot("arms-idle");
            Minecraft.getInstance().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return true;
        });
        for (int f = 1; f <= 4; f++) {
            int frame = f;
            waitTicks(seq, 1);
            seq.add(() -> {
                screenshot("arms-punch-" + frame);
                return true;
            });
        }
        waitTicks(seq, 20);
        seq.add(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.keySprint.setDown(true);
            mc.options.keyUp.setDown(true);
            return true;
        });
        waitTicks(seq, 15);
        seq.add(() -> {
            screenshot("arms-sprint-a");
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            screenshot("arms-sprint-b");
            Minecraft mc = Minecraft.getInstance();
            mc.options.keySprint.setDown(false);
            mc.options.keyUp.setDown(false);
            record("arms", List.of());
            return true;
        });
        waitTicks(seq, 10);
        return seq;
    }

    // --- look: screenshots of the loop screen for eyeballing the frame ----------------------------

    private static List<BooleanSupplier> lookScenario(String name, List<Item> items, boolean shiny) {
        List<BooleanSupplier> seq = new ArrayList<>();
        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        List<ItemStack> contents = items.subList(0, 5).stream().map(ItemStack::new).toList();
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(shiny ? 7 : 5, 0, 0, shiny, 0,
                contents, LoopContents.ALL_RENDER, true));
        seq.add(provision(name, List.of(loop)));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            PacketDistributor.sendToServer(new OpenLoopPayload());
            return true;
        });
        seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof HungryLoopScreen, 100, "loop screen never opened"));
        waitTicks(seq, 20);
        seq.add(() -> {
            screenshot(name + "-a");
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            screenshot(name + "-b");
            record(name, List.of());
            Minecraft.getInstance().player.closeContainer();
            return true;
        });
        return seq;
    }

    // --- overlay ----------------------------------------------------------------------------------

    private static void openInventoryInto(List<BooleanSupplier> seq) {
        seq.add(() -> {
            Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            return true;
        });
        seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof InventoryScreen, 40, "inventory never opened"));
    }

    /**
     * Opening the loop from an inventory-type screen keeps that screen as the backdrop, and closing
     * the loop returns to it (instead of dropping to the game).
     */
    private static List<BooleanSupplier> overlayScenario(String name, java.util.function.Consumer<List<BooleanSupplier>> openParent,
                                                         java.util.function.Predicate<net.minecraft.client.gui.screens.Screen> isParent) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision(name, List.of(loopOf(4, new Item[0]))));
        waitTicks(seq, SETTLE);
        openParent.accept(seq);
        waitTicks(seq, 10);
        seq.add(() -> {
            PacketDistributor.sendToServer(new OpenLoopPayload());
            return true;
        });
        seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof HungryLoopScreen, 100, "loop screen never opened"));
        waitTicks(seq, 10);
        List<String> fails = new ArrayList<>();
        seq.add(() -> {
            if (!LoopOverlay.hasParent()) fails.add("loop screen opened from " + name + " has no backdrop parent");
            screenshot(name);
            Minecraft.getInstance().screen.onClose();
            return true;
        });
        seq.add(waitUntil(() -> isParent.test(Minecraft.getInstance().screen), 100, name + ": closing the loop didn't return to the parent screen"));
        waitTicks(seq, 10);
        seq.add(() -> {
            if (!isParent.test(Minecraft.getInstance().screen)) fails.add("closing the loop left screen " + Minecraft.getInstance().screen);
            fails.addAll(ghostDiff());
            record(name, fails);
            Minecraft.getInstance().player.closeContainer();
            return true;
        });
        return seq;
    }

    // --- abilities & write-back (screen-free, both paths) ------------------------------------------

    private static List<BooleanSupplier> abilityScenarios() {
        List<BooleanSupplier> seq = new ArrayList<>();
        if (wants("abilities")) {
            if (ModList.get().isLoaded("relics")) {
                for (Item item : RelicsTestHooks.relicItems(3)) {
                    seq.addAll(lookupScenario("relics", item, p -> RelicsTestHooks.relicsSeesEquipped(p, item)));
                }
            }
            if (ModList.get().isLoaded("artifacts")) {
                for (Item item : ArtifactsTestHooks.artifactItems(3)) {
                    seq.addAll(lookupScenario("artifacts", item, p -> ArtifactsTestHooks.artifactsSeesEquipped(p, item)));
                }
            }
        }
        if (wants("writeback") && ModList.get().isLoaded("relics")) {
            List<Item> relics = RelicsTestHooks.relicItems(1);
            if (!relics.isEmpty()) seq.addAll(writeBackScenario(relics.get(0)));
        }
        return seq;
    }

    /**
     * The mod's own "is this equipped" lookup - the one its abilities run through - must find the
     * item in the loop. Worn directly first as the control: if the lookup misses that, the oracle is
     * broken, not the loop.
     */
    private static List<BooleanSupplier> lookupScenario(String mod, Item item, Function<ServerPlayer, Boolean> sees) {
        String name = "ability-" + mod + "-" + BuiltInRegistries.ITEM.getKey(item).getPath();
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision(name + " (direct)", List.of()));
        seq.add(() -> {
            onServer(p -> {
                wearDirectly(p, List.of(item));
                return null;
            });
            return true;
        });
        waitTicks(seq, SETTLE);
        boolean[] control = {false};
        seq.add(() -> {
            control[0] = onServer(sees);
            return true;
        });
        seq.add(provision(name + " (in loop)", List.of(loopOf(4, item))));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            if (!control[0]) {
                // Not wearable here at all (e.g. a hand-held relic Accessories' compat layer gives no
                // slot) - nothing for the loop to match, so not a loop failure.
                log("SKIP " + name + " (" + mod + " doesn't see it even worn directly)");
                return true;
            }
            List<String> fails = new ArrayList<>();
            if (!onServer(sees)) fails.add(mod + " doesn't see " + item + " in the loop - its abilities won't run");
            record(name, fails);
            return true;
        });
        return seq;
    }

    /**
     * Progress made on a worn item lives on its mirror (that is what Relics ticks and levels). It must
     * be copied back into the loop's stored item, or it is gone the moment the loop comes off.
     * Unlocks the mirror relic, waits past the write-back interval, takes the loop off, and requires
     * the stored relic to be unlocked.
     */
    private static List<BooleanSupplier> writeBackScenario(Item relic) {
        String name = "writeback-relic";
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision(name, List.of(loopOf(4, relic))));
        waitTicks(seq, SETTLE);
        List<String> fails = new ArrayList<>();
        seq.add(() -> {
            onServer(p -> {
                ItemStack loop = CuriosApi.getCuriosInventory(p).flatMap(h -> h.findFirstCurio(
                        s -> s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s))).map(SlotResult::stack).orElse(ItemStack.EMPTY);
                ItemStack stored = storedFirst(loop);
                if (stored.isEmpty() || RelicsTestHooks.isUnlocked(p, stored)) {
                    fails.add("precondition: stored relic missing or already unlocked");
                }
                ItemStack mirror = CuriosApi.getCuriosInventory(p).flatMap(h -> h.findFirstCurio(
                        s -> LoopSlotMirror.isMirror(s) && s.is(relic))).map(SlotResult::stack).orElse(ItemStack.EMPTY);
                if (mirror.isEmpty()) fails.add("no mirror of " + relic + " to progress");
                else RelicsUnlock.unlockAll(p, mirror);
                return null;
            });
            return true;
        });
        waitTicks(seq, 70); // past LoopSlotMirror's 40-tick write-back interval
        seq.add(() -> {
            onServer(p -> {
                CuriosApi.getCuriosInventory(p).ifPresent(h -> {
                    ItemStack loop = h.getStacksHandler(LOOP).map(sh -> sh.getStacks().getStackInSlot(0).copy()).orElse(ItemStack.EMPTY);
                    h.setEquippedCurio(LOOP, 0, ItemStack.EMPTY);
                    p.getInventory().setItem(9, loop);
                });
                return null;
            });
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            onServer(p -> {
                ItemStack stored = storedFirst(p.getInventory().getItem(9));
                if (stored.isEmpty()) fails.add("loop or its relic missing after taking it off");
                else if (!RelicsTestHooks.isUnlocked(p, stored)) fails.add("relic progress made while worn was lost on unequip (stored copy still locked)");
                if (Snap.of(p).mirrors != 0) fails.add("mirrors left after unequip: " + Snap.of(p).mirrors);
                return null;
            });
            record(name, fails);
            return true;
        });
        return seq;
    }

    private static ItemStack storedFirst(ItemStack loop) {
        var items = loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT).items();
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    // --- Accessories (with its Curios compat layer) ----------------------------------------------

    private static final boolean ACCESSORIES = dev.watdafawx.hungryloop.integration.accessories.AccessoriesSupport.ENABLED;

    private static String variant(boolean experimental) {
        return experimental ? "experimental" : "original";
    }

    private static void openAccessoriesInto(List<BooleanSupplier> seq, boolean experimental) {
        seq.add(() -> {
            onServer(p -> {
                AccessoriesTestHooks.open(p, experimental);
                return null;
            });
            return true;
        });
        seq.add(waitUntil(() -> AccessoriesTestHooks.isScreen(Minecraft.getInstance().screen, experimental), 100,
                "Accessories " + variant(experimental) + " screen never opened"));
    }

    /** Client vs server, server invariants, and the screen's own ghost-slot checks. */
    private static List<String> accessoriesCheck(int expectMirrors) {
        List<String> fails = new ArrayList<>(ghostDiff());
        Snap srv = onServer(Snap::of);
        fails.addAll(srv.invariantFails);
        fails.addAll(loopSlotCountFails(srv));
        if (srv.mirrors != expectMirrors) fails.add("mirror count " + srv.mirrors + " (expected " + expectMirrors + ")");
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        fails.addAll(AccessoriesTestHooks.layoutFails(menu));
        int rows = AccessoriesTestHooks.mirrorRows(menu);
        if (expectMirrors > 0 && rows == 0) fails.add("menu has no mirror rows - nothing was hidden, check exercised nothing");
        log("    client curios (* = mirror): " + Snap.of(Minecraft.getInstance().player).curios);
        return fails;
    }

    private static BooleanSupplier accessoriesCheckStep(String name, int expectMirrors) {
        return () -> {
            if (accessoriesCheck(expectMirrors).isEmpty()) {
                record(name, List.of());
                screenshot(name);
                return true;
            }
            List<BooleanSupplier> seq = new ArrayList<>();
            waitTicks(seq, RECHECK);
            seq.add(() -> {
                record(name, accessoriesCheck(expectMirrors));
                screenshot(name);
                return true;
            });
            queueFront(seq);
            return true;
        };
    }

    /** Loop already worn, then the Accessories screen is opened: mirrors hidden, no gaps, no desync. */
    private static List<BooleanSupplier> accessoriesOpenScenario(List<Item> items, boolean experimental) {
        List<BooleanSupplier> seq = new ArrayList<>();
        String name = "acc-open-" + variant(experimental);
        seq.add(provision(name, List.of(loopOf(8, items.get(0), items.get(1), items.get(2), items.get(0)))));
        waitTicks(seq, SETTLE);
        openAccessoriesInto(seq, experimental);
        waitTicks(seq, SETTLE);
        seq.add(accessoriesCheckStep(name, 4));
        return seq;
    }

    /**
     * The player's path through the Accessories screen with real clicks: pick the loop up from the
     * inventory, drop it into an empty loop slot, then take it back out into the inventory. Checks
     * conservation (exactly one loop, nowhere duplicated) on top of the usual layout/desync checks.
     */
    private static List<BooleanSupplier> accessoriesClickScenario(List<Item> items, boolean experimental) {
        List<BooleanSupplier> seq = new ArrayList<>();
        String name = "acc-click-" + variant(experimental);
        seq.add(provision(name, List.of()));
        seq.add(() -> {
            onServer(p -> {
                p.getInventory().setItem(9, loopOf(8, items.get(0), items.get(3), items.get(4), items.get(0)));
                return null;
            });
            return true;
        });
        waitTicks(seq, 10);
        openAccessoriesInto(seq, experimental);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            click(playerSlotHolding(ModItems.HUNGRY_LOOP.get()));
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            click(AccessoriesTestHooks.loopSlot(Minecraft.getInstance().player.containerMenu, true));
            return true;
        });
        waitTicks(seq, SETTLE * 2);
        seq.add(() -> {
            List<String> fails = accessoriesCheck(4);
            int loops = onServer(p -> Snap.of(p).loops);
            if (loops != 1) fails.add("loop count " + loops + " after equip click (expected 1)");
            record(name + "-equip", fails);
            screenshot(name + "-equip");
            return true;
        });
        seq.add(() -> {
            click(AccessoriesTestHooks.loopSlot(Minecraft.getInstance().player.containerMenu, false));
            return true;
        });
        // Picking the loop up shrinks the granted containers, and Accessories reopens its menu on
        // any resize - wait for that (a player's next click is never 5 ticks later), cursor kept.
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            String carried = onServer(p -> key(p.containerMenu.getCarried()));
            log("    after pickup: server cursor holds " + carried);
            if (!carried.startsWith(HungryLoop.MODID)) fail(name + "-pickup", "loop not on the cursor after the menu reopened (cursor: " + carried + ")");
            click(firstEmptyMainInventorySlot());
            return true;
        });
        waitTicks(seq, SETTLE * 2);
        seq.add(() -> {
            List<String> fails = accessoriesCheck(0);
            int loops = onServer(p -> Snap.of(p).loops);
            if (loops != 1) fails.add("loop count " + loops + " after unequip click (expected 1)");
            if (playerSlotHolding(ModItems.HUNGRY_LOOP.get()) == null) fails.add("loop didn't land back in the inventory");
            record(name + "-unequip", fails);
            screenshot(name + "-unequip");
            return true;
        });
        return seq;
    }

    /** The screen is open while the loop goes on and comes off - the reported ghost path. */
    private static List<BooleanSupplier> accessoriesEquipWhileOpenScenario(List<Item> items, boolean experimental) {
        List<BooleanSupplier> seq = new ArrayList<>();
        String name = "acc-equip-while-open-" + variant(experimental);
        seq.add(provision(name, List.of()));
        openAccessoriesInto(seq, experimental);
        waitTicks(seq, SETTLE);
        seq.add(provision(name + " (equip)", List.of(loopOf(8, items.get(3), items.get(4), items.get(5))), false));
        waitTicks(seq, SETTLE * 2);
        seq.add(accessoriesCheckStep(name, 3));
        seq.add(provision(name + " (unequip)", List.of(), false));
        waitTicks(seq, SETTLE * 2);
        seq.add(accessoriesCheckStep("acc-unequip-while-open-" + variant(experimental), 0));
        return seq;
    }

    /**
     * The loop's own slot type must have exactly the configured number of slots - independent of
     * the type's base size (Accessories' compat layer turns our size-0 type into size 1).
     */
    private static List<String> loopSlotCountFails(Snap srv) {
        int have = srv.curios.getOrDefault(LOOP, List.of()).size();
        return have == Config.loopSlots() ? List.of()
                : List.of("loop slot type has " + have + " slots, config says " + Config.loopSlots());
    }

    /**
     * A curio slot that isn't parked but can't be used: on screen as an empty cell, never
     * interactive. This is the "ghost slot" players see.
     */
    private static List<String> deadDrawnSlots() {
        List<String> fails = new ArrayList<>();
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
            if (slot instanceof CurioSlot cs && cs.x != LoopSlotMirror.PARKED && !cs.isActive()) {
                fails.add("DEAD slot on panel " + cs.getIdentifier() + ":" + cs.getSlotIndex() + " holds " + key(cs.getItem())
                        + " at " + cs.x + "," + cs.y);
            }
        }
        return fails;
    }

    /**
     * The on-panel curio slots must fill the grid row by row with no empty cell before the last
     * slot. Independent of how the mixin lays things out: it only reads final positions.
     */
    private static List<String> gridHoles() {
        List<int[]> cells = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
            if (slot instanceof CurioSlot cs && cs.x != LoopSlotMirror.PARKED) {
                cells.add(new int[]{cs.x, cs.y});
                minX = Math.min(minX, cs.x);
                minY = Math.min(minY, cs.y);
                maxX = Math.max(maxX, cs.x);
            }
        }
        if (cells.isEmpty()) return List.of();
        int columns = (maxX - minX) / 18 + 1;
        Set<Integer> taken = new TreeSet<>();
        for (int[] c : cells) taken.add(((c[1] - minY) / 18) * columns + (c[0] - minX) / 18);
        List<String> fails = new ArrayList<>();
        int last = ((TreeSet<Integer>) taken).last();
        for (int i = 0; i < last; i++) if (!taken.contains(i)) fails.add("HOLE in panel grid at cell " + i + " (" + columns + " columns)");
        if (taken.size() != cells.size()) fails.add("two curio slots share a cell");
        return fails;
    }

    /**
     * With a loop worn and nothing happening, the Curios screen's slot list must not be rebuilt.
     * Every rebuild replaces every {@code Slot} object, and anything keying state on slot identity
     * (immersive-ui's hover animation) restarts. Also reports, server-side, which worn stack stopped
     * matching its previous-tick copy and which component differed - that is what makes Curios
     * cycle the stack's slot modifiers and resync the types they touch.
     */
    private static List<BooleanSupplier> idleChurnScenario(List<Item> items, boolean viaLoop) {
        List<BooleanSupplier> seq = new ArrayList<>();
        List<Item> contents = new ArrayList<>(items);
        contents.addAll(onServer(SelfTest::multiTypeItems));
        // Relics items carry live, ticking data (the reported churn came from a loop holding them).
        BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(HungryLoop.RELICS)).sorted()
                .map(BuiltInRegistries.ITEM::get)
                .filter(i -> CuriosApi.getCurio(new ItemStack(i)).isPresent() && !contents.contains(i))
                .limit(8).forEach(contents::add);
        log("idle-churn contents: " + contents.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).toList());
        String label = viaLoop ? "idle-churn-loop" : "idle-churn-direct";
        if (viaLoop) {
            seq.add(provision(label, List.of(loopOf(8, contents.toArray(Item[]::new)))));
        } else {
            seq.add(provision(label, List.of()));
            waitTicks(seq, SETTLE); // let the previous loop's granted slots actually go away first
            seq.add(() -> {
                onServer(p -> { wearDirectly(p, contents); return null; });
                return true;
            });
        }
        openCuriosInto(seq);
        waitTicks(seq, SETTLE * 2);
        int[] rebuilds = {0};
        int[] ticks = {0};
        Slot[] firstSlot = new Slot[1];
        Map<String, ItemStack> prev = new LinkedHashMap<>();
        Map<String, Integer> changedByType = new LinkedHashMap<>();
        List<String> componentNotes = new ArrayList<>();
        seq.add(() -> {
            firstSlot[0] = firstCurioSlot();
            return true;
        });
        seq.add(() -> {
            Slot now = firstCurioSlot();
            if (now != firstSlot[0]) rebuilds[0]++;
            firstSlot[0] = now;
            onServer(p -> {
                CuriosApi.getCuriosInventory(p).ifPresent(h -> h.getCurios().forEach((id, sh) -> {
                    for (int i = 0; i < sh.getStacks().getSlots(); i++) {
                        String key = id + ":" + i;
                        ItemStack cur = sh.getStacks().getStackInSlot(i);
                        ItemStack old = prev.put(key, cur.copy());
                        if (old != null && !ItemStack.matches(cur, old)) {
                            changedByType.merge(key + " " + key(cur), 1, Integer::sum);
                            if (componentNotes.size() < 6) componentNotes.add(key + " " + key(cur) + " changed: " + componentDiff(old, cur));
                        }
                    }
                }));
                return null;
            });
            return ++ticks[0] >= 100;
        });
        seq.add(() -> {
            log(label + ": idle 100 ticks, client slot-list rebuilds=" + rebuilds[0] + ", relics worn (incl. mirrors) at end=" + onServer(p -> CuriosApi.getCuriosInventory(p).map(h -> h.findCurios(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(HungryLoop.RELICS)).size()).orElse(0)));
            log("worn stacks that changed tick-to-tick (key -> ticks): " + changedByType);
            componentNotes.forEach(n -> log("    " + n));
            record(label + "-no-rebuild", rebuilds[0] == 0 ? List.of()
                    : List.of("Curios slot list rebuilt " + rebuilds[0] + "x in 100 idle ticks (restarts slot-keyed UI state)"));
            // A resync replaces each handler's render list; a slot must read the current one, not a
            // stale copy left behind by a skipped rebuild.
            List<String> stale = new ArrayList<>();
            CuriosApi.getCuriosInventory(Minecraft.getInstance().player).ifPresent(h -> {
                for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
                    if (slot instanceof CurioSlot cs && !cs.isCosmetic()) {
                        h.getStacksHandler(cs.getIdentifier()).ifPresent(sh -> {
                            if (((dev.watdafawx.hungryloop.curio.CurioSlotRebind) cs).hungryloop$renders() != sh.getRenders()) {
                                stale.add("slot " + cs.getIdentifier() + ":" + cs.getSlotIndex() + " reads a stale render list");
                            }
                        });
                    }
                }
            });
            record(label + "-slots-read-current-lists", stale);
            if (!viaLoop) {
                // Skipped rebuilds must not leave slots reading a stale render list: toggle a worn
                // relic's render flag through Curios' own packet and check the on-screen slot sees it.
                Slot target = null;
                for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
                    if (slot instanceof CurioSlot cs && !cs.isCosmetic() && cs.getItem().getItem() != net.minecraft.world.item.Items.AIR
                            && BuiltInRegistries.ITEM.getKey(cs.getItem().getItem()).getNamespace().equals(HungryLoop.RELICS)) {
                        target = cs;
                        break;
                    }
                }
                if (target instanceof CurioSlot cs) {
                    String id = cs.getIdentifier();
                    int idx = cs.getSlotIndex();
                    boolean before = cs.getRenderStatus();
                    PacketDistributor.sendToServer(new CPacketToggleRender(id, idx));
                    queueFront(List.of(waitStep(SETTLE), () -> {
                        Slot now = curioSlot(id, idx);
                        boolean server = onServer(p -> CuriosApi.getCuriosInventory(p).flatMap(h -> h.getStacksHandler(id))
                                .map(sh -> sh.getRenders().get(idx)).orElse(true));
                        List<String> fails = new ArrayList<>();
                        if (server == before) fails.add("toggle didn't reach the server (test didn't exercise anything)");
                        if (now instanceof CurioSlot slotNow && slotNow.getRenderStatus() != server) {
                            fails.add("slot " + id + ":" + idx + " shows render=" + slotNow.getRenderStatus() + " but handler has " + server + " (stale render list)");
                        }
                        record(label + "-render-toggle-live", fails);
                        return true;
                    }));
                } else {
                    fail(label + "-render-toggle-live", "no worn relic slot on screen to toggle");
                }
            }
            return true;
        });
        return seq;
    }

    /**
     * The loop's slot type comes first in the Curios panel, and only loops can go in it: a wildcard
     * item (valid in every type) must not land there by right-click equip - which walks types in
     * panel order - nor by clicking it into the slot.
     */
    private static List<BooleanSupplier> loopSlotOrderScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("loop-slot-order", List.of(loopOf(4, items.get(0)))));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            CurioSlot first = null;
            for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
                if (slot instanceof CurioSlot cs && cs.x != LoopSlotMirror.PARKED
                        && (first == null || cs.y < first.y || (cs.y == first.y && cs.x < first.x))) {
                    first = cs;
                }
            }
            if (first == null || !first.getIdentifier().equals(LOOP)) {
                fails.add("top-left curio slot is " + (first == null ? "none" : first.getIdentifier()) + ", expected loop");
            }
            record("loop-slots-first", fails);
            screenshot("loop-slots-first");
            return true;
        });

        List<Item> wildcards = onServer(SelfTest::multiTypeItems);
        if (wildcards.isEmpty()) {
            seq.add(() -> {
                log("SKIP loop-slot-validator (no wildcard curio items installed)");
                return true;
            });
            return seq;
        }
        Item wildcard = wildcards.get(0);

        // right-click with both loop slots empty
        seq.add(provision("loop-slot-validator (right-click)", List.of(), false));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            onServer(p -> {
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, new ItemStack(wildcard));
                return null;
            });
            Minecraft mc = Minecraft.getInstance();
            mc.player.closeContainer();
            mc.player.getInventory().selected = 0;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
            return true;
        });
        waitTicks(seq, 10);
        seq.add(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            String where = onServer(p -> wornTypeOf(p, wildcard, false));
            List<String> fails = new ArrayList<>();
            if (where == null) fails.add("right-click didn't equip " + wildcard + " anywhere (test didn't exercise the path)");
            else if (where.equals(LOOP)) fails.add("right-click equipped " + wildcard + " into the loop slot");
            else log("    right-click equipped the wildcard into " + where);
            record("wildcard-rightclick-not-loop", fails);
            return true;
        });

        // click it into an empty loop slot
        seq.add(provision("loop-slot-validator (click)", List.of()));
        seq.add(() -> {
            onServer(p -> {
                p.getInventory().setItem(9, new ItemStack(wildcard));
                return null;
            });
            return true;
        });
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            click(playerSlotHolding(wildcard));
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            click(curioSlot(LOOP, 0));
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            String inLoop = onServer(p -> CuriosApi.getCuriosInventory(p).flatMap(h -> h.getStacksHandler(LOOP))
                    .map(sh -> key(sh.getStacks().getStackInSlot(0))).orElse("-"));
            if (!inLoop.equals("-")) fails.add("clicking " + wildcard + " into loop:0 was accepted (loop:0 holds " + inLoop + ")");
            fails.addAll(ghostDiff());
            record("wildcard-click-not-loop", fails);
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) click(firstEmptyPlayerSlot());
            return true;
        });
        return seq;
    }

    /** Equip each item in the first free slot of its resolved type, no loop involved (control group). */
    private static void wearDirectly(ServerPlayer p, List<Item> contents) {
        CuriosApi.getCuriosInventory(p).ifPresent(h -> {
            List<String> worn = new ArrayList<>();
            List<Item> relicsFirst = new ArrayList<>(contents);
            relicsFirst.sort(Comparator.comparing(i -> !BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(HungryLoop.RELICS)));
            for (Item item : relicsFirst) {
                ItemStack stack = new ItemStack(item);
                String type = ContentForwarding.resolveSlot(stack, p);
                h.getStacksHandler(type).ifPresent(sh -> {
                    for (int i = 0; i < sh.getSlots(); i++) {
                        if (sh.getStacks().getStackInSlot(i).isEmpty()) {
                            sh.getStacks().setStackInSlot(i, stack);
                            worn.add(BuiltInRegistries.ITEM.getKey(item) + "@" + type + ":" + i);
                            return;
                        }
                    }
                });
            }
            log("worn directly: " + worn);
        });
    }

    private static Slot firstCurioSlot() {
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) if (slot instanceof CurioSlot) return slot;
        return null;
    }

    /** Which data components differ between two stacks of the same item. */
    private static String componentDiff(ItemStack a, ItemStack b) {
        if (!ItemStack.isSameItem(a, b) || a.getCount() != b.getCount()) return "item/count " + key(a) + " -> " + key(b);
        List<String> diffs = new ArrayList<>();
        Set<net.minecraft.core.component.DataComponentType<?>> types = new LinkedHashSet<>(a.getComponents().keySet());
        types.addAll(b.getComponents().keySet());
        for (var type : types) {
            Object x = a.get(type);
            Object y = b.get(type);
            if (!java.util.Objects.equals(x, y)) {
                diffs.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + (java.util.Objects.equals(String.valueOf(x), String.valueOf(y))
                        ? " (equals() false, same toString - broken equals)" : " " + abbreviate(x) + " -> " + abbreviate(y)));
            }
        }
        return diffs.isEmpty() ? "no component differs (matches() false anyway)" : String.join("; ", diffs);
    }

    private static String abbreviate(Object o) {
        String s = String.valueOf(o);
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    /** A loop stored inside the loop must not be granted a slot it is never mirrored into. */
    private static List<BooleanSupplier> nestedLoopScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        ItemStack inner = loopOf(4, items.get(1));
        seq.add(provision("nested-loop", List.of(loopOf(4, new ItemStack(items.get(0)), inner))));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            capture(0, 1);
            queueFront(List.of(checkStep("nested-loop")));
            return true;
        });
        return seq;
    }

    /** Two loops worn at once, overlapping in slot type. */
    private static List<BooleanSupplier> twoLoopsScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("two-loops", List.of(
                loopOf(4, items.get(0), items.get(1)),
                loopOf(4, items.get(0), items.get(3)))));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            capture(-1, 4);
            queueFront(List.of(checkStep("two-loops")));
            return true;
        });
        return seq;
    }

    /**
     * Contents past capacity (6 items, 4 slots) are handed back, never kept or deleted: a worn loop
     * keeps 4 (all mirrored) and the 2 extras land in the inventory; same for a loop sitting in the
     * inventory; with the inventory full they drop at the player's feet. Conservation is the oracle.
     */
    private static List<BooleanSupplier> overflowScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        Item[] six = items.subList(0, 6).toArray(Item[]::new);

        seq.add(provision("overflow-worn", List.of(loopOf(4, six))));
        waitTicks(seq, SETTLE * 2);
        seq.add(() -> {
            List<String> fails = new ArrayList<>(ghostDiff());
            onServer(p -> {
                int inLoop = wornLoopItemCount(p);
                if (inLoop != 4) fails.add("worn loop holds " + inLoop + " after eject, expected 4");
                if (Snap.of(p).mirrors != 4) fails.add("mirrors " + Snap.of(p).mirrors + ", expected 4");
                for (Item extra : List.of(six[4], six[5])) {
                    if (countInInventory(p, extra) != 1) fails.add(extra + " not ejected into the inventory");
                }
                fails.addAll(Snap.of(p).invariantFails);
                return null;
            });
            record("overflow-worn", fails);
            return true;
        });

        seq.add(provision("overflow-inventory", List.of()));
        seq.add(() -> {
            onServer(p -> {
                p.getInventory().setItem(9, loopOf(4, six));
                return null;
            });
            return true;
        });
        waitTicks(seq, SETTLE * 2);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            onServer(p -> {
                int inLoop = p.getInventory().getItem(9).getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT).items().size();
                if (inLoop != 4) fails.add("inventory loop holds " + inLoop + " after eject, expected 4");
                for (Item extra : List.of(six[4], six[5])) {
                    if (countInInventory(p, extra) != 1) fails.add(extra + " not ejected into the inventory");
                }
                return null;
            });
            record("overflow-inventory", fails);
            return true;
        });

        seq.add(provision("overflow-full-inventory", List.of(loopOf(4, six))));
        seq.add(() -> {
            onServer(p -> {
                for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
                return null;
            });
            return true;
        });
        waitTicks(seq, SETTLE * 2);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            onServer(p -> {
                for (Item extra : List.of(six[4], six[5])) {
                    long dropped = p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            p.getBoundingBox().inflate(6), e -> e.getItem().is(extra)).size();
                    if (dropped < 1) fails.add(extra + " neither in the full inventory nor dropped - lost");
                }
                p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(6))
                        .forEach(e -> e.discard());
                return null;
            });
            record("overflow-full-inventory", fails);
            return true;
        });
        return seq;
    }

    private static int countInInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    /**
     * The actual requirement: an item works from the loop as well as it works worn. The reference is
     * not our slot-type rule (that would test the rule against itself): the stack is worn directly in
     * <em>every</em> slot type Curios accepts it in, and the placement with the largest attribute
     * effect is taken as ground truth. Then the same stack goes in a loop instead, and every player
     * attribute must match that placement. With Apotheosis loaded the stack is affixed first, so an
     * affix whose loot category reads a different slot type than the loop mirrored into shows up as
     * an attribute difference.
     */
    private static List<BooleanSupplier> parityScenario(Item item) {
        String name = "parity-" + BuiltInRegistries.ITEM.getKey(item).getPath();
        ItemStack[] stack = new ItemStack[1];
        Map<String, Double>[] baseline = new Map[1];
        Map<String, Double>[] best = new Map[1];
        String[] bestType = new String[1];
        double[] bestScore = {-1};

        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision(name, List.of()));
        waitTicks(seq, 10);
        seq.add(() -> {
            stack[0] = onServer(p -> {
                ItemStack s = new ItemStack(item);
                return ModList.get().isLoaded(HungryLoop.APOTHEOSIS) ? affix(p, s) : s;
            });
            baseline[0] = onServer(SelfTest::attributes);
            List<String> types = onServer(p -> {
                List<String> t = new ArrayList<>(new TreeSet<>(CuriosApi.getItemStackSlots(stack[0], p).keySet()));
                t.remove(LOOP);
                t.removeIf(id -> CuriosApi.getCuriosInventory(p).flatMap(h -> h.getStacksHandler(id))
                        .map(sh -> sh.getSlots() == 0).orElse(true));
                return t;
            });
            List<BooleanSupplier> tries = new ArrayList<>();
            for (String type : types) {
                tries.add(() -> {
                    onServer(p -> {
                        clearPlayer(p);
                        CuriosApi.getCuriosInventory(p).ifPresent(h -> h.setEquippedCurio(type, 0, stack[0].copy()));
                        return null;
                    });
                    return true;
                });
                waitTicks(tries, 10);
                tries.add(() -> {
                    Map<String, Double> worn = onServer(SelfTest::attributes);
                    double score = 0;
                    for (var e : worn.entrySet()) score += Math.abs(e.getValue() - baseline[0].getOrDefault(e.getKey(), e.getValue()));
                    if (score > bestScore[0]) {
                        bestScore[0] = score;
                        best[0] = worn;
                        bestType[0] = type;
                    }
                    return true;
                });
            }
            tries.add(provision(name + " (in loop)", List.of(loopOf(8, stack[0].copy()))));
            waitTicks(tries, SETTLE);
            tries.add(() -> {
                if (best[0] == null) {
                    log("SKIP " + name + " (no slot type accepts it)");
                    return true;
                }
                String loopType = onServer(p -> wornTypeOf(p, item, true));
                Map<String, Double> viaLoop = onServer(SelfTest::attributes);
                List<String> fails = new ArrayList<>();
                Set<String> keys = new TreeSet<>(best[0].keySet());
                keys.addAll(viaLoop.keySet());
                for (String k : keys) {
                    double a = best[0].getOrDefault(k, Double.NaN);
                    double b = viaLoop.getOrDefault(k, Double.NaN);
                    if (Double.isNaN(a) != Double.isNaN(b) || Math.abs(a - b) > 1e-6) {
                        fails.add("attribute " + k + " best-worn=" + a + " in-loop=" + b);
                    }
                }
                if (!fails.isEmpty()) fails.add(0, "works best in " + bestType[0] + ", loop mirrored into " + loopType);
                record(name, fails);
                if (fails.isEmpty()) log("    (best " + bestType[0] + " = loop " + loopType + ")");
                return true;
            });
            queueFront(tries);
            return true;
        });
        return seq;
    }

    /**
     * Curios' right-click equip walks the curio <em>handlers</em> directly (not our guarded GUI slots)
     * and, when every valid slot is full, swaps with the first extractable one. Set up the case that
     * could reach a mirror: the loop's wildcard mirrors into {@code curio}, the player's own item fills
     * the base {@code curio} slot, and the player right-clicks a third curio. The swap must take the
     * player's own item, never hand out a mirror or push the real item into a granted slot.
     */
    private static List<BooleanSupplier> rightClickSwapScenario(Item wildcard, Item own, Item held) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("rightclick-swap", List.of(loopOf(4, wildcard))));
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            onServer(p -> {
                // Fill every empty slot with the player's own item, so right-click has nowhere empty to
                // go and must take the swap path. Granted slots already hold mirrors.
                CuriosApi.getCuriosInventory(p).ifPresent(h -> h.getCurios().values().forEach(sh -> {
                    for (int i = 0; i < sh.getSlots(); i++) {
                        if (sh.getStacks().getStackInSlot(i).isEmpty()) sh.getStacks().setStackInSlot(i, new ItemStack(own));
                    }
                }));
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, new ItemStack(held));
                return null;
            });
            Minecraft mc = Minecraft.getInstance();
            mc.player.getInventory().selected = 0;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = onServer(p -> {
                List<String> f = new ArrayList<>(Snap.of(p).invariantFails);
                ICuriosItemHandler h = CuriosApi.getCuriosInventory(p).orElseThrow();
                if (Snap.of(p).mirrors != 1) f.add("mirrors " + Snap.of(p).mirrors + ", expected 1");
                // a real (non-mirror) item must never sit in a slot the loop granted
                h.getCurios().forEach((id, sh) -> {
                    int granted = 0;
                    for (var m : sh.getModifiers().entrySet()) if (m.getKey().getPath().contains(GRANT_TAG)) granted += (int) m.getValue().amount();
                    for (int i = sh.getSlots() - granted; i < sh.getSlots(); i++) {
                        ItemStack s = sh.getStacks().getStackInSlot(i);
                        if (!s.isEmpty() && !LoopSlotMirror.isMirror(s)) f.add("real item " + key(s) + " pushed into granted slot " + id + ":" + i);
                    }
                });
                var worn = h.findFirstCurio(s -> s.is(held) && !LoopSlotMirror.isMirror(s));
                if (worn.isEmpty()) {
                    f.add("swap path not taken: held " + held + " was not equipped (test didn't exercise the bug)");
                } else {
                    log("    right-click swapped " + held + " into " + worn.get().slotContext().identifier() + ":" + worn.get().slotContext().index());
                }
                if (!p.getInventory().contains(new ItemStack(own))) f.add("the displaced " + own + " didn't come back to the player");
                return f;
            });
            fails.addAll(ghostDiff());
            record("rightclick-swap", fails);
            return true;
        });
        return seq;
    }

    private static ItemStack affix(ServerPlayer p, ItemStack s) {
        return ApothLoot.decorate(p, s, 0).stack();
    }

    /** The curio type holding {@code item} - the real item, or its mirror. Null if not worn. */
    private static String wornTypeOf(ServerPlayer p, Item item, boolean mirror) {
        return CuriosApi.getCuriosInventory(p).flatMap(h -> h.findFirstCurio(
                        s -> s.is(item) && LoopSlotMirror.isMirror(s) == mirror))
                .map(r -> r.slotContext().identifier()).orElse(null);
    }

    /** Every attribute the player has, by id - curios' own slot-count attributes excluded. */
    private static Map<String, Double> attributes(ServerPlayer p) {
        Map<String, Double> values = new LinkedHashMap<>();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(h -> {
            String id = h.key().location().toString();
            if (id.startsWith("curios:") || !p.getAttributes().hasAttribute(h)) return;
            values.put(id, p.getAttributeValue(h));
        });
        return values;
    }

    private static List<Item> multiTypeItems(ServerPlayer p) {
        List<Item> out = new ArrayList<>();
        BuiltInRegistries.ITEM.keySet().stream().sorted().forEach(id -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            if (stack.getItem() instanceof HungryLoopItem || CuriosApi.getCurio(stack).isEmpty()) return;
            Set<String> types = new TreeSet<>(CuriosApi.getItemStackSlots(stack, p).keySet());
            types.remove(ContentForwarding.GENERIC_SLOT);
            if (types.size() >= 2) out.add(stack.getItem());
        });
        return out;
    }

    // --- moves -----------------------------------------------------------------------------------

    private static BooleanSupplier moveStep(String name, int carryTicks) {
        return () -> {
            int dest = otherLoopSlot();
            if (dest < 0) {
                fail(name, "no free second loop slot to move into");
                return true;
            }
            queueFront(moveTo(name, carryTicks, dest));
            return true;
        };
    }

    private static List<BooleanSupplier> moveTo(String name, int carryTicks, int dest) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(() -> {
            Slot src = findLoopSlot();
            if (src == null) {
                fail(name, "loop not found in a loop slot before move");
                return true;
            }
            expectLoopAt = dest;
            click(src);
            if (carryTicks == 0) click(curioSlot(LOOP, dest)); // same tick
            return true;
        });
        if (carryTicks > 0) {
            waitTicks(seq, carryTicks);
            // re-resolve by curio index: picking the loop up drops its grants and rebuilds the list
            seq.add(() -> {
                click(curioSlot(LOOP, dest));
                return true;
            });
        }
        waitTicks(seq, SETTLE);
        seq.add(checkStep(name));
        return seq;
    }

    // --- provisioning (server thread) ------------------------------------------------------------

    private static ItemStack loopOf(int baseSlots, Item... contents) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : contents) stacks.add(new ItemStack(item));
        return loopOf(baseSlots, stacks.toArray(ItemStack[]::new));
    }

    private static ItemStack loopOf(int baseSlots, ItemStack... contents) {
        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(baseSlots, 0, 0, false, 0,
                List.of(contents), LoopContents.ALL_RENDER, true));
        return loop;
    }

    private static BooleanSupplier provision(String scenario, List<ItemStack> loops) {
        return provision(scenario, loops, true);
    }

    /**
     * Empties the player's curios, inventory and cursor, then wears {@code loops} in loop slots 0..
     * With {@code closeScreen} false the open screen is left up, to exercise changes under an open GUI.
     */
    private static BooleanSupplier provision(String scenario, List<ItemStack> loops, boolean closeScreen) {
        return () -> {
            log("-- scenario: " + scenario);
            if (closeScreen) Minecraft.getInstance().player.closeContainer();
            onServer(p -> {
                clearPlayer(p);
                CuriosApi.getCuriosInventory(p).ifPresent(h -> {
                    for (int i = 0; i < loops.size(); i++) h.setEquippedCurio(LOOP, i, loops.get(i).copy());
                });
                return null;
            });
            return true;
        };
    }

    private static void clearPlayer(ServerPlayer p) {
        p.containerMenu.setCarried(ItemStack.EMPTY);
        p.getInventory().clearContent();
        CuriosApi.getCuriosInventory(p).ifPresent(h -> h.getCurios().values().forEach(sh -> {
            for (int i = 0; i < sh.getStacks().getSlots(); i++) sh.getStacks().setStackInSlot(i, ItemStack.EMPTY);
            for (int i = 0; i < sh.getCosmeticStacks().getSlots(); i++) sh.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
        }));
    }

    /** Curio items that resolve to a real (non-generic) slot type, one per type first, deterministic. */
    private static List<Item> pickItems(ServerPlayer p, int n) {
        Map<String, List<Item>> byType = new LinkedHashMap<>();
        BuiltInRegistries.ITEM.keySet().stream().sorted().forEach(id -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (item instanceof HungryLoopItem || CuriosApi.getCurio(stack).isEmpty()) return;
            String type = ContentForwarding.resolveSlot(stack, p);
            if (type.equals(ContentForwarding.GENERIC_SLOT)) return;
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(item);
        });
        List<Item> picked = new ArrayList<>();
        for (int round = 0; picked.size() < n; round++) {
            boolean any = false;
            for (List<Item> list : byType.values()) {
                if (round < list.size() && picked.size() < n) {
                    picked.add(list.get(round));
                    any = true;
                }
            }
            if (!any) break;
        }
        log("picked items: " + picked.stream().map(i -> BuiltInRegistries.ITEM.getKey(i) + "->"
                + ContentForwarding.resolveSlot(new ItemStack(i), p)).toList());
        return picked;
    }

    /** Discovery for the multi-type tie-break: which items could go in more than one real type. */
    private static Void logMultiTypeItems(ServerPlayer p) {
        List<String> multi = new ArrayList<>();
        BuiltInRegistries.ITEM.keySet().stream().sorted().forEach(id -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            if (stack.getItem() instanceof HungryLoopItem || CuriosApi.getCurio(stack).isEmpty()) return;
            Set<String> types = new TreeSet<>(CuriosApi.getItemStackSlots(stack, p).keySet());
            types.remove(ContentForwarding.GENERIC_SLOT);
            if (types.size() >= 2) multi.add(id + " " + types + " -> " + ContentForwarding.resolveSlot(stack, p));
        });
        log("multi-type curio items (" + multi.size() + "):");
        multi.forEach(m -> log("    " + m));
        return null;
    }

    private static int wornLoopItemCount(ServerPlayer p) {
        return CuriosApi.getCuriosInventory(p).map(h -> h.findCurios(s -> s.getItem() instanceof HungryLoopItem
                        && !LoopSlotMirror.isMirror(s)).stream()
                .mapToInt(r -> (int) r.stack().getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT)
                        .items().stream().filter(s -> !s.isEmpty()).count())
                .sum()).orElse(0);
    }

    private static int countMirrors(Player p) {
        return Snap.of(p).mirrors;
    }

    // --- checks ----------------------------------------------------------------------------------

    /** Records the expectations the generic check compares against. -1 skips that expectation. */
    private static void capture(int loopAt, int expectedMirrors) {
        Minecraft mc = Minecraft.getInstance();
        Snap client = Snap.of(mc.player);
        baseLoops = client.loops;
        baseMirrors = expectedMirrors >= 0 ? expectedMirrors : client.mirrors;
        baseVisible = visibleCurioSlots(mc.player.containerMenu);
        expectLoopAt = loopAt;
        log("expect: loops=" + baseLoops + " mirrors=" + baseMirrors + " visible curio slots=" + baseVisible
                + (loopAt >= 0 ? " loop at loop:" + loopAt : ""));
        log("client curios (* = mirror): " + client.curios);
    }

    private static BooleanSupplier checkStep(String name) {
        return () -> {
            if (check().isEmpty()) {
                record(name, List.of());
                screenshot(name);
                return true;
            }
            // Transient disagreement is fine (a packet in flight); a ghost is one still there later.
            List<BooleanSupplier> seq = new ArrayList<>();
            waitTicks(seq, RECHECK);
            seq.add(() -> {
                record(name, check());
                screenshot(name);
                return true;
            });
            queueFront(seq);
            return true;
        };
    }

    private static List<String> check() {
        Minecraft mc = Minecraft.getInstance();
        List<String> fails = new ArrayList<>(ghostDiff());
        Snap srv = onServer(Snap::of);
        fails.addAll(loopSlotCountFails(srv));

        if (srv.loops != baseLoops) fails.add("loop count " + srv.loops + " (expected " + baseLoops + ") - dupe or loss");
        if (srv.mirrors != baseMirrors) fails.add("mirror count " + srv.mirrors + " (expected " + baseMirrors + ")");
        fails.addAll(srv.invariantFails);

        if (expectLoopAt >= 0) {
            List<String> loopSlots = srv.curios.getOrDefault(LOOP, List.of());
            String at = loopSlots.size() > expectLoopAt ? loopSlots.get(expectLoopAt) : "-";
            if (!at.startsWith(HungryLoop.MODID + ":")) fails.add("loop not in loop:" + expectLoopAt + " (found " + at + ")");
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        int visible = visibleCurioSlots(menu);
        // On a multi-page panel mirrors are compacted out of their own page only, so that page shows
        // fewer slots than before - the count isn't an invariant there, a hole-free panel is.
        boolean paged = menu instanceof CuriosContainer pc && pc.totalPages > 1;
        if (!paged && visible != baseVisible) fails.add("visible curio slots " + visible + " (expected " + baseVisible + ")");
        fails.addAll(deadDrawnSlots());
        fails.addAll(gridHoles());
        for (Slot slot : menu.slots) {
            if (slot instanceof CurioSlot cs && LoopSlotMirror.isMirror(cs.getItem()) != (cs.x == LoopSlotMirror.PARKED)) {
                fails.add("parking: " + cs.getIdentifier() + ":" + cs.getSlotIndex() + " mirror="
                        + LoopSlotMirror.isMirror(cs.getItem()) + " x=" + cs.x);
            }
        }
        if (menu instanceof CuriosContainer cc && cc.totalPages <= 1 && !cc.isViewingCosmetics) {
            Map<String, Integer> built = new LinkedHashMap<>();
            for (Slot slot : menu.slots) if (slot instanceof CurioSlot cs) built.merge(cs.getIdentifier(), 1, Integer::sum);
            CuriosApi.getCuriosInventory(mc.player).ifPresent(h -> h.getCurios().forEach((id, sh) -> {
                if (sh.isVisible() && built.getOrDefault(id, 0) != sh.getSlots()) {
                    fails.add("STALE slot list " + id + ": screen has " + built.getOrDefault(id, 0)
                            + ", handler has " + sh.getSlots());
                }
            }));
        }
        return fails;
    }

    /** Client vs server, per curio slot, per container slot, and the cursor. */
    private static List<String> ghostDiff() {
        List<String> fails = new ArrayList<>();
        Snap client = Snap.of(Minecraft.getInstance().player);
        Snap srv = onServer(Snap::of);
        for (var e : srv.curios.entrySet()) {
            List<String> c = client.curios.get(e.getKey());
            List<String> s = e.getValue();
            if (c == null || c.size() != s.size()) {
                fails.add("curio type " + e.getKey() + " size client=" + (c == null ? "missing" : c.size()) + " server=" + s.size());
                continue;
            }
            for (int i = 0; i < s.size(); i++) {
                if (!c.get(i).equals(s.get(i))) fails.add("GHOST curio " + e.getKey() + ":" + i + " client=" + c.get(i) + " server=" + s.get(i));
            }
        }
        if (client.menu.size() != srv.menu.size()) {
            fails.add("menu size client=" + client.menu.size() + " server=" + srv.menu.size());
        } else {
            for (int i = 0; i < srv.menu.size(); i++) {
                if (!client.menu.get(i).equals(srv.menu.get(i))) fails.add("GHOST menu slot " + i + " client=" + client.menu.get(i) + " server=" + srv.menu.get(i));
            }
        }
        if (!client.carried.equals(srv.carried)) fails.add("carried client=" + client.carried + " server=" + srv.carried);
        return fails;
    }

    private record Snap(Map<String, List<String>> curios, List<String> menu, String carried, int loops, int mirrors,
                        List<String> invariantFails) {
        static Snap of(Player p) {
            Map<String, List<String>> curios = new LinkedHashMap<>();
            int[] loops = {0};
            int[] mirrors = {0};
            List<String> invariantFails = new ArrayList<>();
            CuriosApi.getCuriosInventory(p).ifPresent(h -> {
                h.getCurios().forEach((id, sh) -> {
                    List<String> keys = new ArrayList<>();
                    int typeMirrors = 0;
                    for (int i = 0; i < sh.getStacks().getSlots(); i++) {
                        ItemStack s = sh.getStacks().getStackInSlot(i);
                        keys.add(key(s));
                        if (LoopSlotMirror.isMirror(s)) typeMirrors++;
                        else if (s.getItem() instanceof HungryLoopItem) loops[0]++;
                    }
                    mirrors[0] += typeMirrors;
                    curios.put(id, keys);
                    // every slot the loop grants must end up holding a mirror, and vice versa
                    int granted = 0;
                    for (var m : sh.getModifiers().entrySet()) {
                        if (m.getKey().getPath().contains(GRANT_TAG)) granted += (int) m.getValue().amount();
                    }
                    if (granted != typeMirrors) invariantFails.add("grant/mirror " + id + ": granted " + granted + ", mirrored " + typeMirrors);
                });
                renderInvariant(h, invariantFails);
            });
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s)) loops[0]++;
                if (LoopSlotMirror.isMirror(s)) invariantFails.add("mirror escaped into inventory slot " + i + ": " + key(s));
            }
            ItemStack carried = p.containerMenu.getCarried();
            if (carried.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(carried)) loops[0]++;

            List<String> menu = new ArrayList<>();
            for (Slot slot : p.containerMenu.slots) menu.add(key(slot.getItem()));
            return new Snap(curios, menu, key(carried), loops[0], mirrors[0], invariantFails);
        }

        private static void renderInvariant(ICuriosItemHandler h, List<String> fails) {
            for (SlotResult worn : h.findCurios(s -> s.getItem() instanceof HungryLoopItem && !LoopSlotMirror.isMirror(s))) {
                LoopContents data = worn.stack().getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT);
                int idx = worn.slotContext().index();
                h.getStacksHandler(worn.slotContext().identifier()).ifPresent(sh -> {
                    if (idx < sh.getRenders().size() && sh.getRenders().get(idx) != data.selfRenders()) {
                        fails.add("render flag loop:" + idx + "=" + sh.getRenders().get(idx) + " but selfRenders=" + data.selfRenders());
                    }
                });
            }
        }
    }

    private static String key(ItemStack s) {
        if (s.isEmpty()) return "-";
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + (LoopSlotMirror.isMirror(s) ? "*" : "")
                + (s.getCount() > 1 ? "x" + s.getCount() : "");
    }

    // --- slot helpers ----------------------------------------------------------------------------

    private static Slot findLoopSlot() {
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
            if (slot instanceof CurioSlot cs && cs.getIdentifier().equals(LOOP) && cs.getItem().getItem() instanceof HungryLoopItem) return cs;
        }
        return null;
    }

    private static int otherLoopSlot() {
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
            if (slot instanceof CurioSlot cs && cs.getIdentifier().equals(LOOP) && cs.getItem().isEmpty()) return cs.getSlotIndex();
        }
        return -1;
    }

    private static Slot curioSlot(String id, int index) {
        for (Slot slot : Minecraft.getInstance().player.containerMenu.slots) {
            if (slot instanceof CurioSlot cs && cs.getIdentifier().equals(id) && cs.getSlotIndex() == index) return cs;
        }
        return null;
    }

    private static List<Slot> loopMenuSlots() {
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        return menu.slots.subList(0, Math.min(HungryLoopMenu.MAX_LOOP_SLOTS, menu.slots.size()));
    }

    private static Slot firstEmptyPlayerSlot() {
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        for (Slot slot : menu.slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory() && !slot.hasItem()) return slot;
        }
        return null;
    }

    /** Like {@link #firstEmptyPlayerSlot} but never an armor/offhand slot (those are inventory slots too). */
    private static Slot firstEmptyMainInventorySlot() {
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        for (Slot slot : menu.slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory() && slot.getContainerSlot() >= 9
                    && slot.getContainerSlot() < 36 && !slot.hasItem()) return slot;
        }
        return null;
    }

    private static Slot playerSlotHolding(Item item) {
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        for (Slot slot : menu.slots) {
            if (slot.container == Minecraft.getInstance().player.getInventory() && slot.getItem().is(item)) return slot;
        }
        return null;
    }

    private static int visibleCurioSlots(AbstractContainerMenu menu) {
        int n = 0;
        for (Slot slot : menu.slots) if (slot instanceof CurioSlot cs && cs.x != LoopSlotMirror.PARKED) n++;
        return n;
    }

    private static void click(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (slot == null) {
            fail("click", "target slot not found in open menu");
            return;
        }
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot.index, 0, ClickType.PICKUP, mc.player);
    }

    // --- step plumbing ---------------------------------------------------------------------------

    private static <T> T onServer(Function<ServerPlayer, T> fn) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        return server.submit(() -> fn.apply(server.getPlayerList().getPlayer(mc.player.getUUID()))).join();
    }

    private static void openCurios() {
        List<BooleanSupplier> seq = new ArrayList<>();
        openCuriosInto(seq);
        STEPS.addAll(seq);
    }

    private static void openCuriosInto(List<BooleanSupplier> seq) {
        seq.add(() -> {
            PacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY));
            return true;
        });
        seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof CuriosScreen, 100, "Curios screen never opened"));
    }

    private static void settle() {
        waitTicks(STEPS, SETTLE);
    }

    private static void queueFront(List<BooleanSupplier> seq) {
        for (int i = seq.size() - 1; i >= 0; i--) STEPS.addFirst(seq.get(i));
    }

    private static void waitTicks(Deque<BooleanSupplier> into, int n) {
        int[] left = {n};
        into.addLast(() -> --left[0] <= 0);
    }

    private static void waitTicks(List<BooleanSupplier> seq, int n) {
        int[] left = {n};
        seq.add(() -> --left[0] <= 0);
    }

    private static BooleanSupplier waitStep(int n) {
        int[] left = {n};
        return () -> --left[0] <= 0;
    }

    private static BooleanSupplier waitUntil(BooleanSupplier cond, int timeout, String failMsg) {
        int[] left = {timeout};
        return () -> {
            if (cond.getAsBoolean()) return true;
            if (--left[0] > 0) return false;
            fail("wait", failMsg);
            return true;
        };
    }

    private static void finish() {
        log("done: " + (failures == 0 ? "ALL PASS" : failures + " FAILURE(S)"));
        writeReport();
        if (AUTO) {
            int[] left = {60}; // let screenshots finish writing on the io pool
            STEPS.addLast(() -> {
                if (--left[0] > 0) return false;
                Minecraft.getInstance().stop();
                return true;
            });
        } else {
            STEPS.addLast(() -> {
                running = false;
                return true;
            });
        }
    }

    private static void writeReport() {
        try {
            Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve("hungryloop-selftest.txt"), REPORT);
        } catch (IOException e) {
            HungryLoop.LOG.error("hungryloop-selftest: couldn't write report", e);
        }
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String file = String.format("hungryloop-selftest-%02d-%s.png", shot++, name);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {});
    }

    private static void record(String name, List<String> fails) {
        if (fails.isEmpty()) {
            log("PASS " + name);
        } else {
            failures++;
            log("FAIL " + name);
            new LinkedHashSet<>(fails).forEach(f -> log("    " + f));
        }
    }

    private static void fail(String name, String why) {
        record(name, List.of(why));
    }

    private static void log(String line) {
        REPORT.add(line);
        HungryLoop.LOG.info("hungryloop-selftest: {}", line);
    }
}
