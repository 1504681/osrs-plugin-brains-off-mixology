package com.mixologybatch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BatchSceneOverlayTest
{
	@Test
	public void labelsEveryStepForDistinctIngredients()
	{
		assertEquals("1", BatchSceneOverlay.leverLabel(Potion.MAL, Component.MOX));
		assertEquals("2", BatchSceneOverlay.leverLabel(Potion.MAL, Component.AGA));
		assertEquals("3", BatchSceneOverlay.leverLabel(Potion.MAL, Component.LYE));
	}

	@Test
	public void combinesRepeatedStepsOnTheirLever()
	{
		assertEquals("1 / 3", BatchSceneOverlay.leverLabel(Potion.ALA, Component.AGA));
		assertEquals("2", BatchSceneOverlay.leverLabel(Potion.ALA, Component.LYE));
		assertNull(BatchSceneOverlay.leverLabel(Potion.ALA, Component.MOX));
	}
}
