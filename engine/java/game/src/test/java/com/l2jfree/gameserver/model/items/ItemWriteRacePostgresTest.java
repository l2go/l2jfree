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
package com.l2jfree.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.model.L2Augmentation;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.persistence.WorldDatabase;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.item.ItemRepository;
import com.l2jfree.gameserver.persistence.item.ItemRepository.ItemAttributes;
import com.l2jfree.gameserver.persistence.item.ItemRepository.ItemRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerRow;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * The physical item row follows the insert, the delete and the rollback, and a newer write is the one that remains.
 */
@Tag("integration")
class ItemWriteRacePostgresTest
{
	private static final int PLAYER = 1_960_000_301;
	private static final int ATTR = 1_960_000_311;
	private static final int CHANGED = 1_960_000_312;
	private static final int CONSUMED = 1_960_000_313;
	private static final int REMOVED = 1_960_000_314;
	private static final int ROLLED = 1_960_000_315;
	private static final int RACED = 1_960_000_316;
	
	private static final PlayerRepository PLAYERS = PlayerRepository.getInstance();
	private static final ItemRepository ITEMS = ItemRepository.getInstance();
	
	@BeforeAll
	static void startTheDatabase()
	{
		WorldDatabase.start();
	}
	
	@AfterEach
	void removeWhatTheTestAdded()
	{
		ThreadPoolManager.setJdbcGate(() -> {
		});
		WorldDatabase.execute("DELETE FROM item_attribute WHERE item_id IN (" + ATTR + ", " + CHANGED + ", " + CONSUMED
				+ ", " + REMOVED + ", " + ROLLED + ", " + RACED + ")");
		WorldDatabase.execute("DELETE FROM item WHERE id IN (" + ATTR + ", " + CHANGED + ", " + CONSUMED + ", "
				+ REMOVED + ", " + ROLLED + ", " + RACED + ")");
		WorldDatabase.execute("DELETE FROM player WHERE id = " + PLAYER);
	}
	
	@Test
	void anInsertWritesAugmentationAndElementOnTheSameConnection() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(ATTR, 1);
		rememberAugmentation(item, 15);
		item.setElementAttr((byte)0, 10);
		
		assertThat(WorldTransaction.run("insert the item", () -> item.updateDatabase(true))).isTrue();
		
		ItemAttributes stored = ITEMS.loadAttributes(null, ATTR);
		assertThat(stored).isNotNull();
		assertThat(stored.augmentationAttributes()).isEqualTo(15);
		assertThat(stored.elementType()).isEqualTo(0);
		assertThat(stored.elementValue()).isEqualTo(10);
		assertThat(item.existsInDatabase()).isTrue();
		assertThat(item.storedInDatabase()).isTrue();
	}
	
	@Test
	void aChangeDuringInsertDoesNotInsertASecondRow() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(CHANGED, 4);
		writeWhile(item, () -> item.changeCount("test", 5, null, null));
		
		assertThat(item.existsInDatabase()).isTrue();
		assertThat(item.storedInDatabase()).isFalse();
		assertThat(stored(CHANGED).count()).isEqualTo(4);
		
		assertThat(WorldTransaction.run("write the change", () -> item.updateDatabase(true))).isTrue();
		assertThat(stored(CHANGED).count()).isEqualTo(9);
		assertThat(item.storedInDatabase()).isTrue();
		assertThat(countRows(CHANGED)).isEqualTo(1);
	}
	
	@Test
	void consumingTheItemDuringInsertDeletesTheRowOnTheNextWrite() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(CONSUMED, 4);
		writeWhile(item, () -> item.setCount(0));
		
		assertThat(item.existsInDatabase()).isTrue();
		assertThat(stored(CONSUMED).count()).isEqualTo(4);
		
		assertThat(WorldTransaction.run("remove the consumed item", () -> item.updateDatabase(true))).isTrue();
		assertThat(stored(CONSUMED)).isNull();
		assertThat(item.existsInDatabase()).isFalse();
	}
	
	@Test
	void aChangeDuringRemoveInsertsTheItemAgain() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(REMOVED, 4);
		assertThat(WorldTransaction.run("insert", () -> item.updateDatabase(true))).isTrue();
		item.setCount(0);
		
		writeWhile(item, () -> item.setCount(5));
		
		assertThat(item.existsInDatabase()).isFalse();
		assertThat(stored(REMOVED)).isNull();
		
		assertThat(WorldTransaction.run("insert again", () -> item.updateDatabase(true))).isTrue();
		assertThat(stored(REMOVED).count()).isEqualTo(5);
		assertThat(item.existsInDatabase()).isTrue();
	}
	
	@Test
	void aRolledBackInsertRestoresExistenceAfterAChange() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(ROLLED, 3);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ThreadPoolManager.setJdbcGate(() -> {
			entered.countDown();
			await(release);
		});
		AtomicReference<Boolean> committed = new AtomicReference<>();
		Thread writer = new Thread(() -> committed.set(WorldTransaction.run("insert that rolls back", () -> {
			item.updateDatabase(true);
			throw new IllegalStateException("the rest of the work failed");
		})));
		writer.start();
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		item.changeCount("test", 2, null, null);
		release.countDown();
		writer.join(5000);
		
		assertThat(committed.get()).isFalse();
		assertThat(item.existsInDatabase()).isFalse();
		assertThat(item.storedInDatabase()).isFalse();
		assertThat(stored(ROLLED)).isNull();
		
		assertThat(WorldTransaction.run("insert after the rollback", () -> item.updateDatabase(true))).isTrue();
		assertThat(stored(ROLLED).count()).isEqualTo(5);
		assertThat(item.existsInDatabase()).isTrue();
	}
	
	@Test
	void anOlderUpdateDoesNotReplaceANewerOne() throws Exception
	{
		PLAYERS.insertPlayer(newPlayer());
		L2ItemInstance item = item(RACED, 4);
		assertThat(WorldTransaction.run("insert", () -> item.updateDatabase(true))).isTrue();
		item.setCount(10);
		
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch secondDone = new CountDownLatch(1);
		ThreadPoolManager.setJdbcGate(() -> {
			entered.countDown();
			await(release);
		});
		Thread older = new Thread(() -> WorldTransaction.run("older update", () -> item.updateDatabase(true)));
		older.start();
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		item.setCount(20);
		Thread newer = new Thread(() -> {
			WorldTransaction.run("newer update", () -> item.updateDatabase(true));
			secondDone.countDown();
		});
		newer.start();
		Thread.sleep(100);
		release.countDown();
		older.join(5000);
		assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
		
		assertThat(stored(RACED).count()).isEqualTo(20);
		assertThat(item.storedInDatabase()).isTrue();
		assertThat(countRows(RACED)).isEqualTo(1);
	}
	
	private static void writeWhile(L2ItemInstance item, Runnable duringSql) throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ThreadPoolManager.setJdbcGate(() -> {
			entered.countDown();
			await(release);
		});
		Thread writer = new Thread(() -> WorldTransaction.run("write", () -> item.updateDatabase(true)));
		writer.start();
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		duringSql.run();
		release.countDown();
		writer.join(5000);
		ThreadPoolManager.setJdbcGate(() -> {
		});
	}
	
	private static void rememberAugmentation(L2ItemInstance item, int attributes) throws Exception
	{
		L2Augmentation augmentation = org.mockito.Mockito.mock(L2Augmentation.class);
		org.mockito.Mockito.when(augmentation.getAttributes()).thenReturn(attributes);
		Field field = L2ItemInstance.class.getDeclaredField("_augmentation");
		field.setAccessible(true);
		field.set(item, augmentation);
	}
	
	private static PlayerRow newPlayer()
	{
		PlayerRow row = new PlayerRow();
		row.id = PLAYER;
		row.accountName = "ItemWriteRacePostgresTest_account";
		row.name = "ItemWriteRace";
		row.title = "";
		row.raceId = 0;
		row.activeClassId = 0;
		row.baseClassId = 0;
		row.level = 1;
		row.newbieRewardMask = 1;
		return row;
	}
	
	private static L2ItemInstance item(int id, long count)
	{
		L2ItemInstance item = new L2ItemInstance(id, adena());
		item.setOwnerId(PLAYER);
		item.setLocation(L2ItemInstance.ItemLocation.INVENTORY);
		item.setCount(count);
		return item;
	}
	
	private static L2Item adena()
	{
		StatsSet set = new StatsSet();
		set.set("item_id", 57);
		set.set("item_display_id", 57);
		set.set("name", "Adena");
		set.set("type1", 4);
		set.set("type2", 4);
		set.set("weight", 0);
		set.set("crystallizable", false);
		set.set("stackable", true);
		set.set("material", 0);
		set.set("crystal_type", 0);
		set.set("duration", -1);
		set.set("time", -1);
		set.set("bodypart", 0);
		set.set("price", 0);
		set.set("crystal_count", 0);
		set.set("handler", "none");
		set.set("skills_item", "");
		return new L2EtcItem(L2EtcItemType.MONEY, set);
	}
	
	private static ItemRow stored(int id) throws Exception
	{
		List<ItemRow> rows = ITEMS.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL");
		for (ItemRow row : rows)
		{
			if (row.id() == id)
				return row;
		}
		return null;
	}
	
	private static int countRows(int id) throws Exception
	{
		int count = 0;
		for (ItemRow row : ITEMS.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL"))
		{
			if (row.id() == id)
				count++;
		}
		return count;
	}
	
	private static void await(CountDownLatch latch)
	{
		try
		{
			if (!latch.await(5, TimeUnit.SECONDS))
				throw new IllegalStateException("timed out");
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
