package dev.fiki.forgehax.main.mods.player;

import com.pixelmonmod.pixelmon.blocks.ApricornLeavesBlock;
import com.pixelmonmod.pixelmon.blocks.BerryLeavesBlock;
import dev.fiki.forgehax.api.BlockHelper;
import dev.fiki.forgehax.api.cmd.settings.IntegerSetting;
import dev.fiki.forgehax.api.cmd.settings.KeyBindingSetting;
import dev.fiki.forgehax.api.color.Colors;
import dev.fiki.forgehax.api.common.PriorityEnum;
import dev.fiki.forgehax.api.draw.Render2D;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.events.game.PreGameTickEvent;
import dev.fiki.forgehax.api.events.render.RenderPlaneEvent;
import dev.fiki.forgehax.api.extension.BlockEx;
import dev.fiki.forgehax.api.extension.EntityEx;
import dev.fiki.forgehax.api.extension.VectorEx;
import dev.fiki.forgehax.api.extension.VertexBuilderEx;
import dev.fiki.forgehax.api.key.KeyConflictContexts;
import dev.fiki.forgehax.api.key.KeyInput;
import dev.fiki.forgehax.api.key.KeyInputs;
import dev.fiki.forgehax.api.mod.AbstractMod;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import lombok.experimental.ExtensionMethod;
import lombok.val;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static dev.fiki.forgehax.main.Common.*;

@RegisterMod(
    name = "ApricornHarvest",
    description = "Use to harvest nearby apricorns",
    category = Category.PLAYER
)
@ExtensionMethod({BlockEx.class, EntityEx.class, VectorEx.class, VertexBuilderEx.class})
public class ApricornHarvest extends ToggleMod {

  Field apricornField = null;
  IntegerSetting radius = newIntegerSetting()
      .name("radius")
      .description("Radius in blocks")
      .defaultTo(8)
      .min(2)
      .max(64)
      .build();
  IntegerSetting interval = newIntegerSetting()
      .name("interval")
      .description("Interval in ms")
      .defaultTo(100)
      .min(20)
      .max(4000)
      .build();
  private final KeyBindingSetting harvestBind;
  void onHarvest(KeyBinding key)
  {
    fillBlocks();
  }
  public ApricornHarvest() {
/*
          .name("bind")
          .description("Key bind to enable the mod")
          .unbound()
          .defaultKeyName()
          .defaultKeyCategory()
          .conflictContext(KeyConflictContexts.inGame())
          .keyDownListener(this::onKeyDown)
          .keyPressedListener(this::onKeyPressed)
          .keyReleasedListener(this::onKeyReleased)
          .build();
      */
    harvestBind = newKeyBindingSetting()
        .name("harvestBind")
        .description("Key bind to start harvesting")
        .unbound()
        .keyName("harvest")
        .defaultKeyCategory()
        .conflictContext(KeyConflictContexts.inGame())
        .keyPressedListener(this::onHarvest)
        .build();
    try {
      apricornField = ApricornLeavesBlock.class.getDeclaredField("apricorn");
      apricornField.setAccessible(true);
    } catch (Throwable ex) {
    }
  }

  @Override
  protected void onEnabled() {
    fillBlocks();
  }

  final ArrayList<BlockPos> blocks = new ArrayList<>();

  void fillBlocks() {
    val player = getLocalPlayer();
    ClientWorld world = getWorld();
    Vector3d playerPos = player.getPosition(1.0f);
    List<BlockPos> _blocks = BlockHelper.getBlocksInRadius(playerPos, (double) radius.getValue());
    for (BlockPos pos : _blocks) {
      BlockState state = world.getBlockState(pos);
      Block block = state.getBlock();
      if (!(block instanceof ApricornLeavesBlock ||
          block instanceof BerryLeavesBlock)) continue;
      Integer age = state.getValue(block instanceof ApricornLeavesBlock
          ? ApricornLeavesBlock.AGE : BerryLeavesBlock.AGE);
      if (age < 2) continue;
      if (!blocks.contains(pos)) {
        blocks.add(pos);
      }

    }
  }

  boolean harvest(BlockPos pos, BlockState state, Block block,
                  ClientPlayerEntity player, ClientWorld world, PlayerController controller) {
    Vector3d pos3d = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
    Direction dir = Direction.DOWN;
    BlockRayTraceResult res = new BlockRayTraceResult(pos3d, dir, pos, false);
    try {
      Integer age = state.getValue(block instanceof ApricornLeavesBlock
          ? ApricornLeavesBlock.AGE : BerryLeavesBlock.AGE);
      if (age < 2) return false;
      controller.useItemOn(player, world, Hand.MAIN_HAND, res);
      return true;
    } catch (Throwable x) {
    }
    return false;
  }


  long nextUpdate = 0;

  @SubscribeListener
  public void onTick(PreGameTickEvent event) {
    long time = System.currentTimeMillis();
    if (time < nextUpdate || blocks.isEmpty()) return;
    val player = getLocalPlayer();
    val controller = getPlayerController();
    ClientWorld world = getWorld();
    BlockPos pos;
    int idx;
    idx = blocks.size() - 1;
    pos = blocks.get(idx);

    BlockState state = world.getBlockState(pos);
    Block block = state.getBlock();
    if (harvest(pos, state, block,
        player, world, controller)) {
      nextUpdate = time + interval.getValue();
    }
    blocks.remove(idx);
  }

  @SubscribeListener(priority = PriorityEnum.LOW)
  public void onRender2D(final RenderPlaneEvent.Back event) {
    int size = blocks.size();
    if (size == 0) return;
    val buffers = getBufferProvider();
    val source = buffers.getBufferSource();
    val stack = event.getStack();
    stack.pushPose();
    String string = String.format("%d blocks left", size);
    float x = (float) (Render2D.getStringWidth(string) / 2.f);
    float y = (float) Render2D.getStringHeight();

    stack.scale(1.f, 1.f, 0.f);
    stack.translate(-x, -y, 0.d);

    Render2D.renderString(source, string, 180, 0, Colors.PINK, true);
    stack.popPose();
    source.endBatch();
    RenderHelper.setupFor3DItems();
  }
}
