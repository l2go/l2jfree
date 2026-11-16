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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
