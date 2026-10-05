/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.zaxxer.hikari.HikariConfig;

@Tag("integration")
@Testcontainers
class L2DatabaseFactoryMySqlTest
{
	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@Test
	void startsAndChecksOutAConnectionWithoutASyntheticPoolTable() throws Exception
	{
		HikariConfig config = new HikariConfig();
		config.setPoolName("gameserver-mysql-integration-test");
		config.setDriverClassName("com.mysql.cj.jdbc.Driver");
		config.setJdbcUrl(MYSQL.getJdbcUrl());
		config.setUsername(MYSQL.getUsername());
		config.setPassword(MYSQL.getPassword());
		config.setAutoCommit(true);
		config.setMinimumIdle(1);
		config.setMaximumPoolSize(3);
		config.setConnectionTimeout(10_000);

		L2DatabaseFactory factory = new L2DatabaseFactory(config, L2DatabaseFactory.ProviderType.MySql);
		try
		{
			try (Connection connection = factory.getConnection();
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT 1"))
			{
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(1);
			}
			assertThat(factory.getConnectionPoolStatus()).contains("max=3");
		}
		finally
		{
			factory.shutdown();
		}
	}
}
