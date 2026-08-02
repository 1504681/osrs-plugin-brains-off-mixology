package com.mixologybatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps the configured logical station batches onto physical inventory slots.
 * Existing potions keep their place in the projected inventory and the lowest
 * available slots are reserved for the potions that will be mixed next.
 */
final class CyclePlan
{
	private final BatchPlan target;
	private final List<Integer> inventorySlots;
	private final Set<Integer> blockedSlots;
	private final String error;

	private CyclePlan(
		BatchPlan target,
		List<Integer> inventorySlots,
		Set<Integer> blockedSlots,
		String error)
	{
		this.target = target;
		this.inventorySlots = inventorySlots;
		this.blockedSlots = blockedSlots;
		this.error = error;
	}

	static CyclePlan create(BatchPlan target, List<InventorySlot> inventory)
	{
		if (!target.isValid())
		{
			return invalid(target, target.getError());
		}

		List<Integer> slots = new ArrayList<>(target.size());
		for (int slot = 0; slot < inventory.size(); slot++)
		{
			if (inventory.get(slot).isPotion())
			{
				slots.add(slot);
			}
		}
		if (slots.size() > target.size())
		{
			return invalid(target, "Inventory contains more potions than the configured batch.");
		}

		for (int slot = 0; slot < inventory.size() && slots.size() < target.size(); slot++)
		{
			if (inventory.get(slot).isEmpty())
			{
				slots.add(slot);
			}
		}
		for (int slot = 0; slot < inventory.size() && slots.size() < target.size(); slot++)
		{
			if (!slots.contains(slot))
			{
				slots.add(slot);
			}
		}
		if (slots.size() < target.size())
		{
			return invalid(target, "The configured batch does not fit in the inventory.");
		}

		Collections.sort(slots);
		Set<Integer> blocked = new HashSet<>();
		for (int slot : slots)
		{
			InventorySlot actual = inventory.get(slot);
			if (!actual.isEmpty() && !actual.isPotion())
			{
				blocked.add(slot);
			}
		}
		return new CyclePlan(
			target,
			Collections.unmodifiableList(slots),
			blocked,
			null);
	}

	private static CyclePlan invalid(BatchPlan target, String error)
	{
		return new CyclePlan(target, Collections.emptyList(), Collections.emptySet(), error);
	}

	boolean isValid()
	{
		return error == null;
	}

	String getError()
	{
		return error;
	}

	boolean belongsTo(BatchPlan plan)
	{
		return target == plan;
	}

	boolean containsPotionSlots(List<InventorySlot> inventory)
	{
		for (int slot = 0; slot < inventory.size(); slot++)
		{
			if (inventory.get(slot).isPotion() && !inventorySlots.contains(slot))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Reserve non-potion slots for the remainder of this cycle. If the game
	 * later places a potion into a reserved slot, resume tracking that slot.
	 * An empty reserved slot stays reserved so a disappearing digweed cannot
	 * make guidance jump backwards to an earlier station batch.
	 */
	void observeInventory(List<InventorySlot> inventory)
	{
		for (int slot : inventorySlots)
		{
			InventorySlot actual = inventory.get(slot);
			if (actual.isPotion())
			{
				blockedSlots.remove(slot);
			}
			else if (!actual.isEmpty())
			{
				blockedSlots.add(slot);
			}
		}
	}

	int getPotionCapacity()
	{
		return inventorySlots.size() - blockedSlots.size();
	}

	int firstEmptySlot(List<InventorySlot> inventory)
	{
		for (int slot : inventorySlots)
		{
			if (!blockedSlots.contains(slot) && inventory.get(slot).isEmpty())
			{
				return slot;
			}
		}
		return -1;
	}

	BatchEntry entryForSlot(int inventorySlot, Potion potion)
	{
		int rank = inventorySlots.indexOf(inventorySlot);
		return rank < 0 ? null : target.get(rank).remap(potion, inventorySlot);
	}

	BatchEntry entryAtRank(int rank, Potion potion)
	{
		return target.get(rank).remap(potion, inventorySlots.get(rank));
	}

	int size()
	{
		return inventorySlots.size();
	}

	int slotAtRank(int rank)
	{
		return inventorySlots.get(rank);
	}
}
