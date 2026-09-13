package dev.mtop.hungryloop.client;

import dev.mtop.hungryloop.HungryLoop;
import dev.mtop.hungryloop.component.LoopContents;
import dev.mtop.hungryloop.component.ModDataComponents;
import dev.mtop.hungryloop.curio.ContentForwarding;
import dev.mtop.hungryloop.curio.LoopSlotMirror;
import dev.mtop.hungryloop.integration.apotheosis.ApothLoot;
import dev.mtop.hungryloop.item.HungryLoopItem;
import dev.mtop.hungryloop.ModItems;
import dev.mtop.hungryloop.menu.HungryLoopMenu;
import dev.mtop.hungryloop.net.OpenLoopPayload;
import net.minecraft.client.Minecraft;
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
            suite.addAll(idleChurnScenario(items, true));
            suite.addAll(idleChurnScenario(items, false));
            suite.addAll(equipWhileOpenScenario(items));
            suite.addAll(provisionedMoves(items));
            suite.addAll(nestedLoopScenario(items));
            suite.addAll(twoLoopsScenario(items));
            suite.addAll(overflowScenario(items));
            List<Item> parityItems = new ArrayList<>(items);
            parityItems.addAll(onServer(SelfTest::multiTypeItems));
            for (Item item : parityItems) suite.addAll(parityScenario(item));
            List<Item> wildcards = onServer(SelfTest::multiTypeItems);
            if (!wildcards.isEmpty()) suite.addAll(rightClickSwapScenario(wildcards.get(0), items.get(1), items.get(2)));
            suite.add(() -> {
                finish();
                return true;
            });
            queueFront(suite);
            return true;
        });
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
                            if (((dev.mtop.hungryloop.curio.CurioSlotRebind) cs).hungryloop$renders() != sh.getRenders()) {
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
     * Contents past capacity (6 items, 4 slots): all six stay equipped, the loop screen shows all six,
     * an overflow item can be taken out, and nothing can be put back past capacity.
     */
    private static List<BooleanSupplier> overflowScenario(List<Item> items) {
        List<BooleanSupplier> seq = new ArrayList<>();
        seq.add(provision("overflow", List.of(loopOf(4, items.subList(0, 6).toArray(Item[]::new)))));
        openCuriosInto(seq);
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            capture(0, 6);
            queueFront(List.of(checkStep("overflow-equipped")));
            return true;
        });
        seq.add(() -> {
            Minecraft.getInstance().player.closeContainer();
            PacketDistributor.sendToServer(new OpenLoopPayload());
            return true;
        });
        seq.add(waitUntil(() -> Minecraft.getInstance().screen instanceof HungryLoopScreen, 100, "loop screen never opened"));
        waitTicks(seq, 15);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            int shown = 0;
            for (Slot slot : loopMenuSlots()) if (slot.isActive() && slot.hasItem()) shown++;
            if (shown != 6) fails.add("loop screen shows " + shown + " filled slots, contents hold 6");
            record("overflow-visible", fails);
            screenshot("overflow-visible");
            Slot overflow = loopMenuSlots().get(5);
            click(overflow);
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            click(firstEmptyPlayerSlot()); // drop the taken overflow item into the inventory
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            int inLoop = onServer(SelfTest::wornLoopItemCount);
            int mirrors = onServer(p -> countMirrors(p));
            if (inLoop != 5) fails.add("after taking an overflow item the loop holds " + inLoop + ", expected 5");
            if (mirrors != 5) fails.add("mirrors " + mirrors + " after taking an overflow item, expected 5");
            fails.addAll(ghostDiff());
            record("overflow-take", fails);
            screenshot("overflow-take");
            // now try to put it back past capacity: pick it up from the inventory, click overflow slot 5
            click(playerSlotHolding(items.get(5)));
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            click(loopMenuSlots().get(5));
            return true;
        });
        waitTicks(seq, 5);
        seq.add(() -> {
            if (!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) {
                click(firstEmptyPlayerSlot()); // rejected placement leaves it on the cursor - put it away
            }
            return true;
        });
        waitTicks(seq, SETTLE);
        seq.add(() -> {
            List<String> fails = new ArrayList<>();
            int inLoop = onServer(SelfTest::wornLoopItemCount);
            if (inLoop != 5) fails.add("placing into a slot past capacity was accepted: loop holds " + inLoop);
            fails.addAll(ghostDiff());
            record("overflow-no-refill", fails);
            screenshot("overflow-no-refill");
            Minecraft.getInstance().player.closeContainer();
            return true;
        });
        return seq;
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
        if (visible != baseVisible) fails.add("visible curio slots " + visible + " (expected " + baseVisible + ")");
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
