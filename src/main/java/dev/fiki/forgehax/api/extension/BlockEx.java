package dev.fiki.forgehax.api.extension;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import javax.annotation.Nullable;

public class BlockEx {
  public static boolean isPlaceable(Block block, World world, BlockPos pos) {
    return block.defaultBlockState().isCollisionShapeFullBlock(world, pos);
  }
  public static BlockState getBlockStateFast(Chunk chunk, BlockPos pos)
  {
    int i = pos.getX();
    int j = pos.getY();
    int k = pos.getZ();
    ChunkSection[] sections = chunk.getSections();
    if (j >= 0 && j >> 4 < sections.length) {
      ChunkSection chunksection = sections[j >> 4];
      if (!ChunkSection.isEmpty(chunksection)) {
        return chunksection.getBlockState(i & 15, j & 15, k & 15);
      }
    }
    return Blocks.AIR.defaultBlockState();
  }
}
