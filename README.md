# Brains Off Mixology Helper

A RuneLite Plugin Hub-style plugin for the low-attention, premade-inventory Mastering Mixology method. It guides one manual click at a time; it does not click, move the mouse, select menu entries, or automate gameplay.

All overlays and guidance activate only while the Mastering Mixology order interface is visible inside the laboratory. Leaving the playable room immediately hides the plugin and clears its transient cycle state.

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

- During mixing, the complete recipe is visible at once: every required lever is outlined with its step number (`1`, `2`, `3`) while the vessel shows `4`. Repeated ingredients combine their step numbers on the same lever. The instruction panel shows the letter sequence separately, such as `M A L`.
- A non-blocking potion queue shows the previous two completed potions, the current potion, and the next three potions. Every row includes the potion code and its complete three-letter recipe.
- Clicking `Mix` predicts the next potion locally so its lever numbers appear immediately instead of waiting for the mixing animation. The prediction is reconciled with the actual inventory result and expires safely if the action does not complete.
- The instruction panel shows the current inventory slot, potion code, complete four-click recipe, and the station batch that potion belongs to.
- The panel always lists how many of every configured recipe remain in the inventory, including potions carried into a refill cycle.
- Each inventory potion is marked `#1`, `#2`, or `#3` for its planned station batch.
- Once the available potion slots are full, the correct processing station is outlined. It remains the target until that contiguous batch is finished, then the next station is highlighted.
- After all 28 potions are processed, the conveyor is highlighted.
- Deposited inventory gaps and recipes mixed out of the suggested order are accepted. Actual potions in the inventory count toward the configured recipe totals.
- Using a lever while potions remain starts a rolling refill cycle around those existing potions. Wrong processing stations or a total above 28 still pause with a specific correction.
- During delivery, MAL is ignored for the rolling-reset threshold. As soon as only two or fewer processed non-MAL potions remain, the conveyor highlight clears and a fresh mixing queue starts around every potion still carried.
- Held Digweed and other non-potion items reserve their inventory slots for the current cycle instead of pausing guidance. The panel shows the reduced potion capacity (for example, `27/28 potion slots available`) and the remaining slots stay station-ordered.
- Refill selection is calculated separately for each contiguous station batch. It completes every recipe assigned to station batch `#1` before generating `#2`, then `#3`; it never satisfies a recipe by making all three station variants consecutively.
- Both unfinished and finished inventory item IDs count toward their assigned station batch. Active-station varbits keep a potion accounted for while it is temporarily removed from the inventory, while potions removed by the conveyor cease to count toward the next refill.

## Custom batches

Open the plugin configuration and set a count from 0 to 28 for each recipe. The combined total must be 28 or fewer. Counts are distributed round-robin over the selected station order, then flattened into contiguous station batches.

For example, a count of 3 makes one variant for each station, a count of 6 makes two for each station, and a count of 4 makes two for the first station plus one for each remaining station.

For the first cycle, starting with an empty inventory is simplest. Later cycles can retain unfinished or finished potions: the plugin projects the station batches over those existing potions and the empty slots that will be filled next.

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
- Holding Digweed is supported. The helper does not decide which potion should receive the Digweed bonus.
- The established Mastering Mixology plugin can still be used for its order UI, quick-action timing, and Digweed features. If its inventory recipe labels are enabled, they may visually share space with this plugin's small batch numbers.

## Acknowledgements

The implementation was informed by the open-source Mastering Mixology, Noot's Mixology, and Simplifying Mixology plugins. Their notices are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
