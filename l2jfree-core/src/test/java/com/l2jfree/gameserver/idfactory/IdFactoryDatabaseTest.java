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
package com.l2jfree.gameserver.idfactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class IdFactoryDatabaseTest
{
	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	@BeforeEach
	void createTables() throws Exception
	{
		try (Connection con = connection(); Statement statement = con.createStatement())
		{
			statement.execute("DROP TABLE IF EXISTS couples");
			statement.execute("DROP TABLE IF EXISTS clan_data");
			statement.execute("DROP TABLE IF EXISTS itemsonground");
			statement.execute("DROP TABLE IF EXISTS items");
			statement.execute("DROP TABLE IF EXISTS characters");
			statement.execute("CREATE TABLE characters (charId INT PRIMARY KEY)");
			statement.execute("CREATE TABLE items (object_id INT PRIMARY KEY)");
			statement.execute("CREATE TABLE clan_data (clan_id INT PRIMARY KEY, crest_id INT, crest_large_id INT, ally_crest_id INT)");
			statement.execute("CREATE TABLE couples (id INT PRIMARY KEY)");
			statement.execute("CREATE TABLE itemsonground (object_id INT PRIMARY KEY)");
		}
	}

	@Test
	void allPersistedFactoryIdsAreReservedOnRestart() throws Exception
	{
		int first = IdFactory.FIRST_OID;
		try (Connection con = connection(); Statement statement = con.createStatement())
		{
			statement.executeUpdate("INSERT INTO characters VALUES (" + (first + 1) + ")");
			statement.executeUpdate("INSERT INTO items VALUES (" + (first + 2) + ")");
			statement.executeUpdate("INSERT INTO clan_data VALUES (" + (first + 3) + ", "
					+ (first + 4) + ", " + (first + 5) + ", " + (first + 6) + ")");
			statement.executeUpdate("INSERT INTO couples VALUES (" + (first + 7) + ")");
			statement.executeUpdate("INSERT INTO itemsonground VALUES (" + (first + 8) + ")");
			statement.executeUpdate("INSERT INTO clan_data VALUES (" + (first + 9) + ", 0, NULL, 0)");

			assertThat(IdFactory.readUsedObjectIds(con)).containsExactly(
					first + 1, first + 2, first + 3, first + 4, first + 5,
					first + 6, first + 7, first + 8, first + 9);
		}
	}

	@Test
	void deletedCrestAndCoupleIdsAreNoLongerReserved() throws Exception
	{
		int first = IdFactory.FIRST_OID;
		try (Connection con = connection(); Statement statement = con.createStatement())
		{
			statement.executeUpdate("INSERT INTO clan_data VALUES (" + (first + 1) + ", "
					+ (first + 2) + ", NULL, NULL)");
			statement.executeUpdate("INSERT INTO couples VALUES (" + (first + 3) + ")");
			statement.executeUpdate("UPDATE clan_data SET crest_id = NULL");
			statement.executeUpdate("DELETE FROM couples");

			assertThat(IdFactory.readUsedObjectIds(con)).containsExactly(first + 1);
		}
	}

	private static Connection connection() throws Exception
	{
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}
}
