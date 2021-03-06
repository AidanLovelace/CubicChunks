package io.github.opencubicchunks.cubicchunks.chunk.ticket;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeHolder;
import io.github.opencubicchunks.cubicchunks.chunk.ICubeHolderListener;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.chunk.util.Utils;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.concurrent.DelegatedTaskExecutor;
import net.minecraft.util.concurrent.ITaskExecutor;
import net.minecraft.util.concurrent.ITaskQueue;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ChunkHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CubeTaskPriorityQueueSorter implements AutoCloseable, ChunkHolder.IListener, ICubeHolderListener {

    private final Map<ITaskExecutor<?>, CubeTaskPriorityQueue<? extends Function<ITaskExecutor<Unit>, ?>>> queues;
    private final Set<ITaskExecutor<?>> actors;
    private final DelegatedTaskExecutor<ITaskQueue.RunnableWithPriority> sorter;

    public CubeTaskPriorityQueueSorter(List<ITaskExecutor<?>> taskExecutors, Executor executor, int p_i50713_3_) {
        this.queues = taskExecutors.stream().collect(Collectors.toMap(Function.identity(), (p_219084_1_) ->
                new CubeTaskPriorityQueue<>(p_219084_1_.getName() + "_queue", p_i50713_3_)));
        this.actors = Sets.newHashSet(taskExecutors);
        this.sorter = new DelegatedTaskExecutor<>(new ITaskQueue.Priority(4), executor, "sorter");
    }

    // func_219069_a
    public static CubeTaskPriorityQueueSorter.FunctionEntry<Runnable> createMsg(Runnable runnable, long pos, IntSupplier p_219069_3_) {
        return new CubeTaskPriorityQueueSorter.FunctionEntry<>((p_219072_1_) -> () -> {
            runnable.run();
            p_219072_1_.enqueue(Unit.INSTANCE);
        }, pos, p_219069_3_);
    }

    // func_219081_a
    public static CubeTaskPriorityQueueSorter.FunctionEntry<Runnable> createMsg(ChunkHolder holder, Runnable p_219081_1_) {
        return createMsg(p_219081_1_, ((ICubeHolder) holder).getCubePos().asLong(), holder::func_219281_j);
    }

    // func_219073_a
    public static CubeTaskPriorityQueueSorter.RunnableEntry createSorterMsg(Runnable p_219073_0_, long p_219073_1_, boolean p_219073_3_) {
        return new CubeTaskPriorityQueueSorter.RunnableEntry(p_219073_0_, p_219073_1_, p_219073_3_);
    }

    // func_219087_a
    public <T> ITaskExecutor<CubeTaskPriorityQueueSorter.FunctionEntry<T>> createExecutor(ITaskExecutor<T> iTaskExecutor, boolean p_219087_2_) {
        return this.sorter.<ITaskExecutor<CubeTaskPriorityQueueSorter.FunctionEntry<T>>>func_213141_a((p_219086_3_) -> new ITaskQueue.RunnableWithPriority(0, () -> {
            this.getQueue(iTaskExecutor);
            p_219086_3_.enqueue(ITaskExecutor.inline("chunk priority sorter around " + iTaskExecutor.getName(), (p_219071_3_) -> this.execute(iTaskExecutor, p_219071_3_.task, p_219071_3_.cubePos, p_219071_3_.field_219430_c, p_219087_2_)));
        })).join();
    }

    // func_219091_a
    public ITaskExecutor<CubeTaskPriorityQueueSorter.RunnableEntry> createSorterExecutor(ITaskExecutor<Runnable> p_219091_1_) {
        return this.sorter.<ITaskExecutor<CubeTaskPriorityQueueSorter.RunnableEntry>>func_213141_a((p_219080_2_) -> new ITaskQueue.RunnableWithPriority(0, () -> p_219080_2_.enqueue(ITaskExecutor.inline("chunk priority sorter around " + p_219091_1_.getName(), (p_219075_2_) -> this.sort(p_219091_1_, p_219075_2_.pos, p_219075_2_.runnable, p_219075_2_.field_219436_c))))).join();
    }

    // func_219066_a
    @Override
    public void onUpdateCubeLevel(CubePos pos, IntSupplier getLevel, int level, IntConsumer setLevel) {
        this.sorter.enqueue(new ITaskQueue.RunnableWithPriority(0, () -> {
            int i = getLevel.getAsInt();
            this.queues.values().forEach((cubeTaskPriorityQueue) ->
                    cubeTaskPriorityQueue.updateCubeLevel(i, pos, level));
            setLevel.accept(level);
        }));
    }

    // func_219074_a
    private <T> void sort(ITaskExecutor<T> p_219074_1_, long p_219074_2_, Runnable p_219074_4_, boolean p_219074_5_) {
        this.sorter.enqueue(new ITaskQueue.RunnableWithPriority(1, () -> {
            CubeTaskPriorityQueue<Function<ITaskExecutor<Unit>, T>> cubeTaskPriorityQueue = this.getQueue(p_219074_1_);
            cubeTaskPriorityQueue.clearPostion(p_219074_2_, p_219074_5_);
            if (this.actors.remove(p_219074_1_)) {
                this.func_219078_a(cubeTaskPriorityQueue, p_219074_1_);
            }

            p_219074_4_.run();
        }));
    }

    // func_219067_a
    private <T> void execute(ITaskExecutor<T> p_219067_1_, Function<ITaskExecutor<Unit>, T> p_219067_2_, long p_219067_3_, IntSupplier p_219067_5_, boolean p_219067_6_) {
        this.sorter.enqueue(new ITaskQueue.RunnableWithPriority(2, () -> {
            CubeTaskPriorityQueue<Function<ITaskExecutor<Unit>, T>> cubeTaskPriorityQueue = this.getQueue(p_219067_1_);
            int i = p_219067_5_.getAsInt();
            cubeTaskPriorityQueue.add(Optional.of(p_219067_2_), p_219067_3_, i);
            if (p_219067_6_) {
                cubeTaskPriorityQueue.add(Optional.empty(), p_219067_3_, i);
            }

            if (this.actors.remove(p_219067_1_)) {
                this.func_219078_a(cubeTaskPriorityQueue, p_219067_1_);
            }

        }));
    }

    // func_219078_a
    private <T> void func_219078_a(CubeTaskPriorityQueue<Function<ITaskExecutor<Unit>, T>> p_219078_1_, ITaskExecutor<T> p_219078_2_) {
        this.sorter.enqueue(new ITaskQueue.RunnableWithPriority(3, () -> {
            Stream<Either<Function<ITaskExecutor<Unit>, T>, Runnable>> stream = p_219078_1_.poll();
            if (stream == null) {
                this.actors.add(p_219078_2_);
            } else {
                Util.gather(stream.map((p_219092_1_) -> p_219092_1_.map(p_219078_2_::func_213141_a, (p_219077_0_) -> {
                    p_219077_0_.run();
                    return CompletableFuture.completedFuture(Unit.INSTANCE);
                })).collect(Collectors.toList())).thenAccept((p_219088_3_) ->
                        this.func_219078_a(p_219078_1_, p_219078_2_));
            }

        }));
    }

    // getQueue
    private <T> CubeTaskPriorityQueue<Function<ITaskExecutor<Unit>, T>> getQueue(ITaskExecutor<T> p_219068_1_) {
        CubeTaskPriorityQueue<? extends Function<ITaskExecutor<Unit>, ?>> queue = this.queues.get(p_219068_1_);
        if (queue == null) {
            throw Util.pauseDevMode((new IllegalArgumentException("No queue for: " + p_219068_1_)));
        } else {
            return Utils.unsafeCast(queue);
        }
    }

    // close
    public void close() {
        this.queues.keySet().forEach(ITaskExecutor::close);
    }

    @Override
    public void func_219066_a(ChunkPos pos, IntSupplier p_219066_2_, int p_219066_3_, IntConsumer p_219066_4_) {
        throw new AbstractMethodError("This function should never be called! EVER");
    }

    public static final class FunctionEntry<T> {
        private final Function<ITaskExecutor<Unit>, T> task;
        private final long cubePos;
        private final IntSupplier field_219430_c;

        private FunctionEntry(Function<ITaskExecutor<Unit>, T> p_i50028_1_, long p_i50028_2_, IntSupplier p_i50028_4_) {
            this.task = p_i50028_1_;
            this.cubePos = p_i50028_2_;
            this.field_219430_c = p_i50028_4_;
        }
    }

    public static final class RunnableEntry {
        private final Runnable runnable;
        private final long pos;
        private final boolean field_219436_c;

        private RunnableEntry(Runnable runnable, long pos, boolean p_i50026_4_) {
            this.runnable = runnable;
            this.pos = pos;
            this.field_219436_c = p_i50026_4_;
        }
    }
}