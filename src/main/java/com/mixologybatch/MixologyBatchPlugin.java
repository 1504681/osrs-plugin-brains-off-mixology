package com.mixologybatch;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
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

	@Inject
	private BatchQueueOverlay queueOverlay;

	private final BatchStateResolver resolver = new BatchStateResolver();
	private BatchPlan plan = BatchPlan.defaultPlan();
	private CyclePlan cyclePlan;
	private Guidance guidance = Guidance.outside();
	private EnumMap<Potion, Integer> currentPotionCounts = new EnumMap<>(Potion.class);
	private final Deque<Potion> previousQueuePotions = new ArrayDeque<>();
	private List<Potion> upcomingQueuePotions = Collections.emptyList();
	private List<Potion> plannedPotionQueue = Collections.emptyList();
	private Potion trackedPotion;
	private int trackedStep = 1;
	private MixPrediction mixPrediction;
	private int gameTickCounter;
	private boolean refilling = true;
	private boolean inLab;

	@Override
	protected void startUp()
	{
		overlayManager.add(panelOverlay);
		overlayManager.add(sceneOverlay);
		overlayManager.add(inventoryOverlay);
		overlayManager.add(queueOverlay);
		rebuildPlan();
		clientThread.invokeLater(this::updateState);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(panelOverlay);
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(inventoryOverlay);
		overlayManager.remove(queueOverlay);
		deactivateLab();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		gameTickCounter++;
		updateState();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (inLab && event.getContainerId() == InventoryID.INV)
		{
			clientThread.invokeLater(this::updateState);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!inLab || "Examine".equalsIgnoreCase(event.getMenuOption()))
		{
			return;
		}

		Component lever = componentForLeverObject(event.getId());
		if (lever != null)
		{
			handleLeverClick(lever);
			return;
		}
		if (event.getId() == LabObject.MIXING_VESSEL.getObjectId()
			&& "Mix".equalsIgnoreCase(event.getMenuOption()))
		{
			handleMixClick();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				deactivateLab();
				break;
			default:
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.MM_OVERLAY)
		{
			clientThread.invokeLater(this::updateState);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.MM_OVERLAY)
		{
			deactivateLab();
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
		cyclePlan = null;
		resetActionQueue();
	}

	private void updateState()
	{
		boolean wasInLab = inLab;
		inLab = isPlayerInMixologyRoom();
		if (!inLab)
		{
			deactivateLab();
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
		reconcileMixPrediction(inventory);

		List<InventorySlot> planningInventory = inventory;
		Potion planningVesselPotion = vesselPotion;
		int[] planningMixerSlots = mixerSlots;
		if (mixPrediction != null && inventory.get(mixPrediction.inventorySlot).isEmpty())
		{
			planningInventory = new ArrayList<>(inventory);
			planningInventory.set(
				mixPrediction.inventorySlot,
				InventorySlot.fromItemId(mixPrediction.potion.getUnfinishedItemId()));
			planningVesselPotion = null;
			planningMixerSlots = new int[]{0, 0, 0};
		}

		Guidance previousGuidance = guidance;
		int inventoryPotionCount = countInventoryPotions(planningInventory);
		boolean mixingActivity = vesselPotion != null || BatchStateResolver.hasMixerContents(mixerSlots);
		if (!wasInLab || cyclePlan == null || !cyclePlan.belongsTo(plan))
		{
			cyclePlan = CyclePlan.create(plan, planningInventory);
			refilling = inventoryPotionCount == 0 || mixingActivity;
		}

		if (!refilling && mixingActivity && activeStations.isEmpty())
		{
			cyclePlan = CyclePlan.create(plan, planningInventory);
			refilling = true;
		}
		else if (refilling && cyclePlan != null && !cyclePlan.containsPotionSlots(planningInventory))
		{
			cyclePlan = CyclePlan.create(plan, planningInventory);
		}
		if (cyclePlan != null && cyclePlan.isValid())
		{
			cyclePlan.observeInventory(planningInventory);
		}

		if (refilling
			&& cyclePlan != null
			&& cyclePlan.isValid()
			&& cyclePlan.getPotionCapacity() > 0
			&& inventoryPotionCount >= cyclePlan.getPotionCapacity()
			&& !mixingActivity)
		{
			refilling = false;
		}
		else if (!refilling
			&& activeStations.isEmpty()
			&& !mixingActivity
			&& BatchStateResolver.shouldStartRollingRefill(planningInventory))
		{
			cyclePlan = CyclePlan.create(plan, planningInventory);
			refilling = true;
			resetActionQueue();
		}
		else if (!refilling && inventoryPotionCount == 0 && activeStations.isEmpty())
		{
			cyclePlan = CyclePlan.create(plan, planningInventory);
			refilling = true;
		}

		currentPotionCounts = BatchStateResolver.potionCounts(planningInventory);
		if (planningVesselPotion != null)
		{
			currentPotionCounts.merge(planningVesselPotion, 1, Integer::sum);
		}
		for (Potion activePotion : activeStations.values())
		{
			currentPotionCounts.merge(activePotion, 1, Integer::sum);
		}
		plannedPotionQueue = buildPlannedPotionQueue(planningInventory, 4);

		guidance = resolver.resolve(
			plan,
			cyclePlan,
			refilling,
			planningInventory,
			planningVesselPotion,
			planningMixerSlots,
			activeStations,
			previousGuidance);
		synchronizeActionQueue();
	}

	private void handleLeverClick(Component clicked)
	{
		if (!refilling)
		{
			mixPrediction = null;
			cyclePlan = CyclePlan.create(plan, readInventory());
			refilling = true;
			trackedPotion = null;
			trackedStep = 1;
			updateState();
		}
		if (trackedPotion == null || trackedStep < 1 || trackedStep > 3)
		{
			return;
		}

		MixQueueAction expected = MixQueueAction.forStep(trackedPotion, trackedStep);
		if (expected.getComponent() != clicked)
		{
			// Do not let a mistaken click advance or replace the intended sequence.
			refreshPotionQueue();
			return;
		}
		trackedStep++;
		refreshPotionQueue();
	}

	private void handleMixClick()
	{
		if (trackedPotion == null || cyclePlan == null || !cyclePlan.isValid())
		{
			return;
		}

		List<InventorySlot> inventory = readInventory();
		int inventorySlot = cyclePlan.firstEmptySlot(inventory);
		if (inventorySlot < 0)
		{
			return;
		}

		Potion predictedPotion = trackedPotion;
		addPreviousPotion(predictedPotion);
		mixPrediction = new MixPrediction(
			predictedPotion,
			inventorySlot,
			BatchStateResolver.potionCounts(inventory),
			gameTickCounter + 8);
		trackedPotion = null;
		trackedStep = 1;
		updateState();
	}

	private void reconcileMixPrediction(List<InventorySlot> inventory)
	{
		if (mixPrediction == null)
		{
			return;
		}

		Potion actualPotion = null;
		InventorySlot predictedSlot = inventory.get(mixPrediction.inventorySlot);
		if (predictedSlot.isPotion())
		{
			actualPotion = predictedSlot.getPotion();
		}
		else
		{
			EnumMap<Potion, Integer> actualCounts = BatchStateResolver.potionCounts(inventory);
			for (Potion potion : Potion.values())
			{
				if (actualCounts.getOrDefault(potion, 0)
					> mixPrediction.baselineCounts.getOrDefault(potion, 0))
				{
					actualPotion = potion;
					break;
				}
			}
		}

		if (actualPotion != null)
		{
			if (actualPotion != mixPrediction.potion)
			{
				replaceLastMixedPotion(actualPotion);
			}
			mixPrediction = null;
			trackedPotion = null;
			trackedStep = 1;
		}
		else if (gameTickCounter > mixPrediction.expiresAfterTick)
		{
			mixPrediction = null;
			trackedPotion = null;
			trackedStep = 1;
		}
	}

	private void synchronizeActionQueue()
	{
		if (guidance.getPhase() != Guidance.Phase.MIXING || guidance.getEntry() == null)
		{
			trackedPotion = null;
			trackedStep = 1;
			upcomingQueuePotions = Collections.emptyList();
			return;
		}

		Potion guidedPotion = guidance.getEntry().getPotion();
		if (trackedPotion != guidedPotion)
		{
			trackedPotion = guidedPotion;
			trackedStep = guidance.getStepNumber() >= 1 && guidance.getStepNumber() <= 4
				? guidance.getStepNumber() : 1;
		}
		else if (guidance.getStepNumber() > trackedStep && guidance.getStepNumber() <= 4)
		{
			trackedStep = guidance.getStepNumber();
		}
		refreshPotionQueue();
	}

	private void refreshPotionQueue()
	{
		if (trackedPotion == null || trackedStep < 1 || trackedStep > 4)
		{
			upcomingQueuePotions = Collections.emptyList();
			return;
		}

		List<Potion> potions = new ArrayList<>(4);
		potions.add(trackedPotion);
		int potionIndex = !plannedPotionQueue.isEmpty() && plannedPotionQueue.get(0) == trackedPotion ? 1 : 0;
		while (potions.size() < 4 && potionIndex < plannedPotionQueue.size())
		{
			potions.add(plannedPotionQueue.get(potionIndex++));
		}
		upcomingQueuePotions = Collections.unmodifiableList(potions);
	}

	private List<Potion> buildPlannedPotionQueue(List<InventorySlot> inventory, int maximum)
	{
		if (!refilling || cyclePlan == null || !cyclePlan.isValid())
		{
			return Collections.emptyList();
		}

		List<InventorySlot> projectedInventory = new ArrayList<>(inventory);
		List<Potion> potions = new ArrayList<>(maximum);
		while (potions.size() < maximum)
		{
			int nextSlot = cyclePlan.firstEmptySlot(projectedInventory);
			if (nextSlot < 0)
			{
				break;
			}
			Potion potion = BatchStateResolver.nextNeededPotion(
				plan, cyclePlan, projectedInventory, nextSlot);
			if (potion == null)
			{
				break;
			}
			potions.add(potion);
			projectedInventory.set(nextSlot, InventorySlot.fromItemId(potion.getUnfinishedItemId()));
		}
		return Collections.unmodifiableList(potions);
	}

	private void addPreviousPotion(Potion potion)
	{
		previousQueuePotions.addLast(potion);
		while (previousQueuePotions.size() > 2)
		{
			previousQueuePotions.removeFirst();
		}
	}

	private void replaceLastMixedPotion(Potion actualPotion)
	{
		if (previousQueuePotions.isEmpty())
		{
			return;
		}
		previousQueuePotions.removeLast();
		previousQueuePotions.addLast(actualPotion);
	}

	private void resetActionQueue()
	{
		previousQueuePotions.clear();
		upcomingQueuePotions = Collections.emptyList();
		plannedPotionQueue = Collections.emptyList();
		trackedPotion = null;
		trackedStep = 1;
		mixPrediction = null;
	}

	private static Component componentForLeverObject(int objectId)
	{
		for (Component component : Component.values())
		{
			if (component.getLever().getObjectId() == objectId)
			{
				return component;
			}
		}
		return null;
	}

	private static int countInventoryPotions(List<InventorySlot> inventory)
	{
		int count = 0;
		for (InventorySlot slot : inventory)
		{
			if (slot.isPotion())
			{
				count++;
			}
		}
		return count;
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

	private boolean isPlayerInMixologyRoom()
	{
		Player player = client.getLocalPlayer();
		Widget ordersOverlay = client.getWidget(InterfaceID.MM_OVERLAY, 0);
		return player != null
			&& player.getWorldLocation().getRegionID() == LAB_REGION
			&& player.getWorldLocation().getPlane() == 0
			&& ordersOverlay != null
			&& !ordersOverlay.isSelfHidden();
	}

	private void deactivateLab()
	{
		inLab = false;
		cyclePlan = null;
		currentPotionCounts.clear();
		resetActionQueue();
		refilling = true;
		guidance = Guidance.outside();
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

	BatchEntry getCycleEntry(int inventorySlot, Potion potion)
	{
		return cyclePlan == null || !cyclePlan.isValid()
			? null
			: cyclePlan.entryForSlot(inventorySlot, potion);
	}

	int getCurrentPotionCount(Potion potion)
	{
		return currentPotionCounts.getOrDefault(potion, 0);
	}

	int getCyclePotionCapacity()
	{
		return cyclePlan == null || !cyclePlan.isValid()
			? plan.size()
			: cyclePlan.getPotionCapacity();
	}

	List<Potion> getPreviousQueuePotions()
	{
		return Collections.unmodifiableList(new ArrayList<>(previousQueuePotions));
	}

	List<Potion> getUpcomingQueuePotions()
	{
		return upcomingQueuePotions;
	}

	private static final class MixPrediction
	{
		private final Potion potion;
		private final int inventorySlot;
		private final EnumMap<Potion, Integer> baselineCounts;
		private final int expiresAfterTick;

		private MixPrediction(
			Potion potion,
			int inventorySlot,
			EnumMap<Potion, Integer> baselineCounts,
			int expiresAfterTick)
		{
			this.potion = potion;
			this.inventorySlot = inventorySlot;
			this.baselineCounts = baselineCounts;
			this.expiresAfterTick = expiresAfterTick;
		}
	}

	@Provides
	MixologyBatchConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MixologyBatchConfig.class);
	}
}
