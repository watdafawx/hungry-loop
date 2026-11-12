package dev.watdafawx.hungryloop.gametest;

import com.mojang.authlib.GameProfile;
import dev.watdafawx.hungryloop.Config;
import dev.watdafawx.hungryloop.HungryLoop;
import dev.watdafawx.hungryloop.ModItems;
import dev.watdafawx.hungryloop.component.LoopContents;
import dev.watdafawx.hungryloop.component.ModDataComponents;
import dev.watdafawx.hungryloop.curio.ContentForwarding;
import dev.watdafawx.hungryloop.curio.HungryLoopCurio;
import dev.watdafawx.hungryloop.curio.LoopSlotMirror;
import dev.watdafawx.hungryloop.integration.relics.RelicsTestHooks;
import dev.watdafawx.hungryloop.integration.relics.RelicsUnlock;
import dev.watdafawx.hungryloop.item.HungryLoopItem;
import dev.watdafawx.hungryloop.menu.HungryLoopMenu;
import dev.watdafawx.hungryloop.menu.LoopLocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side regression suite, run by {@code build.bat runGameTestServer} on a real dedicated
 * server - the counterpart of the client {@code client.SelfTest}, which runs singleplayer and drives
 * the UI. These cover the server's half with a fake player ticked by hand: mirrors and slot grants,
 * unequip, overflow eject, relic write-back, the loop slot count. No rendering, so CI runs them.
 *
 * <p>Only active when {@code neoforge.enabledGameTestNamespaces} includes {@code hungryloop}.
 * Tests needing curio items from other mods skip (pass with a log line) when those aren't installed.
 */
@GameTestHolder(HungryLoop.MODID)
@PrefixGameTestTemplate(false)
public final class LoopGameTests {

    private LoopGameTests() {}

    private static ServerPlayer player(GameTestHelper helper, String name) {
        UUID id = UUID.nameUUIDFromBytes(("hungryloop-gametest-" + name).getBytes(StandardCharsets.UTF_8));
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "hl_" + name));
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.setPos(at.x, at.y, at.z);
        clear(p);
        return p;
    }

    private static ICuriosItemHandler curios(GameTestHelper helper, ServerPlayer p) {
        ICuriosItemHandler h = CuriosApi.getCuriosInventory(p).orElse(null);
        if (h == null) helper.fail("no Curios inventory on the fake player");
        return h;
    }

    private static void clear(ServerPlayer p) {
        p.getInventory().clearContent();
        CuriosApi.getCuriosInventory(p).ifPresent(h -> h.getCurios().values().forEach(sh -> {
            for (int i = 0; i < sh.getStacks().getSlots(); i++) sh.getStacks().setStackInSlot(i, ItemStack.EMPTY);
        }));
    }

    /**
     * One real player tick, as the level runs it: FakePlayer#tick is a no-op, and EntityTickEvent -
     * which Curios ticks on (change detection, slot modifiers) - is fired by the level's entity loop
     * around the entity's tick, not from inside doTick. Our PlayerTickEvent handlers fire inside it.
     */
    private static void tick(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            if (EventHooks.fireEntityTickPre(p).isCanceled()) continue;
            p.doTick();
            EventHooks.fireEntityTickPost(p);
        }
    }

    private static ItemStack loopOf(int baseSlots, List<ItemStack> contents) {
        ItemStack loop = new ItemStack(ModItems.HUNGRY_LOOP.get());
        loop.set(ModDataComponents.LOOP_CONTENTS.get(), new LoopContents(baseSlots, 0, 0, false, 0,
                contents, LoopContents.ALL_RENDER, true));
        return loop;
    }

    private static ItemStack wornLoop(ICuriosItemHandler h) {
        return h.getStacksHandler(HungryLoopCurio.LOOP_SLOT)
                .map(sh -> sh.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    private static List<ItemStack> contents(ItemStack loop) {
        return loop.getOrDefault(ModDataComponents.LOOP_CONTENTS.get(), LoopContents.DEFAULT).items();
    }

    /** Curio items with a real (non-generic) slot type, one per type, deterministic. */
    private static List<Item> curioItems(ServerPlayer p, int n) {
        Map<String, Item> firstPerType = new LinkedHashMap<>();
        BuiltInRegistries.ITEM.keySet().stream().sorted().forEach(id -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack s = new ItemStack(item);
            if (item instanceof HungryLoopItem || CuriosApi.getCurio(s).isEmpty()) return;
            String type = ContentForwarding.resolveSlot(s, p);
            if (!type.equals(ContentForwarding.GENERIC_SLOT)) firstPerType.putIfAbsent(type, item);
        });
        return firstPerType.values().stream().limit(n).toList();
    }

    private static String dump(ICuriosItemHandler h) {
        StringBuilder b = new StringBuilder();
        h.getCurios().forEach((type, sh) -> {
            b.append(type).append('=').append(sh.getSlots()).append('[');
            for (int i = 0; i < sh.getStacks().getSlots(); i++) {
                ItemStack s = sh.getStacks().getStackInSlot(i);
                b.append(s.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + (LoopSlotMirror.isMirror(s) ? "*" : "")).append(',');
            }
            b.append("] ");
        });
        return b.toString();
    }

    private static int mirrors(ICuriosItemHandler h) {
        int[] n = {0};
        h.getCurios().values().forEach(sh -> {
            for (int i = 0; i < sh.getStacks().getSlots(); i++) {
                if (LoopSlotMirror.isMirror(sh.getStacks().getStackInSlot(i))) n[0]++;
            }
        });
        return n[0];
    }

    private static boolean mirroredIn(ICuriosItemHandler h, String type, Item item) {
        return h.getStacksHandler(type).map(sh -> {
            for (int i = 0; i < sh.getStacks().getSlots(); i++) {
                ItemStack s = sh.getStacks().getStackInSlot(i);
                if (LoopSlotMirror.isMirror(s) && s.is(item)) return true;
            }
            return false;
        }).orElse(false);
    }

    /** n curio item types, or null after passing the test with a skip note. */
    private static List<Item> needItems(GameTestHelper helper, ServerPlayer p, int n) {
        List<Item> items = curioItems(p, n);
        if (items.size() < n) {
            HungryLoop.LOG.warn("hungryloop gametest: only {} curio item types installed, need {} - skipped", items.size(), n);
            helper.succeed();
            return null;
        }
        return items;
    }

    @GameTest(template = "empty")
    public static void loopSlotCountMatchesConfig(GameTestHelper helper) {
        ServerPlayer p = player(helper, "slots");
        ICuriosItemHandler h = curios(helper, p);
        EventHooks.firePlayerLoggedIn(p); // LoopSlotProvider applies the loop slot count on login
        tick(p, 5);
        int have = h.getStacksHandler(HungryLoopCurio.LOOP_SLOT).map(sh -> sh.getSlots()).orElse(0);
        helper.assertTrue(have == Config.loopSlots(), "loop slot type has " + have + " slots, config says " + Config.loopSlots());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipMirrorsEachItemInItsTypeAndUnequipPurges(GameTestHelper helper) {
        ServerPlayer p = player(helper, "mirror");
        ICuriosItemHandler h = curios(helper, p);
        EventHooks.firePlayerLoggedIn(p);
        List<Item> items = needItems(helper, p, 3);
        if (items == null) return;

        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, loopOf(4, items.stream().map(ItemStack::new).toList()));
        tick(p, 20);
        helper.assertTrue(mirrors(h) == 3, "expected 3 mirrors, have " + mirrors(h));
        for (Item item : items) {
            String type = ContentForwarding.resolveSlot(new ItemStack(item), p);
            helper.assertTrue(mirroredIn(h, type, item), item + " not mirrored into its type " + type);
        }

        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, ItemStack.EMPTY);
        tick(p, 10);
        helper.assertTrue(mirrors(h) == 0, "mirrors left after unequip: " + mirrors(h) + " " + dump(h));
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            helper.assertFalse(LoopSlotMirror.isMirror(p.getInventory().getItem(i)), "a mirror escaped into the inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overflowIsEjectedNotLost(GameTestHelper helper) {
        ServerPlayer p = player(helper, "overflow");
        ICuriosItemHandler h = curios(helper, p);
        EventHooks.firePlayerLoggedIn(p);
        List<Item> items = needItems(helper, p, 6);
        if (items == null) return;

        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, loopOf(4, items.stream().map(ItemStack::new).toList()));
        tick(p, 45);
        int kept = contents(wornLoop(h)).size();
        helper.assertTrue(kept == 4, "loop holds " + kept + " after overflow, expected 4");
        for (Item extra : items.subList(4, 6)) {
            helper.assertTrue(p.getInventory().hasAnyMatching(s -> s.is(extra)), extra + " was not handed back");
        }
        helper.assertTrue(mirrors(h) == 4, "mirrors " + mirrors(h) + ", expected 4");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relicProgressWrittenBackOnUnequip(GameTestHelper helper) {
        if (!ModList.get().isLoaded("relics") || RelicsTestHooks.relicItems(1).isEmpty()) {
            helper.succeed();
            return;
        }
        ServerPlayer p = player(helper, "writeback");
        ICuriosItemHandler h = curios(helper, p);
        EventHooks.firePlayerLoggedIn(p);
        Item relic = RelicsTestHooks.relicItems(1).get(0);

        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, loopOf(4, List.of(new ItemStack(relic))));
        tick(p, 10);
        ItemStack stored = contents(wornLoop(h)).get(0);
        helper.assertFalse(RelicsTestHooks.isUnlocked(p, stored), "precondition: relic already unlocked");
        ItemStack mirror = h.findFirstCurio(s -> LoopSlotMirror.isMirror(s) && s.is(relic))
                .map(r -> r.stack()).orElse(ItemStack.EMPTY);
        helper.assertFalse(mirror.isEmpty(), "no mirror of " + relic);
        RelicsUnlock.unlockAll(p, mirror);
        tick(p, 50); // past LoopSlotMirror's 40-tick write-back interval

        ItemStack loop = wornLoop(h).copy();
        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, ItemStack.EMPTY);
        tick(p, 5);
        List<ItemStack> after = contents(loop);
        helper.assertFalse(after.isEmpty(), "loop lost its relic");
        helper.assertTrue(RelicsTestHooks.isUnlocked(p, after.get(0)), "relic progress made while worn was lost on unequip");
        helper.succeed();
    }

    /**
     * Leveling a relic from its Relics description screen while it sits in the loop menu. Relics'
     * PacketAbilityTweak mutates {@code containerMenu.getSlot(n).getItem()} in place and then calls
     * {@code slot.set(sameStack)}. That edit has to reach the stored item and the worn mirror, or the
     * mirror's stale data is written back over it a couple of seconds later.
     */
    /** What {@link HungryLoopMenu#open} does; a FakePlayer's openMenu is a no-op. */
    private static void openMenu(ServerPlayer p) {
        LoopSlotMirror.writeBackNow(p);
        p.containerMenu = new HungryLoopMenu(1, p.getInventory(), LoopLocator.curio(HungryLoopCurio.LOOP_SLOT, 0));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void relicLeveledInLoopMenuSticks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("relics") || RelicsTestHooks.relicItems(1).isEmpty()) {
            helper.succeed();
            return;
        }
        ServerPlayer p = player(helper, "menulevel");
        ICuriosItemHandler h = curios(helper, p);
        EventHooks.firePlayerLoggedIn(p);
        Item relic = RelicsTestHooks.relicItems(1).get(0);

        h.setEquippedCurio(HungryLoopCurio.LOOP_SLOT, 0, loopOf(4, List.of(new ItemStack(relic))));
        tick(p, 10);
        helper.assertFalse(RelicsTestHooks.isUnlocked(p, contents(wornLoop(h)).get(0)), "precondition: relic already unlocked");

        openMenu(p);
        tick(p, 5);

        // What PacketAbilityTweak does on the server.
        var slot = p.containerMenu.getSlot(0);
        ItemStack inMenu = slot.getItem();
        helper.assertTrue(inMenu.is(relic), "menu slot 0 holds " + inMenu);
        RelicsUnlock.unlockAll(p, inMenu);
        slot.set(inMenu);

        tick(p, 50); // write-back interval passes with the menu still open
        p.doCloseContainer();
        tick(p, 100);

        helper.assertTrue(RelicsTestHooks.isUnlocked(p, contents(wornLoop(h)).get(0)), "stored relic lost its levels");
        ItemStack mirror = h.findFirstCurio(s -> LoopSlotMirror.isMirror(s) && s.is(relic))
                .map(r -> r.stack()).orElse(ItemStack.EMPTY);
        helper.assertTrue(RelicsTestHooks.isUnlocked(p, mirror), "worn mirror never got the levels");

        openMenu(p);
        helper.assertTrue(RelicsTestHooks.isUnlocked(p, p.containerMenu.getSlot(0).getItem()), "reopened menu shows relic at 0");
        p.doCloseContainer();
        helper.succeed();
    }
}
