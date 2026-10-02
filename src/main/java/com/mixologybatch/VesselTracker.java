package com.mixologybatch;

import java.util.EnumMap;
import java.util.List;

/**
 * The vessel varbit stays set while the vessel drains, after its potion has
 * already landed in the inventory. Reporting the vessel as empty during the
 * drain keeps one potion from being seen in both places.
 */
final class VesselTracker
{
	private static final int DRAIN_TICKS = 2;

	private EnumMap<Potion, Integer> previousUnfinishedCounts;
	private Potion takenPotion;
	private int takenTick;

	Potion waitingPotion(Potion vesselPotion, List<InventorySlot> inventory, int tick)
	{
		EnumMap<Potion, Integer> unfinishedCounts = unfinishedCounts(inventory);
		if (vesselPotion != takenPotion || tick - takenTick > DRAIN_TICKS)
		{
			takenPotion = null;
		}
		// Unfinished potions only enter the inventory from the vessel.
		if (vesselPotion != null
			&& previousUnfinishedCounts != null
			&& unfinishedCounts.getOrDefault(vesselPotion, 0)
				> previousUnfinishedCounts.getOrDefault(vesselPotion, 0))
		{
			takenPotion = vesselPotion;
			takenTick = tick;
		}
		previousUnfinishedCounts = unfinishedCounts;
		return takenPotion == null ? vesselPotion : null;
	}

	void reset()
	{
		previousUnfinishedCounts = null;
		takenPotion = null;
	}

	private static EnumMap<Potion, Integer> unfinishedCounts(List<InventorySlot> inventory)
	{
		EnumMap<Potion, Integer> counts = new EnumMap<>(Potion.class);
		for (InventorySlot slot : inventory)
		{
			if (slot.isPotion() && !slot.isFinished())
			{
				counts.merge(slot.getPotion(), 1, Integer::sum);
			}
		}
		return counts;
	}
}
