# The Backrooms: Multi-Level Minecraft Horror Plugin

## Context

The existing plugin creates a single "backrooms" dimension with yellow terracotta rooms and froglight ceilings. We want to expand this into a multi-level horror experience themed around **Minecraft fan lore** -- Herobrine, the Far Lands, corrupted worlds, missing textures, Disc 11, the void. The goal: players should feel like they've accidentally glitched through the game and found old broken code that shouldn't exist.

This is NOT faithful to Backrooms canon. It's Minecraft's own mythology turned into a liminal horror dimension system.

---

## How Players Enter the Backrooms

All entries should feel like genuine game bugs -- no menus, no portal animations. A screen stutter, a garbled chat message, and you're somewhere else.

### 1. Bedrock Breach (Primary)
Player falls below Y=-64 in any world. Brief blindness + nausea, teleported to Level 0. Chat prints `"Saving chunks..."` in gray italic -- then nothing. The game hiccupped.

### 2. The Wrong Nether Portal
A portal containing crying obsidian in the frame has a configurable chance (default 5%) of sending the player to Level 0 instead of the Nether. The purple swirl lasts too long, shifts yellow-green. Listen for `PlayerPortalEvent`, check frame blocks.

### 3. Far Lands Drift
Player crosses a configurable coordinate threshold (default +/- 100,000). As they approach, escalating visual corruption: nausea, screen flashes, client-side block glitches. At the boundary, a title flashes `"NaN"` in red and they're teleported to Level 3 (The Far Lands) directly.

### 4. The Impossible Block
On chunk load (very low probability, ~0.1%), place an anomalous block in the overworld -- a sponge in a cave, a command block underground, a structure block in a field. Breaking or right-clicking it triggers a 2-second freeze, `BLOCK_END_PORTAL_SPAWN` sound, then teleport to Level 0.

### 5. The Bed at World Origin
Sleeping at X=0, Z=0 during a thunderstorm. Sleep animation plays normally, but the player "wakes up" in Level 0. Their bed is gone. The standard `"You have no home bed or charged respawn anchor, or it was obstructed"` message appears -- they didn't die.

---

## The Levels

### Level 0: The Loading Zone
**Theme:** The space between chunks. A buffer zone that was never meant to be occupied.

- **Generation:** The existing `BackroomsChunkGenerator` -- yellow terracotta rooms, froglight ceilings, random walls/pillars. Add occasional "furniture": oak stair chairs, slab-on-fence tables, paintings. All empty.
- **Atmosphere:** Time frozen at noon. Fluorescent hum (`AMBIENT_BASALT_DELTAS_LOOP` at low pitch). Hunger drains 50% faster.
- **Horror:** Footstep sounds from empty corridors. Rarely, armor stands labeled `"????"` appear at render distance, facing the player -- despawn if approached within 20 blocks. After 10+ minutes, phantom "players" (armor stands with random heads) appear in the distance. Blackout events increase over time.
- **Loot:** Soggy bread (renamed "Damp Bread"), clocks, empty maps, paper. Survival supplies are scarce.

### Level 1: Herobrine's Domain
**Theme:** The place where "Removed Herobrine" actually moved him.

- **Generation:** Flat dark forest biome, no trees. Cobblestone ruins: roofless 5x5 houses, 2x2 tunnels carved at random angles, sand pyramids with TNT centers. Redstone torches are the only light -- dim, red, oppressive. Perpetual fog (via slight blindness effect at distance).
- **Horror:** Signs appear with messages: `"STOP"`, `"I am watching"`, `"Not real"`, `"Wake up"`, `"I was the first player"`. The "Herobrine" entity: an armor stand with Steve's skin but white eyes, appears at the end of corridors, always facing the player. Never moves when observed. When you look away and back, it's either gone or closer. At 5 blocks, you take 2 hearts of damage from nothing and it vanishes. Redstone torches near you turn off as you approach.
- **Loot:** Golden apples, enchanted books with impossible enchantments (Sharpness on a helmet), written books titled `"changelog.txt"` with fake patch notes referencing "removed Herobrine."

### Level 2: The Missing Texture Plane
**Theme:** The world when the renderer gives up. A developer debug scene never meant to be seen.

- **Generation:** Strict magenta + black concrete checkerboard. Perfect cubes, floating platforms, stairs leading to walls, doorframes opening onto 40-block drops. "UI elements" built into walls: gray concrete rectangles (buttons), white concrete lines (text placeholders).
- **Horror:** Gravity glitches -- random Levitation/Slow Falling every 30-60 seconds. "Placeholder" entities: armor stands in T-pose wearing checkerboard-dyed leather armor, slowly rotating. Invisible barrier walls. Blocks placed by the player have a 50% chance of becoming a random block after 10 seconds.
- **Atmosphere:** Static hiss, UI click sounds, error buzzes. No sky.

### Level 3: The Far Lands
**Theme:** Terrain generation encountering NaN. Integer overflow made physical.

- **Generation:** Catastrophically broken terrain. Walls of stone to build height, swiss-cheese caves, waterfalls from nowhere, lava on grass. Biomes shift every few blocks: snow next to desert next to mushroom. 1x1 pillars of random blocks stretching bedrock to sky. Materials selected by noise: stone, sand, netherrack, end stone, soul sand -- impossible geology. Permanent sunset (time 12500).
- **Horror:** Fake coordinates in the action bar showing `X: 12550821 Y: 63 Z: -8371022`, ticking randomly. Player "drifts" -- teleported 0.5-1 block in a random direction every 10-20 seconds (floating point errors). Terrain behind the player subtly changes when they're not looking. Mobs are wrong: giant chickens, invisible creepers, zombies in diamond armor.
- **Loot:** Paradoxically great mining -- diamond/emerald/ancient debris at high rates, but veins mixed with dirt and gravel.

### Level 4: The Void
**Theme:** The coordinate space beneath bedrock. It exists, but it was never meant to contain anything.

- **Generation:** Flat bedrock at Y=0. End sky (black, no sun/moon/stars). Soul torches every 32 blocks. Scattered obsidian/crying obsidian/deepslate clusters. Floating bedrock islands (8x8) at Y=20-40 with a chest, a soul torch, and ender pearls. Faint structures visible in the distance that can never be reached -- they generate 100+ blocks away and despawn on approach.
- **Horror:** Almost total darkness. Endermen are the only mob -- they pick up bedrock blocks from the floor, destroying your footing. All sounds play at 30% volume, except occasional full-volume Enderman screams, doors closing, or Disc 11 melody fragments. Your own footsteps echo with a 0.3-second delay.
- **Falling:** Void damage disabled. Falling for 10+ seconds teleports to Level 5.

### Level 5: Disc 11
**Theme:** You are in the tunnels where Disc 11 was recorded. The thing that was chasing the recorder is now chasing you.

- **Generation:** Narrow 2-wide tunnels through solid stone (recursive backtracker maze, 2-wide, 3-tall). Mix of stone, cobblestone, mossy cobblestone. No rooms larger than 4x4. Dead ends and loops -- no solution. Every ~100 blocks, a 4x4 room with a jukebox containing Disc 11 or 13, and bone items on the floor.
- **Horror:** **The Pursuer** -- an invisible entity represented only by sound. Footsteps play behind the player at ~30 blocks distance. When you stop, they continue 2-3 more steps, then stop. When you move again, they resume after 1 second. If you stand still for 15+ seconds, the distance starts closing: 30, 25, 20, 15, 10, 5... At 5 blocks, you take half a heart every 3 seconds. Moving resets to 30.
- **Light decay:** Player-placed torches have 30% chance of going out after 60 seconds. Every 2-3 minutes, all torches within 20 blocks extinguish simultaneously (wind burst sound).
- **Loot:** Written books with first-person accounts: `"Running. Breathing. Something behind me. Don't look. Don't stop. The walls are -- [DATA CORRUPTED]"`

### Level 6: The Removed Dimension
**Theme:** A graveyard of deleted features. Minecraft's recycle bin. Deletion doesn't mean destruction -- it means being moved somewhere nobody looks.

- **Generation:** Flat world on old Nether reactor core pattern (cobblestone + gold cross, tiled). 16x16 "exhibit rooms" separated by nether brick corridors. Each room showcases a removed feature:
  - **The Human Mob:** Spawner with signs `"mob_human.class - DEPRECATED"`, spawning zombie villagers in leather armor.
  - **The Nether Reactor:** Perfect core structure. Hitting the center spawns Nether items.
  - **The Sky Dimension:** Open room with glowstone ceiling at Y=200+, floating grass islands.
  - **Crying Obsidian (original):** Room of crying obsidian with signs about the original spawn-point mechanic.
- **Mood:** Peaceful. Melancholy. Nostalgic. The first genuinely safe place. Mobs here use old behavior: zombies don't burn in light, skeletons shoot slowly.
- **Key NPC:** A villager with no trades except: nether star for a written book `"EULA.txt"` -- this contains the final escape clue.
- **The Empty Exhibit:** A room with signs reading `"[RESERVED] return_home.class"` and a single cobblestone block. This is where the exit is built.

### Level 7: The Corrupted Chunk
**Theme:** A JPEG that is 80% corrupted. You can almost recognize what this was supposed to be.

- **Generation:** Pure chaos. Multiple overlapping noise functions controlling different biome palettes. Structure fragments (villages, temples, bastions) placed partially, rotated, overlapping. Fire on water, snow on magma, crops on diamond blocks.
- **Horror:** Every 30 seconds, a random 4x4x4 cube near the player has all blocks replaced with random materials -- the world is re-corrupting. Gravity blocks (sand, gravel) don't fall until you're within 8 blocks, then cascade. Scrambled mobs: zombies that shoot arrows, skeletons that explode, creepers that drop cobwebs. Fake server error messages in chat: `"[Server] WARN: ChunkLoadError at region r.4.7.mca"`, `"[Server] java.lang.NullPointerException"`.
- **Time:** Rapid cycling (20x speed). Random potion effects every 2 minutes.
- **Loot:** Insane loot tables: 64 diamonds next to dirt, enchanted sticks, maps to nowhere. The diamond block needed for escape is here.

---

## Moving Between Levels

Transitions feel like further glitching -- the bug that brought you here is getting worse.

| From | To | Trigger |
|------|-----|---------|
| L0 | L1 | Rare cobblestone patch in yellow walls with a sign reading `"come"`. Break it, crawl through the 1-block hole. |
| L0 | L2 | During a blackout, one wall flickers to magenta/black checkerboard. Walk into it within 2-3 seconds before lights return. |
| L1 | L3 | Follow Herobrine through 5 consecutive appearances. The 5th leads to a gravel wall that collapses, revealing a mossy cobblestone portal frame. Walk through. |
| L2 | L4 | Certain black floor tiles (1-in-500) are secretly air with a block display. Step on one, fall through into 5-second freefall, land on Level 4's bedrock plane. |
| L3 | L5 | When fake coordinates show a "cursed" number (X=Z, or any coordinate reads 404), a jukebox appears playing Disc 11. Right-click it within 3 seconds. |
| L4 | L6 | An Enderman places a block. Break it to reveal a nether reactor core pattern beneath. Stand on it and look straight down. |
| L5 | L7 | Play Disc 11 in a jukebox room. At the exact moment it ends (the "crash"), the room's walls shatter, revealing Level 7. |
| L7 | L0 | Dying in Level 7 returns you to Level 0, not the overworld. The loop restarts. |

Non-linear shortcuts:
- L4 void fall: 25% chance returns to L0, 75% sends to L5
- Compass in item frame on Level 0 ceiling froglight: opens path directly to L6 (speedrun route)

---

## Getting Out

### The Changelog Puzzle
Written books titled `"changelog.txt"` are scattered across levels. Together they describe a bug and its fix:

1. **L0:** `"v0.0.1 - Added boundary check for chunk loading. WARNING: Players may clip through unloaded regions. TODO: Add return vector."`
2. **L1:** `"v0.0.2 - Removed entity_303 from mob registry. Note: Entity persists in memory. Do not acknowledge."`
3. **L2:** `"v0.0.3 - Texture atlas rebuild failed. Fallback renderer active. Missing: return_portal.png"`
4. **L3:** `"v0.0.4 - Float overflow in terrain gen. Coordinates wrapped to NaN. FIX: Reset origin to 0,0,0"`
5. **L5:** `"v0.0.5 - Audio log corrupted. Disc_11 playback causes entity attraction. Do not play in enclosed spaces."`
6. **L6:** Via the villager's `EULA.txt` trade: `"v0.0.6 - Deprecated return method found in removed features archive. Reconstruct reactor core with CORRECTED materials to invoke."`

### The Exit: Rebuild the Nether Reactor
In Level 6's empty exhibit room (`"[RESERVED] return_home.class"`):

1. Gather materials from across levels:
   - 4 crying obsidian (Level 4 island chests)
   - 4 gold blocks (Level 6 reactor exhibits)
   - 1 diamond block (Level 7 corrupted loot)
   - 1 compass (rare in Level 0 chests, always points to world origin)

2. Build the "corrected" reactor on the cobblestone block:
   - Bottom 3x3: cobblestone corners, crying obsidian edges, gold center
   - Middle 3x3: gold corners, air edges, diamond block center
   - Top: compass in item frame on the diamond block

3. Structure activates: blocks convert to crying obsidian, beam of end rods shoots upward, 3-second blindness, teleport to original overworld location. Message: `"Connection re-established. Welcome back."`

### Admin Escape
`/backrooms leave` remains for ops. Regular players must solve the puzzle.

---

## Universal Mechanics

- **Inventory persists** across all levels (essential for the escape puzzle)
- **Death in Backrooms** respawns at Level 0, not overworld. Items drop at death location.
- **Chat isolation:** Backrooms players can't see overworld chat. Messages between Backrooms players are "corrupted" -- random characters replaced with unicode blocks, corruption increasing with level number.
- **HUD:** Action bar shows current level: `"LEVEL 0"` etc.

---

## Implementation Architecture

### Key Changes to Existing Code

1. **`BackroomsManager`** (BackroomsManager.java)
   - Expand from single world to `Map<Integer, World>` managing 8 worlds (levels 0-7)
   - Each world gets its own `ChunkGenerator` subclass and game rules
   - `loadOrCreateWorld()` becomes `loadOrCreateWorlds()`

2. **`BackroomsChunkGenerator`** (BackroomsChunkGenerator.java)
   - Rename to `Level0ChunkGenerator`, keep as-is with minor additions (furniture, chests)
   - Create 7 new generators: `Level1ChunkGenerator` through `Level7ChunkGenerator`

3. **`BackroomsCommand`** (BackroomsCommand.java)
   - Add level-aware teleportation (`/backrooms <level>` for admins)
   - `returnLocations` moves to a `BackroomsPlayerState` class

4. **New classes to create:**
   - `BackroomsEntryListener` -- listens for overworld events (5 entry methods)
   - `BackroomsTransitionManager` -- handles inter-level movement triggers
   - `BackroomsPlayerState` -- tracks: return location, current level, collected items, time per level, found changelogs
   - `BackroomsAmbianceManager` -- base class for per-level ambient effects
   - Per-level ambiance subclasses (Herobrine stalker, footstep pursuer, gravity anomalies, etc.)
   - `Level1ChunkGenerator` through `Level7ChunkGenerator`
   - `BackroomsExitManager` -- handles the reactor puzzle and exit logic

5. **`PluginTemplatePlugin`** (PluginTemplatePlugin.java)
   - Wire up all new managers, listeners, transition system

### Implementation Order
1. Refactor BackroomsManager for multi-world support + BackroomsPlayerState
2. Implement Level 0 refinements (furniture, ambiance, armor stand entities)
3. Implement entry methods (BackroomsEntryListener)
4. Build levels one at a time: L1 -> L2 -> L3 -> L4 -> L5 -> L6 -> L7
5. Implement transition triggers between levels
6. Implement the exit puzzle
7. Add chat corruption, HUD, death handling

### Verification
- Start dev server with `make server-start`, test each entry method
- Walk through each level, verify generation and atmosphere
- Test full puzzle path: enter -> traverse all levels -> collect materials -> build reactor -> exit
- Test death/respawn behavior, chat isolation, inventory persistence
- Test `/backrooms leave` admin escape from each level
