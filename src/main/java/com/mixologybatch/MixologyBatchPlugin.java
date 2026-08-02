package com.mixologybatch;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Brains Off Mixology Helper",
	description = "Guides configurable low-attention Mastering Mixology inventories through ordered mixing and station batches",
	tags = {"mastering", "mixology", "herblore", "batch", "helper", "skilling", "varlamore"}
)
public class MixologyBatchPlugin extends Plugin
{
	private static final int LAB_REGION = 5521;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private MixologyBatchConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BatchPanelOverlay panelOverlay;

	@Inject
	private BatchSceneOverlay sceneOverlay;

	@Inject
	private BatchInventoryOverlay inventoryOverlay;

	private final BatchStateResolver resolver = new BatchStateResolver();
	private BatchPlan plan = BatchPlan.defaultPlan();
	private Guidance guidance = Guidance.outside();
	private boolean inLab;

	@Override
	protected void startUp()
	{
		overlayManager.add(panelOverlay);
		overlayManager.add(sceneOverlay);
		overlayManager.add(inventoryOverlay);
		rebuildPlan();
		clientThread.invokeLater(this::updateState);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(panelOverlay);
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(inventoryOverlay);
		inLab = false;
		guidance = Guidance.outside();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateState();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				inLab = false;
				guidance = Guidance.outside();
				break;
			default:
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MixologyBatchConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		rebuildPlan();
		clientThread.invokeLater(this::updateState);
	}

	private void rebuildPlan()
	{
		EnumMap<Potion, Integer> counts = new EnumMap<>(Potion.class);
		counts.put(Potion.MMM, config.mmmCount());
		counts.put(Potion.MMA, config.mmaCount());
		counts.put(Potion.MML, config.mmlCount());
		counts.put(Potion.AAA, config.aaaCount());
		counts.put(Potion.AAM, config.aamCount());
		counts.put(Potion.ALA, config.alaCount());
		counts.put(Potion.LLL, config.lllCount());
		counts.put(Potion.MLL, config.mllCount());
		counts.put(Potion.ALL, config.allCount());
		counts.put(Potion.MAL, config.malCount());
		plan = BatchPlan.create(counts, config.stationOrder());
	}

	private void updateState()
	{
		inLab = isPlayerInLab();
		if (!inLab)
		{
			guidance = Guidance.outside();
			return;
		}

		List<InventorySlot> inventory = readInventory();
		Potion vesselPotion = Potion.fromVarbit(client.getVarbitValue(VarbitID.MM_LAB_VESSEL_READY));
		int[] mixerSlots = {
			client.getVarbitValue(VarbitID.MM_LAB_MIXER_SLOT_0),
			client.getVarbitValue(VarbitID.MM_LAB_MIXER_SLOT_1),
			client.getVarbitValue(VarbitID.MM_LAB_MIXER_SLOT_2)
		};
		Map<Station, Potion> activeStations = new EnumMap<>(Station.class);
		for (Station station : Station.values())
		{
			Potion potion = Potion.fromVarbit(client.getVarbitValue(station.getPotionVarbit()));
			if (potion != null)
			{
				activeStations.put(station, potion);
			}
		}
		guidance = resolver.resolve(plan, inventory, vesselPotion, mixerSlots, activeStations);
	}

	private List<InventorySlot> readInventory()
	{
		List<InventorySlot> result = new ArrayList<>(BatchPlan.INVENTORY_SIZE);
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		Item[] items = container == null ? null : container.getItems();
		for (int slot = 0; slot < BatchPlan.INVENTORY_SIZE; slot++)
		{
			if (items == null || slot >= items.length || items[slot] == null)
			{
				result.add(InventorySlot.empty());
			}
			else
			{
				result.add(InventorySlot.fromItemId(items[slot].getId()));
			}
		}
		return result;
	}

	private boolean isPlayerInLab()
	{
		Player player = client.getLocalPlayer();
		return player != null
			&& player.getWorldLocation().getRegionID() == LAB_REGION
			&& player.getWorldLocation().getPlane() == 0;
	}

	boolean isInLab()
	{
		return inLab;
	}

	Guidance getGuidance()
	{
		return guidance;
	}

	BatchPlan getPlan()
	{
		return plan;
	}

	@Provides
	MixologyBatchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MixologyBatchConfig.class);
	}
}
