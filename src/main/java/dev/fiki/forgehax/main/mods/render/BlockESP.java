package dev.fiki.forgehax.main.mods.render;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.fiki.forgehax.api.FileHelper;
import dev.fiki.forgehax.api.cmd.listener.ICommandListener;
import dev.fiki.forgehax.api.draw.Render2D;
import dev.fiki.forgehax.api.events.render.RenderPlaneEvent;
import dev.fiki.forgehax.api.extension.BlockEx;
import dev.fiki.forgehax.api.math.ScreenPos;
import dev.fiki.forgehax.api.math.VectorUtil;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jocl.*;
import com.google.common.collect.Sets;
import org.lwjgl.opengl.GL11;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.mojang.blaze3d.systems.RenderSystem.*;
import static dev.fiki.forgehax.main.Common.*;
import static org.lwjgl.opengl.GL11.*;

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
  final SimpleSettingList<Boolean> tracers;
  final BooleanSetting defaultTracers;
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
      int tracersSize = tracers.size();

      int maxsDiff = clsSize - maxsSize;
      int colorsDiff = clsSize - colorsSize;
      int tracersDiff = clsSize - tracersSize;
      if (maxsDiff > 0) {
        maxs.addAll(Collections.nCopies(maxsDiff, defaultMax.intValue()));
      } else if (maxsDiff < 0) {
        maxs.subList(maxsSize + maxsDiff, maxsSize).clear();
      }

      if (colorsDiff > 0) {
        colors.addAll(Collections.nCopies(colorsDiff, defaultColor.getValue()));
      } else if (colorsDiff < 0) {
        colors.subList(colorsSize + colorsDiff, colorsSize).clear();
      }

      if (tracersDiff > 0) {
        tracers.addAll(Collections.nCopies(tracersDiff, defaultTracers.getValue()));
      } else if (tracersDiff < 0) {
        tracers.subList(tracersSize + tracersDiff, tracersSize).clear();
      }
      updateClassLocations();
      updateBlockDataList(threads.intValue());
    }
  }

  private class OnColorsUpdateListener implements IOnUpdate {
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

  private class OnMaxUpdateListener implements IOnUpdate {
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
        .description("Render all blocks near the player. Range > 1 will cause lags!!!")
        .defaultTo(false)
        .build();
    showPerformance = newBooleanSetting()
        .name("perf")
        .description("Shows how fast threads do their job. If orange/red - increase number of threads and/or freq")
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
        .defaultsTo(new Integer(128))
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
        .description("Search radius in chunks")
        .defaultTo(16)
        .min(1)
        .max(32)
        .build();
    updateInterval = newIntegerSetting()
        .name("freq")
        .description("Frequency of updates in ms")
        .defaultTo(1000)
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
    tracers = newSimpleSettingList(Boolean.class)
        .name("tracers")
        .description("Show tracers")
        .supplier(ArrayList::new)
        .argument(Arguments.newBooleanArgument().label("value").build())
        .defaultsTo(false)
        .build();
    defaultTracers = newBooleanSetting()
        .name("tracer")
        .description("Default tracing setting")
        .defaultTo(false)
        .build();

    recreateExecutor();
    classLocations = new ArrayList<>();
    newSimpleCommand()
        .name("dumpit")
        .description("Dumps all class resources found atm")
        .argument(Arguments.newStringArgument().label("path").build())
        .executor(x -> {
          val arg = x.getFirst();
          String path = (String) arg.getValue();
          try {
            int bufferSize = 0x1000000;
            BufferedWriter writer = new BufferedWriter(new FileWriter(path), bufferSize);
            int written = 0;
            writer.write("[\n");
            for (ArrayList<ResourceLocation> arl:
                classLocations) {
                for (ResourceLocation rl : arl)
                {
                  ++written;
                  writer.write(String.format("\"%s\",\n", rl.toString()));
                }
            }
            writer.write("]");
            writer.close();
            x.inform(String.format("Successfully dumped %d classes", written));
          } catch (IOException e) {
            x.inform("Exception thrown: " + e.getMessage());
          }
        })
        .build();
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
    BlockPos center = new BlockPos((int) eyePos.x >> 4, (int) eyePos.y >> 4, (int) eyePos.z >> 4);
    ClientWorld world = getWorld();

    int _threads = threads.intValue();
    ArrayList<ImmutablePair<Vector3i, List<ChunkSection>>> sections = new ArrayList<>();
    {
      int x0 = center.getX() - renderDistance;
      int x1 = center.getX() + renderDistance;
      int y0 = Math.max(0, center.getY() - renderDistance);
      int y1 = Math.max(0, center.getY() + renderDistance);
      int z0 = center.getZ() - renderDistance;
      int z1 = center.getZ() + renderDistance;
      for (int x = x0; x <= x1; ++x)
        for (int z = z0; z <= z1; ++z) {
          Chunk chunk = world.getChunk(x, z);
          List<ChunkSection> _sections = Arrays.asList(chunk.getSections());
          if (_sections.size() > y0) {
            List<ChunkSection> subList = IntStream.rangeClosed(y0, Math.min(y1, _sections.size() - 1))
                .filter(i -> !ChunkSection.isEmpty(_sections.get(i)))
                .mapToObj(_sections::get)
                .collect(Collectors.toList());
            if (subList.isEmpty()) continue;
            sections.add(
                new ImmutablePair<>(new Vector3i(x, y0, z), subList)
            );
          }
        }
    }
    int sectionsLen = sections.size();
    int step = sectionsLen / _threads;

    for (int t = 0; t < _threads; ++t) {
      final int x1 = (step * t);
      int x2 = (step * (t + 1));
      if (t + 1 == _threads) {
        x2 += sectionsLen - (_threads * step);
      }
      final int threadIdx = t;
      final int _x2 = x2;
      val subList = sections.subList(x1, _x2);
      executor.execute(() -> {
        long start = System.currentTimeMillis();
        continueBlockSearch(threadIdx, subList);
        long end = System.currentTimeMillis();
        long exec = end - start;
        lastExecutionTime.set(exec);
        //log.debug("[{}] {}ms.", threadIdx, exec);
      });
    }
  }

  private void continueBlockSearch(int threadId, List<ImmutablePair<Vector3i, List<ChunkSection>>> sections) {
    final Lock lock = locks.get(threadId);
    lock.lock();
    blockDebugLists.get(threadId).clear();
    for (int i = 0; i < classLocations.size(); ++i) {
      blockDatas.get(threadId).get(i).blocks.clear();
    }
    lock.unlock();

    val blockDataList = blockDatas.get(threadId);
    sections.forEach((t) -> {
      final int baseX = t.left.getX() << 4;
      final int baseZ = t.left.getZ() << 4;
      final int baseY = t.left.getY() << 4;
      t.right.forEach((section) -> {
        for (int x = 0; x < 16; ++x)
          for (int z = 0; z < 16; ++z)
            for (int y = 0; y < 16; ++y) {
              BlockState state = section.getBlockState(x, y, z);
              if (state.getMaterial() == Material.AIR) continue;

              BlockPos pos = new BlockPos(baseX + x, section.bottomBlockY() + y, baseZ + z);
              if (debugMode.getValue()) {
                lock.lock();
                blockDebugLists.get(threadId).add(pos);
                lock.unlock();
              }

              Block block = state.getBlock();
              ResourceLocation registry = block.getRegistryName();

              for (int i = 0; i < classLocations.size(); ++i) {
                BlockData blockData = blockDataList.get(i);
                if (blockData.max <= blockData.blocks.size()) continue;
                if (classLocations.get(i).contains(registry)) {
                  lock.lock();
                  blockDataList.get(i).blocks.add(pos);
                  lock.unlock();
                  break;
                }
              }
            }
      });
    });

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

  private long lastUpdate = 0;

  @SubscribeListener
  public void onRender(RenderSpaceEvent event) {
    if (!this.isEnabled()) return;
    BufferBuilder buffer = event.getBuffer();
    RenderSystem.enableBlend();
    val buffers = getBufferProvider().getBufferSource();
    val stack = event.getStack();
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

    int sides = GeometryMasks.Quad.ALL;
// Enabling this will require to synchronize with tracers, which i think is expensive.
// So i will leave MAX be max * threads
//    int[] toDraw = new int[classLocations.size()];
//    for (int i = 0; i < classLocations.size(); ++i) {
//      toDraw[i] = (maxs.contains(i) ? maxs.get(i) : defaultMax.intValue());
//    }
    for (int t = 0; t < threads.intValue(); ++t) {
      locks.get(t).lock();
      ArrayList<BlockData> blockDataList = cachedBlockDatas.get(t);
      final int size = blockDataList.size();
      for (int i = 0; i < size; ++i) {
        BlockData data = blockDataList.get(i);
        int blocksSize = data.blocks.size();
//        if (toDraw[i] <= 0) break;
//        --toDraw[i];
        for (int b = 0; b < blocksSize; ++b) {
          BlockPos pos = data.blocks.get(b);
          buffer.filledCube(new AxisAlignedBB(pos), sides, data.color, stack.getLastMatrix());
        }
      }
      locks.get(t).unlock();
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

//    if (tracers.getValue())
//    {
//     Vector3d cameraPos = getGameRenderer().getMainCamera().getPosition();
//     cameraPos = new Vector3d(cameraPos.x, cameraPos.y + 0.01, cameraPos.z);
//     RenderSystem.disableBlend();
//     RenderSystem.enableDepthTest();
//     buffer.beginLines(DefaultVertexFormats.POSITION_COLOR);
//      Vector3d finalCameraPos = cameraPos;
//      drawn.forEach((c, l) ->
//     {
//       Color color = Color.of(c);
//       l.forEach((pos) -> {
//         VertexBuilderEx.line(buffer, finalCameraPos, Vector3d.atCenterOf(pos), color, stack.getLastMatrix());
//       });
//     });
//     buffer.draw();
//    }

    stack.popPose();

    //GlStateManager._depthMask(true);
  }

  @SubscribeListener
  public void onRender2D(RenderPlaneEvent.Back event) {
    if (!this.isEnabled()) return;
    val buffers = getBufferProvider();
    val source = buffers.getBufferSource();
    val stack = event.getStack();
    val main = buffers.getBuffer(RenderTypeEx.glTriangle());
    if (showPerformance.getValue()) {
      stack.pushPose();
      final long time = lastExecutionTime.get();
      String string = "Time: " + time + "ms. ";
      float x = (float) (Render2D.getStringWidth(string) / 2.f);
      float y = (float) Render2D.getStringHeight();

      stack.scale(1.f, 1.f, 0.f);
      stack.translate(-x, -y, 0.d);

      Render2D.renderString(
          source, string, 140, 0,
          (time >= updateInterval.getValue() ? Colors.RED :
              time * 2 >= updateInterval.getValue() ? Colors.ORANGE :
                  Colors.GREEN), true
      );
      stack.popPose();
      source.endBatch();
    }
    final double cx = event.getScreenWidth() / 2.f;
    final double cy = event.getScreenHeight() / 2.f;

    disableBlend();
    disableTexture();
    for (int t = 0; t < threads.intValue(); ++t) {
      locks.get(t).lock();
      ArrayList<BlockData> blockDataList = cachedBlockDatas.get(t);
      final int size = blockDataList.size();
      for (int i = 0; i < size; ++i) {
        if (tracers.size() >= (i + 1) ? !tracers.get(i) : !defaultTracers.getValue()) continue;
        BlockData data = blockDataList.get(i);
        int blocksSize = data.blocks.size();
        Color color = data.color;
        for (int b = 0; b < blocksSize; ++b) {
          BlockPos pos = data.blocks.get(b);
          ScreenPos screenPos = VectorUtil.toScreen(Vector3d.atCenterOf(pos));
          glColor4f(color.getRedAsFloat(),
              color.getGreenAsFloat(),
              color.getBlueAsFloat(),
              color.getAlphaAsFloat());
          glBegin(GL11.GL_LINES);
          {
            GL11.glVertex2d(cx, cy);
            GL11.glVertex2d(screenPos.getX(), screenPos.getY());
          }
          glEnd();
        }
      }
      locks.get(t).unlock();
//      drawn.forEach((c, l) ->
//      {
//        Color color = Color.of(c);
//        l.forEach((pos) -> {
//          ScreenPos screenPos = VectorUtil.toScreen(Vector3d.atCenterOf(pos));
//
//          //glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
//          glColor4f(color.getRedAsFloat(),
//              color.getGreenAsFloat(),
//              color.getBlueAsFloat(),
//              color.getAlphaAsFloat());
//          GL11.glVertex2d(cx, cy);
//          GL11.glVertex2d(screenPos.getX(), screenPos.getY());
//        });
//      });
    }
    enableTexture();
    disableBlend();
    GL11.glColor4i(255, 255, 255, 255);
    source.endBatch();

    RenderHelper.setupFor3DItems();
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
