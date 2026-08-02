# Brains Off Mixology Helper

A RuneLite Plugin Hub-style plugin for the low-attention, premade-inventory Mastering Mixology method. It guides one manual click at a time; it does not click, move the mouse, select menu entries, or automate gameplay.

## The default batch

The defaults reproduce the potion totals in the reference inventory:

| Potion | Copies |
| --- | ---: |
| ALA | 3 |
| ALL | 3 |
| MMA | 3 |
| MLL | 3 |
| LLL | 3 |
| AAM | 3 |
| MML | 4 |
| MAL | 6 |
| **Total** | **28** |

Every three copies are distributed one-per-station. Six MALs become two per station. The fourth MML goes into the first station batch. With the recommended station order, this produces three contiguous inventory batches:

1. Alembic / Crystallise: 10 potions
2. Agitator / Homogenise: 9 potions
3. Retort / Concentrate: 9 potions

Retort is last by default so its repeated-click interaction cannot accidentally pull a potion from a later batch.

## What the plugin shows

- During mixing, only the next lever is outlined and labelled (`1 M`, `2 A`, `3 L`), followed by `4 MIX` on the vessel.
- The instruction panel shows the current inventory slot, potion code, complete four-click recipe, and the station batch that potion belongs to.
- Each inventory potion is marked `#1`, `#2`, or `#3` for its planned station batch.
- Once the inventory is full, the correct processing station is outlined. It remains the target until that contiguous batch is finished, then the next station is highlighted.
- After all 28 potions are processed, the conveyor is highlighted.
- A wrong potion, inventory gap, wrong station, or total above 28 pauses guidance with a specific correction instead of silently drifting out of sync.

## Custom batches

Open the plugin configuration and set a count from 0 to 28 for each recipe. The combined total must be 28 or fewer. Counts are distributed round-robin over the selected station order, then flattened into contiguous station batches.

For example, a count of 3 makes one variant for each station, a count of 6 makes two for each station, and a count of 4 makes two for the first station plus one for each remaining station.

Start with an empty inventory and do not reorder potion slots mid-batch. The plugin intentionally treats inventory position as the plan because all three processed variants share the same finished item ID.

## Development

Requirements: Java 11.

```text
./gradlew clean test
```

To launch a development RuneLite client, run the `MixologyBatchPluginTest.main` class from the IDE.

The repository uses `build=standard` and has no third-party runtime dependencies, which keeps a future Plugin Hub review straightforward. Before submitting, update `author` in `runelite-plugin.properties`, publish this code in a public GitHub repository, and add a Plugin Hub manifest containing that repository URL and a full commit hash.

## Compatibility notes

- The helper is active only in the Mastering Mixology lab region.
- It reads RuneLite's mixer-slot, vessel, inventory, and station varbits; no data leaves the client.
- Digweed-modified potions are outside this fixed-batch workflow.
- The established Mastering Mixology plugin can still be used for its order UI, quick-action timing, and Digweed features. If its inventory recipe labels are enabled, they may visually share space with this plugin's small batch numbers.

## Acknowledgements

The implementation was informed by the open-source Mastering Mixology, Noot's Mixology, and Simplifying Mixology plugins. Their notices are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
