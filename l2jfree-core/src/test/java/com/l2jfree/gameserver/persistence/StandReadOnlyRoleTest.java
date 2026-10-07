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
package com.l2jfree.gameserver.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/** The read-only role of the stand profile ({@code deploy/stand/simulator/readonly-role.sql}). */
@Tag("integration")
class StandReadOnlyRoleTest
{
	private static final Path SCRIPT = Path.of("..", "deploy", "stand", "simulator", "readonly-role.sql");
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	/** Roles belong to the whole server, so the test uses its own name instead of simulator_ro. */
	private static final String ROLE =
			"simulator_ro_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
	private static final String PASSWORD = "stand-test";
	
	@BeforeAll
	static void createRole() throws Exception
	{
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
		String sql = Files.readString(SCRIPT).replace("simulator_ro", ROLE);
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			statement.execute(sql);
			statement.execute(sql); // the script can run again, for example after a password change
			statement.execute("ALTER ROLE " + ROLE + " PASSWORD '" + PASSWORD + "'");
		}
	}
	
	@AfterAll
	static void dropRole() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			statement.execute("DROP OWNED BY " + ROLE);
			statement.execute("DROP ROLE " + ROLE);
		}
	}
	
	@Test
	void readsTheFiveColumnsOfThePlayerTable() throws Exception
	{
		try (Connection con = connectAsRole(); Statement statement = con.createStatement();
				ResultSet rs = statement.executeQuery("SELECT id, name, account_name, exp, sp FROM world.player"))
		{
			assertThat(rs.getMetaData().getColumnCount()).isEqualTo(5);
		}
	}
	
	@Test
	void sessionsAreReadOnly() throws Exception
	{
		try (Connection con = connectAsRole(); Statement statement = con.createStatement();
				ResultSet rs = statement.executeQuery("SHOW transaction_read_only"))
		{
			rs.next();
			assertThat(rs.getString(1)).isEqualTo("on");
		}
	}
	
	@ParameterizedTest
	@ValueSource(strings = {
		"SELECT * FROM world.player",
		"SELECT title FROM world.player",
		"SELECT count(*) FROM world.item",
		"UPDATE world.player SET exp = 0",
		"DELETE FROM world.player",
		"INSERT INTO world.player (id) VALUES (1)",
		"TRUNCATE world.player",
		"CREATE TABLE world.stand_probe (id integer)",
		"CREATE TABLE public.stand_probe (id integer)",
		"CREATE SCHEMA stand_probe",
	})
	void isRefusedEverythingElse(String sql) throws Exception
	{
		try (Connection con = connectAsRole(); Statement statement = con.createStatement())
		{
			// The session may switch read-only mode off; the privileges must refuse the statement anyway.
			statement.execute("SET default_transaction_read_only = off");
			assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class)
					.extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("42501");
		}
	}
	
	private static Connection connectAsRole() throws SQLException
	{
		return DriverManager.getConnection(POSTGRES.jdbcUrl(), ROLE, PASSWORD);
	}
}
