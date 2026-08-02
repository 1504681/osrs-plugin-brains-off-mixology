package com.mixologybatch;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BatchStateResolverTest
{
	private BatchPlan plan;
	private BatchStateResolver resolver;

	@Before
	public void setUp()
	{
		plan = BatchPlan.defaultPlan();
		resolver = new BatchStateResolver();
	}

	@Test
	public void emptyInventoryStartsAtFirstLever()
	{
		Guidance guidance = resolver.resolve(plan, emptyInventory(), null, new int[]{0, 0, 0}, noActive());

		assertEquals(Guidance.Phase.MIXING, guidance.getPhase());
		assertEquals(Guidance.Action.PULL_LEVER, guidance.getAction());
		assertEquals(0, guidance.getEntry().getInventorySlot());
		assertEquals(Component.MOX, guidance.getComponent());
	}

	@Test
	public void fullInventoryStartsFirstStationBatch()
	{
		List<InventorySlot> inventory = inventoryForPlan(false);
		Guidance guidance = resolver.resolve(plan, inventory, null, new int[]{0, 0, 0}, noActive());

		assertEquals(Guidance.Phase.PROCESSING, guidance.getPhase());
		assertEquals(Guidance.Action.USE_STATION, guidance.getAction());
		assertEquals(Station.CRYSTALLISE, guidance.getEntry().getStation());
		assertEquals(0, guidance.getEntry().getStationPosition());
	}

	@Test
	public void finishedFirstBatchMovesToSecondStation()
	{
		List<InventorySlot> inventory = inventoryForPlan(false);
		for (int slot = 0; slot < 10; slot++)
		{
			inventory.set(slot, InventorySlot.fromItemId(plan.get(slot).getPotion().getFinishedItemId()));
		}
		Guidance guidance = resolver.resolve(plan, inventory, null, new int[]{0, 0, 0}, noActive());

		assertEquals(Station.HOMOGENISE, guidance.getEntry().getStation());
		assertEquals(10, guidance.getEntry().getInventorySlot());
	}

	@Test
	public void activeCorrectStationWaitsWithoutLosingSlot()
	{
		List<InventorySlot> inventory = inventoryForPlan(false);
		inventory.set(0, InventorySlot.empty());
		Map<Station, Potion> active = new EnumMap<>(Station.class);
		active.put(Station.CRYSTALLISE, plan.get(0).getPotion());

		Guidance guidance = resolver.resolve(plan, inventory, null, new int[]{0, 0, 0}, active);

		assertEquals(Guidance.Action.WAIT_STATION, guidance.getAction());
		assertEquals(0, guidance.getEntry().getInventorySlot());
	}

	@Test
	public void activeWrongStationPausesWithCorrection()
	{
		List<InventorySlot> inventory = inventoryForPlan(false);
		inventory.set(0, InventorySlot.empty());
		Map<Station, Potion> active = new EnumMap<>(Station.class);
		active.put(Station.CONCENTRATE, plan.get(0).getPotion());

		Guidance guidance = resolver.resolve(plan, inventory, null, new int[]{0, 0, 0}, active);

		assertEquals(Guidance.Phase.INVALID, guidance.getPhase());
		assertTrue(guidance.getMessage().contains("Alembic"));
	}

	@Test
	public void fullyFinishedBatchPointsToConveyor()
	{
		Guidance guidance = resolver.resolve(plan, inventoryForPlan(true), null, new int[]{0, 0, 0}, noActive());

		assertEquals(Guidance.Phase.COMPLETE, guidance.getPhase());
		assertEquals(Guidance.Action.DEPOSIT, guidance.getAction());
	}

	private List<InventorySlot> inventoryForPlan(boolean finished)
	{
		List<InventorySlot> inventory = emptyInventory();
		for (BatchEntry entry : plan.getEntries())
		{
			int itemId = finished
				? entry.getPotion().getFinishedItemId()
				: entry.getPotion().getUnfinishedItemId();
			inventory.set(entry.getInventorySlot(), InventorySlot.fromItemId(itemId));
		}
		return inventory;
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

	private static Map<Station, Potion> noActive()
	{
		return new EnumMap<>(Station.class);
	}
}

