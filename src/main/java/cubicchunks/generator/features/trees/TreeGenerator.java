/*
 *  This file is part of Cubic Chunks, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2014 Tall Worlds
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.generator.features.trees;

import cubicchunks.generator.features.SurfaceFeatureGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

public abstract class TreeGenerator extends SurfaceFeatureGenerator {
	
	private static final Block[] REPLACABLE_OPEN_BLOCKS = { Blocks.AIR, Blocks.SAPLING, Blocks.FLOWING_WATER, Blocks.WATER, Blocks.FLOWING_LAVA,
		Blocks.LAVA, Blocks.LOG, Blocks.LOG2, Blocks.LEAVES, Blocks.LEAVES2 };
	
	private static final Block[] REPLACABLE_SOLID_BLOCKS = { Blocks.GRASS, Blocks.DIRT, Blocks.SAND, Blocks.GRAVEL };
	
	protected static boolean canReplaceBlockDefault(final Block blockToCheck) {
		return canReplace(blockToCheck, REPLACABLE_OPEN_BLOCKS) || canReplace(blockToCheck, REPLACABLE_SOLID_BLOCKS);
	}
	
	protected static boolean canReplace(final Block blockToCheck, final Block[] blockArray) {
		final Material blockMaterial = blockToCheck.getMaterial();
		
		for(Block block : blockArray){
				if(blockMaterial == block.getMaterial()){
					return true;
				}
		}
		return false;
	}
	
	protected static boolean canReplaceWithLeaves(final Block blockToCheck) {
		final Material blockMaterial = blockToCheck.getMaterial();
		return blockMaterial == Material.AIR || blockMaterial == Material.LEAVES;
	}

	protected static boolean canReplaceWithWood(final Block blockToCheck) {
		return blockToCheck == Blocks.GRASS || blockToCheck == Blocks.DIRT || blockToCheck == Blocks.LOG
				|| blockToCheck == Blocks.LOG2 || blockToCheck == Blocks.SAPLING || blockToCheck == Blocks.VINE;
	}

	protected final IBlockState woodBlock;
	protected final IBlockState leafBlock;

	public TreeGenerator(final World world, final IBlockState woodBlock, final IBlockState leafBlock) {
		super(world);
		this.woodBlock = woodBlock;
		this.leafBlock = leafBlock;
	}

	protected boolean tryToPlaceDirtUnderTree(final World world, final BlockPos blockPos) {
		if (world.getBlockStateAt(blockPos).getBlock() != Blocks.DIRT) {
			return this.setBlockOnly(blockPos, Blocks.DIRT.getDefaultState());
		} else {
			// it's already dirt, so just say it was placed successfully
			return true;
		}
	}
}
