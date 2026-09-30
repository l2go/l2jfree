/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CommandExecutionException;
import liquibase.exception.ValidationFailedException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class LiquibaseMySql84CompatibilityTest
{
	private static final String CHANGELOG = "db/changelog/liquibase-compatibility.sql";

	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Test
	void appliesRepeatValidatesAndRejectsChangedChecksumsOnMysql84() throws Exception
	{
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
				MYSQL.getPassword()))
		{
			Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
			Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
			Contexts contexts = new Contexts();
			LabelExpression labels = new LabelExpression();

			liquibase.update(contexts, labels);
			liquibase.validate();
			liquibase.update(contexts, labels);

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG"))
			{
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(2);
			}

			String checksum;
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT MD5SUM FROM DATABASECHANGELOG WHERE ID = ? AND AUTHOR = ?"))
			{
				statement.setString(1, "1");
				statement.setString(2, "l2jfree");
				try (ResultSet resultSet = statement.executeQuery())
				{
					assertThat(resultSet.next()).isTrue();
					checksum = resultSet.getString(1);
				}
			}

			String changedChecksum = flipChecksumDigit(checksum);
			try (PreparedStatement statement = connection.prepareStatement(
					"UPDATE DATABASECHANGELOG SET MD5SUM = ? WHERE ID = ? AND AUTHOR = ?"))
			{
				statement.setString(1, changedChecksum);
				statement.setString(2, "1");
				statement.setString(3, "l2jfree");
				assertThat(statement.executeUpdate()).isEqualTo(1);
			}

			assertThatThrownBy(liquibase::validate).isInstanceOf(CommandExecutionException.class)
					.hasCauseInstanceOf(ValidationFailedException.class);
		}
	}

	private static String flipChecksumDigit(String checksum)
	{
		int digitIndex = checksum.indexOf(':') + 1;
		if (digitIndex == 0 || digitIndex >= checksum.length())
		{
			throw new IllegalStateException("Unexpected Liquibase checksum format: " + checksum);
		}
		char original = checksum.charAt(digitIndex);
		char changed = original == '0' ? '1' : '0';
		return checksum.substring(0, digitIndex) + changed + checksum.substring(digitIndex + 1);
	}
}
