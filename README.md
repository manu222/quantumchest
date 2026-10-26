# QuantumChest

A Minecraft mod with one chest for everything. Any item, any amount, in a single block, with category tabs and a search bar. Capacity isn't free: you upgrade the chest tier by tier until it becomes unlimited, and fill its upgrade slots with things like a magnet, a compactor or a link to another chest.

Built for **Minecraft 26.2** on both **Forge** and **NeoForge** from the same codebase.

> Early development. Right now the chest can be placed and stores items with right click; the real interface comes next.

## Tiers

| Tier | Capacity | Upgrade slots |
|---|---|---|
| Basic | 10,000 items | 1 |
| Reinforced | 100,000 | 2 |
| Advanced | 1,000,000 | 3 |
| Elite | 100,000,000 | 5 |
| Quantum | unlimited | 7 |

Capacity counts items, not item types. Mix as many different items as you like.

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
