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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
	
	private static final long MAX_DELAY = TimeUnit.NANOSECONDS.toMillis(Long.MAX_VALUE - System.nanoTime()) / 2;
	
	/** Test seam. Production leaves it empty. A stalled gate must not sit on the world thread. */
	static volatile Runnable jdbcGate = () -> {
	};
	
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
	/** Blocking work (admin commands, cleanups, JDBC): one virtual thread per task. */
	private final VirtualTaskExecutor _longRunningPool;
	
	private ThreadPoolManager()
	{
		_world = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("l2jfree-world").factory());
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
		return "l2jfree-world".equals(Thread.currentThread().getName());
	}
	
	/**
	 * Applies a short world change on {@code l2jfree-world}. The caller waits when it is some other thread,
	 * so a rift timer or a match thread can sleep on its own and still not write a creature beside the world.
	 */
	public void runOnWorld(Runnable action)
	{
		if (isWorldThread())
		{
			action.run();
			return;
		}
		
		try
		{
			submit(action).get();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while applying world state", e);
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
	 */
	public void runBackgroundOnce(AtomicBoolean inflight, Runnable body)
	{
		if (!isWorldThread())
		{
			body.run();
			return;
		}
		
		if (!inflight.compareAndSet(false, true))
			return;
		
		executeLongRunning(() -> {
			try
			{
				body.run();
			}
			finally
			{
				inflight.set(false);
			}
		});
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
