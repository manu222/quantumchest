# QuantumChest

A Minecraft mod with one chest for everything. Any item, any amount, in a single block, with tabs and a search bar. Capacity isn't free: you upgrade the chest tier by tier until it holds an unlimited amount, and you fill its upgrade slots with things like a magnet, a compactor or a crafting grid.

Built for **Minecraft 26.2** on both **Forge** and **NeoForge** from the same codebase.

## What it does

- **One grid for every item.** Scroll, search by name, mod (`@create`) or tag (`#c:ores`), sort by amount, name or mod.
- **Tabs.** The creative tabs are there out of the box, and you can make your own from the chest screen (pick items, type tags, choose an icon).
- **Breaking the chest keeps everything.** The contents live with the world, not the block, so a broken chest drops an item that brings it all back. The item doesn't burn, and if it's lost anyway an operator can get it back with `/quantumchest give`.
- **Owner and guests.** Chests are private by default. Add friends by name or make the chest public. Guests can use the chest, but only the owner fits or takes out upgrades.
- **Input filter.** A whitelist or blacklist for whatever arrives on its own (hoppers, pipes, magnet). By hand you can always put anything in.
- **Hoppers and pipes** work with every face, like with a normal chest. Pulling items out needs the I/O upgrade.

## Tiers

| Tier | Capacity | Upgrade slots |
|---|---|---|
| Basic | 10,000 items | 1 |
| Reinforced | 100,000 | 2 |
| Advanced | 1,000,000 | 3 |
| Elite | 100,000,000 | 5 |
| Quantum | unlimited | 7 |

Capacity counts items, not item types. Mix as many different items as you like. Upgrade kits take a placed chest to the next tier without emptying it.

## Upgrades

Each upgrade with settings gets a small gear next to its slot that opens its own screen.

| Upgrade | What it does | From tier |
|---|---|---|
| Magnet | Picks up items around the chest. Range and on/off per chest | Basic |
| Destroyer | Keeps up to the amount you choose of an item or tag and deletes the rest as it arrives | Basic |
| Crafting | A 3x3 under the grid. The ingredients refill from the chest after each craft | Basic |
| Compactor | Lists everything the loaded recipes can turn into blocks (other mods too). Switch on what you want and how many to leave loose | Reinforced |
| I/O | Each face can take items in, pull from the block next to it, export a tab or eject what the chest turns away | Reinforced |
| Auto-refill | Keeps up to 12 items topped up in your inventory while you carry a linked terminal | Advanced |
| Smelting | A furnace inside the chest. Pick what to smelt and which fuels it may burn | Advanced |
| Quantum tunnel | Linked chests and the terminal work across dimensions | Elite |

And two tools that don't take a slot:

- **Quantum entangler**: click one chest, then another of the same tier, and they share their storage.
- **Superposition terminal**: opens a linked chest from far away. 64 blocks on Advanced, the whole dimension on Elite and up.

## Config

The server writes `config/quantumchest.json` the first time a world loads. It has the capacity of each tier, the magnet range, the I/O and smelting speeds and the terminal range. Players see the server's numbers in the chest screen, so a modpack can change them without touching anyone's game.

## Building

You need a JDK 17 or newer to run Gradle. The JDK 25 that Minecraft 26.2 needs is downloaded automatically.

```
./gradlew build
```

Jars end up in `forge/build/libs` and `neoforge/build/libs`.

To start the game with the mod loaded:

```
./gradlew :neoforge:runClient
./gradlew :forge:runClient
```

## Project layout

```
common/     shared code, compiled against vanilla Minecraft
forge/      Forge entry point and platform code
neoforge/   NeoForge entry point and platform code
buildSrc/   Gradle conventions shared by the subprojects
```

Loader specific code sits behind small interfaces in `common` and is looked up at runtime with `ServiceLoader`.

## License

MIT
