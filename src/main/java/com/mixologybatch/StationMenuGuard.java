package com.mixologybatch;

final class StationMenuGuard
{
	private StationMenuGuard()
	{
	}

	static Station stationForObjectId(int objectId)
	{
		for (Station station : Station.values())
		{
			if (station.getLabObject().getObjectId() == objectId)
			{
				return station;
			}
		}
		return null;
	}

	static boolean canUseStation(
		Guidance guidance,
		Station station,
		boolean stationContainsPotion,
		Station partialInventoryStation)
	{
		if (stationContainsPotion)
		{
			return true;
		}
		if (guidance.getPhase() == Guidance.Phase.PROCESSING)
		{
			return guidance.getEntry() != null
				&& guidance.getEntry().getStation() == station
				&& (guidance.getAction() == Guidance.Action.USE_STATION
					|| guidance.getAction() == Guidance.Action.WAIT_STATION);
		}
		return partialInventoryStation == station;
	}
}
