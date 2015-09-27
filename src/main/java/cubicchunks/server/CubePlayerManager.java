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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.IPacket;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cubicchunks.network.PacketBulkCubeData;
import cubicchunks.network.PacketUnloadColumns;
import cubicchunks.network.PacketUnloadCubes;
import cubicchunks.util.AddressTools;
import cubicchunks.util.Coords;
import cubicchunks.visibility.CubeSelector;
import cubicchunks.visibility.CuboidalCubeSelector;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;

public class CubePlayerManager extends PlayerManager {
	
	private final Logger LOGGER = LoggerFactory.getLogger("CubePlayerManager");
	
	private static class PlayerInfo {
		
		public Set<Long> watchedCubeAddresses;
		public Set<Long> watchedColumnAddresses;
		public List<Cube> cubesToLoad;
		public List<Cube> cubesToUnload;
		public Set<Long> columnAddressesToLoad;
		public List<Long> columnAddressesToUnload;
		public CubeSelector cubeSelector;
		public int blockX;
		public int blockY;
		public int blockZ;
		public long address;
		
		public PlayerInfo() {
			this.watchedCubeAddresses = new TreeSet<>();
			this.watchedColumnAddresses = new TreeSet<>();
			this.cubesToLoad = new LinkedList<>();
			this.cubesToUnload = new LinkedList<>();
			this.columnAddressesToLoad = new TreeSet<>();
			this.columnAddressesToUnload = new ArrayList<>();
			this.cubeSelector = new CuboidalCubeSelector();
			this.blockX = 0;
			this.blockY = 0;
			this.blockZ = 0;
			this.address = 0;
		}
		
		public void sortOutgoingCubesToLoad() {
			
			// get the player chunk position
			final int cubeX = AddressTools.getX(this.address);
			final int cubeY = AddressTools.getY(this.address);
			final int cubeZ = AddressTools.getZ(this.address);
			
			// sort cubes so they load radially away from the player
			Collections.sort(this.cubesToLoad, new Comparator<Cube>() {
				
				@Override
				public int compare(Cube a, Cube b) {
					return getManhattanDist(a) - getManhattanDist(b);
				}
				
				private int getManhattanDist(Cube cube) {
					int dx = Math.abs(cube.getX() - cubeX);
					int dy = Math.abs(cube.getY() - cubeY);
					int dz = Math.abs(cube.getZ() - cubeZ);
					return dx + dy + dz;
				}
			});
		}
		
		public void removeOutOfRangeOutgoingCubesToLoad() {
			Iterator<Cube> iter = this.cubesToLoad.iterator();
			while (iter.hasNext()) {
				Cube cube = iter.next();
				if (!this.cubeSelector.isCubeVisible(cube.getAddress())) {
					iter.remove();
				}
			}
		}
	}
	
	private WorldServer m_worldServer;
	private ServerCubeCache m_cubeCache;
	private int m_viewDistance;
	private TreeMap<Long,CubeWatcher> m_watchers;
	private TreeMap<Integer,PlayerInfo> m_players;
	
	public CubePlayerManager(WorldServer worldServer) {
		super(worldServer);
		
		this.m_worldServer = worldServer;
		this.m_cubeCache = (ServerCubeCache)m_worldServer.serverChunkCache;
		this.m_viewDistance = worldServer.getMinecraftServer().getConfigurationManager().getViewRadius();
		this.m_watchers = Maps.newTreeMap();
		this.m_players = Maps.newTreeMap();
	}
	
	@Override
	public void addPlayer(EntityPlayerMP player) {
		
		// make new player info
		PlayerInfo info = new PlayerInfo();
		this.m_players.put(player.getEntityId(), info);
		
		// set initial player position
		info.blockX = MathHelper.floor(player.xPos);
		info.blockY = MathHelper.floor(player.yPos);
		info.blockZ = MathHelper.floor(player.zPos);
		int cubeX = Coords.blockToCube(info.blockX);
		int cubeY = Coords.blockToCube(info.blockY);
		int cubeZ = Coords.blockToCube(info.blockZ);
		info.address = AddressTools.getAddress(cubeX, cubeY, cubeZ);
		
		// compute initial visibility
		info.cubeSelector.setPlayerPosition(info.address, this.m_viewDistance);
		
		// add player to watchers and collect the cubes to send over
		for (long address : info.cubeSelector.getVisibleCubes()) {
			CubeWatcher watcher = getOrCreateWatcher(address);
			watcher.addPlayer(player);
			info.watchedCubeAddresses.add(address);
			info.cubesToLoad.add(watcher.getCube());
		}
		info.watchedColumnAddresses.addAll(info.cubeSelector.getVisibleColumns());
		info.columnAddressesToLoad.addAll(info.cubeSelector.getVisibleColumns());
	}
	
	@Override
	public void removePlayer(EntityPlayerMP player) {
		
		// get the player info
		PlayerInfo info = this.m_players.get(player.getEntityId());
		if (info == null) {
			return;
		}
		
		// remove player from all its cubes
		for (long address : info.watchedCubeAddresses) {
			
			// skip non-existent cubes
			if (!cubeExists(address)) {
				continue;
			}
			
			// get the watcher
			CubeWatcher watcher = getWatcher(address);
			if (watcher == null) {
				continue;
			}
			
			// remove from the watcher
			watcher.removePlayer(player);
			
			// cleanup empty watchers and cubes
			if (!watcher.hasPlayers()) {
				this.m_watchers.remove(address);
				m_cubeCache.unloadCube(watcher.getCube());
			}
		}
		
		// remove the info
		this.m_players.remove(player.getEntityId());
	}
	
	@Override
	public void updatePlayerInstances()	{
		
		// responsibilities:
		// update chunk properties
		// send chunk updates to players
		
		for (CubeWatcher watcher : this.m_watchers.values()) {
			watcher.sendUpdates();
			watcher.tick();
		}
		
		// did all the players leave an alternate dimension?
		if (this.m_players.isEmpty() && !this.m_worldServer.dimension.canRespawnHere()) {
			// unload everything
			m_cubeCache.unloadAllChunks();
		}
	}
	
	@Override
	public void markBlockForUpdate(BlockPos pos) {
		
		// get the watcher
		int cubeX = Coords.blockToCube(pos.getX());
		int cubeY = Coords.blockToCube(pos.getY());
		int cubeZ = Coords.blockToCube(pos.getZ());
		CubeWatcher watcher = getWatcher(cubeX, cubeY, cubeZ);
		if (watcher == null) {
			return;
		}
		
		// pass off to watcher
		int localX = Coords.blockToLocal(pos.getX());
		int localY = Coords.blockToLocal(pos.getY());
		int localZ = Coords.blockToLocal(pos.getZ());
		watcher.setDirtyBlock(localX, localY, localZ);
	}
	
	@Override
	public void updateMountedMovingPlayer(EntityPlayerMP player) {
		// the player moved
		// if the player moved into a new chunk, update which chunks the player needs to know about
		// then update the list of chunks that need to be sent to the client
		
		// get the player info
		PlayerInfo info = this.m_players.get(player.getEntityId());
		if (info == null) {
			return;
		}
		
		// did the player move far enough to matter?
		int newBlockX = MathHelper.floor(player.xPos);
		int newBlockY = MathHelper.floor(player.yPos);
		int newBlockZ = MathHelper.floor(player.zPos);
		int manhattanDistance = Math.abs(newBlockX - info.blockX) + Math.abs(newBlockY - info.blockY) + Math.abs(newBlockZ - info.blockZ);
		if (manhattanDistance < 8) {
			return;
		}
		
		// did the player move into a new cube?
		int newCubeX = Coords.blockToCube(newBlockX);
		int newCubeY = Coords.blockToCube(newBlockY);
		int newCubeZ = Coords.blockToCube(newBlockZ);
		long newAddress = AddressTools.getAddress(newCubeX, newCubeY, newCubeZ);
		if (newAddress == info.address) {
			return;
		}
		
		// update player info
		info.blockX = newBlockX;
		info.blockY = newBlockY;
		info.blockZ = newBlockZ;
		info.address = newAddress;
		
		this.updatePlayer(player, info, newAddress);
	}
	
	private void updatePlayer(EntityPlayerMP player, PlayerInfo info, long newAddress){
		
		// calculate new visibility
		info.cubeSelector.setPlayerPosition(newAddress, this.m_viewDistance);
		
		// add to new watchers
		for (long address : info.cubeSelector.getNewlyVisibleCubes()) {
			CubeWatcher watcher = getOrCreateWatcher(address);
			watcher.addPlayer(player);
			info.cubesToLoad.add(watcher.getCube());
		}
		
		// remove from old watchers
		for (long address : info.cubeSelector.getNewlyHiddenCubes()) {
			CubeWatcher watcher = getWatcher(address);
			if (watcher == null) {
				continue;
			}
			
			watcher.removePlayer(player);
			info.cubesToUnload.add(watcher.getCube());
			
			// cleanup empty watchers and cubes
			if (!watcher.hasPlayers()) {
				this.m_watchers.remove(address);
				m_cubeCache.unloadCube(watcher.getCube());
			}
		}
		
		// handle columns too
		info.watchedColumnAddresses.removeAll(info.cubeSelector.getNewlyHiddenColumns());
		info.watchedColumnAddresses.addAll(info.cubeSelector.getNewlyVisibleColumns());
		info.columnAddressesToLoad.addAll(info.cubeSelector.getNewlyVisibleColumns());
		info.columnAddressesToUnload.addAll(info.cubeSelector.getNewlyHiddenColumns());
	}
	
	@Override
	public boolean isPlayerWatchingChunk(EntityPlayerMP player, int cubeX, int cubeZ) {
		
		// get the info
		PlayerInfo info = this.m_players.get(player.getEntityId());
		if (info == null) {
			return false;
		}
		
		return info.watchedColumnAddresses.contains(AddressTools.getAddress(cubeX, cubeZ));
	}
	
	public void processCubeQueues(EntityPlayerMP player) {
		// this method flushes outgoing cubes to the player

		// get the outgoing cubes to load
		PlayerInfo info = this.m_players.get(player.getEntityId());
		if (info == null) {
			return;
		}
		
		if (!info.cubesToLoad.isEmpty()) {
			sendCubesAndColumnsToLoad(player, info);
		}
		if (!info.cubesToUnload.isEmpty()) {
			sendCubesAndColumnsToUnload(player, info);
		}
	}
	
	private void sendCubesAndColumnsToLoad(EntityPlayerMP player, PlayerInfo info) {
		
		info.removeOutOfRangeOutgoingCubesToLoad();
		info.sortOutgoingCubesToLoad();

		//LOGGER.trace("Server cubes to load: {}", info.cubesToLoad.size());
		
		// pull off enough cubes from the queue to fit in a packet
		final int MaxCubesToSend = 100;
		List<Cube> cubesToSend = new ArrayList<Cube>();
		List<BlockEntity> blockEntitiesToSend = new ArrayList<BlockEntity>();
		Iterator<Cube> iter = info.cubesToLoad.iterator();
		while (iter.hasNext() && cubesToSend.size() < MaxCubesToSend) {
			Cube cube = iter.next();
			
			// check to see if the cube is live before sending
			// if it is not live, skip to the next cube in the iterator
			if (!cube.getGeneratorStage().isLastStage()) {
				LOGGER.trace("Cube at {}, {}, {} at stage {}, skipping!", cube.getX(), cube.getY(), cube.getZ(), cube.getGeneratorStage());
				continue;
			}
			
			// add this cube to the send buffer
			cubesToSend.add(cube);
			iter.remove();
			
			// add tile entities too
			for (BlockEntity blockEntity : cube.getBlockEntities()) {
				blockEntitiesToSend.add(blockEntity);
			}
		}
		
		if (cubesToSend.isEmpty()) {
			return;
		}
		
		// get the columns to send
		List<Column> columnsToSend = Lists.newArrayList();
		for (Cube cube : cubesToSend) {
			Column column = cube.getColumn();
			long columnAddress = column.getAddress();
			if (info.columnAddressesToLoad.contains(columnAddress)) {
				info.columnAddressesToLoad.remove(columnAddress);
				columnsToSend.add(column);
			}
		}
		
		/*{ // DEBUG: what y-levels are we sending?
			Multiset<Integer> counts = TreeMultiset.create();
			for (Column column : columnsToSend) {
				for (Cube cube : column.getCubes()) {
					counts.add(cube.getY());
				}
			}
			LOGGER.trace("Cube Y counts: {}", counts);
		}*/

		// send the cube data
		player.netServerHandler.send(new PacketBulkCubeData(columnsToSend, cubesToSend));
		
		/*
		LOGGER.trace("Server sent {}/{} cubes, {}/{} columns to player",
			cubesToSend.size(), cubesToSend.size() + info.cubesToLoad.size(),
			columnsToSend.size(), columnsToSend.size() + info.columnAddressesToLoad.size()
		);
		*/
		
		// tell the cube watchers which cubes were sent for this player
		for (Cube cube : cubesToSend) {
			
			// get the watcher
			CubeWatcher watcher = getWatcher(cube.getAddress());
			if (watcher == null) {
				continue;
			}
			
			watcher.setPlayerSawCube(player);
		}
		
		// send tile entity data
		for (BlockEntity blockEntity : blockEntitiesToSend) {
			IPacket<?> packet = blockEntity.getDescriptionPacket();
			if (packet != null) {
				player.netServerHandler.send(packet);
			}
		}
		
		// watch entities on the columns we just sent
		EntityTracker entityTracker = this.m_worldServer.getEntityTracker();
		for (Column column : columnsToSend) {
			entityTracker.a(player, column);
		}
	}

	private void sendCubesAndColumnsToUnload(EntityPlayerMP player, PlayerInfo info) {
		int numPackets = (info.cubesToUnload.size() + PacketUnloadCubes.MAX_SIZE - 1)/PacketUnloadCubes.MAX_SIZE;
		for (int i=0; i<numPackets; i++) {
			int from = i*PacketUnloadCubes.MAX_SIZE;
			int to = Math.min((i+1)*PacketUnloadCubes.MAX_SIZE, info.cubesToUnload.size() - 1);
			player.netServerHandler.send(new PacketUnloadCubes(info.cubesToUnload.subList(from, to)));
			//LOGGER.debug("Server sent {} cubes to player to unload", info.cubesToUnload.size());
		}
		info.cubesToUnload.clear();
		
		//even with render distance 64 and teleporting it's not possible with current MAX_SIZE
		assert info.columnAddressesToUnload.size() < PacketUnloadColumns.MAX_SIZE;
		player.netServerHandler.send(new PacketUnloadColumns(info.columnAddressesToUnload));
		//LOGGER.debug("Server sent {} columns to player to unload", info.columnAddressesToUnload.size());
		info.columnAddressesToUnload.clear();
	}
	
	public Iterable<Long> getVisibleCubeAddresses(EntityPlayerMP player) {
		
		// get the info
		PlayerInfo info = this.m_players.get(player.getEntityId());
		if (info == null) {
			return null;
		}
		
		return info.cubeSelector.getVisibleCubes();
	}
	
	private CubeWatcher getWatcher(int cubeX, int cubeY, int cubeZ) {
		return getWatcher(AddressTools.getAddress(cubeX, cubeY, cubeZ));
	}
	
	private CubeWatcher getWatcher(long address) {
		return this.m_watchers.get(address);
	}
	
	private boolean cubeExists(long address) {
		int cubeX = AddressTools.getX(address);
		int cubeY = AddressTools.getY(address);
		int cubeZ = AddressTools.getZ(address);
		return m_cubeCache.cubeExists(cubeX, cubeY, cubeZ);
	}
	
	private CubeWatcher getOrCreateWatcher(long address) {
		CubeWatcher watcher = this.m_watchers.get(address);
		if (watcher == null) {
			// get the cube
			int cubeX = AddressTools.getX(address);
			int cubeY = AddressTools.getY(address);
			int cubeZ = AddressTools.getZ(address);
			m_cubeCache.loadCube(cubeX, cubeY, cubeZ);
			
			// make a new watcher
			watcher = new CubeWatcher(m_cubeCache.getCube(cubeX, cubeY, cubeZ));
			this.m_watchers.put(address, watcher);
		}
		return watcher;
	}
	
	@Override
	public void setPlayerViewRadius(int newViewDistance) {
		this.m_viewDistance = newViewDistance;
		if(this.m_worldServer == null) {
			//this method is used in superconstructor. Don't send chunks in this case.
			return;
		}
		//load new chunks/unload old chunks
		for(EntityPlayer player : this.m_worldServer.players) {
			int id = player.getEntityId();
			PlayerInfo info = this.m_players.get(id);
			//use current address, player position didn't change
			this.updatePlayer((EntityPlayerMP) player, info, info.address);
		}
	}
}
