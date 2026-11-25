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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.util.concurrent.L2RejectedExecutionHandler;

/**
 * The world thread keeps moving while background JDBC waits, a second save is not started until the first
 * finishes, and a creature change asked from another thread is applied on the world thread.
 */
class WorldBoundaryTest
{
	@AfterEach
	void resetGate()
	{
		ThreadPoolManager.jdbcGate = () -> {
		};
	}
	
	@Test
	void backgroundJdbcDoesNotStopAnotherTask() throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ThreadPoolManager.jdbcGate = () -> {
			entered.countDown();
			await(release);
		};
		CountDownLatch moved = new CountDownLatch(1);
		try
		{
			ThreadPoolManager.getInstance().execute(() -> {
				ThreadPoolManager.getInstance().runBackgroundOnce(new AtomicBoolean(), ThreadPoolManager::enterBackgroundJdbc);
				ThreadPoolManager.getInstance().execute(moved::countDown);
			});
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(moved.await(5, TimeUnit.SECONDS)).isTrue();
		}
		finally
		{
			release.countDown();
		}
	}
	
	@Test
	void timerDoesNotStartASecondSaveWhileTheFirstWaits() throws Exception
	{
		AtomicInteger started = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ThreadPoolManager.jdbcGate = () -> {
			started.incrementAndGet();
			entered.countDown();
			await(release);
		};
		AtomicBoolean inflight = new AtomicBoolean();
		try
		{
			ThreadPoolManager.getInstance().execute(() -> {
				ThreadPoolManager pool = ThreadPoolManager.getInstance();
				pool.runBackgroundOnce(inflight, ThreadPoolManager::enterBackgroundJdbc);
				pool.runBackgroundOnce(inflight, ThreadPoolManager::enterBackgroundJdbc);
				pool.runBackgroundOnce(inflight, ThreadPoolManager::enterBackgroundJdbc);
			});
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(200);
			assertThat(started).hasValue(1);
		}
		finally
		{
			release.countDown();
		}
		assertThat(awaitFalse(inflight)).isTrue();
		
		CountDownLatch again = new CountDownLatch(1);
		ThreadPoolManager.jdbcGate = again::countDown;
		ThreadPoolManager.getInstance().execute(() -> ThreadPoolManager.getInstance().runBackgroundOnce(inflight,
				ThreadPoolManager::enterBackgroundJdbc));
		assertThat(again.await(5, TimeUnit.SECONDS)).isTrue();
	}
	
	@Test
	void failedSaveLetsTheNextOneStart() throws Exception
	{
		AtomicBoolean inflight = new AtomicBoolean();
		CountDownLatch failed = new CountDownLatch(1);
		ThreadPoolManager.getInstance().execute(() -> ThreadPoolManager.getInstance().runBackgroundOnce(inflight, () -> {
			failed.countDown();
			throw new IllegalStateException("database");
		}));
		assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(awaitFalse(inflight)).isTrue();
		
		CountDownLatch next = new CountDownLatch(1);
		ThreadPoolManager.getInstance().execute(() -> ThreadPoolManager.getInstance().runBackgroundOnce(inflight, next::countDown));
		assertThat(next.await(5, TimeUnit.SECONDS)).isTrue();
	}
	
	@Test
	void theCopyIsTakenOnTheWorldThreadAndTheWriteRunsOnAVirtualThread() throws Exception
	{
		ThreadPoolManager pool = ThreadPoolManager.getInstance();
		AtomicBoolean inflight = new AtomicBoolean();
		CompletableFuture<Boolean> copiedOnWorld = new CompletableFuture<>();
		CompletableFuture<Boolean> wroteOnVirtual = new CompletableFuture<>();
		
		pool.execute(() -> pool.copyThenRunBackgroundOnce(inflight, () -> {
			copiedOnWorld.complete(pool.isWorldThread());
			return () -> wroteOnVirtual.complete(Thread.currentThread().isVirtual());
		}));
		
		assertThat(copiedOnWorld.get(5, TimeUnit.SECONDS)).isTrue();
		assertThat(wroteOnVirtual.get(5, TimeUnit.SECONDS)).isTrue();
		assertThat(awaitFalse(inflight)).isTrue();
	}
	
	@Test
	void nothingIsCopiedForARunThatWouldBeSkipped() throws Exception
	{
		ThreadPoolManager pool = ThreadPoolManager.getInstance();
		AtomicBoolean inflight = new AtomicBoolean(true);
		AtomicInteger copies = new AtomicInteger();
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		
		pool.execute(() -> result.complete(pool.copyThenRunBackgroundOnce(inflight, () -> {
			copies.incrementAndGet();
			return () -> {
			};
		})));
		
		assertThat(result.get(5, TimeUnit.SECONDS)).isFalse();
		assertThat(copies).hasValue(0);
		assertThat(inflight).isTrue();
	}
	
	@Test
	void aCopyOfNothingStartsNothingAndLeavesTheFlagFree() throws Exception
	{
		ThreadPoolManager pool = ThreadPoolManager.getInstance();
		AtomicBoolean inflight = new AtomicBoolean();
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		
		pool.execute(() -> result.complete(pool.copyThenRunBackgroundOnce(inflight, () -> null)));
		
		assertThat(result.get(5, TimeUnit.SECONDS)).isFalse();
		assertThat(inflight).isFalse();
	}
	
	@Test
	void creatureMonitorIsFreeWhileJdbcWaits() throws Exception
	{
		Object player = new Object();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch bypass = new CountDownLatch(1);
		ThreadPoolManager.jdbcGate = () -> {
			entered.countDown();
			await(release);
		};
		try
		{
			ThreadPoolManager.getInstance().executeLongRunning(() -> ThreadPoolManager.releaseThen(player, () -> {
			}, () -> {
			}));
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			ThreadPoolManager.getInstance().execute(() -> {
				synchronized (player)
				{
					bypass.countDown();
				}
			});
			assertThat(bypass.await(5, TimeUnit.SECONDS)).isTrue();
		}
		finally
		{
			release.countDown();
		}
	}
	
	@Test
	void foreignMutationRunsOnTheWorldThread() throws Exception
	{
		java.util.concurrent.CompletableFuture<Thread> movement = new java.util.concurrent.CompletableFuture<>();
		ThreadPoolManager.getInstance().execute(() -> movement.complete(Thread.currentThread()));
		Thread moved = movement.get(5, TimeUnit.SECONDS);
		
		java.util.concurrent.CompletableFuture<Thread> applied = new java.util.concurrent.CompletableFuture<>();
		Thread asker = new Thread(() -> ThreadPoolManager.getInstance().runOnWorld(() -> applied.complete(Thread.currentThread())),
				"rift-timer");
		asker.start();
		
		assertThat(applied.get(5, TimeUnit.SECONDS)).isSameAs(moved);
		assertThat(moved.getName()).isEqualTo("l2jfree-world");
		assertThat(moved.isVirtual()).isFalse();
	}
	
	@Test
	void slowCommandDoesNotHoldTheWorldThread() throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch packet = new CountDownLatch(1);
		ThreadPoolManager.jdbcGate = () -> {
			entered.countDown();
			await(release);
		};
		try
		{
			ThreadPoolManager.getInstance().execute(() -> {
				ThreadPoolManager.getInstance().executeLongRunning(ThreadPoolManager::enterBackgroundJdbc);
				packet.countDown();
			});
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(packet.await(5, TimeUnit.SECONDS)).isTrue();
		}
		finally
		{
			release.countDown();
		}
	}
	
	@Test
	void hpChangeUnderTheCreatureMonitorDoesNotWaitForTheWorld() throws Exception
	{
		L2Creature creature = mock(L2Creature.class, RETURNS_DEEP_STUBS);
		CreatureStatus status = new CreatureStatus(creature);
		CountDownLatch holderHasMonitor = new CountDownLatch(1);
		CountDownLatch worldEntered = new CountDownLatch(1);
		Thread holder = new Thread(() -> {
			synchronized (creature)
			{
				holderHasMonitor.countDown();
				try
				{
					Thread.sleep(200);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				status.setCurrentHp(0);
			}
		});
		holder.start();
		ThreadPoolManager.getInstance().execute(() -> {
			await(holderHasMonitor);
			synchronized (creature)
			{
				worldEntered.countDown();
			}
		});
		assertThat(worldEntered.await(3, TimeUnit.SECONDS)).isTrue();
		holder.join(1000);
		assertThat(holder.isAlive()).isFalse();
	}
	
	@Test
	void aWorldStepIsNotSplitByAnotherWorldTask() throws Exception
	{
		AtomicInteger phase = new AtomicInteger();
		AtomicBoolean sawMiddle = new AtomicBoolean();
		CountDownLatch middle = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		CountDownLatch observed = new CountDownLatch(1);
		Thread caller = new Thread(() -> {
			ThreadPoolManager.getInstance().runOnWorld(() -> {
				phase.set(1);
				middle.countDown();
				await(release);
				phase.set(2);
			});
			finished.countDown();
		});
		caller.start();
		assertThat(middle.await(5, TimeUnit.SECONDS)).isTrue();
		ThreadPoolManager.getInstance().execute(() -> {
			if (phase.get() == 1)
				sawMiddle.set(true);
			observed.countDown();
		});
		release.countDown();
		assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(observed.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(sawMiddle).isFalse();
		assertThat(phase).hasValue(2);
	}
	
	@Test
	void handedOffApplyReportsTheFailureAndSkipsTheNextStep()
	{
		AtomicBoolean next = new AtomicBoolean();
		assertThatThrownBy(() -> ThreadPoolManager.getInstance().runOnWorld(() -> {
			throw new IllegalStateException("apply failed");
		})).isInstanceOf(IllegalStateException.class).hasMessage("apply failed");
		assertThat(next).isFalse();
		
		assertThatThrownBy(() -> ThreadPoolManager.getInstance().runOnWorld(() -> {
			throw new AssertionError("apply error");
		})).isInstanceOf(AssertionError.class).hasMessage("apply error");
	}
	
	@Test
	void aClosedWorldDoesNotLeaveTheCallerWaiting()
	{
		ScheduledThreadPoolExecutor world = new ScheduledThreadPoolExecutor(1);
		world.setRejectedExecutionHandler(new L2RejectedExecutionHandler());
		world.shutdown();
		assertThatThrownBy(() -> ThreadPoolManager.applyOn(false, world, () -> {
		})).isInstanceOf(RejectedExecutionException.class);
	}
	
	@Test
	void interruptingTheCallerDoesNotLeaveTheApplyOrTheCallerHanging() throws Exception
	{
		ScheduledThreadPoolExecutor world = new ScheduledThreadPoolExecutor(1);
		world.setRejectedExecutionHandler(new L2RejectedExecutionHandler());
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean finished = new AtomicBoolean();
		Thread caller = new Thread(() -> ThreadPoolManager.applyOn(false, world, () -> {
			started.countDown();
			await(release);
			finished.set(true);
		}));
		caller.start();
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		caller.interrupt();
		caller.join(3000);
		assertThat(caller.isAlive()).isFalse();
		release.countDown();
		for (int i = 0; i < 50 && !finished.get(); i++)
			Thread.sleep(20);
		assertThat(finished).isTrue();
		world.shutdownNow();
	}
	
	@Test
	void aRejectedBackgroundStartClearsTheInflightFlag()
	{
		AtomicBoolean inflight = new AtomicBoolean();
		assertThatThrownBy(() -> ThreadPoolManager.startOnce(inflight, () -> {
		}, task -> {
			throw new RejectedExecutionException("pool stopped");
		})).isInstanceOf(RejectedExecutionException.class);
		assertThat(inflight).isFalse();
	}
	
	@Test
	void aRunInFlightIsReportedAsSkippedAndTheNextOneStartsAfterIt()
	{
		AtomicBoolean inflight = new AtomicBoolean();
		Runnable[] started = new Runnable[1];
		
		assertThat(ThreadPoolManager.startOnce(inflight, () -> {
		}, task -> started[0] = task)).isTrue();
		assertThat(ThreadPoolManager.startOnce(inflight, () -> {
		}, task -> {
			throw new AssertionError("a second run must not start beside the first");
		})).isFalse();
		
		started[0].run();
		assertThat(inflight).isFalse();
		assertThat(ThreadPoolManager.startOnce(inflight, () -> {
		}, task -> started[0] = task)).isTrue();
	}
	
	@Test
	void aRejectionWhileTheServerStopsIsASkipNotAnError()
	{
		AtomicBoolean inflight = new AtomicBoolean();
		
		assertThat(ThreadPoolManager.startUnlessStopping(inflight, () -> {
		}, task -> {
			throw new RejectedExecutionException("pool stopped");
		}, () -> true)).isFalse();
		assertThat(inflight).isFalse();
		
		assertThatThrownBy(() -> ThreadPoolManager.startUnlessStopping(inflight, () -> {
		}, task -> {
			throw new RejectedExecutionException("pool broken");
		}, () -> false)).isInstanceOf(RejectedExecutionException.class);
		assertThat(inflight).isFalse();
	}
	
	private static void await(CountDownLatch latch)
	{
		try
		{
			if (!latch.await(5, TimeUnit.SECONDS))
				throw new IllegalStateException("timed out waiting for the jdbc gate");
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
	
	private static boolean awaitFalse(AtomicBoolean flag) throws InterruptedException
	{
		for (int i = 0; i < 50; i++)
		{
			if (!flag.get())
				return true;
			Thread.sleep(20);
		}
		return !flag.get();
	}
}
