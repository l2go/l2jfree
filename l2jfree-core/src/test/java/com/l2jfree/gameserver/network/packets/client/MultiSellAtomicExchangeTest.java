/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellEntry;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellIngredient;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;

@Testcontainers
class MultiSellAtomicExchangeTest
{
	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@BeforeEach
	void createTables() throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement())
		{
			statement.execute("DROP TABLE IF EXISTS pets");
			statement.execute("DROP TABLE IF EXISTS item_attributes");
			statement.execute("DROP TABLE IF EXISTS items");
			statement.execute("CREATE TABLE items (object_id INT PRIMARY KEY, owner_id INT, item_id INT, "
					+ "count BIGINT, enchant_level INT, loc VARCHAR(10), loc_data INT)");
			statement.execute("CREATE TABLE item_attributes (itemId INT PRIMARY KEY)");
			statement.execute("CREATE TABLE pets (item_obj_id INT PRIMARY KEY)");
			statement.execute("INSERT INTO items VALUES (10, 100, 1, 5, 0, 'INVENTORY', 0)");
		}
	}

	@Test
	void laterCreditFailureLeavesLiveInventoryAndReloadedRowsUnchanged() throws Exception
	{
		Fixture fixture = fixture();

		assertThat(fixture.exchange.execute()).isFalse();
		verify(fixture.inventory, never()).publishCommittedMultisell(anyMap(), anyList());
		assertThat(fixture.ingredient.getCount()).isEqualTo(5);
		assertThat(count(10)).isEqualTo(5);
		assertThat(count(20)).isEqualTo(-1);
	}

	@Test
	void successfulExchangePublishesOnlyAfterDurableCommit() throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement())
		{
			statement.execute("INSERT INTO items VALUES (20, 100, 2, 1, 0, 'INVENTORY', 0)");
		}
		Fixture fixture = fixture();

		assertThat(fixture.exchange.execute()).isTrue();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<L2ItemInstance, Long>> counts = ArgumentCaptor.forClass(Map.class);
		verify(fixture.inventory).publishCommittedMultisell(counts.capture(), anyList());
		assertThat(counts.getValue()).containsEntry(fixture.ingredient, 4L)
				.containsEntry(fixture.reward, 2L);
		assertThat(count(10)).isEqualTo(4);
		assertThat(count(20)).isEqualTo(2);
	}

	private static Fixture fixture() throws Exception
	{
		L2Player player = mock(L2Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		L2ItemInstance ingredient = item(10, 1, 5);
		L2ItemInstance reward = item(20, 2, 1);
		L2Item template = mock(L2Item.class);
		when(template.isStackable()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getObjectId()).thenReturn(100);
		when(inventory.itemSetLock()).thenReturn(new Object());
		when(inventory.getItems()).thenReturn(new L2ItemInstance[] { ingredient, reward });
		when(inventory.getAllItemsByItemId(2)).thenReturn(new L2ItemInstance[] { reward });

		MultiSellEntry entry = new MultiSellEntry(1);
		entry.addIngredient(new MultiSellIngredient(1, 1));
		entry.addProduct(new MultiSellIngredient(2, 1));
		MultiSellAtomicExchange.Resources resources = new MultiSellAtomicExchange.Resources()
		{
			@Override
			public void flush(L2ItemInstance[] items)
			{
			}

			@Override
			public L2Item template(int itemId)
			{
				return template;
			}

			@Override
			public int nextObjectId()
			{
				throw new AssertionError("An existing reward stack must be reused");
			}

			@Override
			public void releaseObjectId(int objectId)
			{
			}

			@Override
			public Connection connection() throws java.sql.SQLException
			{
				return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
			}
		};
		return new Fixture(inventory, ingredient, reward,
				new MultiSellAtomicExchange(player, entry, 1, false, null, 0, resources));
	}

	private static L2ItemInstance item(int objectId, int itemId, long count)
	{
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getObjectId()).thenReturn(objectId);
		when(item.getItemId()).thenReturn(itemId);
		when(item.getCount()).thenReturn(count);
		when(item.isStackable()).thenReturn(true);
		return item;
	}

	private static long count(int objectId) throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT count FROM items WHERE object_id=" + objectId))
		{
			return result.next() ? result.getLong(1) : -1;
		}
	}

	private static Connection connection() throws Exception
	{
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	private static final class Fixture
	{
		final PlayerInventory inventory;
		final L2ItemInstance ingredient;
		final L2ItemInstance reward;
		final MultiSellAtomicExchange exchange;

		Fixture(PlayerInventory inventory, L2ItemInstance ingredient, L2ItemInstance reward,
				MultiSellAtomicExchange exchange)
		{
			this.inventory = inventory;
			this.ingredient = ingredient;
			this.reward = reward;
			this.exchange = exchange;
		}
	}
}
