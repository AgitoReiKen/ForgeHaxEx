package dev.fiki.forgehax.main.mods.player;

import dev.fiki.forgehax.api.cmd.settings.BooleanSetting;
import dev.fiki.forgehax.api.cmd.settings.FloatSetting;
import dev.fiki.forgehax.api.cmd.settings.IntegerSetting;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.events.entity.LocalPlayerUpdateEvent;
import dev.fiki.forgehax.api.events.game.PreGameTickEvent;
import dev.fiki.forgehax.api.key.BindingHelper;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import lombok.val;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;

import static dev.fiki.forgehax.main.Common.*;

@RegisterMod(
    name = "AutoWalk",
    description = "Automatically walks forward",
    category = Category.PLAYER
)
public class AutoWalkMod extends ToggleMod {

  public final BooleanSetting stop_at_unloaded_chunks = newBooleanSetting()
      .name("stop-at-unloaded-chunks")
      .description("Stops moving at unloaded chunks")
      .defaultTo(true)
      .build();

  @Override
  protected void onEnabled() {
    BindingHelper.disableContextHandler(getGameSettings().keyUp);
  }

  @Override
  public void onDisabled() {
    getGameSettings().keyUp.setDown(false);
    BindingHelper.restoreContextHandler(getGameSettings().keyUp);
  }
  IntegerSetting rotationInterval = newIntegerSetting()
      .name("rotinterval")
      .description("Interval in ms for rotation")
      .defaultTo(1000)
      .min(0)
      .max(1000000)
      .build();
  FloatSetting rotation = newFloatSetting()
      .name("rot")
      .description("Rotation to apply")
      .defaultTo(90)
      .min(-180)
      .max(180)
      .build();

  long lastUpdate =0;
  @SubscribeListener
  public void onUpdate(LocalPlayerUpdateEvent event) {
    getGameSettings().keyUp.setDown(true);
    long now = System.currentTimeMillis();
    if (now >= lastUpdate)
    {
      lastUpdate = now + rotationInterval.getValue();
      val player = getLocalPlayer();
      float f = MathHelper.wrapDegrees(player.yRot);
      player.yRot = f + rotation.getValue();
    }
    if (stop_at_unloaded_chunks.getValue()) {
      if (!getWorld().isAreaLoaded(getLocalPlayer().blockPosition(), 1)) {
        getGameSettings().keyUp.setDown(false);
      }
    }
  }
}
