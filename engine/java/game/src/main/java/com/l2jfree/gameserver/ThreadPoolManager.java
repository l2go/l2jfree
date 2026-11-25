/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.util.concurrent.ExecuteWrapper;
import com.l2jfree.util.concurrent.L2RejectedExecutionHandler;
import com.l2jfree.util.concurrent.RunnableStatsManager;
import com.l2jfree.util.concurrent.RunnableStatsManager.SortBy;
import com.l2jfree.util.concurrent.ScheduledFutureWrapper;
import com.l2jfree.util.concurrent.VirtualTaskExecutor;

/**
 * World tasks share one platform thread: movement, knownlist, effects, AI and client packet logic.
 * A task queued from that thread runs after the current one returns; it does not run beside it.
 * Background JDBC uses virtual threads, one outstanding task at a time, and does not hold a creature
 * monitor while it waits. A save the action itself performs still runs on the caller.
 *
 * @author -Wooden-, NB4L1
 */
public final class ThreadPoolManager
{
	private static final Logger _log = LoggerFactory.getLogger(ThreadPoolManager.class);
	
	public static final long MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING = 5000;
	
	private static final String WORLD_THREAD_NAME = "l2jfree-world";
	
	private static final long MAX_DELAY = TimeUnit.NANOSECONDS.toMillis(Long.MAX_VALUE - System.nanoTime()) / 2;
	
	/** Test seam, for tests only: production leaves it empty. A stalled gate must not sit on the world thread. */
	static volatile Runnable jdbcGate = () -> {
	};
	
	public static void setJdbcGate(Runnable gate)
	{
		jdbcGate = gate;
	}
	
	public static void enterBackgroundJdbc()
	{
		jdbcGate.run();
	}
	
	/**
	 * Copies what the monitor protects, releases it, then runs the write. The write may wait on JDBC.
	 * Callers that still need the monitor, such as a bypass, do not wait for that write.
	 */
	public static void releaseThen(Object monitor, Runnable underLock, Runnable afterRelease)
	{
		synchronized (monitor)
		{
			underLock.run();
		}
		enterBackgroundJdbc();
		afterRelease.run();
	}
	
	public static ThreadPoolManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/** Movement, knownlist, effects, AI and client packet logic. One thread, so two tasks cannot write one creature at once. */
	private final ScheduledThreadPoolExecutor _world;
	private volatile Thread _worldThread;
	/** Blocking work (admin commands, cleanups, JDBC): one virtual thread per task. */
	private final VirtualTaskExecutor _longRunningPool;
	
	private ThreadPoolManager()
	{
		final ThreadFactory worldThreads = Thread.ofPlatform().name(WORLD_THREAD_NAME).factory();
		_world = new ScheduledThreadPoolExecutor(1, task -> {
			// A dead worker is replaced by a new thread, so the reference follows the factory, not the first thread.
			final Thread thread = worldThreads.newThread(task);
			_worldThread = thread;
			return thread;
		}) {
			@Override
			protected void beforeExecute(Thread t, Runnable r)
			{
				com.l2jfree.gameserver.util.WorldTimings.taskBegin();
			}
			
			@Override
			protected void afterExecute(Runnable r, Throwable t)
			{
				com.l2jfree.gameserver.util.WorldTimings.taskEnd();
			}
		};
		_world.setRejectedExecutionHandler(new L2RejectedExecutionHandler());
		_world.prestartAllCoreThreads();
		
		_longRunningPool = new VirtualTaskExecutor("long-running");
		
		scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				purge();
			}
		}, 60000, 60000);
		
		_log.info("ThreadPoolManager: world tasks on one platform thread (" + _world.getPoolSize()
				+ "); long running tasks on virtual threads.");
	}
	
	private final long validate(long delay)
	{
		return Math.max(0, Math.min(MAX_DELAY, delay));
	}
	
	private static final class ThreadPoolExecuteWrapper extends ExecuteWrapper
	{
		private ThreadPoolExecuteWrapper(Runnable runnable)
		{
			super(runnable);
		}
		
		@Override
		protected long getMaximumRuntimeInMillisecWithoutWarning()
		{
			return MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING;
		}
	}
	
	// ===========================================================================================
	
	public final ScheduledFuture<?> schedule(Runnable r, long delay)
	{
		r = new ThreadPoolExecuteWrapper(r);
		delay = validate(delay);
		
		return new ScheduledFutureWrapper(_world.schedule(r, delay, TimeUnit.MILLISECONDS));
	}
	
	public final ScheduledFuture<?> scheduleEffect(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	public final ScheduledFuture<?> scheduleGeneral(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	public final ScheduledFuture<?> scheduleAi(Runnable r, long delay)
	{
		return schedule(r, delay);
	}
	
	// ===========================================================================================
	
	public final ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long delay, long period)
	{
		r = new ThreadPoolExecuteWrapper(r);
		delay = validate(delay);
		period = validate(period);
		
		return new ScheduledFutureWrapper(_world.scheduleAtFixedRate(r, delay, period, TimeUnit.MILLISECONDS));
	}
	
	public final ScheduledFuture<?> scheduleEffectAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	public final ScheduledFuture<?> scheduleGeneralAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	public final ScheduledFuture<?> scheduleAiAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate(r, delay, period);
	}
	
	// ===========================================================================================
	
	public final void execute(Runnable r)
	{
		r = new ThreadPoolExecuteWrapper(r);
		
		_world.execute(r);
	}
	
	public final void executeTask(Runnable r)
	{
		execute(r);
	}
	
	public final void executeLongRunning(Runnable r)
	{
		r = new ExecuteWrapper(r);
		
		_longRunningPool.execute(r);
	}
	
	public boolean isWorldThread()
	{
		return Thread.currentThread() == _worldThread;
	}
	
	/**
	 * Applies one short step on {@code l2jfree-world} and waits for it. A direct call and a handed-off call
	 * report the same exception. A closed world completes the wait with an error. An interrupt before the step
	 * starts cancels it; a step that has started runs to the end, and the caller still returns.
	 */
	public void runOnWorld(Runnable action)
	{
		applyOn(isWorldThread(), _world, action);
	}
	
	static void applyOn(boolean callerIsOwner, ExecutorService executor, Runnable action)
	{
		if (callerIsOwner)
		{
			action.run();
			return;
		}
		
		if (executor.isShutdown())
			throw new RejectedExecutionException("world stopped");
		
		FutureTask<Void> task = new FutureTask<>(() -> {
			action.run();
			return null;
		});
		executor.execute(task);
		if (executor.isShutdown() && task.cancel(false))
			throw new RejectedExecutionException("world did not accept the apply");
		
		try
		{
			task.get();
		}
		catch (InterruptedException e)
		{
			task.cancel(false);
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while applying world state", e);
		}
		catch (CancellationException e)
		{
			throw new RejectedExecutionException("world did not run the apply");
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime)
				throw runtime;
			if (cause instanceof Error error)
				throw error;
			throw new IllegalStateException(cause);
		}
	}
	
	/**
	 * From the world thread, starts {@code body} on a virtual thread unless one started earlier is still running.
	 * From any other thread, runs {@code body} on the caller. The flag clears after success and after failure.
	 * <p>
	 * While the server stops, the background pool no longer accepts work. A world task that wakes up then has nothing
	 * left to do, so it is skipped without an error; the final saves run on the stopping thread, not here.
	 *
	 * @return true when {@code body} ran or started; false when it was skipped because the previous run is still in
	 *         flight or the server is stopping
	 */
	public boolean runBackgroundOnce(AtomicBoolean inflight, Runnable body)
	{
		if (!isWorldThread())
		{
			body.run();
			return true;
		}
		
		return startUnlessStopping(inflight, body, this::executeLongRunning, _longRunningPool::isShutdown);
	}
	
	/**
	 * Like {@link #runBackgroundOnce(AtomicBoolean, Runnable)} for work that must read the world. {@code copy} runs on
	 * the caller, which is the world thread when it matters, only when no earlier run is in flight, so nothing is
	 * copied for a run that would be skipped. It returns the write that persists the copy, or null for nothing to
	 * write. The write runs on a virtual thread and reads only the copy.
	 * <p>
	 * Only the world thread starts runs, so the check and the start cannot be interleaved with another start.
	 *
	 * @return true when the write ran or started
	 */
	public boolean copyThenRunBackgroundOnce(AtomicBoolean inflight, Supplier<Runnable> copy)
	{
		if (isWorldThread() && inflight.get())
			return false;
		
		final Runnable write = copy.get();
		if (write == null)
			return false;
		
		return runBackgroundOnce(inflight, write);
	}
	
	/** {@link #startOnce} that treats a rejection during shutdown as "nothing left to do" and any other as an error. */
	static boolean startUnlessStopping(AtomicBoolean inflight, Runnable body, Consumer<Runnable> start,
			BooleanSupplier stopping)
	{
		try
		{
			return startOnce(inflight, body, start);
		}
		catch (RejectedExecutionException e)
		{
			if (!stopping.getAsBoolean())
				throw e;
			
			_log.debug("Background work skipped, the server is stopping.");
			return false;
		}
	}
	
	/**
	 * Starts {@code body} once. A rejected start clears the flag instead of leaving it stuck, and the caller sees the
	 * rejection.
	 *
	 * @return false when a run is already in flight, so {@code body} was not started
	 */
	static boolean startOnce(AtomicBoolean inflight, Runnable body, Consumer<Runnable> start)
	{
		if (!inflight.compareAndSet(false, true))
			return false;
		
		try
		{
			start.accept(() -> {
				try
				{
					body.run();
				}
				finally
				{
					inflight.set(false);
				}
			});
			return true;
		}
		catch (RuntimeException e)
		{
			inflight.set(false);
			throw e;
		}
	}
	
	// ===========================================================================================
	
	public final Future<?> submit(Runnable r)
	{
		r = new ThreadPoolExecuteWrapper(r);
		
		return _world.submit(r);
	}
	
	public final Future<?> submitLongRunning(Runnable r)
	{
		r = new ExecuteWrapper(r);
		
		return _longRunningPool.submit(r);
	}
	
	// ===========================================================================================
	
	public List<String> getStats()
	{
		List<String> list = new ArrayList<String>();
		
		list.add("");
		list.add("World thread:");
		list.add("=================================================");
		list.add("\tgetActiveCount: ...... " + _world.getActiveCount());
		list.add("\tgetCorePoolSize: ..... " + _world.getCorePoolSize());
		list.add("\tgetPoolSize: ......... " + _world.getPoolSize());
		list.add("\tgetLargestPoolSize: .. " + _world.getLargestPoolSize());
		list.add("\tgetMaximumPoolSize: .. " + _world.getMaximumPoolSize());
		list.add("\tgetCompletedTaskCount: " + _world.getCompletedTaskCount());
		list.add("\tgetQueuedTaskCount: .. " + _world.getQueue().size());
		list.add("\tgetTaskCount: ........ " + _world.getTaskCount());
		list.add("");
		list.add("Long running tasks (virtual threads):");
		list.add("=================================================");
		list.add("\tgetActiveCount: ...... " + _longRunningPool.getActiveCount());
		list.add("\tgetLargestActive: .... " + _longRunningPool.getLargestActiveCount());
		list.add("\tgetCompletedTaskCount: " + _longRunningPool.getCompletedTaskCount());
		list.add("");
		
		return list;
	}
	
	private boolean awaitTermination(long timeoutInMillisec) throws InterruptedException
	{
		final long begin = System.currentTimeMillis();
		
		while (System.currentTimeMillis() - begin < timeoutInMillisec)
		{
			if (!_world.awaitTermination(10, TimeUnit.MILLISECONDS) && _world.getActiveCount() > 0)
				continue;
			
			if (!_longRunningPool.awaitTermination(10, TimeUnit.MILLISECONDS) && _longRunningPool.getActiveCount() > 0)
				continue;
			
			return true;
		}
		
		return false;
	}
	
	private int getTaskCount(ThreadPoolExecutor tp)
	{
		return tp.getQueue().size() + tp.getActiveCount();
	}
	
	public void shutdown()
	{
		final long begin = System.currentTimeMillis();
		
		System.out.println("ThreadPoolManager: Shutting down.");
		System.out.println("\t... executing " + getTaskCount(_world) + " world tasks.");
		System.out.println("\t... executing " + _longRunningPool.getActiveCount() + " long running tasks.");
		
		_world.shutdown();
		_longRunningPool.shutdown();
		
		boolean success = false;
		try
		{
			success |= awaitTermination(5000);
			
			_world.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
			_world.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
			
			success |= awaitTermination(10000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		System.out.println("\t... success: " + success + " in " + (System.currentTimeMillis() - begin) + " msec.");
		System.out.println("\t... " + getTaskCount(_world) + " world tasks left.");
		System.out.println("\t... " + _longRunningPool.getActiveCount() + " long running tasks left.");
		
		if (TimeUnit.HOURS.toMillis(12) < (System.currentTimeMillis() - GameServer.getStartedTime().getTimeInMillis()))
			RunnableStatsManager.dumpClassStats(SortBy.TOTAL);
	}
	
	public void purge()
	{
		_world.purge();
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ThreadPoolManager _instance = new ThreadPoolManager();
	}
}
