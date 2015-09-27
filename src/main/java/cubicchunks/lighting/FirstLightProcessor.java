/*
 *  This file is part of Tall Worlds, licensed under the MIT License (MIT).
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
package cubicchunks.lighting;

import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import cubicchunks.generator.GeneratorStage;
import cubicchunks.util.Coords;
import cubicchunks.util.processor.CubeProcessor;
import cubicchunks.world.ICubeCache;
import cubicchunks.world.OpacityIndex;
import cubicchunks.world.WorldContext;
import cubicchunks.world.cube.Cube;

public class FirstLightProcessor extends CubeProcessor {
	
	public FirstLightProcessor(String name, ICubeCache cache, int batchSize) {
		super(name, cache, batchSize);
	}
	
	@Override
	public boolean calculate(Cube cube) {
		
		// only continue if the neighboring cubes are at least in the lighting stage
		WorldContext worldContext = WorldContext.get(cube.getWorld());
		if (!worldContext.cubeAndNeighborsExist(cube, true, GeneratorStage.LIGHTING)) {
			return false;
		}
		
		int minBlockX = Coords.cubeToMinBlock(cube.getX());
		int maxBlockX = Coords.cubeToMaxBlock(cube.getX());
		int minBlockY = Coords.cubeToMinBlock(cube.getY());
		int maxBlockY = Coords.cubeToMaxBlock(cube.getY());
		int minBlockZ = Coords.cubeToMinBlock(cube.getZ());
		int maxBlockZ = Coords.cubeToMaxBlock(cube.getZ());
		
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		
		// update the sky light
		for (pos.x = minBlockX; pos.x <= maxBlockX; pos.x++) {
			for (pos.z = minBlockZ; pos.z <= maxBlockZ; pos.z++) {
				updateSkylight(cube, pos);
			}
		}
		
		// smooth the sky light by applying diffuse lighting
		for (pos.x = minBlockX; pos.x <= maxBlockX; pos.x++) {
			for (pos.y = minBlockY; pos.y <= maxBlockY; pos.y++) {
				for (pos.z = minBlockZ; pos.z <= maxBlockZ; pos.z++) {
					boolean wasLit = diffuseBlock(cube, pos);
					
					// if the lighting failed, then try again later
					if (!wasLit) {
						return false;
					}
				}
			}
		}
		
		// smooth the nearby faces of adjacent cubes
		// this is for cases when a sheer wall is up against an empty cube
		// unless this is called, the wall will not get directly lit
		diffuseXSlab(cache.getCube(cube.getX() - 1, cube.getY(), cube.getZ()), 15, pos);
		diffuseXSlab(cache.getCube(cube.getX() + 1, cube.getY(), cube.getZ()), 0, pos);
		diffuseYSlab(cache.getCube(cube.getX(), cube.getY() - 1, cube.getZ()), 15, pos);
		diffuseYSlab(cache.getCube(cube.getX(), cube.getY() + 1, cube.getZ()), 0, pos);
		diffuseZSlab(cache.getCube(cube.getX(), cube.getY(), cube.getZ() - 1), 15, pos);
		diffuseZSlab(cache.getCube(cube.getX(), cube.getY(), cube.getZ() + 1), 0, pos);
		
		return true;
	}
	
	private void updateSkylight(Cube cube, BlockPos.MutableBlockPos pos) {
		
		int localX = Coords.blockToLocal(pos.getX());
		int localZ = Coords.blockToLocal(pos.getZ());
		
		// compute bounds on the sky light gradient
		Integer gradientMaxBlockY = cube.getColumn().getSkylightBlockY(localX, localZ);
		Integer gradientMinBlockY = null;
		if (gradientMaxBlockY != null) {
			gradientMinBlockY = gradientMaxBlockY - 15;
		} else {
			// there are no solid blocks in this column. Everything should be skylit
			gradientMaxBlockY = Integer.MIN_VALUE;
		}
		
		// get the cube bounds
		int cubeMinBlockY = Coords.cubeToMinBlock(cube.getY());
		int cubeMaxBlockY = Coords.cubeToMaxBlock(cube.getY());
		
		// could this sky light possibly reach this cube?
		if (cubeMinBlockY > gradientMaxBlockY) {
			
			// set everything to sky light
			for (pos.y=cubeMinBlockY; pos.y<=cubeMaxBlockY; pos.y++) {
				cube.setLightValue(LightType.SKY, pos, 15);
			}
			
		} else if (cubeMaxBlockY < gradientMinBlockY) {
			
			// set everything to dark
			for (pos.y=cubeMinBlockY; pos.y<=cubeMaxBlockY; pos.y++) {
				cube.setLightValue(LightType.SKY, pos, 0);
			}
			
		} else {
			OpacityIndex index = cube.getColumn().getOpacityIndex();
			
			// need to calculate the light
			int light = 15;
			int startBlockY = Math.max(gradientMaxBlockY, cubeMaxBlockY);
			for (pos.y = startBlockY; pos.y >= cubeMinBlockY; pos.y--) {
				int opacity = index.getOpacity(localX, pos.y, localZ);
				if (opacity == 0 && light < 15) {
					// after something blocks light, apply a linear falloff
					opacity = 1;
				}
				
				// decrease the light
				light = Math.max(0, light - opacity);
				
				if (pos.y <= cubeMaxBlockY) {
					// apply the light
					cube.setLightValue(LightType.SKY, pos, light);
				}
			}
		}
	}
	
	private void diffuseXSlab(Cube cube, int localX, BlockPos.MutableBlockPos pos) {
		pos.x = Coords.localToBlock(cube.getX(), localX);
		int minBlockY = Coords.cubeToMinBlock(cube.getY());
		int maxBlockY = Coords.cubeToMaxBlock(cube.getY());
		int minBlockZ = Coords.cubeToMinBlock(cube.getZ());
		int maxBlockZ = Coords.cubeToMaxBlock(cube.getZ());
		for (pos.y = minBlockY; pos.y <= maxBlockY; pos.y++) {
			for (pos.z = minBlockZ; pos.z <= maxBlockZ; pos.z++) {
				diffuseBlock(cube, pos);
			}
		}
	}
	
	private void diffuseYSlab(Cube cube, int localY, BlockPos.MutableBlockPos pos) {
		int minBlockX = Coords.cubeToMinBlock(cube.getX());
		int maxBlockX = Coords.cubeToMaxBlock(cube.getX());
		pos.y = Coords.localToBlock(cube.getY(), localY);
		int minBlockZ = Coords.cubeToMinBlock(cube.getZ());
		int maxBlockZ = Coords.cubeToMaxBlock(cube.getZ());
		for (pos.x = minBlockX; pos.x <= maxBlockX; pos.x++) {
			for (pos.z = minBlockZ; pos.z <= maxBlockZ; pos.z++) {
				diffuseBlock(cube, pos);
			}
		}
	}
	
	private void diffuseZSlab(Cube cube, int localZ, BlockPos.MutableBlockPos pos) {
		int minBlockX = Coords.cubeToMinBlock(cube.getX());
		int maxBlockX = Coords.cubeToMaxBlock(cube.getX());
		int minBlockY = Coords.cubeToMinBlock(cube.getY());
		int maxBlockY = Coords.cubeToMaxBlock(cube.getY());
		pos.z = Coords.localToBlock(cube.getZ(), localZ);
		for (pos.x = minBlockX; pos.x <= maxBlockX; pos.x++) {
			for (pos.y = minBlockY; pos.y <= maxBlockY; pos.y++) {
				diffuseBlock(cube, pos);
			}
		}
	}
	
	private boolean diffuseBlock(Cube cube, BlockPos pos) {
		
		// we just put raw skylight everywhere
		// and some blocks have block light
		// now we're asking, for this block:
		//   should we diffuse the sunlight here?
		//   should we diffuse the block light here?
		
		// saying no as much as possible will let us do lighting faster
		// but we need to make sure light gets spread correctly
		
		// we need to diffuse the sky light if:
		// 1) the dimension has sky
		// 2) the block is transparent
		// 3) the block has no sunlight, but sunlight could diffuse to it
		
		// basically this means only areas under sun-lit ledges
		
		// 3 is harder to quantify, so how about this:
		// what are the theoretical limits on where sunlight can go?
		//   any translucent or transparent block above sea level
		//   any translucent block above sea level - 16 (since some light gets through water)
		
		// so let's turn 3 into:
		// 3) the block is at or above sea level - 16
		
		// we need to diffuse block light if:
		// 1) it's a block light source
		
		// AND WE'RE OFF!!
		World world = cube.getWorld();
		Block block = cube.getBlockAt(pos);
		
		// should we diffuse sky light?
		if (!world.dimension.hasNoSky && pos.getY() > world.getSeaLevel() - 16 && block.getOpacity() == 0 && world.getLightAt(LightType.SKY, pos) == 0) {
			boolean wasLit = world.updateLightingAt(LightType.SKY, pos);
			if (!wasLit) {
				return false;
			}
		}
		
		// should we diffuse block light?
		if (block.getBrightness() > 0) {
			boolean wasLit = world.updateLightingAt(LightType.BLOCK, pos);
			if (!wasLit) {
				return false;
			}
		}
		
		return true;
	}
}
