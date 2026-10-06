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
package com.l2jfree;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * The connection pool of the world module.
 * <p>
 * Connections see the schemas <code>world</code>, <code>catalog</code>, and <code>public</code> in this order, so a
 * statement names its tables without a schema. Text parameters are sent untyped, so the server types them from the
 * column: a character name is compared as <code>citext</code>, without regard to case (ADR-0009).
 * <p>
 * Inside a {@link WorldTransaction}, {@link #getConnection()} hands out the connection of the transaction. Everything
 * that runs there joins it.
 */
public final class L2DatabaseFactory
{
	private static final Logger _log = LoggerFactory.getLogger(L2DatabaseFactory.class);
	
	private static final class SingletonHolder
	{
		private static final L2DatabaseFactory INSTANCE = new L2DatabaseFactory();
	}
	
	public static L2DatabaseFactory getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public static void close(Connection con)
	{
		if (con == null)
			return;
		
		try
		{
			con.close();
		}
		catch (SQLException e)
		{
			_log.warn("L2DatabaseFactory: Failed to close database connection!", e);
		}
	}
	
	private final HikariDataSource _source;
	
	private L2DatabaseFactory()
	{
		this(createPoolConfig());
	}
	
	L2DatabaseFactory(HikariConfig poolConfig)
	{
		HikariDataSource source = null;
		try
		{
			source = new HikariDataSource(poolConfig);
			
			/* Validate the pool before publishing it to the rest of the server. */
			try (Connection connection = source.getConnection())
			{
				// A successful checkout confirms the configured driver and database are usable.
			}
			
			_source = source;
		}
		catch (Exception e)
		{
			if (source != null)
			{
				try
				{
					source.close();
				}
				catch (RuntimeException closeFailure)
				{
					e.addSuppressed(closeFailure);
				}
			}
			throw new IllegalStateException("L2DatabaseFactory: Failed to initialize database connections", e);
		}
	}
	
	static HikariConfig createPoolConfig()
	{
		if (Config.DATABASE_MAX_CONNECTIONS < 10)
		{
			Config.DATABASE_MAX_CONNECTIONS = 10;
			_log.warn("at least " + Config.DATABASE_MAX_CONNECTIONS + " db connections are required.");
		}
		
		HikariConfig poolConfig = new HikariConfig();
		poolConfig.setPoolName("l2jfree-gameserver");
		poolConfig.setDriverClassName(Config.DATABASE_DRIVER);
		poolConfig.setJdbcUrl(Config.DATABASE_URL);
		poolConfig.setUsername(Config.DATABASE_LOGIN);
		poolConfig.setPassword(Config.DATABASE_PASSWORD);
		poolConfig.setAutoCommit(true);
		poolConfig.setMaximumPoolSize(Config.DATABASE_MAX_CONNECTIONS);
		int idleConnections = Config.DATABASE_MIN_IDLE_CONNECTIONS;
		if (idleConnections < 0)
			idleConnections = 0;
		if (idleConnections > Config.DATABASE_MAX_CONNECTIONS)
			idleConnections = Config.DATABASE_MAX_CONNECTIONS;
		poolConfig.setMinimumIdle(idleConnections);
		poolConfig.setConnectionTimeout(30_000);
		poolConfig.setValidationTimeout(5_000);
		poolConfig.addDataSourceProperty("stringtype", "unspecified");
		poolConfig.addDataSourceProperty("currentSchema", "world,catalog,public");
		poolConfig.addDataSourceProperty("ApplicationName", "l2jfree-world");
		poolConfig.setInitializationFailTimeout(30_000);
		return poolConfig;
	}
	
	public void shutdown() throws Exception
	{
		_source.close();
	}
	
	/** The pool, for the work that does not go through a connection: migrations and the catalog load. */
	public DataSource getDataSource()
	{
		return _source;
	}
	
	public Connection getConnection()
	{
		return getConnection(null);
	}
	
	/**
	 * @param con a connection the caller already has, or null
	 * @return the given connection; otherwise the connection of the running {@link WorldTransaction}, if any; otherwise a
	 *         connection from the pool
	 */
	public Connection getConnection(Connection con)
	{
		if (con != null)
		{
			return con;
		}
		
		Connection transaction = WorldTransaction.current();
		if (transaction != null)
		{
			return transaction;
		}
		
		return getPoolConnection();
	}
	
	/** A connection from the pool, outside any transaction. The caller closes it. */
	public Connection getPoolConnection()
	{
		try
		{
			return _source.getConnection();
		}
		catch (SQLException e)
		{
			_log.error("L2DatabaseFactory: Failed to retrieve database connection", e);
			throw new IllegalStateException("Unable to acquire a database connection", e);
		}
	}
	
	public int getBusyConnectionCount() throws SQLException
	{
		HikariPoolMXBean pool = _source.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getActiveConnections();
	}
	
	public int getIdleConnectionCount() throws SQLException
	{
		HikariPoolMXBean pool = _source.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getIdleConnections();
	}
	
	public String getConnectionPoolStatus()
	{
		HikariPoolMXBean pool = _source.getHikariPoolMXBean();
		if (pool == null)
		{
			return "closed";
		}
		return "active=" + pool.getActiveConnections() + ", idle=" + pool.getIdleConnections() + ", total="
				+ pool.getTotalConnections() + ", waiting=" + pool.getThreadsAwaitingConnection() + ", max="
				+ _source.getMaximumPoolSize();
	}
}
