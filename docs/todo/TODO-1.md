# Backrooms Plugin: Multi-Level Design

## Context

The plugin currently has a single "Level 0" — yellow terracotta corridors with froglight ceilings and basic ambiance. We want to expand it into a multi-dimensional horror experience that feels like **you accidentally broke Minecraft** — uncovering Herobrine's traces, Far Lands corruption, deleted dimensions, and old code that was never meant to be found.

Each level is a separate Paper world with its own `ChunkGenerator`. The creative direction is Minecraft fan lore horror, not faithful Backrooms recreation.

---

## How Players Enter

All entry methods feel like accidental glitches, not intentional portals.

### A. Suffocation Glitch (Primary)
Player takes suffocation damage inside a solid block. On the 2nd-3rd tick of continuous suffocation, screen distorts (blindness + nausea + low-pitch glass break sound), then teleport to Level 0. Mirrors the original "noclip" concept.

### B. Bed Failure
Player sleeps in a bed during a thunderstorm at Y=62-63 (old sea level). Instead of sleeping: 3-second blackout, disc_11 plays at half pitch, bed vanishes, player teleported.

### C. Void Fall with Totem
Player falls into the void while holding a Totem of Undying. Instead of the totem saving them normally, something "catches" them beneath the void. Totem consumed, heavy nausea, teleport.

### D. Chunk Border Phasing (Rare)
Sprint across a chunk border while at 1 heart and sneaking. 2% chance (higher on new moon). Brief freeze, then teleport. References old chunk boundary bugs.

### E. "Removed Herobrine" Easter Egg
Place a sign reading "removed herobrine". Text changes to "HE NEVER LEFT" in dark red, nearby lights extinguish, player teleported.

### Shared Entry Sequence
All methods: store return location -> DARKNESS effect 2 seconds -> fake crash log in chat (`[0x4F2] POSITION FAULT @ chunk 14,-7`) -> teleport to Level 0 -> fluorescent hum begins.

---

## The Levels

### Level 0: `room_generate.dat` (Existing, Enhanced)
**Blocks:** Yellow terracotta, ochre froglight grid, yellow carpet patches, dead bush flower pots, water puddles with dripstone above, orange terracotta "peeling wallpaper"
**Feeling:** Liminal office space. Fluorescent buzz. Infinite identical corridors.
**Mechanics:**
- Footstep echo delay (second footstep sound plays 5-10 ticks later from a nearby offset — sounds like someone else walking)
- Wall shift: walls silently rearrange behind the player when they're not looking
- Stacking hunger effect the longer they stay
**Exit to Level 1:** Trap floors — `YELLOW_GLAZED_TERRACOTTA` (subtly different texture) that collapses in a 3x3 cascade when stood on for 3 seconds. Player falls through to Level 1.

### Level 1: `terrain_null`
**Blocks:** Smooth stone + gravel floor, deepslate ceiling at Y=50, soul lanterns on chains, massive deepslate brick pillars (4x4, some broken with pointed dripstone), shallow water pools over soul sand
**Feeling:** Industrial basement. Concrete parking garage at 3 AM. The scaffolding beneath reality.
**Mechanics:**
- "Concrete Wanderers" — armor stands with deepslate block heads. Don't move, but their heads rotate to face nearest player every 30s. Vanish when approached within 5 blocks (warden vibration sound).
- Water pools apply WITHER if stood in for 10+ seconds — looks normal, is not
- Abnormally loud footsteps (volume 3.0) that "attract attention"
**Exit to Level 2:** Rare impossible staircase — dark oak stairs descending into solid stone. Walking into the bottom stair triggers transition with scrolling error messages: `"null"`, `"NaN"`, `"java.lang.StackOverflowError"`.

### Level 2: `err_redstone_overflow`
**Blocks:** Stone bricks + cracked stone bricks (2-wide, 3-tall corridors), powered redstone wire everywhere, repeaters/comparators facing random directions, command blocks in walls, redstone lamps (flickering), observer blocks facing the corridors, polished blackstone floor
**Feeling:** Inside Minecraft's nervous system. The code is malfunctioning. The observers are watching you. Click-click-click of repeaters constantly.
**Mechanics:**
- Tripwire traps that seal the corridor behind you (forces forward progress)
- Random "command execution" every 60s: inventory shuffle, random teleport 1-3 blocks, yaw snap, or chat message `/kill @a` (nothing happens... this time)
- Observers apply GLOWING for 2s when walked past — you've been "detected"
**Exit to Level 3:** Exposed command block with a fake GUI (chest with barrier items). Click the `STRUCTURE_VOID` named `">> EXECUTE"` to trigger transition.

### Level 3: `ocean_monument_003 [DEPRECATED]`
**Blocks:** Prismarine, dark prismarine, sea lanterns (flickering), dried kelp blocks, dead coral, dry sponge. Ocean monument architecture tiled infinitely, but completely dry.
**Feeling:** A drained, abandoned ocean monument. The Guardians are gone. Something worse replaced them. A deleted biome Mojang tried to erase.
**Mechanics:**
- Phantom Guardians — invisible Guardians that fire visible targeting lasers from nothing. Half damage.
- Constant `AMBIENT_UNDERWATER_LOOP` at low volume despite no water
- Conduits with no prismarine frame: give 5s of CONDUIT_POWER then 30s of MINING_FATIGUE (Elder Guardian curse from nothing)
**Exit to Level 4:** Air pockets where player's oxygen bar depletes despite being in air. At zero, teleported instead of drowning.

### Level 4: `dim-1.bak` (The Deleted Nether)
**Blocks:** Netherrack, nether bricks, soul sand, basalt columns, empty magma block lava basins, unlit nether portal frames everywhere, soul torches, wither skull + soul sand T-patterns, exposed ancient debris
**Feeling:** The Nether with everything living deleted. Dead silence. An archived backup of a wiped dimension. Portal frames mock you — the exits are right there but don't work.
**Mechanics:**
- Complete silence. No ambient sounds. Only footsteps and magma hiss.
- Ghost fires: soul fire appears briefly on soul sand near player. Standing in it = no damage but SLOW_FALLING + disc_13 plays
- Placing a 4th wither skull: skulls break, chat says `"NOT HERE. NOT ANYMORE."`
- Steve player heads appear on the ground facing the player
**Exit to Level 5:** A sign by an unlit portal reads `"Type /home to return"`. Typing `/home` (intercepted via `PlayerCommandPreprocessEvent`) ignites the portal briefly and teleports to Level 5.

### Level 5: `farlands.jar`
**Blocks:** Grass, dirt, stone, oak trees — normal overworld materials, but generated WRONG. Massive vertical terrain walls, swiss-cheese holes, sideways trees (horizontal trunks), flowers on vertical surfaces, exposed cobblestone veins. Permanent sunset.
**Feeling:** Beta 1.7 Far Lands. Nostalgic and deeply wrong. Beautiful in a broken way.
**Mechanics:**
- **Herobrine Sightings:** Fake player entity (Steve with white eyes) at render distance edge. Stands 5 seconds, vanishes. Where it stood: a 2x2 hole with a torch at the bottom.
- Gravity glitches: random LEVITATION level 1 for 3 seconds
- Old C418 melody recreated note-by-note via note blocks from no visible source
- Terrain loops at 500 blocks (teleport back seamlessly)
**Exit to Level 6:** Walk backward (holding S) for 100 blocks continuously. Corridor subtly darkens, walls narrow, floor changes from smooth stone to crying obsidian as you progress.

### Level 6: `null_pointer`
**Blocks:** Flat infinite `WHITE_CONCRETE` plane. Black void sky. Scattered lone objects: a crafting table, a furnace, an enchanting table, each isolated. Glass block outlines on the floor (room floor plans that no longer exist). Cobwebs in mid-air. Rare jukeboxes playing disc_11.
**Feeling:** The end of the game's memory. The null texture. Leftover objects not properly garbage collected. Lonely in the most profound way.
**Mechanics:**
- The Mimic: entity that looks exactly like the player, spawns 50 blocks away, walks toward them silently. If it reaches them: white flash, player's chat name changes to one-letter-off for 30 seconds.
- Scattered objects are wrong: crafting table shows barrier items, furnace is smelting bedrock into air, enchanting table shows reversed text
- No natural health regen. Food restores half.
- Fake server console messages: `"[Server] WARN: Entity Player[Name] exists in unloaded chunk"`
**Exit to Level 7:** Die. Any death. Instead of respawn screen, instant teleport to Level 7 at full health. Death message says `"[Player] fell out of the world"` regardless of cause.

### Level 7: `world.dat.old` (The Exit)
**Blocks:** Grass hills, oak trees, sugar cane, water pond, sand beaches, exposed coal/iron ore. A small 100x100 island surrounded by void. Perpetual bright day.
**Feeling:** Overwhelming relief — then unease. It's TOO perfect. After 6 levels of horror, green grass feels like a trap. The void around the island reminds you this is a memory of the real world, not the real world.
**Mechanics:**
- Passive mobs spawn (cows, pigs, sheep, chickens). Hearing animal sounds after the silence is emotional.
- Breaking any block: sky turns red for 1 second, block regenerates, sign appears: `"DON'T."`
- Falling off the island sends you back to Level 6 (must die again to return)
- **The Door:** A free-standing oak door on a hilltop. Opening it plays `MUSIC_DISC_WAIT`, 10s blindness, teleport to overworld at original entry location. A book appears in inventory: `"changelog_final.txt"` — single page: `"- Removed Herobrine"`.

---

## Cross-Level Systems

### Herobrine ("HIM")
Not a mob. A stateless event system that runs across all levels, escalating with time spent in the backrooms:
- **Redstone torches** placed on walls the player already passed (when not looking)
- **2x2 tunnels** carved dynamically into walls when player is far enough away, torch at dead end
- **Signs** that go blank or change text
- **The Figure** — distant NPC at render edge, any level. Faces player. Vanishes if looked at directly. Moves closer if ignored. Frequency: 1% at 0-30 min, 15% after 2 hours
- **Chat whispers:** `"<Herobrine> "` (empty) or `"<Herobrine> watching"`
- After 3+ hours: gold blocks appear in walls

### Entity 303 (Chat Ghost)
Manifests only through text:
- Private messages: `"[303 -> You] can you see me"`, `"[303 -> You] the exit is a lie"`
- Fake join/leave: `"Entity303 joined the game"` then `"Entity303 left the game"` 5 seconds later
- In Level 2: triggers fake command execution chat + real potion effects

### Null (The Anti-Player)
Black silhouette entity (armor stand, full black leather armor, all-black player head). Roams Levels 2-6:
- Walks toward light sources and extinguishes them
- Herds player into darkness. 15s in complete darkness = 2 hearts/sec damage
- Banished by placing a torch within 3 blocks (enderman scream, dissolves, respawns 5 min later)

### Audio Entities (Disc 11 / Disc 13)
Jukeboxes in dead-end rooms, already playing:
- **Disc 11 playing:** distant block placement sounds, subtle camera shake, nearest light extinguishes permanently when track ends
- **Disc 13 playing:** player gains NIGHT_VISION but lighting inverts (froglights → black concrete, dark blocks → glowstone). Reverts when track ends.

---

## Red Herrings

- **False Door** (Levels 3, 6): Iron door labeled "EXIT" leads to a room that loops. After 3 loops, torch turns red, sign changes to `"HE SEES YOU"`, teleported back.
- **The Bed Trick** (Levels 0, 2): Sleeping in a backrooms bed gives black screen, then you wake up in the same spot but walls have shifted and light color changed. Up to 3 times before bed vanishes.
- **The Chat Lure** (Any level, rare): Message from a player named `"Steve"` or your own name: `"I found the exit. Follow me."` Fake NPC appears at distance, walks into wall, vanishes. Dead end.

---

## Escape Routes

### Route A: The Door (True Ending, Level 7)
Described above. Complete all levels, find the door. Get the `"changelog_final.txt"` book.

### Route B: Debug Escape (Medium, Levels 5-6)
Rare `STRUCTURE_BLOCK` blocks in walls across all levels. Each shows a fake debug menu (book GUI). Find and interact with 3 across any levels → receive `RECOVERY_COMPASS` named `"core_dump.exe"`. Right-click = 30-second channel (must stand still, scrolling console text), teleported to overworld spawn. Lose all backrooms items.

### Route C: Break the Walls (Hard, Level 1 only)
One `YELLOW_CONCRETE` block per 512x512 region (looks almost identical to terracotta but breakable). Behind it: 50-block narrowing tunnel ending in a barrier gap. Walk through → teleported to overworld at Y=320, falling. Survive the fall = free.

---

## Architecture Changes

### Multi-World Level System
- Refactor `BackroomsManager` → level registry (`Map<String, BackroomsLevel>`)
- Each level = separate world + own `ChunkGenerator` subclass
- New `BackroomsLevel` interface: `getId()`, `getChunkGenerator()`, `onPlayerEnter()`, `tick()`

### Player State
- `BackroomsPlayerState`: entry time, return location, levels visited, structure blocks found, herobrine escalation
- Persist to PDC on disconnect

### Event Routing
- Central `BackroomsEventManager` routes events to correct level handler by checking player's world
- Cross-level systems (Herobrine, Entity 303, Null) run independently

### Key Files to Modify
- `BackroomsManager.java` — refactor to multi-world registry
- `BackroomsChunkGenerator.java` — becomes Level 0 generator; template for others
- `BackroomsAmbianceManager.java` — generalize into per-level event system
- `PluginTemplatePlugin.java` — wire up new systems

### New Files Needed
- 7 new `ChunkGenerator` subclasses (one per new level)
- `BackroomsLevel` interface + 8 level implementations
- `BackroomsEntryHandler` — entry detection listeners
- `BackroomsPlayerState` — per-player state tracking
- `HerobrineSystem` — cross-level presence events
- `EntitySystem` — Null, Entity 303, Mimic, Phantom Guardians
- `TransitionHandler` — level-to-level transition logic

## Verification
- Build with `make build`, deploy to test server with `make server`
- Test each entry method triggers correctly
- Walk through all 8 levels verifying generation, mechanics, and transitions
- Verify Herobrine escalation over time
- Test all 3 escape routes
- Test persistence across disconnect/reconnect
