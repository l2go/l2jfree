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
package com.l2jfree.loginserver.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginDataAccessException;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;
import com.l2jfree.loginserver.dao.impl.AccountsDAOJdbc;
import com.l2jfree.sql.SchemaMigration;

/** The login module against a real PostgreSQL 18: the migration, the pool settings, and the DAOs. */
@Tag("integration")
@Testcontainers
class LoginSchemaPostgresTest
{
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
	
	private static LoginDataSource source;
	private static AccountsDAOJdbc accounts;
	
	@BeforeAll
	static void migrateTheSchema() throws Exception
	{
		try (Connection connection = POSTGRES.createConnection(""); Statement statement = connection.createStatement())
		{
			statement.execute("CREATE SCHEMA login");
		}
		
		LoginConfig.DATABASE_DRIVER = "org.postgresql.Driver";
		LoginConfig.DATABASE_URL = POSTGRES.getJdbcUrl();
		LoginConfig.DATABASE_LOGIN = POSTGRES.getUsername();
		LoginConfig.DATABASE_PASSWORD = POSTGRES.getPassword();
		LoginConfig.DATABASE_MAX_CONNECTIONS = 4;
		LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS = 1;
		
		source = new LoginDataSource(LoginDataSource.createPoolConfig());
		SchemaMigration.migrate(source.getDataSource(), LoginDataSource.SCHEMA, "classpath:db/login");
		
		JdbcTransactions transactions = new JdbcTransactions(source.getDataSource());
		accounts = new AccountsDAOJdbc(transactions);
	}
	
	@AfterAll
	static void closeThePool()
	{
		source.close();
	}
	
	@Test
	@DisplayName("the migration is applied once and a second run applies nothing")
	void migrationIsIdempotent() throws Exception
	{
		assertThat(SchemaMigration.migrate(source.getDataSource(), LoginDataSource.SCHEMA, "classpath:db/login"))
				.isZero();
		
		try (Connection connection = source.getDataSource().getConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'login' "
								+ "AND table_name = 'account'");
				ResultSet result = statement.executeQuery())
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
		}
	}
	
	@Test
	@DisplayName("V2 removed the world server table and left the last world of an account a plain number")
	void gameServerTableIsGone() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement())
		{
			try (ResultSet result = statement.executeQuery("SELECT to_regclass('login.game_server')"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getString(1)).isNull();
			}
			try (ResultSet result = statement.executeQuery("SELECT count(*) FROM pg_constraint "
					+ "WHERE conrelid = 'login.account'::regclass AND contype = 'f'"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isZero();
			}
			try (ResultSet result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history "
					+ "WHERE version = '2' AND success"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}
		}
	}
	
	@Test
	@DisplayName("the pool checks out a connection that sees the login schema and sends text untyped")
	void poolUsesTheLoginSchema() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT current_schema()"))
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1)).isEqualTo("login");
		}
		assertThat(source.getPoolStatus()).contains("max=4");
	}
	
	@Test
	@DisplayName("an account keeps its values through the PostgreSQL types and is found without regard to case")
	void accountRoundTrip()
	{
		BigDecimal lastActive = new BigDecimal(1_700_000_123_456L);
		accounts.createAccount(new Accounts("Round_Trip", "hash-1", lastActive, 0, 0, 2000, 2, 29, "192.0.2.7"));
		
		Accounts found = accounts.getAccountById("ROUND_trip");
		
		assertThat(found.getLogin()).isEqualTo("Round_Trip");
		assertThat(found.getPassword()).isEqualTo("hash-1");
		assertThat(found.getLastactive()).isEqualByComparingTo(lastActive);
		assertThat(found.getLastIp()).isEqualTo("192.0.2.7");
		assertThat(found.getLastServerId()).isZero();
		assertThat(found.getBirthYear()).isEqualTo(2000);
		assertThat(found.getBirthMonth()).isEqualTo(2);
		assertThat(found.getBirthDay()).isEqualTo(29);
		assertThat(found.getAccessLevel()).isZero();
	}
	
	@Test
	@DisplayName("an impossible birth date becomes 1900-01-01 and an empty address becomes NULL")
	void impossibleBirthdayAndEmptyAddress()
	{
		accounts.createAccount(new Accounts("Odd_Values", "hash", null, 0, 0, 2001, 2, 30, ""));
		
		Accounts found = accounts.getAccountById("odd_values");
		
		assertThat(found.getBirthYear()).isEqualTo(1900);
		assertThat(found.getBirthMonth()).isEqualTo(1);
		assertThat(found.getBirthDay()).isEqualTo(1);
		assertThat(found.getLastIp()).isNull();
		assertThat(found.getLastactive()).isNull();
	}
	
	@Test
	@DisplayName("createOrUpdate inserts once and then updates only the columns it is given")
	void upsertUpdatesInPlace()
	{
		accounts.createOrUpdate(new Accounts("Upserted", "first", null, 0, 0, 1999, 12, 31, null));
		accounts.createOrUpdate(new Accounts("Upserted", "second", null, 100, null, null, null, null, null));
		
		Accounts found = accounts.getAccountById("upserted");
		
		assertThat(found.getPassword()).isEqualTo("second");
		assertThat(found.getAccessLevel()).isEqualTo(100);
		assertThat(found.getBirthYear()).isEqualTo(1999);
		assertThat(found.getBirthMonth()).isEqualTo(12);
		assertThat(found.getBirthDay()).isEqualTo(31);
	}
	
	@Test
	@DisplayName("update writes every column and reports a missing account")
	void updateWritesAllColumns()
	{
		accounts.createAccount(new Accounts("To_Update", "old", null, 0, 0, 2000, 1, 1, null));
		Accounts account = accounts.getAccountById("to_update");
		account.setPassword("new");
		account.setLastIp("198.51.100.4");
		account.setBirthYear(1990);
		account.setBirthMonth(6);
		account.setBirthDay(15);
		
		accounts.update(account);
		
		Accounts found = accounts.getAccountById("to_update");
		assertThat(found.getPassword()).isEqualTo("new");
		assertThat(found.getLastIp()).isEqualTo("198.51.100.4");
		assertThat(found.getBirthYear()).isEqualTo(1990);
		assertThat(found.getBirthMonth()).isEqualTo(6);
		assertThat(found.getBirthDay()).isEqualTo(15);
		
		assertThatThrownBy(() -> accounts.update(new Accounts("Missing_Account")))
				.isInstanceOf(LoginObjectNotFoundException.class);
	}
	
	@Test
	@DisplayName("updateAccessLevel is true for an existing account even when the level does not change")
	void updateAccessLevelWithoutChange()
	{
		accounts.createAccount(new Accounts("Level_User", "hash", null, 50, 0, 2000, 1, 1, null));
		
		assertThat(accounts.updateAccessLevel("level_user", 50)).isTrue();
		assertThat(accounts.updateAccessLevel("level_user", 75)).isTrue();
		assertThat(accounts.getAccountById("level_user").getAccessLevel()).isEqualTo(75);
		assertThat(accounts.updateAccessLevel("no_such_user", 1)).isFalse();
	}
	
	@Test
	@DisplayName("the last world of an account is stored without a world server table")
	void lastWorldIsAPlainNumber()
	{
		accounts.createAccount(new Accounts("World_User", "hash", null, 0, 7, 2000, 1, 1, null));
		
		assertThat(accounts.getAccountById("world_user").getLastServerId()).isEqualTo(7);
	}
	
	@Test
	@DisplayName("a transaction commits its work and rolls back on failure")
	void transactionsCommitAndRollBack() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement())
		{
			statement.execute("CREATE TABLE transaction_probe (value integer NOT NULL)");
		}
		JdbcTransactions transactions = new JdbcTransactions(source.getDataSource());
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO transaction_probe VALUES (?)"))
			{
				statement.setInt(1, 1);
				statement.executeUpdate();
			}
			return null;
		});
		
		assertThatThrownBy(() -> transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO transaction_probe VALUES (?)"))
			{
				statement.setInt(1, 2);
				statement.executeUpdate();
			}
			throw new SQLException("Force rollback");
		})).isInstanceOf(LoginDataAccessException.class);
		
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT value FROM transaction_probe"))
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
			assertThat(result.next()).isFalse();
		}
	}
}
