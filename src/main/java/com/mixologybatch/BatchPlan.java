package com.mixologybatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class BatchPlan
{
	static final int INVENTORY_SIZE = 28;

	private final List<BatchEntry> entries;
	private final String error;

	private BatchPlan(List<BatchEntry> entries, String error)
	{
		this.entries = entries;
		this.error = error;
	}

	static BatchPlan create(Map<Potion, Integer> counts, StationOrder stationOrder)
	{
		Station[] stations = stationOrder.getStations();
		List<List<Potion>> batches = new ArrayList<>(stations.length);
		for (int i = 0; i < stations.length; i++)
		{
			batches.add(new ArrayList<>());
		}

		int total = 0;
		for (Potion potion : Potion.values())
		{
			int count = counts.getOrDefault(potion, 0);
			if (count < 0)
			{
				return invalid("Potion counts cannot be negative.");
			}
			total += count;
			for (int copy = 0; copy < count; copy++)
			{
				batches.get(copy % stations.length).add(potion);
			}
		}

		if (total > INVENTORY_SIZE)
		{
			return invalid("Configured batch has " + total + " potions; reduce it to 28 or fewer.");
		}

		List<BatchEntry> result = new ArrayList<>(total);
		int slot = 0;
		for (int stationOrdinal = 0; stationOrdinal < stations.length; stationOrdinal++)
		{
			List<Potion> batch = batches.get(stationOrdinal);
			for (int position = 0; position < batch.size(); position++)
			{
				result.add(new BatchEntry(
					batch.get(position),
					stations[stationOrdinal],
					slot++,
					stationOrdinal,
					position,
					batch.size()));
			}
		}

		return new BatchPlan(Collections.unmodifiableList(result), null);
	}

	static BatchPlan defaultPlan()
	{
		EnumMap<Potion, Integer> counts = new EnumMap<>(Potion.class);
		counts.put(Potion.MMA, 3);
		counts.put(Potion.MML, 4);
		counts.put(Potion.AAM, 3);
		counts.put(Potion.ALA, 3);
		counts.put(Potion.LLL, 3);
		counts.put(Potion.MLL, 3);
		counts.put(Potion.ALL, 3);
		counts.put(Potion.MAL, 6);
		return create(counts, StationOrder.CRYSTALLISE_HOMOGENISE_CONCENTRATE);
	}

	private static BatchPlan invalid(String error)
	{
		return new BatchPlan(Collections.emptyList(), error);
	}

	boolean isValid()
	{
		return error == null;
	}

	String getError()
	{
		return error;
	}

	int size()
	{
		return entries.size();
	}

	BatchEntry get(int slot)
	{
		return entries.get(slot);
	}

	List<BatchEntry> getEntries()
	{
		return entries;
	}
}

