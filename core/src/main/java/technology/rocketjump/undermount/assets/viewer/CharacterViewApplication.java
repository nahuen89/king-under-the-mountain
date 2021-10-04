package technology.rocketjump.undermount.assets.viewer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.msg.MessageDispatcher;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.google.inject.Guice;
import com.google.inject.Injector;
import technology.rocketjump.undermount.assets.entities.item.model.ItemPlacement;
import technology.rocketjump.undermount.assets.entities.model.ColoringLayer;
import technology.rocketjump.undermount.entities.EntityAssetUpdater;
import technology.rocketjump.undermount.entities.components.ItemAllocationComponent;
import technology.rocketjump.undermount.entities.components.humanoid.ProfessionsComponent;
import technology.rocketjump.undermount.entities.factories.*;
import technology.rocketjump.undermount.entities.model.Entity;
import technology.rocketjump.undermount.entities.model.physical.creature.*;
import technology.rocketjump.undermount.entities.model.physical.item.ItemEntityAttributes;
import technology.rocketjump.undermount.entities.model.physical.item.ItemTypeDictionary;
import technology.rocketjump.undermount.entities.model.physical.plant.PlantEntityAttributes;
import technology.rocketjump.undermount.gamecontext.GameContext;
import technology.rocketjump.undermount.guice.UndermountGuiceModule;
import technology.rocketjump.undermount.jobs.ProfessionDictionary;
import technology.rocketjump.undermount.materials.GameMaterialDictionary;
import technology.rocketjump.undermount.rendering.RenderMode;
import technology.rocketjump.undermount.rendering.ScreenWriter;
import technology.rocketjump.undermount.rendering.camera.PrimaryCameraWrapper;
import technology.rocketjump.undermount.rendering.entities.EntityRenderer;

import java.util.Random;

import static technology.rocketjump.undermount.assets.entities.model.EntityAssetOrientation.*;

/**
 * This class is to be used from a separate desktop launcher for checking (and reloading) character asset definitions
 */
public class CharacterViewApplication extends ApplicationAdapter {

	private SpriteBatch batch;
	private ShapeRenderer shapeRenderer;

	private EntityRenderer entityRenderer;
	private HumanoidEntityFactory humanoidEntityFactory;
	private PrimaryCameraWrapper cameraManager;

	private CharacterViewUI ui;

	private Entity currentEntity;
	private CreatureEntityAttributes attributes;
	private Color skinColor, hairColor, accessoryColor;
	private ScreenWriter screenWriter;

	private Vector2 rotation = new Vector2(0, 1);
	private AccessoryColorFactory accessoryColorFactory;
	private RaceDictionary raceDictionary;

	// Look at https://github.com/EsotericSoftware/tablelayout for laying out UI

	@Override
	public void create () {
		Injector injector = Guice.createInjector(new UndermountGuiceModule());
		this.entityRenderer = injector.getInstance(EntityRenderer.class);
		this.humanoidEntityFactory = injector.getInstance(HumanoidEntityFactory.class);
		this.cameraManager = injector.getInstance(PrimaryCameraWrapper.class);
		this.screenWriter = injector.getInstance(ScreenWriter.class);
		this.accessoryColorFactory = injector.getInstance(AccessoryColorFactory.class);
		this.raceDictionary = injector.getInstance(RaceDictionary.class);
		screenWriter.offsetPosition.x = 250f;

		batch = new SpriteBatch();
		shapeRenderer = new ShapeRenderer();

		Random random = new Random();
		skinColor = new SkinColorFactory().randomSkinColor(random);
		hairColor = new HairColorFactory().randomHairColor(random);
		accessoryColor = accessoryColorFactory.randomAccessoryColor(random);
		Race race = raceDictionary.getByName("Dwarf");
		attributes = new CreatureEntityAttributes(race, random.nextLong(), hairColor, skinColor, accessoryColor);
		attributes.setGender(Gender.NONE);
		Vector2 facing = new Vector2(0, 0f);
		Vector2 position = new Vector2(cameraManager.getCamera().viewportWidth * 0.75f, cameraManager.getCamera().viewportHeight * 0.8f);
		GameContext gameContext = new GameContext();
		gameContext.setRandom(new RandomXS128());
		currentEntity = humanoidEntityFactory.create(attributes, position, facing, null, null, gameContext);

		ProfessionsComponent professionsComponent = currentEntity.getComponent(ProfessionsComponent.class);
		professionsComponent.add(injector.getInstance(ProfessionDictionary.class).getByName("CARPENTER"), 0.8f);

		injector.getInstance(EntityAssetUpdater.class).updateEntityAssets(currentEntity);

		Entity heldItem = createItemEntity("Resource-Hemp-Bundle", injector, ItemPlacement.BEING_CARRIED);
		ItemAllocationComponent itemAllocationComponent = heldItem.getOrCreateComponent(ItemAllocationComponent.class);
		itemAllocationComponent.init(heldItem, null, null);

//		EquippedItemComponent equippedItemComponent = currentEntity.getOrCreateComponent(EquippedItemComponent.class);
//		equippedItemComponent.setEquippedItem(heldItem, currentEntity, new MessageDispatcher());
		HaulingComponent haulingComponent = new HaulingComponent();
		haulingComponent.setHauledEntity(heldItem, new MessageDispatcher(), currentEntity);
		currentEntity.addComponent(haulingComponent);

		ui = injector.getInstance(CharacterViewUI.class);
		ui.init(currentEntity);

		Gdx.input.setInputProcessor(ui.getStage());
	}

	public static Entity createItemEntity(String itemTypeName, Injector injector, ItemPlacement itemPlacement) {
		ItemEntityFactory entityFactory = injector.getInstance(ItemEntityFactory.class);

		ItemTypeDictionary itemTypeDictionary = injector.getInstance(ItemTypeDictionary.class);
		GameMaterialDictionary gameMaterialDictionary = injector.getInstance(GameMaterialDictionary.class);

		ItemEntityAttributes attributes = new ItemEntityAttributes(0);// or 1 or 2
		attributes.setQuantity(1);
		attributes.setItemType(itemTypeDictionary.getByName(itemTypeName));
//		attributes.setItemSize(ItemSize.SMALL);
//		attributes.setItemStyle(ItemStyle.STYLE_2);


		attributes.setMaterial(gameMaterialDictionary.getByName("Dolostone"));
		attributes.setMaterial(gameMaterialDictionary.getByName("Hematite"));
		attributes.setMaterial(gameMaterialDictionary.getByName("Ruby"));
		attributes.setMaterial(gameMaterialDictionary.getByName("Beech wood"));
		attributes.setMaterial(gameMaterialDictionary.getByName("Iron"));
		attributes.setMaterial(gameMaterialDictionary.getByName("Potato"));

		attributes.setColor(ColoringLayer.BRANCHES_COLOR, Color.BROWN);

		attributes.setColor(ColoringLayer.METAL_COLOR, new Color(0.7f, 0.7f, 0.8f, 1f));
		attributes.setItemPlacement(itemPlacement);

		return entityFactory.create(attributes, new GridPoint2(), true, new GameContext());
	}

	public static Entity createPlantEntity(String speciesName, Injector injector) {
		PlantEntityAttributesFactory entityAttributesFactory = injector.getInstance(PlantEntityAttributesFactory.class);
		PlantEntityAttributes attributes = entityAttributesFactory.createBySpeciesName(speciesName);

		PlantEntityFactory plantEntityFactory = injector.getInstance(PlantEntityFactory.class);
		return plantEntityFactory.create(attributes, null, new GameContext());
	}

	@Override
	public void render () {
		renderBackground();

		batch.begin();
		batch.setProjectionMatrix(cameraManager.getCamera().combined);
		Vector2 originalPosition = currentEntity.getLocationComponent().getWorldPosition().cpy();
		// Render each required orientation

		renderEntityWithOrientation(originalPosition, DOWN.toVector2(), 0, 0, RenderMode.DIFFUSE);
		renderEntityWithOrientation(originalPosition, DOWN_LEFT.toVector2(), -1, 0, RenderMode.DIFFUSE);
		renderEntityWithOrientation(originalPosition, DOWN_RIGHT.toVector2(), 1, 0, RenderMode.DIFFUSE);
		renderEntityWithOrientation(originalPosition, UP.toVector2(), 0, 1, RenderMode.DIFFUSE);
		renderEntityWithOrientation(originalPosition, UP_LEFT.toVector2(), -1, 1, RenderMode.DIFFUSE);
		renderEntityWithOrientation(originalPosition, UP_RIGHT.toVector2(), 1, 1, RenderMode.DIFFUSE);

		// Rotate vector over time
//		float deltaTime = Math.max(Gdx.graphics.getDeltaTime(), 1f);
//		float degreesToRotate = deltaTime * 180f / 50f;
//		rotation.rotate(-degreesToRotate);
//		renderEntityWithOrientation(originalPosition, rotation, 2, 0, RenderMode.DIFFUSE);
//		renderEntityWithOrientation(originalPosition, rotation, 2, -2, RenderMode.NORMALS);

//		renderEntityWithOrientation(originalPosition, DOWN.toVector2(), 0, -2, RenderMode.NORMALS);
//		renderEntityWithOrientation(originalPosition, DOWN_LEFT.toVector2(), -1, -2, RenderMode.NORMALS);
//		renderEntityWithOrientation(originalPosition, DOWN_RIGHT.toVector2(), 1, -2, RenderMode.NORMALS);
//		renderEntityWithOrientation(originalPosition, UP.toVector2(), 0, -1, RenderMode.NORMALS);
//		renderEntityWithOrientation(originalPosition, UP_LEFT.toVector2(), -1, -1, RenderMode.NORMALS);
//		renderEntityWithOrientation(originalPosition, UP_RIGHT.toVector2(), 1, -1, RenderMode.NORMALS);

		batch.end();

		ui.render();
		screenWriter.render();
	}

	private void renderEntityWithOrientation(Vector2 originalPosition, Vector2 orientation, float offsetX, float offsetY, RenderMode renderMode) {
		// Set orientation
		currentEntity.getLocationComponent().setWorldPosition(originalPosition.cpy().add(orientation), true, false);
		// Set position
		currentEntity.getLocationComponent().setWorldPosition(originalPosition.cpy().add(offsetX, offsetY), false, false);
		// Render
		entityRenderer.render(currentEntity, batch, renderMode, null, null, null);
		// Reset position
		currentEntity.getLocationComponent().setWorldPosition(originalPosition, false, false);
	}

	private void renderBackground() {
		Gdx.gl.glClearColor(0.6f, 0.6f, 0.6f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		shapeRenderer.setColor(0.7f, 0.7f, 0.7f, 1f);
		shapeRenderer.setProjectionMatrix(cameraManager.getCamera().combined);
		for (float x = 0; x <= cameraManager.getCamera().viewportWidth + 1f; x += 0.5f) {
			for (float y = 0; y <= cameraManager.getCamera().viewportHeight + 1f; y += 0.5f) {
				boolean xEven = Math.round(x) - x < 0.001f;
				boolean yEven = Math.round(y) - y < 0.001f;

				if ((xEven && !yEven) || (!xEven && yEven)) {
					shapeRenderer.rect(x, y, 0.5f, 0.5f);
				}
			}
		}
		shapeRenderer.end();

	}

	@Override
	public void resize (int width, int height) {
		ui.onResize(width, height);
		cameraManager.onResize(width, height);
		cameraManager.getCamera().zoom = 0.5f;
		cameraManager.getCamera().update();
		screenWriter.onResize(width, height);

		Vector3 newPosition = new Vector3(width, height, 0);
		newPosition.x = newPosition.x * 0.65f;
		newPosition.y = newPosition.y * 0.4f;
		cameraManager.getCamera().unproject(newPosition);
		// Round to nearest tile boundary
		newPosition.x = Math.round(newPosition.x);
		newPosition.y = Math.round(newPosition.y);
		currentEntity.getLocationComponent().setWorldPosition(new Vector2(newPosition.x, newPosition.y), false);
	}

	@Override
	public void dispose () {
		ui.dispose();
		batch.dispose();
	}
}