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
package cubicchunks.server;

import java.util.Map;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldServer;

import com.google.common.collect.Maps;

import cubicchunks.api.generators.ITerrainGenerator;
import cubicchunks.generator.SurfaceProcessor;
import cubicchunks.generator.FeatureProcessor;
import cubicchunks.generator.GeneratorPipeline;
import cubicchunks.generator.GeneratorStage;
import cubicchunks.generator.StructureProcessor;
import cubicchunks.generator.TerrainProcessor;
import cubicchunks.generator.terrain.FlatTerrainGenerator;
import cubicchunks.generator.terrain.VanillaTerrainGenerator;
import cubicchunks.lighting.FirstLightProcessor;
import cubicchunks.world.WorldContext;

public class WorldServerContext extends WorldContext {

	private static Map<WorldServer, WorldServerContext> instances;

	static {
		instances = Maps.newHashMap();
	}

	public static WorldServerContext get(final WorldServer worldServer) {
		return instances.get(worldServer);
	}

	public static void put(final WorldServer worldServer, final WorldServerContext worldServerContext) {
		instances.put(worldServer, worldServerContext);
	}

	public static void clear() {
		instances.clear();
	}
	
	private WorldServer worldServer;
	private ServerCubeCache serverCubeCache;
	private GeneratorPipeline generatorPipeline;
	private ITerrainGenerator terrainGenerator;

	public WorldServerContext(final WorldServer worldServer, final ServerCubeCache serverCubeCache) {
		super(worldServer, serverCubeCache);

		this.worldServer = worldServer;
		this.serverCubeCache = serverCubeCache;
		this.generatorPipeline = new GeneratorPipeline(serverCubeCache);

		final long seed = this.worldServer.getSeed();

		this.terrainGenerator = getTerrainGenerator(this.worldServer.dimension.dimensionType);

		// init the generator pipeline
		this.generatorPipeline.addStage(GeneratorStage.TERRAIN, new TerrainProcessor(this.serverCubeCache, 5, this.terrainGenerator));
		this.generatorPipeline.addStage(GeneratorStage.SURFACE, new SurfaceProcessor(this.serverCubeCache, 10, seed));
		this.generatorPipeline.addStage(GeneratorStage.STRUCTURES, new StructureProcessor("Features", this.serverCubeCache, 10));
		this.generatorPipeline.addStage(GeneratorStage.LIGHTING, new FirstLightProcessor("Lighting", this.serverCubeCache, 5));
		this.generatorPipeline.addStage(GeneratorStage.FEATURES, new FeatureProcessor("Population", worldServer, this.serverCubeCache, 100));
		this.generatorPipeline.checkStages();
	}

	@Override
	public WorldServer getWorld() {
		return this.worldServer;
	}

	@Override
	public ServerCubeCache getCubeCache() {
		return this.serverCubeCache;
	}

	public GeneratorPipeline getGeneratorPipeline() {
		return this.generatorPipeline;
	}

	public ITerrainGenerator getTerrainGenerator(final DimensionType dimensionType) {
		if (dimensionType == DimensionType.FLAT) {
			return new FlatTerrainGenerator(this.worldServer.getSeed());
		}
		return new VanillaTerrainGenerator(this.worldServer.getSeed());
	}
}
