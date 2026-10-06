/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.db;

import javax.sql.DataSource;

import com.l2jfree.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/** Owns the login server's JDBC connection pool. */
public final class LoginDataSource implements AutoCloseable
{
	/** The schema this module owns. */
	public static final String SCHEMA = "login";
	
	private final HikariDataSource dataSource;

	public LoginDataSource()
	{
		this(createPoolConfig());
	}

	static HikariConfig createPoolConfig()
	{
		HikariConfig pool = new HikariConfig();
		pool.setPoolName("l2jfree-loginserver");
		pool.setDriverClassName(Config.DATABASE_DRIVER);
		pool.setJdbcUrl(Config.DATABASE_URL);
		pool.setUsername(Config.DATABASE_LOGIN);
		pool.setPassword(Config.DATABASE_PASSWORD);
		pool.setAutoCommit(true);
		int maximumPoolSize = Math.max(1, Config.DATABASE_MAX_CONNECTIONS);
		int idleConnections = Config.DATABASE_MIN_IDLE_CONNECTIONS;
		if (idleConnections < 0)
			idleConnections = 0;
		if (idleConnections > maximumPoolSize)
			idleConnections = maximumPoolSize;
		pool.setMaximumPoolSize(maximumPoolSize);
		pool.setMinimumIdle(idleConnections);
		pool.setConnectionTimeout(30_000);
		pool.setValidationTimeout(5_000);
		// Text parameters are sent untyped, so the server types them from the column: a login name is
		// compared as citext (without regard to case) and an address is stored as inet.
		pool.addDataSourceProperty("stringtype", "unspecified");
		// The schema of the module comes first. The public schema stays in the path because the citext type lives there.
		pool.addDataSourceProperty("currentSchema", SCHEMA + ",public");
		pool.addDataSourceProperty("ApplicationName", "l2jfree-login");
		pool.setInitializationFailTimeout(30_000);
		return pool;
	}

	LoginDataSource(HikariConfig pool)
	{
		dataSource = createDataSource(pool);
	}

	private static HikariDataSource createDataSource(HikariConfig pool)
	{
		return new HikariDataSource(pool);
	}

	public DataSource getDataSource()
	{
		return dataSource;
	}

	public int getBusyConnectionCount()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getActiveConnections();
	}

	public int getIdleConnectionCount()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getIdleConnections();
	}

	public String getPoolStatus()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		if (pool == null)
		{
			return "closed";
		}
		return "active=" + pool.getActiveConnections() + ", idle=" + pool.getIdleConnections() + ", total="
				+ pool.getTotalConnections() + ", waiting=" + pool.getThreadsAwaitingConnection() + ", max="
				+ dataSource.getMaximumPoolSize();
	}

	@Override
	public void close()
	{
		dataSource.close();
	}
}
