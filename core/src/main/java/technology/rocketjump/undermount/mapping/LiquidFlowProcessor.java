package technology.rocketjump.undermount.mapping;

import com.badlogic.gdx.ai.msg.MessageDispatcher;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.ai.msg.Telegraph;
import com.badlogic.gdx.math.GridPoint2;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import technology.rocketjump.undermount.gamecontext.GameContext;
import technology.rocketjump.undermount.gamecontext.Updatable;
import technology.rocketjump.undermount.mapping.factories.WaterFlowCalculator;
import technology.rocketjump.undermount.mapping.tile.CompassDirection;
import technology.rocketjump.undermount.mapping.tile.MapTile;
import technology.rocketjump.undermount.mapping.tile.underground.TileLiquidFlow;
import technology.rocketjump.undermount.mapping.tile.underground.UnderTile;
import technology.rocketjump.undermount.materials.GameMaterialDictionary;
import technology.rocketjump.undermount.materials.model.GameMaterial;
import technology.rocketjump.undermount.messaging.MessageType;
import technology.rocketjump.undermount.rendering.ScreenWriter;
import technology.rocketjump.undermount.rendering.camera.GlobalSettings;

import java.util.*;

import static technology.rocketjump.undermount.mapping.factories.WaterFlowCalculator.CHANCE_SINGLE_WATER_EVAPORATES;
import static technology.rocketjump.undermount.mapping.tile.underground.TileLiquidFlow.MAX_LIQUID_FLOW_PER_TILE;

@Singleton
public class LiquidFlowProcessor implements Updatable, Telegraph {

	private final GameMaterial waterMaterial;
	private GameContext gameContext;
	private Deque<MapTile> currentLiquidFlowTiles = new ArrayDeque<>();
	private Deque<MapTile> nextActiveLiquidFlowTiles = new ArrayDeque<>();
	private List<WaterFlowCalculator.FlowTransition> transitionsToApply = new ArrayList<>();

	private final ScreenWriter screenWriter;

	@Inject
	public LiquidFlowProcessor(GameMaterialDictionary gameMaterialDictionary, MessageDispatcher messageDispatcher, ScreenWriter screenWriter) {
		// TODO all liquid flow is currently water only, needs to be based on what is adding liquid to flow
		this.waterMaterial = gameMaterialDictionary.getByName("Water");
		this.screenWriter = screenWriter;

		messageDispatcher.addListener(this, MessageType.ADD_LIQUID_TO_FLOW);
	}

	@Override
	public void update(float deltaTime) {
		transitionsToApply.clear();
		float numUpdatesPerSecond = 1000f;
		int numPerFrame = Math.round(numUpdatesPerSecond * deltaTime);
		int numTilesToUpdateThisFrame = Math.min(numPerFrame, Math.max(1, currentLiquidFlowTiles.size()));

		if (GlobalSettings.DEV_MODE) {
			screenWriter.printLine("Active flow tiles: " + currentLiquidFlowTiles.size());
			screenWriter.printLine("Next active flow tiles: " + nextActiveLiquidFlowTiles.size());
			screenWriter.printLine("Updating this frame: " + numTilesToUpdateThisFrame);
		}

		if (currentLiquidFlowTiles.isEmpty()) {
			if (!nextActiveLiquidFlowTiles.isEmpty()) {
				currentLiquidFlowTiles.addAll(nextActiveLiquidFlowTiles);
				nextActiveLiquidFlowTiles.clear();
			}
		} else {
			while (numTilesToUpdateThisFrame > 0) {
				if (currentLiquidFlowTiles.isEmpty()) {
					break;
				} else {
					MapTile tileToUpdate = currentLiquidFlowTiles.pop();
					updateTile(tileToUpdate);
				}
				numTilesToUpdateThisFrame--;
			}
		}

		transitionsToApply.forEach(this::transitionFlow);
	}

	private void updateTile(MapTile tileToUpdate) {
		if (tileToUpdate.getUnderTile().getLiquidFlow() == null) {
			return;
		}
		int cursorTileWaterAmount = tileToUpdate.getUnderTile().getLiquidFlow().getLiquidAmount();
		if (cursorTileWaterAmount == 0) {
			return;
		}
		GridPoint2 cursorPosition = tileToUpdate.getTilePosition();

		List<CompassDirection> randomisedDirections = new ArrayList<>(CompassDirection.CARDINAL_DIRECTIONS);
		Collections.shuffle(randomisedDirections, gameContext.getRandom());

		for (CompassDirection directionToTry : randomisedDirections) {
			MapTile tileInDirection = gameContext.getAreaMap().getTile(cursorPosition.x + directionToTry.getXOffset(), cursorPosition.y + directionToTry.getYOffset());

			if (tileInDirection != null && tileInDirection.getUnderTile() != null && tileInDirection.getUnderTile().liquidCanFlow()) {
				// Liquid flow tile to move to
				TileLiquidFlow liquidFlowInDirection = tileInDirection.getUnderTile().getOrCreateLiquidFlow();
				int liquidAmountInDirection = liquidFlowInDirection.getLiquidAmount();

//				if (liquidAmountInDirection != MAX_LIQUID_FLOW_PER_TILE) {
//					surroundedByMaxLiquid = false;
//				}

				// Only one water transition per tile
				if (liquidAmountInDirection < cursorTileWaterAmount) {
					transitionsToApply.add(new WaterFlowCalculator.FlowTransition(tileToUpdate, tileInDirection, directionToTry));
					if (liquidAmountInDirection < cursorTileWaterAmount / 2) {
						// Double move for more than twice different
						transitionsToApply.add(new WaterFlowCalculator.FlowTransition(tileToUpdate, tileInDirection, directionToTry));
					}
					break;
				}
			}
		}

		transitionsToApply.forEach(this::transitionFlow);
	}

	private void transitionFlow(WaterFlowCalculator.FlowTransition transition) {
		if (transition.source.getUnderTile().getLiquidFlow().getLiquidAmount() <= transition.target.getUnderTile().getOrCreateLiquidFlow().getLiquidAmount()) {
			// This transition is no longer valid
			return;
		}


		transition.source.getUnderTile().getLiquidFlow().decrementWater(transition.flowDirection, gameContext.getRandom());
		// activate tiles around source
		activateTile(transition.source);
		for (CompassDirection neighbourDirection : CompassDirection.CARDINAL_DIRECTIONS) {
			MapTile neighbourTile = gameContext.getAreaMap().getTile(transition.source.getTileX() + neighbourDirection.getXOffset(), transition.source.getTileY() + neighbourDirection.getYOffset());
			if (neighbourTile != null && neighbourTile.getUnderTile() != null && neighbourTile.getUnderTile().liquidCanFlow()) {
				activateTile(neighbourTile);
			}
		}

		boolean liquidEvaporated = transition.source.getUnderTile().getLiquidFlow().getLiquidAmount() == 0 && gameContext.getRandom().nextFloat() < CHANCE_SINGLE_WATER_EVAPORATES;
		if (!liquidEvaporated) {
			transition.target.getUnderTile().getOrCreateLiquidFlow().incrementWater(transition.flowDirection);
			transition.target.getUnderTile().getLiquidFlow().setLiquidMaterial(transition.source.getUnderTile().getLiquidFlow().getLiquidMaterial());
			// activate tiles around target
			activateTile(transition.target);
			for (CompassDirection neighbourDirection : CompassDirection.CARDINAL_DIRECTIONS) {
				MapTile neighbourTile = gameContext.getAreaMap().getTile(transition.target.getTileX() + neighbourDirection.getXOffset(), transition.target.getTileY() + neighbourDirection.getYOffset());
				if (neighbourTile != null && neighbourTile.getUnderTile() != null && neighbourTile.getUnderTile().liquidCanFlow()) {
					activateTile(neighbourTile);
				}
			}
		}

		if (transition.source.getUnderTile().getLiquidFlow().getLiquidAmount() == 0) {
			transition.source.getUnderTile().getLiquidFlow().setLiquidMaterial(null);
		}
	}

	@Override
	public boolean handleMessage(Telegram msg) {
		switch (msg.message) {
			case MessageType.ADD_LIQUID_TO_FLOW: {
				MapTile targetTile = (MapTile) msg.extraInfo;
				UnderTile underTile = targetTile.getOrCreateUnderTile();
				if (underTile.liquidCanFlow()) {
					TileLiquidFlow liquidFlow = underTile.getOrCreateLiquidFlow();
					if (liquidFlow.getLiquidAmount() < MAX_LIQUID_FLOW_PER_TILE) {
						liquidFlow.setLiquidAmount(liquidFlow.getLiquidAmount() + 1);
						liquidFlow.setLiquidMaterial(waterMaterial);
					}
					activateTile(targetTile);
				}
				return true;
			}
			default:
				throw new IllegalArgumentException("Unexpected message type " + msg.message + " received by " + this + ", " + msg);
		}
	}

	@Override
	public void onContextChange(GameContext gameContext) {
		this.gameContext = gameContext;
	}

	private void activateTile(MapTile tileToUpdate) {
		if (!nextActiveLiquidFlowTiles.contains(tileToUpdate)) {
			nextActiveLiquidFlowTiles.add(tileToUpdate);
		}
	}

	@Override
	public void clearContextRelatedState() {
		currentLiquidFlowTiles.clear();
		nextActiveLiquidFlowTiles.clear();
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}
}
