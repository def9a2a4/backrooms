# The Backrooms — Minecraft Creepypasta Edition

## Context

Expand the existing single-level Backrooms plugin into a multi-dimension horror experience. The tone is **not** faithful Backrooms — it's Minecraft-native creepypasta. Players should feel like they've accidentally broken through the floor of the game itself, uncovering leftover debug code, Herobrine's hiding places, and the ruins of deprecated world generation. Think: "what if the Far Lands were a place you could get trapped in, and something lived there?"

**Technical constraints:**
- No resource pack — server-side only
- Use **display entities** and **custom player heads** for visual effects (any cube texture is fair game via player heads)
- Herobrine = armor stand + white-eyed Steve player head
- Entry triggers **configurable per-world** via config.yml

---

## How Players Enter the Backrooms

No `/backrooms` command for normal entry. Players should stumble in *accidentally*, feeling like a genuine glitch. The command stays for admin/testing only. All triggers are configurable per-world in `config.yml`.

**Trigger: Suffocation Noclip**
When a player takes suffocation damage (pushed into a block, caught in a piston, etc.), there's a small configurable chance (~2%) they "noclip through reality." Screen goes black (blindness effect), fake crash message appears in chat (`Internal server error: java.lang.NullPointerException at WorldGenLayer.class`), then they wake up in Level 0.

**Trigger: Void Fallthrough**
Falling into the void below Y=-64 — instead of dying, the player is caught and deposited into the Backrooms. Chat displays: `[Server] Warning: Entity position out of bounds. Relocating...`

**Trigger: Bed Anomaly**
Sleeping during a thunderstorm has a rare chance of triggering entry. The player "wakes up" but the world around them is Level 0. Message: `You wake up. Something is wrong.`

**Trigger: The Wrong Portal**
Walking through a Nether portal occasionally produces a "mislink" — the portal frame on the other side is made of crying obsidian and the player is in the Backrooms instead of the Nether. The portal behind them doesn't work.

---

## The Levels — Pick Which to Build

> **Choose which levels you want.** Level 0 and the final level (Error 422) are the bookends. Everything in between is mix-and-match. The ordering/numbering will adjust to whatever you pick.

---

### Level 0 — "The Lobby" *(already built, enhance it)*

The classic. Endless yellow terracotta rooms with froglight ceilings. But now with Minecraft flavor:
- Scattered **crafting tables** and **furnaces** that contain nothing. Signs reading `null` or `// placeholder - replace with actual content`
- Occasional **books on the ground** (item display entities) with entries like `Day 14. The rooms keep going. Found a crafting table but my recipes don't work here. The wood isn't real wood.`
- Fake chat messages appear periodically: `<Steve> hello?`, `<Steve> is anyone there`, then later `Steve left the game` — but there is no Steve
- **No hostile mobs**, but players hear **cave ambient sounds** constantly, and occasional footsteps that don't belong to anyone

**Transition down:** Find a spot where the floor is "missing" — a 1x1 hole of air through the terracotta, with darkness below. Fall through.

---

### CANDIDATE: "The Far Lands"

The terrain generation is *wrong*. Custom generator mimicking the real Far Lands bug:
- **Walls of stone stretched impossibly tall** — columns of stone, dirt, and gravel reaching to sky limit in narrow, warped pillars
- **Floating water** and **lava source blocks** in mid-air, not flowing correctly
- **Grass blocks underground**, ores exposed on surfaces, gravel floating unsupported
- The **sky is permanently stuck at sunset** — that orange-pink horizon that never moves (time frozen at 12500)
- Uses only blocks available in **Beta 1.7.3** (the last version with Far Lands)
- **No hunger mechanic** — players have permanent saturation (like old Minecraft)
- Occasional **2x1 tunnels** bored perfectly through the terrain — Herobrine's tunnels. They lead nowhere, or they lead deeper.
- **Fog distance reduced.** Particles of falling blocks drift through the air.

**Transition down:** At the bottom of the deepest tunnel, find a ladder descending into a 1x1 shaft made of iron blocks.

---

### CANDIDATE: "The Server Room"

An infinite grid of rooms built from **iron blocks**, **redstone lamps**, and **observer blocks**. The observers are all facing inward — watching.

- **Pistons** fire randomly in the walls — clunking, extending, retracting for no reason
- **Redstone dust** traces lines across the floor spelling fragments of code: `if (entity != null)`, `// FIXME`, `k += seed;`
- **Command blocks** visible in the walls (normally unobtainable), with signs showing commands: `kill @e[type=Herobrine]`, `gamerule showDeathMessages false`, `// this should never execute`
- **Repeaters** and **comparators** clicking endlessly, powering nothing
- **Chests** containing items with corrupt names: a diamond sword named `§k||||||||`, bread named `hunger_placeholder_DO_NOT_SHIP`, a compass named `where`
- **Redstone lamps flicker** unpredictably

**Hostile element:** Redstone Golems — invisible armor stands with damage aura, detectable only by redstone dust lighting up near them. Touch = hotbar items scramble randomly.

**Transition down:** Find a command block with a sign reading `tp @s 0 0 0 //debug_house`. Right-click it.

---

### CANDIDATE: "Herobrine's House"

A single massive structure floating in void. Cobblestone walls, oak planks, glass panes. It looks like a poorly built player house from 2011 — except it's *enormous* and impossibly complex inside.

- **Furnaces** always smelting, but input/output contain barrier blocks renamed to `null`
- **Torches** periodically swap to **redstone torches** and back — light flickers between warm and red
- **Signs** everywhere: `I am still here.`, `They said they removed me.`, `Check the changelog.`, `Why can't you see me?`, `I built this for you.`
- **Paintings** — all the same one (the Wither painting), repeated everywhere
- **2x1 tunnels** branch from every room into the void, perfectly carved, torch-lit, going nowhere
- **Sand pyramids** in random rooms — Herobrine's calling card
- A **bedroom** with an unusable bed: `You cannot rest now, something is watching you.`

**The entity:** Herobrine appears here. An **armor stand** with a custom Steve-white-eyes player head. Appears at the edge of render distance, in doorways, at corridor ends. When you look directly at it — gone. He never attacks. He just *was there*.

**Transition down:** In the deepest basement, a **jukebox** with **Disc 11**. Play it. The floor opens.

---

### CANDIDATE: "The Ancient Version"

Looks like Minecraft **Alpha**. Approximation of old terrain generation:
- **Blocky, simple terrain** — extreme hills and flat plains, nothing in between
- Only Alpha-era blocks: no birch, no jungle, no concrete, no deepslate
- **Red tulips** standing in for roses, **blue orchids** for cyan flowers
- **Sky frozen at noon**, bright blue, no clouds
- **Mob spawning ON but broken** — zombies in mismatched armor dropping wrong items, skeletons shooting slowly, spiders always hostile regardless of light
- **Signs** with old splash text: `Also try Terraria!`, `Woo, /v/!`, `Removed Herobrine`
- **Chests** in hillsides with "legendary" items: `Notch's Apple` (enchanted golden apple), a bow named `Infinity wasn't always an enchantment`, a map showing nothing

**Unsettling:** Periodic chat displays old-style death messages for players who aren't online: `Steve tried to swim in lava`, `Herobrine fell from a high place`, `[Server] Herobrine has been removed from the whitelist.`

**Transition down:** A hand-built **mini stronghold** generated into terrain. End portal activates but leads to the next level, not the End.

---

### CANDIDATE: "The Void Between"

Nearly empty. Player arrives on a small platform of **end stone**. Void in every direction.

- **Floating islands** of end stone, obsidian, and bedrock — some bridgeable, most not
- **Endermen** on every island. They **don't teleport, don't move**. They stand still and **stare at the player** regardless of eye contact. Their eyes track you. They are *wrong*.
- **End portal frames** with eyes of ender on some islands, assembled into complete portals — they don't work. Interaction yields: `This exit has been deprecated.`
- **The Ender Dragon death animation** plays on loop in the far distance — dying forever, XP orbs frozen in air (display entities)
- **Bedrock** scattered through the void at random heights like debris from a broken world floor
- Complete **silence** except for Enderman ambient sound

**The horror:** Strips away all pretense. No fake rooms, no constructed space. Just the raw engine underneath the game, and things that live in it.

**Transition down:** On the furthest island, a **minecart on a rail** leading into a tunnel through a floating bedrock mass.

---

### CANDIDATE: "The Infinite Mineshaft"

An endless mineshaft, scaled up and stretched wrong:
- **Corridors 4 blocks wide** instead of 3 — just enough to feel *off*
- **Rail tracks** everywhere — loops, dead ends at walls, tracks going straight up
- **Minecarts with chests** containing "dev items": `Sharpness X` weapons, potions named `test_potion_DO_NOT_DISTRIBUTE`, spawn eggs labeled `entity_null`
- **Cave spider spawners** that are "broken" — spinning models but spawn **silverfish** or bats instead
- **Torches that emit no light** — placed on walls, area remains dark (use buttons/levers that look like torches from a distance, or place torches then overlay with light-blocking)
- Sound of **pickaxes mining** echoing from somewhere you can never find
- **Cobwebs** filling entire corridors
- **Water** flowing toward the player regardless of slope

**Hostile element:** Invisible cave spiders (only detectable by poison effect). Deeper in: **Wardens** wearing Herobrine player heads via display entities — massive corrupted Steves.

**Transition down:** Rails converge on one track leading down into darkness.

---

### CANDIDATE: "The Poolrooms"

Vast, open chambers made of **light blue terracotta** and **prismarine** tiles. Still, glowing water fills most rooms knee-deep. It's the *only level that feels safe* — and that's what makes it unsettling.

- **Water** everywhere — shallow, warm (players don't take cold damage), perfectly still
- **Sea lanterns** embedded in walls and floors provide soft, even lighting
- **No mobs at all.** No sounds except water. No ambient cave sounds. Just silence and gentle light.
- **Waterslide tunnels** of packed ice and blue glass leading between rooms
- Occasional **signs** that break the calm: `You can rest here. They can't get in.`, `Don't trust the water.`, `Level 37. Safe. Safe. Safe.`
- The water is **slightly too deep** in some rooms. Just enough that you can't see the floor. Something *might* be down there. (Nothing is. Probably.)
- **Drowned** that are frozen mid-stroke in the deeper pools — not moving, not attacking, just... suspended

**The twist:** This is a rest area. Players regenerate health and hunger here. But staying too long (>10 minutes) starts spawning **Guardians** in the deep water — the safe room is expiring.

**Transition down:** Dive into the deepest pool. At the bottom, a 1x1 hole in the prismarine floor.

---

### FINAL LEVEL: "Error 422"

Everything is broken. The world generator has **lost its mind**:
- **Every biome's blocks mixed together** — netherrack next to grass next to end stone next to coral next to ice
- **Bedrock** at random heights, **barrier blocks** (invisible walls) in random places
- **Signs** with raw unicode: `§k§l§o§r`, `ÿÿÿÿÿÿÿ`, `NaN`, `undefined`
- **Mobs spawn wrong:** charged creepers with no AI, baby zombies riding chickens riding spiders, iron golems that attack nothing, nitwit villagers walking into walls
- **Sky cycles through every weather state** rapidly — sun, rain, thunder, night, all in seconds
- **Gravity broken** — sand and gravel float, water doesn't flow, lava moves like water
- **Floor shifts** — blocks change material randomly at the edges of vision (scheduled block updates)
- **Chat flooded** with fake errors: `[WARN] Chunk data corrupted at region r.0.0.mca`, `[ERROR] Entity tick overflow`, `[FATAL] WorldGen seed mismatch — expected: 0, got: ∞`

**The "boss room":** In the center of chaos, a single calm room. Smooth stone floor, four torches, a chest. Inside: a book titled `README_FINAL.txt`:

> *We found something in the code. Not a bug. Something that was always there, underneath the terrain generator, underneath the lighting engine, underneath the tick loop. We don't know what it is. We don't know how long it's been there. The changelogs say "Removed Herobrine" but we never added Herobrine. We never wrote that entry.*
>
> *If you're reading this, you went too deep. The exit is below. It was always below.*
>
> *— Last commit by UNKNOWN, date: January 1, 1970*

Below the room: a **bedrock floor with one block missing**. Fall through.

---

## How to Get Out

**The Hard Way (intended path):** Descend through all levels. Read the final book. Fall through the bedrock hole in Error 422. Wake up in the Overworld. Inventory intact. A book in inventory: `You were gone for 0 ticks.`

**The Desperate Way:** Die in the Backrooms. Respawn in Level 0. Items gone. Start over. Death is not an exit.

**The Secret Way:** Each level has one **hidden sign** with a single digit. Collect all digits (write them down). In Level 0, find a **hidden jukebox**. Place **Disc 13** and type the code in chat. Portal appears. Speedrun exit.

**Admin exit:** `/backrooms leave` for ops.

---

## Global Atmosphere Systems (All Levels)

### Fake System Messages
Periodically display to players in the Backrooms:
- `Herobrine joined the game` / `Herobrine left the game`
- `[Server] Removed Herobrine`
- `Saving chunks...` (never finishes)
- `[WARN] Player position desynchronized`
- `<playername> tried to leave` (using the actual player's name)

### Corruption Over Time
- **Minutes 0-5:** Normal. Occasional ambient sounds.
- **Minutes 5-15:** Fake messages begin. Compass spins. Maps go blank.
- **Minutes 15-30:** Inventory item names glitch briefly. Hotbar flickers.
- **Minutes 30+:** Blindness flashes. Chat messages "from yourself" you didn't type. Armor stand spawning behind you, despawning when you turn around.

### Sound Design (server-side)
- **Cave ambient sounds** at 3x frequency
- **Note blocks** in walls playing discordant notes
- **Sculk sensor** activation sounds when no sculk exists (something is listening)
- Occasional **anvil/thunder** from nowhere

---

## Architecture & Key Files

### New files:
- `BackroomsLevel.java` — enum defining level metadata (ID, name, generator class)
- One `ChunkGenerator` subclass per level (e.g., `Level0Generator.java`, `FarLandsGenerator.java`, etc.)
- `BackroomsListener.java` — event listener for entry triggers (EntityDamageEvent for suffocation, PlayerMoveEvent for void, PlayerBedEnterEvent, PlayerPortalEvent)
- `AtmosphereManager.java` — scheduled tasks for fake messages, corruption effects, sounds
- `HerobrineManager.java` — handles Herobrine armor stand spawning/despawning logic
- `TransitionManager.java` — detects when player reaches level exit, moves them to next level's world

### Existing files to modify:
- `BackroomsManager.java` — manage multiple worlds (one per level)
- `BackroomsCommand.java` — add `/backrooms goto <level>`, `/backrooms leave`, `/backrooms reset`
- `BackroomsChunkGenerator.java` — rename to `Level0Generator.java`
- `plugin.yml` — register listeners, update commands

### Config structure:
```yaml
entry:
  enabled-worlds:
    - world
    - world_nether
  suffocation-chance: 0.02
  void-enabled: true
  bed-storm-chance: 0.05
  portal-mislink-chance: 0.01
atmosphere:
  fake-messages: true
  corruption: true
  herobrine-sightings: true
levels:
  # List of enabled levels in order — adjust to match your picks
  - lobby
  - far-lands
  - server-room
  - herobrine-house
  - ancient-version
  - void-between
  - infinite-mineshaft
  - poolrooms
  - error-422
```

## Verification
- `make build` — compile
- `make server` — deploy to test server
- Test each entry trigger manually in enabled worlds
- Walk through each level, verify generation and transitions
- Verify corruption effects scale over time
- Test death/respawn (should respawn in Level 0, not Overworld)
- Test full escape path through all levels
- Test `/backrooms leave` admin exit
