package com.mixologybatch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class BatchPanelOverlay extends OverlayPanel
{
	private static final Color ERROR = new Color(255, 90, 90);
	private static final Color COMPLETE = new Color(70, 255, 120);

	private final MixologyBatchPlugin plugin;
	private final MixologyBatchConfig config;

	@Inject
	private BatchPanelOverlay(MixologyBatchPlugin plugin, MixologyBatchConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setPreferredSize(new Dimension(270, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInLab() || !config.showPanel())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text("Brains Off Mixology Helper").build());
		Guidance guidance = plugin.getGuidance();
		switch (guidance.getPhase())
		{
			case INVALID:
				addLine("Paused", guidance.getMessage(), ERROR);
				break;
			case EMPTY:
				addLine("No batch", guidance.getMessage(), ERROR);
				break;
			case MIXING:
				renderMixing(guidance);
				break;
			case PROCESSING:
				renderProcessing(guidance);
				break;
			case COMPLETE:
				addLine("Complete", "Deposit / reset", COMPLETE);
				addLine("Potions", plugin.getPlan().size() + "/" + plugin.getPlan().size(), COMPLETE);
				break;
			default:
		}
		return super.render(graphics);
	}

	private void renderMixing(Guidance guidance)
	{
		BatchEntry entry = guidance.getEntry();
		addLine("Mix potion", (entry.getInventorySlot() + 1) + "/" + plugin.getPlan().size(), Color.WHITE);
		addLine(entry.getPotion().name(), entry.getPotion().getDisplayName(), Color.WHITE);
		addLine("Recipe", entry.getPotion().getRecipeSteps(), Color.WHITE);
		if (guidance.getAction() == Guidance.Action.PULL_LEVER)
		{
			Component component = guidance.getComponent();
			addLine("NEXT", guidance.getStepNumber() + " " + component.getCode(), component.getColor());
		}
		else
		{
			addLine("NEXT", "4 MIX", Color.WHITE);
		}
		addLine("Later", "#" + (entry.getStationOrdinal() + 1) + " " + entry.getStation().getObjectName(), config.stationColor());
	}

	private void renderProcessing(Guidance guidance)
	{
		BatchEntry entry = guidance.getEntry();
		addLine("Station batch", (entry.getStationOrdinal() + 1) + "/3", config.stationColor());
		addLine(entry.getStation().getObjectName(), entry.getStation().getActionName(), config.stationColor());
		addLine("Potion", (entry.getStationPosition() + 1) + "/" + entry.getStationTotal() + "  " + entry.getPotion().name(), Color.WHITE);
		addLine("NEXT", guidance.getAction() == Guidance.Action.WAIT_STATION ? "Processing" : "Use station", config.stationColor());
	}

	private void addLine(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build());
	}
}
