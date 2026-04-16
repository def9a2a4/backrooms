# ~~The Backrooms~~

> *If you're not careful and you noclip out of reality in the wrong areas, you'll end up in the Backrooms*

I set off some AI agents to explore how to write level generation plugins. After a few days, I came back to this. They did things I didn't ask for. There's levels and levels, and some of the levels have... things in them.

---

## The Levels

You will descend through **13 procedurally generated floors**. Each one is worse than the last. Each one has its own rules. Some of them have exits. Some of them lie about having exits.

| Level | Name | You will find... |
|------:|------|-----------------|
| 0 | **The Lobby** | Yellow walls. Wet carpet. Fluorescent buzz. The mannequins are closer than they were a second ago. |
| 1 | **The Habitable Zone** | Warehouses. Corridors. Somewhere, a garden grows where no garden should. The water drips upward. |
| 2 | **The Pipe Works** | Metal. Steam. Something is stalking you between the pipes. |
| 3 | **The Server Room** | Command blocks hum with corrupted output. Four terminals. Four exits. Not all of them go where they say. |
| 4 | **The Far Lands** | Coordinates overflow. An Enderman stands perfectly still and watches. It never teleports. That's what's wrong with it. |
| 5 | **Disc 11** | You can hear breathing. Do not touch the jukebox. *Do not touch the jukebox.* |
| 7 | **The Corrupted Chunk** | Reality is rotting. Blocks decay under your feet. A 27-block-tall figure watches from the fog. Where is Level 6? |
| 37 | **The Poolrooms** | Warm water. White tile. Silence. It is beautiful here. *You should be afraid of how beautiful it is.* |
| 64637 | **The Library** | Infinite shelves. 2^64637 entries. One of the books has your name in it. |
| 84 | **The Hedge Maze** | Leaves. Obsidian foundations. If you climb the walls, the walls will hurt you. |
| 94 | **Skyblock** | A platform in the void. Safe, for now. The barriers are not as solid as they look. |

> *There are gaps in the numbering. We don't talk about the gaps.*

---

## They Are In There With You

The Backrooms are not empty. They were never empty.

- **Mannequins** — Player-shaped figures that freeze when you look at them. They are always closer when you look back. Some of them attack. Some of them just... follow.
- **Floating Heads** — Suspended in the air. Watching. Always watching.
- **Herobrine** — Approaches when you aren't looking. Flees when you are. Deals damage when close. You know who this is.
- **The Pursuer** — You will never see it. You will hear footsteps behind you. If you stop moving, it gets closer.
- **The Wrong Enderman** — It doesn't teleport. It doesn't move. It stares at you regardless of eye contact. It makes you *glow*.

Entities spawn more frequently the longer you stay. The Backrooms remember how long you've been inside.

**Escalation thresholds:** 5 min / 15 min / 30 min / 1 hr / 2 hr

After two hours, they stop pretending to be patient.

---

## Things That Will Happen To You

The environment is hostile. Not always violently — sometimes it just wants you to doubt yourself.

- **Blackouts** — All lights in a 30-block radius go dark for 15 seconds.
- **Light Flicker** — The ceiling lights strobe. Four cycles. Then silence.
- **Wall Shifts** — Blocks appear behind you that weren't there before.
- **Footstep Echoes** — You hear your own footsteps, offset by a few blocks and a few ticks. Or are those yours?
- **Fake Chat Messages** — `<Steve> hello?` / `<Unknown> don't look behind you` / `[FATAL] Thread 'Reality' stopped responding`
- **Sign Placements** — Creepy messages materialize on the walls when you aren't looking. `STOP` / `behind you` / `the carpet remembers`
- **Torch Decay** — Your torches go out. The darkness doesn't ask permission.
- **Block Corruption** — 4x4x4 sections of the world rot into netherrack, mycelium, magma.
- **Inventory Glitch** — Your items rearrange themselves. They go back after two seconds. Probably.
- **Player Drift** — Your coordinates shift. The floor is where you left it. You are not.
- **Fake Coordinates** — The F3 screen reads `12,550,821 / -8,371,022`. It drifts. Nothing is where it says it is.

---

## How You Get In

You don't choose to enter the Backrooms. The Backrooms choose you.

| Method | What happens |
|--------|-------------|
| **Suffocation** | Noclip through a solid block. Error message: `java.lang.NullPointerException` |
| **Void Fall** | Fall below the world. Warning: `Entity position out of bounds. Relocating...` |
| **Bed Anomaly** | Sleep during a thunderstorm. 5% chance you don't wake up in the right place. |
| **Herobrine Shrine** | Gold base. Netherrack. Redstone torches. Flint and steel. You know the ritual. |
| **Aether Portal** | Glowstone frame. Water. The light engulfs you. There is no Aether. |
| **Twilight Portal** | Another broken promise of a gentler dimension. |

---

## How You Get Out

Each level has exit conditions. Some require exploration. Some require collection. Some require you to fall.

- Collect **written books** scattered through the shelves (Level 0, Level 64637)
- Submerge below certain Y-levels
- Walk far enough that the floor gives up pretending
- Find the command block terminals and hope they route you somewhere real
- Fall. Keep falling. Eventually the Backrooms let go.

Or they don't.

An **advancement tree** tracks your descent. Discovery. Entry method. Exit hints. There is an achievement for escaping. There is an achievement for visiting every level.

Nobody has earned both.

---

## Installation

### Requirements
- **Paper 1.21+** (tested on 1.21.1)
- **Java 21**

### Setup
1. Place the plugin jar in your server's `plugins/` directory.
2. Start the server. The worlds will generate themselves.
3. There is no step 3. The Backrooms are already running.

### Build from source
```bash
make build
```

---

## Commands

**Base command:** `/backrooms`

| Command | Permission | Description |
|---------|-----------|-------------|
| `/backrooms` | `backrooms.use` | Enter Level 0. |
| `/backrooms leave` | `backrooms.use` | Attempt to return to the overworld. |
| `/backrooms status` | `backrooms.use` | Current level, time inside, escalation tier, levels visited. |
| `/backrooms list` | `backrooms.use` | List all known levels. |
| `/backrooms goto <level>` | `backrooms.admin` | Teleport to a level. |
| `/backrooms enter <trigger>` | `backrooms.use` | Trigger a specific entry method. |
| `/backrooms event <id>` | `backrooms.admin` | Manually trigger an event. |
| `/backrooms spawn <entity>` | `backrooms.admin` | Spawn an entity. |
| `/backrooms despawn` | `backrooms.admin` | Remove nearby entities. |
| `/backrooms escalation <n>` | `backrooms.admin` | Set escalation level. |
| `/backrooms reset` | `backrooms.admin` | Reset player state. |
| `/backrooms regenerate` | `backrooms.regenerate` | Regenerate level terrain. |
| `/backrooms advance <path>` | `backrooms.admin` | Grant advancements. |

---

## Configuration

All levels are defined in `plugins/Backrooms/levels/`. Each YAML file controls:
- Terrain generator and dimension type
- Entity spawns, behaviors, skins, and escalation requirements
- Event types, intervals, chances, and messages
- Exit triggers and transition effects
- Lore books, sign text, and fake chat messages

The main `config.yml` controls entry triggers, entity spawner settings, escalation thresholds, and death behavior.

Everything is configurable. The dread is not.

---

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `backrooms.use` | Everyone | Enter, leave, and interact with the Backrooms |
| `backrooms.admin` | OP | Administrative commands |
| `backrooms.reload` | OP | Reload configuration |
| `backrooms.regenerate` | OP | Regenerate world terrain |

---

<br>

```
[Server] Backup completed. Size: ∞ bytes
[ERR] Permission node 'exit.allowed' not found
```

<br>

> *The fluorescent lights buzz overhead in a pitch you can almost name.*
>
> *You've been reading this file for what feels like hours but the scroll bar says you're near the bottom.*
>
> *You are near the bottom.*
>
> *Close the file. Start the server.*
>
> *The carpet is already damp.*

<br>

---

<sub>If you've found this file, it's already too late. The Backrooms are patient. They have always been patient. They were here before the server started and they will be here after it stops. The lights will keep humming. The mannequins will keep waiting. The library will keep writing your name.</sub>

<sub>You should have read the signs.</sub>
