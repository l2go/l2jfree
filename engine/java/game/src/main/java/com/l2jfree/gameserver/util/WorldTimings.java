package com.l2jfree.gameserver.util;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.ThreadPoolManager;

/**
 * Measurement branch only, not for main. Counts, on the world thread, how long tasks run and how much of that is
 * spent in packet broadcast, and logs one line per window.
 */
public final class WorldTimings
{
	private static final Logger _log = LoggerFactory.getLogger(WorldTimings.class);
	private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5);
	
	// All fields are touched by the world thread only.
	private static int _depth;
	private static long _taskStart;
	private static long _windowStart = System.nanoTime();
	private static long _busy;
	private static long _broadcast;
	private static long _tasks;
	private static long _calls;
	private static long _maxTask;
	private static long _maxBroadcastTask;
	private static long _taskBroadcast;
	
	private WorldTimings()
	{
	}
	
	/** @return a token for {@link #end}, or 0 when the caller is not the world thread or a broadcast is already open */
	public static long begin()
	{
		if (!ThreadPoolManager.getInstance().isWorldThread() || _depth++ > 0)
			return 0L;
		
		return System.nanoTime();
	}
	
	public static void end(long begin)
	{
		if (begin == 0L)
		{
			if (ThreadPoolManager.getInstance().isWorldThread() && _depth > 0)
				_depth--;
			return;
		}
		
		final long spent = System.nanoTime() - begin;
		_depth = 0;
		_broadcast += spent;
		_taskBroadcast += spent;
		_calls++;
	}
	
	public static void taskBegin()
	{
		_taskStart = System.nanoTime();
		_taskBroadcast = 0L;
	}
	
	public static void taskEnd()
	{
		final long now = System.nanoTime();
		final long spent = now - _taskStart;
		_busy += spent;
		_tasks++;
		_maxTask = Math.max(_maxTask, spent);
		_maxBroadcastTask = Math.max(_maxBroadcastTask, _taskBroadcast);
		if (now - _windowStart >= WINDOW_NANOS)
		{
			_log.info("WorldTimings: window " + (now - _windowStart) / 1_000_000 + " ms, busy " + _busy / 1_000_000
					+ " ms, broadcast " + _broadcast / 1_000_000 + " ms in " + _calls + " calls, tasks " + _tasks
					+ ", max task " + _maxTask / 1000 + " us, max broadcast in one task " + _maxBroadcastTask / 1000
					+ " us");
			_windowStart = now;
			_busy = _broadcast = _tasks = _calls = _maxTask = _maxBroadcastTask = 0L;
		}
	}
}
