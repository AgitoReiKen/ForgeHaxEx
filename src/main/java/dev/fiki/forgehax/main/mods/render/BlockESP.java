package dev.fiki.forgehax.main.mods.render;

import dev.fiki.forgehax.api.cmd.listener.ICommandListener;
import dev.fiki.forgehax.api.draw.Render2D;
import dev.fiki.forgehax.api.events.render.RenderPlaneEvent;
import dev.fiki.forgehax.api.extension.BlockEx;
import net.minecraft.world.chunk.ChunkSection;
import org.jocl.*;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fiki.forgehax.api.cmd.ICommand;
import dev.fiki.forgehax.api.cmd.argument.Arguments;
import dev.fiki.forgehax.api.cmd.listener.IOnUpdate;
import dev.fiki.forgehax.api.cmd.settings.BooleanSetting;
import dev.fiki.forgehax.api.cmd.settings.ColorSetting;
import dev.fiki.forgehax.api.cmd.settings.IntegerSetting;
import dev.fiki.forgehax.api.cmd.settings.collections.SimpleSettingList;
import dev.fiki.forgehax.api.color.Color;
import dev.fiki.forgehax.api.color.Colors;
import dev.fiki.forgehax.api.draw.GeometryMasks;
import dev.fiki.forgehax.api.draw.RenderTypeEx;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.events.render.RenderSpaceEvent;
import dev.fiki.forgehax.api.extension.EntityEx;
import dev.fiki.forgehax.api.extension.VectorEx;
import dev.fiki.forgehax.api.extension.VertexBuilderEx;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import it.unimi.dsi.fastutil.booleans.BooleanSet;
import lombok.experimental.ExtensionMethod;
import lombok.val;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static dev.fiki.forgehax.main.Common.*;

@RegisterMod(
    name = "BlockESP",
    description = "Search blocks by pattern",
    category = Category.RENDER
)
@ExtensionMethod({BlockEx.class, EntityEx.class, VectorEx.class, VertexBuilderEx.class})
public class BlockESP extends ToggleMod {
  final IntegerSetting range;

  final ColorSetting defaultColor;
  final SimpleSettingList<Color> colors;
  final IntegerSetting defaultMax;
  final SimpleSettingList<Integer> maxs;
  final SimpleSettingList<Pattern> classes;
  final BooleanSetting debugMode;
  final IntegerSetting threads;
  final ArrayList<ArrayList<ResourceLocation>> classLocations;
  final IntegerSetting updateInterval;
  final BooleanSetting showPerformance;
  ThreadPoolExecutor executor;
  final ArrayList<Lock> locks;
  ArrayList<ArrayList<BlockPos>> cachedBlockDebugLists;
  ArrayList<ArrayList<BlockPos>> blockDebugLists;
  ArrayList<ArrayList<BlockData>> blockDatas;
  ArrayList<ArrayList<BlockData>> cachedBlockDatas;

  private class OnClassesUpdateListener implements IOnUpdate {
    @Override
    public void onUpdate(ICommand command) {
      recreateExecutor();
      int clsSize = classes.size();
      int maxsSize = maxs.size();
      int colorsSize = colors.size();

      int maxsDiff = clsSize - maxsSize;
      int colorsDiff = clsSize - colorsSize;
      if (maxsDiff > 0) {
        maxs.addAll(Collections.nCopies(maxsDiff, defaultMax.intValue()));
      } else if (maxsDiff < 0) {
        maxs.subList(maxs.size() - 1 + maxsDiff, maxs.size() - 1).clear();
      }

      if (colorsDiff > 0) {
        colors.addAll(Collections.nCopies(colorsDiff, defaultColor.getValue()));
      } else if (colorsDiff < 0) {
        colors.subList(colors.size() - 1 + colorsDiff, colors.size() - 1).clear();
      }
      updateClassLocations();
      updateBlockDataList(threads.intValue());
    }
  }
  private class OnColorsUpdateListener implements IOnUpdate
  {
    @Override
    public void onUpdate(ICommand command) {
      recreateExecutor();
      for (int t = 0; t < threads.intValue(); ++t) {
        for (int i = 0; i < classLocations.size(); ++i) {
          blockDatas.get(t).get(i).color = i >= colors.size() ? defaultColor.getValue() : colors.get(i);
          cachedBlockDatas.get(t).get(i).color = i >= colors.size() ? defaultColor.getValue() : colors.get(i);
        }
      }
    }
  }
  private class OnMaxUpdateListener implements IOnUpdate
  {
    @Override
    public void onUpdate(ICommand command) {
      recreateExecutor();
      for (int t = 0; t < threads.intValue(); ++t) {
        for (int i = 0; i < classLocations.size(); ++i) {
          blockDatas.get(t).get(i).max = i >= maxs.size() ? defaultMax.getValue() : maxs.get(i);
          cachedBlockDatas.get(t).get(i).max = i >= maxs.size() ? defaultMax.getValue() : maxs.get(i);
        }
      }
    }
  }
  public BlockESP() {
    debugMode = newBooleanSetting()
        .name("debug")
        .description("Render all blocks near the player. Don't use it with range > 1")
        .defaultTo(false)
        .build();
    showPerformance = newBooleanSetting()
        .name("perf")
        .description("Shows how fast threads do their job")
        .defaultTo(true)
        .build();
    defaultMax = newIntegerSetting()
        .name("max")
        .description("Default max blocks to render for class")
        .defaultTo(128)
        .changedListener(
            (from, to) -> {
              new OnMaxUpdateListener().onUpdate(this);
            }
        )
        .build();
    defaultColor = newColorSetting()
        .name("color")
        .description("Default render color")
        .defaultTo(Color.of(255, 255, 255, 127))
        .changedListener((from, to) -> {
          new OnColorsUpdateListener().onUpdate(this);
        })
        .build();
    classes = newSimpleSettingList(Pattern.class)
        .name("classes")
        .description("Regexp patterns to search for namespace:path")
        .argument(Arguments.newPatternArgument()
            .label("regexp").build()
        )
        .supplier(ArrayList::new)
        .defaultsTo(Pattern.compile("minecraft:.*ore.*"))
        .listener(new OnClassesUpdateListener())
        .build();
    maxs = newSimpleSettingList(Integer.class)
        .name("maxs")
        .description("Max blocks to render for class")
        .argument(Arguments.newIntegerArgument()
            .label("value").build()
        )
        .supplier(ArrayList::new)
        .defaultsTo(new Integer(1))
        .listener(new OnMaxUpdateListener())
        .build();
    colors = newSimpleSettingList(Color.class)
        .name("colors")
        .description("Color to render a class")
        .argument(Arguments.newColorArgument()
            .label("value").build())
        .supplier(ArrayList::new)
        .defaultsTo(defaultColor.getValue())
        .listener(new OnColorsUpdateListener())
        .build();
    range = newIntegerSetting()
        .name("range")
        .description("Search radius in chunk sizes (searched as a sphere)")
        .defaultTo(16)
        .min(1)
        .max(32)
        .build();
    updateInterval = newIntegerSetting()
        .name("freq")
        .description("Frequency of updates in ms")
        .defaultTo(64)
        .min(16)
        .max(2000)
        .build();
    threads = newIntegerSetting()
        .name("threads")
        .description("Number of threads to use")
        .changedListener((from, to) -> {
          onThreadsUpdate(to);
        })
        .min(1)
        // 16 is minimum range (in blocks), to split tasks properly for MT
        .max(16)
        .defaultTo(Runtime.getRuntime().availableProcessors() >= 16
            ? 15 : Runtime.getRuntime().availableProcessors() - 1)
        .build();
    recreateExecutor();
    classLocations = new ArrayList<>();
    blockDatas = new ArrayList<>();
    cachedBlockDatas = new ArrayList<>();
    locks = new ArrayList<>();
    blockDebugLists = new ArrayList<>();
    cachedBlockDebugLists = new ArrayList<>();
  }

  @Override
  public void onLoad() {
    updateClassLocations();
    onThreadsUpdate(threads.intValue());

//    CL.setExceptionsEnabled(true);
//    cl_platform_id[] platforms = new cl_platform_id[1];
//    CL.clGetPlatformIDs(platforms.length, platforms, null);
//    cl_device_id[] devices = new cl_device_id[1];
//    CL.clGetDeviceIDs(platforms[0], CL.CL_DEVICE_TYPE_GPU, devices.length, devices, null);
//    cl_context context = CL.clCreateContext(null, devices.length, devices, null, null, null);
//    cl_queue_properties properties = new cl_queue_properties();
//    cl_command_queue queue = CL.clCreateCommandQueueWithProperties(context, devices[0], properties, null);

  }

  @Override
  public void onDisabled() {
    recreateExecutor();
  }

  public void onThreadsUpdate(int _threads) {
    recreateExecutor();
    executor.setCorePoolSize(_threads);
    locks.clear();
    blockDebugLists.clear();
    cachedBlockDebugLists.clear();
    for (int i = 0; i < _threads; ++i) {
      locks.add(new ReentrantLock());
      blockDebugLists.add(new ArrayList<>());
      cachedBlockDebugLists.add(new ArrayList<>());
    }
    updateBlockDataList(_threads);
  }

  public void recreateExecutor() {
    if (executor != null)
      executor.shutdownNow();
    executor = new ThreadPoolExecutor(threads.intValue(), threads.getMaxValue(),
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>());
  }

  private void updateClassLocations() {
    classLocations.clear();
    for (int i = 0; i < classes.size(); ++i) {
      classLocations.add(new ArrayList<>());
      Pattern p = classes.get(i);
      final int _i = i;
      ForgeRegistries.BLOCKS.forEach(
          x ->
          {
            ResourceLocation registry = x.getRegistryName();
            if (registry == null) return;
            if (p.matcher(registry.toString()).matches()) classLocations.get(_i).add(registry);
          }
      );
      ForgeRegistries.TILE_ENTITIES.forEach(
          x ->
          {
            ResourceLocation registry = x.getRegistryName();
            if (registry == null) return;
            if (p.matcher(registry.toString()).matches()) classLocations.get(_i).add(registry);
          }
      );
      ForgeRegistries.FLUIDS.forEach(
          x ->
          {
            ResourceLocation registry = x.getRegistryName();
            if (registry == null) return;
            if (p.matcher(registry.toString()).matches()) classLocations.get(_i).add(registry);
          }
      );
    }
  }

  private void updateBlockDataList(int _threads) {
    blockDatas.clear();
    cachedBlockDatas.clear();
    for (int t = 0; t < _threads; ++t) {
      ArrayList<BlockData> res1 = new ArrayList<BlockData>();
      ArrayList<BlockData> res2 = new ArrayList<BlockData>();
      for (int i = 0; i < classLocations.size(); ++i) {
        res1.add(makeBlockData(i));
        res2.add(makeBlockData(i));
      }
      blockDatas.add(res1);
      cachedBlockDatas.add(res2);
    }
  }

  private BlockData makeBlockData(int classIndex) {
    Pattern name = classes.get(classIndex);
    Color color = colors.size() <= classIndex ? defaultColor.getValue() : colors.get(classIndex);
    Integer max = maxs.size() <= classIndex ? defaultMax.intValue() : maxs.get(classIndex);
    return new BlockData(name, max, color);
  }
  AtomicLong lastExecutionTime = new AtomicLong();
  private void fillBlockData() {
    Vector3d eyePos = getLocalPlayer().getEyePosition(1f);
    int renderDistance = Math.min(getGameSettings().renderDistance, range.intValue());
    BlockPos center = new BlockPos((int) eyePos.x, (int) eyePos.y, (int) eyePos.z);

    ClientWorld world = getWorld();
    int radius = renderDistance << 3;

    int _threads = threads.intValue();


    // 16 * 2 = 32

    //32 / 2
    // 16
    //0
    // -16 16
    // x1 = x + (partialRadius * t), x2 = x + (partialRadius * (t + 1)):
    // 0
    //x1 = -16, 0
    // x2 = 0, 16
    int partialRadius = radius / _threads;
    int x0 = center.getX() - radius;

    for (int t = 0; t < _threads; ++t) {
      final int x1 = x0 + 2 * (partialRadius * t) + (t == 0 ? 1 : 0);
      int x2 = x0 + 2 * (partialRadius * (t + 1));
      if (t + 1 == _threads) {
        x2 += radius - (partialRadius * _threads);
      }
      final int threadIdx = t;
      final int _x2 = x2;

      executor.execute(() -> {
         long start = System.currentTimeMillis();
          continueBlockSearch(threadIdx, center, radius, x1, _x2, world);
          long end = System.currentTimeMillis();
          long exec = end - start;
          lastExecutionTime.set(exec);
          log.debug("[{}] {}ms.", threadIdx, exec);
      });
    }
  }

  private void continueBlockSearch(int threadId, BlockPos center, int radius, int x1, int x2, ClientWorld world) {
    Lock lock = locks.get(threadId);
    ArrayList<ArrayList<Chunk>> chunks = new ArrayList<>();
    lock.lock();
    blockDebugLists.get(threadId).clear();
    for (int i = 0; i < classLocations.size(); ++i) {
      blockDatas.get(threadId).get(i).blocks.clear();
    }
    lock.unlock();

    BlockPos pos;
    int b = 0;
    int z1 = center.getZ() - radius;
    int z2 = center.getZ() + radius;
    int y1 = center.getY() - radius;
    int y2 = center.getY() + radius;
    int cx1 = x1 >> 4;
    int cz1 = z1 >> 4;
    int xd = Math.abs(x1 - x2);
    int zd = Math.abs(z1 - z2);
    int yd = Math.abs(y1 - y2);
    for (int x = 0; x < xd; ++x) {
      ArrayList<Chunk> res = new ArrayList<>();
      for (int z = 0; z < zd; ++z) {
        res.add((Chunk) world.getChunk(cx1 + x, cz1 + z));
      }
      chunks.add(res);
    }

    for (int x = x1; x < x2; ++x)
      for (int z = z1; z <= z2; ++z) {
        Chunk chunk = chunks.get((x >> 4) - cx1).get((z >> 4) - cz1);
        if (chunk.getHighestSectionPosition() == 0) continue;
        for (int y = y1; y <= y2; ++y) {
          b += y;
          pos = new BlockPos(x, y, z);
          //6ms
          //55ms
          checkAndAddBlock(threadId, pos, chunk);
        }
      }

    if (debugMode.getValue()) {
      lock.lock();
      cachedBlockDebugLists.get(threadId).clear();
      cachedBlockDebugLists.get(threadId).addAll(blockDebugLists.get(threadId));
      lock.unlock();
    }

    lock.lock();
    for (int i = 0; i < classLocations.size(); ++i) {
      cachedBlockDatas.get(threadId).get(i).blocks.clear();
      cachedBlockDatas.get(threadId).get(i).blocks.addAll(blockDatas.get(threadId).get(i).blocks);
    }
    lock.unlock();
  }

  private void checkAndAddBlock(int threadId, BlockPos pos, Chunk chunk) {
    // 19
    //73
    BlockState state = chunk.getBlockStateFast(pos);
    if (state == null || state.getMaterial() == Material.AIR) return;
    //180
    Lock lock = locks.get(threadId);
    if (debugMode.getValue()) {
      lock.lock();
      blockDebugLists.get(threadId).add(pos);
      lock.unlock();
    }
    Block block = state.getBlock();
    ResourceLocation registry = block.getRegistryName();

    //lock.lock();
    for (int i = 0; i < classLocations.size(); ++i) {
      BlockData blockData = blockDatas.get(threadId).get(i);
      if (blockData.max <= blockData.blocks.size()) continue;
      if (classLocations.get(i).contains(registry)) {
        lock.lock();
        blockDatas.get(threadId).get(i).blocks.add(pos);
        lock.unlock();
        return;
      }
    }
    //lock.unlock();
  }

  private long lastUpdate = 0;

  @SubscribeListener
  public void onRender(RenderSpaceEvent event) {
    if (!this.isEnabled()) return;
    BufferBuilder buffer = event.getBuffer();
    RenderSystem.enableBlend();
    val buffers = getBufferProvider().getBufferSource();
    val stack = event.getStack();
    //GlStateManager._depthMask(false);
    stack.pushPose();
    stack.translateVec(event.getProjectedPos().scale(-1));
    buffer.beginQuads(DefaultVertexFormats.POSITION_COLOR);

    long now = System.currentTimeMillis();
    boolean shouldUpdate = false;
    if (now >= lastUpdate + updateInterval.intValue()) {
      lastUpdate = now + updateInterval.intValue();
      shouldUpdate = true;
    }

    if (shouldUpdate) {
      // distance to chunk
      fillBlockData();
    }

    // Commit the job
    // Draw
    // [job fills lists]
    // Draw
    // Draw
    // Draw
    // Commit the job
    // Draw
    // [job fills lists]
    int sides = GeometryMasks.Quad.ALL;
    {
      for (int t = 0; t < threads.intValue(); ++t) {
        locks.get(t).lock();
        ArrayList<BlockData> blockDataList = cachedBlockDatas.get(t);
        final int size = blockDataList.size();
        for (int i = 0; i < size; ++i) {
          BlockData data = blockDataList.get(i);
          int blocksSize = data.blocks.size();
          for (int b = 0; b < blocksSize; ++b) {
            BlockPos pos = data.blocks.get(b);
            buffer.filledCube(new AxisAlignedBB(pos), sides, data.color, stack.getLastMatrix());
          }
        }
        locks.get(t).unlock();
      }
    }

    if (debugMode.getValue()) {
      int i = 0;
      for (int t = 0; t < threads.intValue(); ++t) {
        locks.get(t).lock();
        ArrayList<BlockPos> debugList = cachedBlockDebugLists.get(t);
        int debugListSize = debugList.size();
        for (int b = 0; b < debugListSize; ++b) {
          BlockPos pos = debugList.get(b);
          buffer.filledCube(new AxisAlignedBB(pos), sides,
              i % 2 == 1 ?
                  Color.of(40, 240, 40, 60) :
                  Color.of(240, 40, 40, 60),
              stack.getLastMatrix());
          i = (i + 1) % 2;
        }
        locks.get(t).unlock();

      }
    }

    buffer.draw();
    stack.popPose();

    //GlStateManager._depthMask(true);
  }
  @SubscribeListener
  public void onRender2D(final RenderPlaneEvent.Back event)
  {
    if (!this.isEnabled()) return;
    val buffers = getBufferProvider().getBufferSource();
    val stack = event.getStack();
    if (showPerformance.getValue())
    {
      RenderSystem.enableBlend();
      stack.pushPose();
      final long time = lastExecutionTime.get();
      String string = "Time: " + time + "ms. ";
      float x = (float) (Render2D.getStringWidth(string) / 2.f);
      float y = (float) Render2D.getStringHeight();

      stack.scale(1.f, 1.f, 0.f);
      stack.translate(-x, -y, 0.d);

      Render2D.renderString(
          buffers, string, 140, 0,
          (time >= updateInterval.getValue() ? Colors.RED :
              time * 2 >= updateInterval.getValue() ? Colors.ORANGE :
                  Colors.GREEN), true
      );
      stack.popPose();
      RenderSystem.disableBlend();
      RenderHelper.turnOff();
      RenderHelper.setupForFlatItems();

      buffers.endBatch(RenderTypeEx.blockTranslucentCull());
      buffers.endBatch(RenderTypeEx.blockCutout());
      buffers.endBatch(RenderType.glint());
      buffers.endBatch(RenderType.entityGlint());
      MC.renderBuffers().bufferSource().endBatch();
      buffers.endBatch();

      RenderHelper.setupFor3DItems();
    }
  }
  private class BlockData {
    public final Pattern name;
    public int max;
    public List<BlockPos> blocks;
    public Color color;

    BlockData(Pattern name, int max, Color color) {
      this.name = name;
      this.max = max;
      this.color = color;
      this.blocks = new ArrayList<>();
    }
  }
}
