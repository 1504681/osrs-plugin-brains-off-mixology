package com.mixologybatch;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

final class BatchInventoryOverlay extends WidgetItemOverlay
{
	private final MixologyBatchPlugin plugin;
	private final MixologyBatchConfig config;

	@Inject
	private BatchInventoryOverlay(MixologyBatchPlugin plugin, MixologyBatchConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.isInLab() || !config.showInventoryBatches() || Potion.fromItemId(itemId) == null)
		{
			return;
		}
		int slot = widgetItem.getWidget().getIndex();
		Potion potion = Potion.fromItemId(itemId);
		BatchEntry entry = plugin.getCycleEntry(slot, potion);
		if (entry == null)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		String text = "#" + (entry.getStationOrdinal() + 1);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, bounds.x + 3, bounds.y + 12);
		graphics.setColor(batchColor(entry.getStationOrdinal()));
		graphics.drawString(text, bounds.x + 2, bounds.y + 11);

		Guidance guidance = plugin.getGuidance();
		if (guidance.getPhase() == Guidance.Phase.PROCESSING
			&& guidance.getEntry() != null
			&& guidance.getEntry().getInventorySlot() == slot)
		{
			graphics.setColor(config.stationColor());
			graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
			graphics.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
		}
	}

	private static Color batchColor(int stationOrdinal)
	{
		switch (stationOrdinal)
		{
			case 0:
				return new Color(80, 220, 255);
			case 1:
				return new Color(255, 210, 70);
			default:
				return new Color(255, 110, 230);
		}
	}
}
