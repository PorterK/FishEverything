# FishEverything

A Paper plugin for **Minecraft 26.3** that lets players fish up *any item in the game*. Every catch rolls a rarity tier, and the rarer the catch, the bigger the celebration.

## Download

**[⬇ FishEverything.jar (latest build)](https://github.com/PorterK/FishEverything/releases/download/latest/FishEverything.jar)**

Drop it into your server's `plugins/` folder and restart. You need Paper 26.3 running on Java 25.

## Rarity tiers

| Tier | Default chance | Example items | Catch animation |
|---|---|---|---|
| Common | 60% | dirt, flowers, wool, most blocks | water splash |
| Uncommon | 25% | iron, copper, food, logs, bows | green sparkles and a two-note pling |
| Rare | 10% | gold, emerald, ores, ender pearls, name tags | blue spiral climbing the player, chime arpeggio |
| Epic | 4% | diamond gear, shulker boxes, spawn eggs, enchanted books | purple and pink double helix, enchanting glyphs, firework pop, title card |
| Legendary | 0.9% | netherite, elytra, trident, beacon, music discs | harmless lightning, golden totem fountain, 3 fireworks, server broadcast |
| Mythic | 0.1% | dragon egg, nether star, mace, heavy core, notch apple | ring of lightning, sky beam, rainbow shockwaves, fireworks finale, dragon roar, player gets lifted into the air, server broadcast |

Inside a tier, every item is equally likely. **Luck of the Sea** boosts every tier above common (+35% weight per level by default). Enchanted books come with a random enchantment. The celebration fireworks never do damage.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/fe odds` shows each tier's chance and item count | `fisheverything.odds` | everyone |
| `/fe test <rarity>` gives a random item from a tier and plays its animation | `fisheverything.admin` | op |
| `/fe reload` reloads config.yml | `fisheverything.admin` | op |
| *(fishing gives FishEverything loot)* | `fisheverything.use` | everyone |

`/fisheverything` works as well as `/fe`.

## Configuration

`plugins/FishEverything/config.yml` controls:

- **Tier weights**, the display name of each tier (MiniMessage), the max stack size per catch, and whether a tier is broadcast to the server.
- **Which items go in which tier**, using item ID patterns with `*` wildcards (`"netherite_*"`, `"*_ore"`). Tiers are checked rarest first. Anything that doesn't match a pattern is Common.
- **Exclusions.** Operator-only items (command blocks, barriers, the debug stick, and so on) and the wither and ender dragon spawn eggs are excluded by default. Clear the list if you really want *everything*.
- `replace-chance` to keep some vanilla catches, plus `disabled-worlds`, the Luck of the Sea bonus, and the mythic levitation toggle.

New items added in future Minecraft versions become fishable automatically, as Common unless a pattern matches them.

## Building

```sh
./gradlew build    # needs JDK 25 → build/libs/FishEverything-<version>.jar
```

GitHub Actions builds every push. Pushes to `main` update the **latest** release. Pushing a tag like `v1.0.1` creates a versioned release.
