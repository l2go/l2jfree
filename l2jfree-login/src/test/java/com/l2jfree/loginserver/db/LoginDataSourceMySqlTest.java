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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.zaxxer.hikari.HikariConfig;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.impl.AccountsDAOJdbc;
import com.l2jfree.loginserver.dao.LoginDataAccessException;
import com.l2jfree.loginserver.dao.JdbcTransactions;

@Tag("integration")
@Testcontainers
class LoginDataSourceMySqlTest
{
	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Test
	void checksOutAConnectionWithoutCreatingAPoolTestTable() throws Exception
	{
		HikariConfig config = new HikariConfig();
		config.setPoolName("loginserver-mysql-integration-test");
		config.setDriverClassName("com.mysql.cj.jdbc.Driver");
		config.setJdbcUrl(MYSQL.getJdbcUrl());
		config.setUsername(MYSQL.getUsername());
		config.setPassword(MYSQL.getPassword());
		config.setMinimumIdle(1);
		config.setMaximumPoolSize(3);
		config.setConnectionTimeout(10_000);

		try (LoginDataSource source = new LoginDataSource(config);
				Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT 1"))
		{
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt(1)).isEqualTo(1);
			assertThat(source.getBusyConnectionCount()).isEqualTo(1);
			assertThat(source.getPoolStatus()).contains("max=3");
		}
	}

	@Test
	void jdbcTransactionsCommitAndRollbackAgainstMysql84() throws Exception
	{
		HikariConfig config = new HikariConfig();
		config.setPoolName("loginserver-transaction-integration-test");
		config.setDriverClassName("com.mysql.cj.jdbc.Driver");
		config.setJdbcUrl(MYSQL.getJdbcUrl());
		config.setUsername(MYSQL.getUsername());
		config.setPassword(MYSQL.getPassword());
		config.setMinimumIdle(1);
		config.setMaximumPoolSize(3);
		config.setConnectionTimeout(10_000);

		try (LoginDataSource source = new LoginDataSource(config))
		{
			try (Connection connection = source.getDataSource().getConnection();
					Statement statement = connection.createStatement())
			{
				statement.execute("CREATE TABLE transaction_probe (value INT NOT NULL)");
			}

			JdbcTransactions transactions = new JdbcTransactions(source.getDataSource());
			transactions.inTransaction(connection -> {
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO transaction_probe (value) VALUES (?)"))
				{
					statement.setInt(1, 1);
					statement.executeUpdate();
				}
				return null;
			});

			assertThatThrownBy(() -> transactions.inTransaction(connection -> {
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO transaction_probe (value) VALUES (?)"))
				{
					statement.setInt(1, 2);
					statement.executeUpdate();
				}
				throw new SQLException("Force rollback");
			})).isInstanceOf(LoginDataAccessException.class);

			try (Connection connection = source.getDataSource().getConnection();
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT value FROM transaction_probe"))
			{
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(1);
				assertThat(resultSet.next()).isFalse();
			}
		}
	}

	@Test
	void accountDaoCreatesUpdatesAndReadsRowsUsingMysql84UpsertSyntax() throws Exception
	{
		HikariConfig config = new HikariConfig();
		config.setPoolName("loginserver-account-dao-integration-test");
		config.setDriverClassName("com.mysql.cj.jdbc.Driver");
		config.setJdbcUrl(MYSQL.getJdbcUrl());
		config.setUsername(MYSQL.getUsername());
		config.setPassword(MYSQL.getPassword());
		config.setMinimumIdle(1);
		config.setMaximumPoolSize(3);
		config.setConnectionTimeout(10_000);

		try (LoginDataSource source = new LoginDataSource(config))
		{
			try (InputStream schema = getClass().getClassLoader().getResourceAsStream("accounts.sql");
					Connection connection = source.getDataSource().getConnection();
					Statement statement = connection.createStatement())
			{
				assertThat(schema).as("accounts SQL schema resource").isNotNull();
				statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
			}

			AccountsDAOJdbc accounts = new AccountsDAOJdbc(new JdbcTransactions(source.getDataSource()));
			accounts.createOrUpdate(new Accounts("jdbc_integration", "first_hash", null, 0, 0, 2000, 1, 1, null));
			accounts.createOrUpdate(new Accounts("jdbc_integration", "updated_hash", null, 200, 34, 2000, 1, 1, null));

			Accounts persisted = accounts.getAccountById("jdbc_integration");
			assertThat(persisted.getPassword()).isEqualTo("updated_hash");
			assertThat(persisted.getAccessLevel()).isEqualTo(200);
			assertThat(persisted.getLastServerId()).isEqualTo(34);
		}
	}
}
