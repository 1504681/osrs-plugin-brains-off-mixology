package com.mixologybatch;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StationMenuGuardTest
{
	@Test
	public void onlyPlannedStationIsUsableDuringProcessing()
	{
		BatchEntry entry = BatchPlan.defaultPlan().get(0);
		Guidance guidance = Guidance.processing(entry, false);

		assertTrue(StationMenuGuard.canUseStation(guidance, entry.getStation(), false, null));
		assertFalse(StationMenuGuard.canUseStation(guidance, Station.HOMOGENISE, false, null));
		assertFalse(StationMenuGuard.canUseStation(guidance, Station.CONCENTRATE, false, null));
	}

	@Test
	public void allStationsAreGuardedWhileMixing()
	{
		BatchEntry entry = BatchPlan.defaultPlan().get(0);
		Guidance guidance = Guidance.mixing(
			entry,
			MixStep.resolve(entry.getPotion(), new int[]{0, 0, 0}, false));

		for (Station station : Station.values())
		{
			assertFalse(StationMenuGuard.canUseStation(guidance, station, false, null));
		}
	}

	@Test
	public void earliestPartialInventoryStationRemainsUsableWhileMixing()
	{
		BatchEntry entry = BatchPlan.defaultPlan().get(0);
		Guidance guidance = Guidance.mixing(
			entry,
			MixStep.resolve(entry.getPotion(), new int[]{0, 0, 0}, false));

		assertTrue(StationMenuGuard.canUseStation(
			guidance, Station.HOMOGENISE, false, Station.HOMOGENISE));
		assertFalse(StationMenuGuard.canUseStation(
			guidance, Station.CRYSTALLISE, false, Station.HOMOGENISE));
		assertFalse(StationMenuGuard.canUseStation(
			guidance, Station.CONCENTRATE, false, Station.HOMOGENISE));
	}

	@Test
	public void stationHoldingAPotionRemainsUsableForRecovery()
	{
		assertTrue(StationMenuGuard.canUseStation(
			Guidance.invalid("Wrong station"), Station.CONCENTRATE, true, null));
	}

	@Test
	public void stationObjectIdsAreRecognized()
	{
		for (Station station : Station.values())
		{
			assertSame(station, StationMenuGuard.stationForObjectId(
				station.getLabObject().getObjectId()));
		}
	}
}
