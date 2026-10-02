package com.mixologybatch;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VesselTrackerTest
{
	@Test
	public void readyVesselKeepsWaitingUntilThePotionIsTaken()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();

		assertNull(tracker.waitingPotion(null, inventory, 1));
		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 2));
		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 3));
	}

	@Test
	public void drainingVesselIsEmptyOncePotionReachesTheInventory()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		tracker.waitingPotion(Potion.ALA, inventory, 1);
		inventory.set(4, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));

		assertNull(tracker.waitingPotion(Potion.ALA, inventory, 2));
		assertNull(tracker.waitingPotion(Potion.ALA, inventory, 2));
		assertNull(tracker.waitingPotion(null, inventory, 3));
	}

	@Test
	public void sameRecipeMixedAgainIsWaitingAfterTheVesselEmptied()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		tracker.waitingPotion(Potion.ALA, inventory, 1);
		inventory.set(4, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));
		tracker.waitingPotion(Potion.ALA, inventory, 2);
		tracker.waitingPotion(null, inventory, 3);

		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 6));
	}

	@Test
	public void potionsAlreadyCarriedDoNotEmptyTheVessel()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		inventory.set(0, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));

		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 1));
	}

	@Test
	public void otherInventoryChangesDoNotEmptyTheVessel()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		inventory.set(0, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));
		tracker.waitingPotion(Potion.ALA, inventory, 1);
		inventory.set(0, InventorySlot.fromItemId(Potion.ALA.getFinishedItemId()));
		inventory.set(1, InventorySlot.fromItemId(Potion.MAL.getUnfinishedItemId()));

		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 2));
	}

	@Test
	public void vesselStillFullAfterTheDrainWindowIsWaitingAgain()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		tracker.waitingPotion(Potion.ALA, inventory, 1);
		inventory.set(4, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));

		assertNull(tracker.waitingPotion(Potion.ALA, inventory, 2));
		assertNull(tracker.waitingPotion(Potion.ALA, inventory, 4));
		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 5));
	}

	@Test
	public void resetForgetsTheCarriedInventory()
	{
		VesselTracker tracker = new VesselTracker();
		List<InventorySlot> inventory = emptyInventory();
		tracker.waitingPotion(Potion.ALA, inventory, 1);
		tracker.reset();
		inventory.set(4, InventorySlot.fromItemId(Potion.ALA.getUnfinishedItemId()));

		assertEquals(Potion.ALA, tracker.waitingPotion(Potion.ALA, inventory, 2));
	}

	private static List<InventorySlot> emptyInventory()
	{
		List<InventorySlot> inventory = new ArrayList<>(BatchPlan.INVENTORY_SIZE);
		for (int slot = 0; slot < BatchPlan.INVENTORY_SIZE; slot++)
		{
			inventory.add(InventorySlot.empty());
		}
		return inventory;
	}
}
