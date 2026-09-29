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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MultiSellPersistenceTest
{
	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@BeforeEach
	void createTables() throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement())
		{
			statement.execute("DROP TABLE IF EXISTS castle");
			statement.execute("DROP TABLE IF EXISTS clan_data");
			statement.execute("DROP TABLE IF EXISTS characters");
			statement.execute("DROP TABLE IF EXISTS pets");
			statement.execute("DROP TABLE IF EXISTS item_attributes");
			statement.execute("DROP TABLE IF EXISTS items");
			statement.execute("CREATE TABLE items (object_id INT PRIMARY KEY, owner_id INT, item_id INT, "
					+ "count BIGINT, enchant_level INT, loc VARCHAR(10), loc_data INT, mana_left INT, time BIGINT)");
			statement.execute("CREATE TABLE item_attributes (itemId INT PRIMARY KEY, augAttributes INT, "
					+ "augSkillId INT, augSkillLevel INT, elemType INT, elemValue INT)");
			statement.execute("CREATE TABLE pets (item_obj_id INT PRIMARY KEY)");
			statement.execute("CREATE TABLE characters (charId INT PRIMARY KEY, fame INT)");
			statement.execute("CREATE TABLE clan_data (clan_id INT PRIMARY KEY, reputation_score INT)");
			statement.execute("CREATE TABLE castle (id INT PRIMARY KEY, treasury BIGINT)");
			statement.execute("INSERT INTO items VALUES (10, 100, 1, 5, 0, 'INVENTORY', 0, -1, 0)");
			statement.execute("INSERT INTO characters VALUES (100, 50)");
			statement.execute("INSERT INTO clan_data VALUES (200, 100)");
			statement.execute("INSERT INTO castle VALUES (1, 1000)");
		}
	}

	@Test
	void productInsertFailureRollsBackEarlierDebit() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(10, 100, 5, 2);
		exchange.insert(10, 100, 2, 1, 0);

		try (Connection connection = connection())
		{
			assertThatThrownBy(() -> exchange.commit(connection)).isInstanceOf(java.sql.SQLException.class);
		}

		assertThat(count(10)).isEqualTo(5);
	}

	@Test
	void successfulExchangePersistsDebitAndProductTogether() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(10, 100, 5, 2);
		exchange.insert(11, 100, 2, 1, 3);

		try (Connection connection = connection())
		{
			exchange.commit(connection);
		}

		assertThat(count(10)).isEqualTo(2);
		assertThat(count(11)).isEqualTo(1);
	}

	@Test
	void laterFailureRestoresDeletedIngredientAndAttributes() throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement())
		{
			statement.execute("INSERT INTO item_attributes VALUES (10, -1, -1, -1, 2, 100)");
			statement.execute("INSERT INTO pets VALUES (10)");
		}

		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(10, 100, 5, 0);
		exchange.insert(11, 100, 2, 1, 0);
		exchange.insert(11, 100, 2, 1, 0);

		try (Connection connection = connection())
		{
			assertThatThrownBy(() -> exchange.commit(connection)).isInstanceOf(java.sql.SQLException.class);
		}

		assertThat(count(10)).isEqualTo(5);
		assertThat(hasRow("item_attributes", "itemId", 10)).isTrue();
		assertThat(hasRow("pets", "item_obj_id", 10)).isTrue();
	}

	@Test
	void failedProductInsertRollsBackAllAuxiliaryBalances() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(10, 100, 5, 4);
		exchange.adjustFame(100, 50, 40);
		exchange.adjustClanReputation(200, 100, 90);
		exchange.adjustCastleTreasury(1, 1000, 1010);
		exchange.insert(10, 100, 2, 1, 0);

		try (Connection connection = connection())
		{
			assertThatThrownBy(() -> exchange.commit(connection)).isInstanceOf(java.sql.SQLException.class);
		}

		assertThat(count(10)).isEqualTo(5);
		assertThat(value("characters", "fame", "charId", 100)).isEqualTo(50);
		assertThat(value("clan_data", "reputation_score", "clan_id", 200)).isEqualTo(100);
		assertThat(value("castle", "treasury", "id", 1)).isEqualTo(1000);
	}

	@Test
	void productAttributesCommitWithItem() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(10, 100, 5, 4);
		exchange.insert(11, 100, 2, 1, 3);
		exchange.insertAttributes(11, 123, 456, 7, (byte)2, 100);

		try (Connection connection = connection())
		{
			exchange.commit(connection);
		}

		assertThat(count(11)).isEqualTo(1);
		assertThat(value("item_attributes", "augAttributes", "itemId", 11)).isEqualTo(123);
		assertThat(value("item_attributes", "elemValue", "itemId", 11)).isEqualTo(100);
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

	private static boolean hasRow(String table, String column, int objectId) throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT 1 FROM " + table + " WHERE " + column + "=" + objectId))
		{
			return result.next();
		}
	}

	private static long value(String table, String column, String key, int id) throws Exception
	{
		try (Connection connection = connection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
						"SELECT " + column + " FROM " + table + " WHERE " + key + "=" + id))
		{
			assertThat(result.next()).isTrue();
			return result.getLong(1);
		}
	}
}
