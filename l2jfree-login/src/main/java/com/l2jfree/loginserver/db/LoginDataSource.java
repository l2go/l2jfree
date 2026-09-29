/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.db;

import java.beans.PropertyVetoException;
import java.sql.SQLException;

import com.l2jfree.Config;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.PooledDataSource;

/** Owns the login server's c3p0 connection pool. */
public final class LoginDataSource implements AutoCloseable
{
	private final ComboPooledDataSource dataSource;

	public LoginDataSource()
	{
		ComboPooledDataSource pool = new ComboPooledDataSource();
		try
		{
			pool.setDriverClass(Config.DATABASE_DRIVER);
		}
		catch (PropertyVetoException e)
		{
			pool.close();
			throw new IllegalStateException("Unable to load login database driver " + Config.DATABASE_DRIVER, e);
		}

		pool.setJdbcUrl(Config.DATABASE_URL);
		pool.setUser(Config.DATABASE_LOGIN);
		pool.setPassword(Config.DATABASE_PASSWORD);
		pool.setAcquireIncrement(5);
		pool.setAcquireRetryAttempts(0);
		pool.setAcquireRetryDelay(500);
		pool.setIdleConnectionTestPeriod(600);
		pool.setMaxIdleTime(1800);
		pool.setBreakAfterAcquireFailure(false);
		pool.setCheckoutTimeout(0);
		pool.setInitialPoolSize(3);
		pool.setMinPoolSize(1);
		pool.setMaxPoolSize(20);
		pool.setMaxStatementsPerConnection(100);
		pool.setAutoCommitOnClose(true);
		pool.setAutomaticTestTable("connection_test_table");
		pool.setTestConnectionOnCheckin(true);
		pool.setNumHelperThreads(3);
		dataSource = pool;
	}

	public ComboPooledDataSource getDataSource()
	{
		return dataSource;
	}

	public int getBusyConnectionCount() throws SQLException
	{
		return ((PooledDataSource)dataSource).getNumBusyConnectionsDefaultUser();
	}

	public int getIdleConnectionCount() throws SQLException
	{
		return ((PooledDataSource)dataSource).getNumIdleConnectionsDefaultUser();
	}

	@Override
	public void close()
	{
		dataSource.close();
	}
}
