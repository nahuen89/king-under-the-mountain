package technology.rocketjump.undermount.entities.factories;

import com.badlogic.gdx.math.RandomXS128;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import technology.rocketjump.undermount.entities.model.physical.humanoid.Gender;
import technology.rocketjump.undermount.entities.model.physical.humanoid.HumanoidEntityAttributes;
import technology.rocketjump.undermount.gamecontext.GameContext;
import technology.rocketjump.undermount.materials.GameMaterialDictionary;
import technology.rocketjump.undermount.materials.model.GameMaterial;
import technology.rocketjump.undermount.misc.twitch.TwitchDataStore;
import technology.rocketjump.undermount.misc.twitch.model.TwitchViewer;
import technology.rocketjump.undermount.persistence.UserPreferences;

import java.util.Random;

@Singleton
public class HumanoidEntityAttributesFactory {

	private final HairColorFactory hairColorFactory;
	private final SkinColorFactory skinColorFactory;
	private final AccessoryColorFactory accessoryColorFactory;
	private final DwarvenNameGenerator nameGenerator;
	private final UserPreferences userPreferences;
	private final TwitchDataStore twitchDataStore;
	private final Random random = new RandomXS128();
	private final GameMaterial fleshMaterial;

	@Inject
	public HumanoidEntityAttributesFactory(HairColorFactory hairColorFactory, SkinColorFactory skinColorFactory,
										   AccessoryColorFactory accessoryColorFactory, DwarvenNameGenerator nameGenerator,
										   UserPreferences userPreferences, TwitchDataStore twitchDataStore,
										   GameMaterialDictionary gameMaterialDictionary) {
		this.hairColorFactory = hairColorFactory;
		this.skinColorFactory = skinColorFactory;
		this.accessoryColorFactory = accessoryColorFactory;
		this.nameGenerator = nameGenerator;
		this.userPreferences = userPreferences;
		this.twitchDataStore = twitchDataStore;

		this.fleshMaterial = gameMaterialDictionary.getByName("Flesh");
	}

	public HumanoidEntityAttributes create(GameContext gameContext) {
		HumanoidEntityAttributes attributes = new HumanoidEntityAttributes(random.nextLong(),
				hairColorFactory.randomHairColor(random),
				skinColorFactory.randomSkinColor(random),
				accessoryColorFactory.randomAccessoryColor(random), fleshMaterial);

		if (twitchSettlerNameReplacementsEnabled()) {
			for (TwitchViewer twitchViewer : twitchDataStore.getPrioritisedViewers()) {
				if (!gameContext.getSettlementState().usedTwitchViewers.contains(twitchViewer)) {
					attributes.setName(twitchViewer.toName());
					gameContext.getSettlementState().usedTwitchViewers.add(twitchViewer);
					break;
				}
			}
		}

		if (attributes.getName() == null) {
			attributes.setName(nameGenerator.create(attributes.getSeed(), attributes.getGender()));
		}


		if (random.nextFloat() <= chanceToHaveHair(attributes)) {
			attributes.setHasHair(true);
		} else {
			attributes.setHasHair(false);
		}

		return attributes;
	}

	private boolean twitchSettlerNameReplacementsEnabled() {
		return Boolean.parseBoolean(userPreferences.getPreference(UserPreferences.PreferenceKey.TWITCH_INTEGRATION_ENABLED, "false")) &&
				Boolean.parseBoolean(userPreferences.getPreference(UserPreferences.PreferenceKey.TWITCH_VIEWERS_AS_SETTLER_NAMES, "false"));
	}

	private float chanceToHaveHair(HumanoidEntityAttributes attributes) {
		// MODDING expose these values
		if (attributes.getGender().equals(Gender.MALE)) {
			return 0.3f;
		} else if (attributes.getGender().equals(Gender.FEMALE)) {
			return 1f;
		} else{
			return 0;
		}
	}
}
