package com.mixologybatch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

final class BatchSceneOverlay extends Overlay
{
	private static final Color VESSEL_COLOR = Color.WHITE;
	private static final Color COMPLETE_COLOR = new Color(0, 255, 90);

	private final Client client;
	private final MixologyBatchPlugin plugin;
	private final MixologyBatchConfig config;
	private final LabObjectResolver objects;
	private final ModelOutlineRenderer outliner;

	@Inject
	private BatchSceneOverlay(
		Client client,
		MixologyBatchPlugin plugin,
		MixologyBatchConfig config,
		LabObjectResolver objects,
		ModelOutlineRenderer outliner)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.objects = objects;
		this.outliner = outliner;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInLab() || !config.showSceneGuidance())
		{
			return null;
		}

		Guidance guidance = plugin.getGuidance();
		if (guidance.getPhase() == Guidance.Phase.MIXING)
		{
			renderMixingRecipe(graphics, guidance.getEntry().getPotion());
			return null;
		}

		LabObject target;
		Color color;
		String label;
		switch (guidance.getAction())
		{
			case USE_STATION:
				target = guidance.getEntry().getStation().getLabObject();
				color = config.stationColor();
				label = stationLabel(guidance.getEntry(), false);
				break;
			case WAIT_STATION:
				target = guidance.getEntry().getStation().getLabObject();
				color = config.stationColor();
				label = stationLabel(guidance.getEntry(), true);
				break;
			case DEPOSIT:
				target = LabObject.CONVEYOR;
				color = COMPLETE_COLOR;
				label = "BATCH READY";
				break;
			default:
				return null;
		}

		drawTarget(graphics, target, label, color);
		return null;
	}

	private void renderMixingRecipe(Graphics2D graphics, Potion potion)
	{
		for (Component component : Component.values())
		{
			String label = leverLabel(potion, component);
			if (label != null)
			{
				drawTarget(graphics, component.getLever(), label, component.getColor());
			}
		}
		drawTarget(graphics, LabObject.MIXING_VESSEL, "4", VESSEL_COLOR);
	}

	static String leverLabel(Potion potion, Component target)
	{
		StringBuilder label = new StringBuilder();
		Component[] recipe = potion.getRecipe();
		for (int index = 0; index < recipe.length; index++)
		{
			if (recipe[index] != target)
			{
				continue;
			}
			if (label.length() > 0)
			{
				label.append(" / ");
			}
			label.append(index + 1);
		}
		return label.length() == 0 ? null : label.toString();
	}

	private void drawTarget(Graphics2D graphics, LabObject target, String label, Color color)
	{
		TileObject object = objects.find(target);
		if (object == null)
		{
			return;
		}
		outliner.drawOutline(object, config.outlineWidth(), color, config.outlineFeather());
		drawLabel(graphics, object, label, color);
	}

	private static String stationLabel(BatchEntry entry, boolean active)
	{
		return (entry.getStationPosition() + 1) + "/" + entry.getStationTotal()
			+ " " + (active ? "PROCESS" : entry.getStation().getActionName().toUpperCase());
	}

	private void drawLabel(Graphics2D graphics, TileObject object, String text, Color color)
	{
		Point location = Perspective.getCanvasTextLocation(client, graphics, object.getLocalLocation(), text, 120);
		if (location == null)
		{
			return;
		}
		graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 16f));
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, location.getX() + 1, location.getY() + 1);
		graphics.setColor(color);
		graphics.drawString(text, location.getX(), location.getY());
	}
}
