package com.mixologybatch;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class BatchStateResolver
{
	Guidance resolve(
		BatchPlan plan,
		List<InventorySlot> inventory,
		Potion vesselPotion,
		int[] mixerSlots,
		Map<Station, Potion> activeStations)
	{
		if (!plan.isValid())
		{
			return Guidance.invalid(plan.getError());
		}
		if (plan.size() == 0)
		{
			return Guidance.empty();
		}

		boolean[] occupied = new boolean[plan.size()];
		for (int slot = 0; slot < plan.size(); slot++)
		{
			InventorySlot actual = inventory.get(slot);
			if (actual.isEmpty())
			{
				continue;
			}
			if (!actual.isPotion())
			{
				return Guidance.invalid("Clear inventory slot " + (slot + 1) + " before continuing.");
			}
			Potion expected = plan.get(slot).getPotion();
			if (actual.getPotion() != expected)
			{
				return Guidance.invalid(
					"Slot " + (slot + 1) + " should be " + expected.name()
						+ ", but contains " + actual.getPotion().name() + ".");
			}
			occupied[slot] = true;
		}

		for (int slot = plan.size(); slot < inventory.size(); slot++)
		{
			if (inventory.get(slot).isPotion())
			{
				return Guidance.invalid("A mixology potion is outside the configured batch slots.");
			}
		}

		BatchEntry activeEntry = null;
		for (Map.Entry<Station, Potion> active : activeStations.entrySet())
		{
			if (active.getValue() == null)
			{
				continue;
			}
			if (activeEntry != null)
			{
				return Guidance.invalid("More than one processing station contains a potion.");
			}
			int firstGap = firstMissing(occupied);
			if (firstGap < 0)
			{
				return Guidance.invalid("A station contains a potion outside the configured batch.");
			}
			BatchEntry expected = plan.get(firstGap);
			if (expected.getPotion() != active.getValue() || expected.getStation() != active.getKey())
			{
				return Guidance.invalid(
					"Wrong station: slot " + (firstGap + 1) + " needs "
						+ expected.getStation().getObjectName() + " for " + expected.getPotion().name() + ".");
			}
			occupied[firstGap] = true;
			activeEntry = expected;
		}

		int firstGap = firstMissing(occupied);
		boolean mixerHasContents = hasMixerContents(mixerSlots);

		if (vesselPotion != null)
		{
			if (activeEntry != null)
			{
				return Guidance.invalid("Collect the station potion before mixing another batch potion.");
			}
			if (firstGap < 0)
			{
				return Guidance.invalid("The mixing vessel contains an extra potion.");
			}
			BatchEntry expected = plan.get(firstGap);
			if (expected.getPotion() != vesselPotion)
			{
				return Guidance.invalid(
					"The vessel contains " + vesselPotion.name() + "; slot " + (firstGap + 1)
						+ " expects " + expected.getPotion().name() + ".");
			}
			String gapError = validateNoLaterItems(occupied, firstGap);
			if (gapError != null)
			{
				return Guidance.invalid(gapError);
			}
			return Guidance.mixing(expected, MixStep.resolve(expected.getPotion(), mixerSlots, true));
		}

		if (activeEntry != null)
		{
			if (firstGap >= 0)
			{
				return Guidance.invalid("Finish mixing the full inventory before processing it.");
			}
			return Guidance.processing(activeEntry, true);
		}

		if (mixerHasContents)
		{
			if (firstGap < 0)
			{
				return Guidance.invalid("The mixer contains ingredients after the batch is full.");
			}
			String gapError = validateNoLaterItems(occupied, firstGap);
			if (gapError != null)
			{
				return Guidance.invalid(gapError);
			}
			BatchEntry expected = plan.get(firstGap);
			return Guidance.mixing(expected, MixStep.resolve(expected.getPotion(), mixerSlots, false));
		}

		if (firstGap >= 0)
		{
			String gapError = validateNoLaterItems(occupied, firstGap);
			if (gapError != null)
			{
				return Guidance.invalid(gapError);
			}
			for (int slot = 0; slot < firstGap; slot++)
			{
				if (inventory.get(slot).isFinished())
				{
					return Guidance.invalid("Finish mixing the full inventory before processing it.");
				}
			}
			BatchEntry expected = plan.get(firstGap);
			return Guidance.mixing(expected, MixStep.resolve(expected.getPotion(), mixerSlots, false));
		}

		for (int slot = 0; slot < plan.size(); slot++)
		{
			if (!inventory.get(slot).isFinished())
			{
				return Guidance.processing(plan.get(slot), false);
			}
		}
		return Guidance.complete();
	}

	private static boolean hasMixerContents(int[] mixerSlots)
	{
		for (int slot : mixerSlots)
		{
			if (slot != 0)
			{
				return true;
			}
		}
		return false;
	}

	private static int firstMissing(boolean[] occupied)
	{
		for (int i = 0; i < occupied.length; i++)
		{
			if (!occupied[i])
			{
				return i;
			}
		}
		return -1;
	}

	private static String validateNoLaterItems(boolean[] occupied, int firstGap)
	{
		for (int i = firstGap + 1; i < occupied.length; i++)
		{
			if (occupied[i])
			{
				return "Inventory order has a gap before slot " + (i + 1) + ".";
			}
		}
		return null;
	}

	static Map<Station, Potion> noActiveStations()
	{
		return new EnumMap<>(Station.class);
	}
}
