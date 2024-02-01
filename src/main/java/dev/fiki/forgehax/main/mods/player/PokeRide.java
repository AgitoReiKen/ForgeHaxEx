package dev.fiki.forgehax.main.mods.player;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.parameters.ControlledMovementLogic;
import com.pixelmonmod.pixelmon.api.pokemon.species.parameters.mounted.MountedFlyingParameters;
import com.pixelmonmod.pixelmon.entities.pixelmon.AbstractMovesEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import dev.fiki.forgehax.api.cmd.settings.DoubleSetting;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.events.entity.LivingUpdateEvent;
import dev.fiki.forgehax.api.extension.EntityEx;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;

import java.lang.reflect.Field;

import static dev.fiki.forgehax.main.Common.getLocalPlayer;
import static dev.fiki.forgehax.main.Common.getMountedEntity;

@RegisterMod(
    name = "PokeRide",
    description = "Change the stats of driven pokemon",
    category = Category.PLAYER
)
public class PokeRide extends ToggleMod {
  private final DoubleSetting jumpHeight = newDoubleSetting()
      .name("jump-height")
      .description("Modified horse jump height attribute. Default: 1")
      .defaultTo(1.0D)
      .build();

  private final DoubleSetting speed = newDoubleSetting()
      .name("speed")
      .description("Base speed")
      .defaultTo(AbstractMovesEntity.MIN_MOVE_MULTIPLIER)
      .min(AbstractMovesEntity.MIN_MOVE_MULTIPLIER)
      .max(AbstractMovesEntity.MAX_MOVE_MULTIPLIER)
      .build();

  Field flyPowerField = null;
  Field moveMultiplierField = null;
  Field accelerationRateField = null;
  Field decelerationRateField = null;
  Field maxFlySpeedField = null;

  public PokeRide() {

    try {
      accelerationRateField = MountedFlyingParameters.class.getDeclaredField("accelerationRate");
      accelerationRateField.setAccessible(true);
    } catch (Throwable x) {
    }
    try {
      decelerationRateField = MountedFlyingParameters.class.getDeclaredField("decelerationRate");
      decelerationRateField.setAccessible(true);
    } catch (Throwable x) {
    }
    try {
      maxFlySpeedField = MountedFlyingParameters.class.getDeclaredField("maxFlySpeed");
      maxFlySpeedField.setAccessible(true);
    } catch (Throwable x) {
    }
  }

  @Override
  public void onDisabled() {
    if (getMountedEntity() instanceof AbstractHorseEntity) {
      applyStats(jumpHeight.getDefaultValue(), speed.getDefaultValue());
    }
  }

  @SubscribeListener
  public void onLivingUpdate(LivingUpdateEvent event) throws IllegalAccessException {
    if (EntityEx.isDrivenByPlayer(event.getLiving())
        && getMountedEntity() instanceof PixelmonEntity) {

      double newSpeed = speed.getValue();
      PixelmonEntity entity = (PixelmonEntity) getMountedEntity();
      if (maxFlySpeedField != null)
      {
        MountedFlyingParameters mountedFlyingParameters = (MountedFlyingParameters)
            entity.getPokemon().getForm().getMovement().getMountedFlyingParameters();
        maxFlySpeedField.set(mountedFlyingParameters, newSpeed);
      }
    }
  }

  private void applyStats(double newJump, double newSpeed) {
    LivingEntity living = (LivingEntity) getMountedEntity();
    if (living != null) {
      living.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(newJump);
      living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);

    }
  }
}
