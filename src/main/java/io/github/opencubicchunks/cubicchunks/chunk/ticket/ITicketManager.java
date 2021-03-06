package io.github.opencubicchunks.cubicchunks.chunk.ticket;

import io.github.opencubicchunks.cubicchunks.chunk.cube.CubeStatus;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.concurrent.ITaskExecutor;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.Ticket;
import net.minecraft.world.server.TicketType;

import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

public interface ITicketManager {
    int PLAYER_CUBE_TICKET_LEVEL = 33 + CubeStatus.getDistance(ChunkStatus.FULL) - 2;

    boolean processUpdates(ChunkManager chunkManager);

    <T> void registerWithLevel(TicketType<T> type, CubePos pos, int level, T value);

    <T> void releaseWithLevel(TicketType<T> type, CubePos pos, int level, T value);

    <T> void register(TicketType<T> type, CubePos pos, int distance, T value);

    void registerCube(long chunkPosIn, Ticket<?> ticketIn);

    <T> void release(TicketType<T> type, CubePos pos, int distance, T value);

    void releaseCube(long chunkPosIn, Ticket<?> ticketIn);

    // forceChunk
    void forceCube(CubePos pos, boolean add);

    void updateCubePlayerPosition(CubePos cubePos, ServerPlayerEntity player);

    void removeCubePlayer(CubePos cubePosIn, ServerPlayerEntity player);

    int getSpawningCubeCount();

    boolean isCubeOutsideSpawningRadius(long cubePosIn);

    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getCubeTickets();

    Long2ObjectMap<ObjectSet<ServerPlayerEntity>> getPlayersByCubePos();

    ITaskExecutor<CubeTaskPriorityQueueSorter.FunctionEntry<Runnable>> getCubePlayerTicketThrottler();

    ITaskExecutor<CubeTaskPriorityQueueSorter.RunnableEntry> getPlayerCubeTicketThrottlerSorter();

    LongSet getCubePositions();

    Set<ChunkHolder> getCubeHolders();

    @Nullable
    ChunkHolder getCubeHolder(long chunkPosIn);

    @Nullable
    ChunkHolder setCubeLevel(long cubePosIn, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    boolean containsCubes(long cubePosIn);

    Executor executor();

    CubeTaskPriorityQueueSorter getCubeTaskPriorityQueueSorter();
}