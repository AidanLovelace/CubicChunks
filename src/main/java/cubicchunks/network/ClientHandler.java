/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
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
package cubicchunks.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;

import cubicchunks.CubicChunks;
import cubicchunks.client.CubeProviderClient;
import cubicchunks.lighting.LightingManager;
import cubicchunks.util.CubePos;
import cubicchunks.world.ClientHeightMap;
import cubicchunks.world.ICubicWorldClient;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.BlankCube;
import cubicchunks.world.cube.Cube;
import io.netty.buffer.ByteBuf;

public class ClientHandler implements INetHandler {

	private static ClientHandler m_instance;

	public static ClientHandler getInstance() {
		if (m_instance == null) {
			m_instance = new ClientHandler();
		}
		return m_instance;
	}

	@Override
	public void onDisconnect(@Nonnull ITextComponent chat) {
		// nothing to do
	}

	public void handle(PacketCube packet) {
		IThreadListener taskQueue = Minecraft.getMinecraft();
		if (!taskQueue.isCallingFromMinecraftThread()) {
			taskQueue.addScheduledTask(() -> handle(packet));
			return;
		}

		ICubicWorldClient worldClient = (ICubicWorldClient) Minecraft.getMinecraft().theWorld;
		CubeProviderClient cubeCache = worldClient.getCubeCache();

		CubePos cubePos = packet.getCubePos();

		Column column = cubeCache.provideColumn(cubePos.getX(), cubePos.getZ());
		//isEmpty actually checks if the column is a BlankColumn
		if (column.isEmpty()) {
			CubicChunks.LOGGER.error("Out of order cube received! No column for cube at {} exists!", cubePos);
			return;
		}

		Cube cube;
		if (packet.getType() == PacketCube.Type.NEW_CUBE) {
			cube = cubeCache.loadCube(column, cubePos.getY());
		} else {
			cube = column.getCube(cubePos.getY());
			if (cube instanceof BlankCube) {
				CubicChunks.LOGGER.error("Ignored cube update to blank cube {}", cubePos);
				return;
			}
		}

		byte[] data = packet.getData();
		ByteBuf buf = WorldEncoder.createByteBufForRead(data);
		WorldEncoder.decodeCube(new PacketBuffer(buf), cube);
		cube.markForRenderUpdate();

		for (NBTTagCompound tag : packet.getTileEntityTags()) {
			int blockX = tag.getInteger("x");
			int blockY = tag.getInteger("y");
			int blockZ = tag.getInteger("z");
			BlockPos pos = new BlockPos(blockX, blockY, blockZ);
			TileEntity tileEntity = worldClient.getTileEntity(pos);

			if (tileEntity != null) {
				tileEntity.handleUpdateTag(tag);
			}
		}
	}

	public void handle(PacketColumn packet) {
		IThreadListener taskQueue = Minecraft.getMinecraft();
		if (!taskQueue.isCallingFromMinecraftThread()) {
			taskQueue.addScheduledTask(() -> handle(packet));
			return;
		}

		ICubicWorldClient worldClient = (ICubicWorldClient) Minecraft.getMinecraft().theWorld;
		CubeProviderClient cubeCache = worldClient.getCubeCache();

		ChunkPos chunkPos = packet.getChunkPos();

		Column column = cubeCache.loadChunk(chunkPos.chunkXPos, chunkPos.chunkZPos);

		byte[] data = packet.getData();
		ByteBuf buf = WorldEncoder.createByteBufForRead(data);

		WorldEncoder.decodeColumn(new PacketBuffer(buf), column);
	}

	public void handle(final PacketUnloadCube packet) {
		IThreadListener taskQueue = Minecraft.getMinecraft();
		if (!taskQueue.isCallingFromMinecraftThread()) {
			taskQueue.addScheduledTask(() -> handle(packet));
			return;
		}

		ICubicWorldClient worldClient = (ICubicWorldClient) Minecraft.getMinecraft().theWorld;
		CubeProviderClient cubeCache = worldClient.getCubeCache();

		cubeCache.unloadCube(packet.getCubePos());
	}

	public void handle(final PacketUnloadColumn packet) {
		IThreadListener taskQueue = Minecraft.getMinecraft();
		if (!taskQueue.isCallingFromMinecraftThread()) {
			taskQueue.addScheduledTask(() -> handle(packet));
			return;
		}

		ICubicWorldClient worldClient = (ICubicWorldClient) Minecraft.getMinecraft().theWorld;
		CubeProviderClient cubeCache = worldClient.getCubeCache();

		ChunkPos chunkPos = packet.getColumnPos();
		cubeCache.unloadChunk(chunkPos.chunkXPos, chunkPos.chunkZPos);
	}

	public void handle(final PacketCubeBlockChange packet) {
		IThreadListener taskQueue = Minecraft.getMinecraft();
		if (!taskQueue.isCallingFromMinecraftThread()) {
			taskQueue.addScheduledTask(() -> handle(packet));
			return;
		}

		ICubicWorldClient worldClient = (ICubicWorldClient) Minecraft.getMinecraft().theWorld;
		CubeProviderClient cubeCache = worldClient.getCubeCache();

		// get the cube
		Cube cube = cubeCache.getCube(packet.cubePos);
		if (cube instanceof BlankCube) {
			CubicChunks.LOGGER.error("Ignored block update to blank cube {}", packet.cubePos);
			return;
		}

		ClientHeightMap index = (ClientHeightMap) cube.getColumn().getOpacityIndex();
		LightingManager lm = worldClient.getLightingManager();
		for (int hmapUpdate : packet.heightValues) {
			int x = hmapUpdate & 0xF;
			int z = (hmapUpdate >> 4) & 0xF;
			//height is signed, so don't use unsigned shift
			int height = hmapUpdate >> 8;

			int oldHeight = index.getTopBlockY(x, z);
			index.setHeight(x, z, height);

			int minY = Math.min(oldHeight, height);
			int maxY = Math.max(oldHeight, height);
			lm.columnSkylightUpdate(LightingManager.UpdateType.QUEUED, cube.getColumn(), x, minY, maxY, z);
		}
		// apply the update
		for (int i = 0; i < packet.localAddresses.length; i++) {
			BlockPos pos = cube.localAddressToBlockPos(packet.localAddresses[i]);
			worldClient.invalidateRegionAndSetBlock(pos, packet.blockStates[i]);
		}
		for (TileEntity blockEntity : cube.getTileEntityMap().values()) {
			blockEntity.updateContainingBlockInfo();
		}
	}
}
