# Changelog

## 0.8.0

- New loop screen: an animated frame inspired by Path of Exile's unique items (copper title plate with turning end caps, a breathing ember border, drifting embers, the flavour line), in teal for shiny loops.
- Contained items now sit on a ring around your player model instead of a snake; the ring widens as the loop grows.
- The screen is wide and short, so it fits every GUI scale (it used to cut off the hotbar on smaller screens).
- The loop's tooltip uses the same colours and separators.

## 0.7.0

- Progress made on items while they are in a worn loop (Relics XP, levels and research, durability, charges) is now kept when the loop comes off. Before, it was lost on unequip.
- Items past a loop's capacity are handed back to your inventory (or dropped at your feet if it is full) instead of staying stuck in the loop.
- The loop screen opens on top of the inventory, Curios or Accessories screen you opened it from, and closing it takes you back there.
- Items in the loop screen show which slot they count as ("Acts as: Ring").
- Accessories' original screen: group tabs no longer jump to the wrong row while a loop is worn.

## 0.6.6

- The Curios panel no longer rebuilds itself every few seconds when it has more than one page (packs with many slot types) and Relics are worn, so hover animations stop restarting.

## 0.6.5

- Fixed empty, unclickable "ghost" slots in the Curios panel when a loop is worn and the panel has more than one page (packs with many slot types).
- Accessories + Accessories Compat Layer support: loop contents work, their slots stay hidden in both Accessories screens and can't be picked up, and the client no longer falls out of sync with the server.
- With Accessories, players now get the configured number of loop slots (was one extra).

## 0.6.4

- Author is now watdafawx (was mtop); Java packages moved from dev.mtop to dev.watdafawx. No gameplay changes.

## 0.6.3

- New mod icon based on the Hungry Loop item.
- Issue tracker link now points to github.com/watdafawx/hungry-loop.

## 0.6.2

- Loop slots now come first in the Curios panel.
- Only Hungry Loops can go in the loop slot; wildcard curios can no longer be clicked or right-click-equipped into it.
