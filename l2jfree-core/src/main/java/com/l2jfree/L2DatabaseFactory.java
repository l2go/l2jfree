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
import java.util.Locale;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class L2DatabaseFactory
{
	private static final Logger _log = LoggerFactory.getLogger(L2DatabaseFactory.class);
	
	public static enum ProviderType
	{
		MySql,
		MsSql
	}
	
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
	
	private final ProviderType _providerType;
	private final HikariDataSource _source;
	
	private L2DatabaseFactory()
	{
		this(createPoolConfig(), Config.DATABASE_DRIVER.toLowerCase(Locale.ROOT).contains("microsoft")
				? ProviderType.MsSql : ProviderType.MySql);
	}

	L2DatabaseFactory(HikariConfig poolConfig, ProviderType providerType)
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

			_providerType = providerType;
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

	private static HikariConfig createPoolConfig()
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
		poolConfig.setMinimumIdle(10);
		poolConfig.setConnectionTimeout(30_000);
		poolConfig.setValidationTimeout(5_000);
		if (Config.DATABASE_DRIVER.toLowerCase(Locale.ROOT).contains("mysql"))
		{
			poolConfig.addDataSourceProperty("cachePrepStmts", "true");
			poolConfig.addDataSourceProperty("prepStmtCacheSize", "100");
			poolConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		}
		poolConfig.setInitializationFailTimeout(30_000);
		return poolConfig;
	}
	
	public void shutdown() throws Exception
	{
		_source.close();
	}
	
	public String safetyString(String... whatToCheck)
	{
		// NOTE: Use brace as a safty percaution just incase name is a reserved word
		String braceLeft = "`";
		String braceRight = "`";
		if (getProviderType() == ProviderType.MsSql)
		{
			braceLeft = "[";
			braceRight = "]";
		}
		
		String result = "";
		for (String word : whatToCheck)
		{
			if (!result.isEmpty())
				result += ", ";
			
			result += braceLeft + word + braceRight;
		}
		return result;
	}
	
	public Connection getConnection()
	{
		return getConnection(null);
	}
	
	public Connection getConnection(Connection con)
	{
		if (con != null)
		{
			return con;
		}
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
	
	public ProviderType getProviderType()
	{
		return _providerType;
	}
	
	/*@SuppressWarnings("unused")
	private static final class L2DatabaseFactoryConnectionWrapper extends ConnectionWrapper
	{
		private static final Map<StackTraceElement, Integer> CALLS = new FastMap<StackTraceElement, Integer>();
		
		private static final ThreadLocal<List<Connection>> CONNECTIONS = new ThreadLocal<List<Connection>>() {
			@Override
			protected List<Connection> initialValue()
			{
				return new ArrayList<Connection>();
			}
		};
		
		public L2DatabaseFactoryConnectionWrapper(Connection connection)
		{
			super(connection);
			
			final List<Connection> list = CONNECTIONS.get();
			
			list.add(this);
			
			final int size = list.size();
			
			if (size > 1)
			{
				synchronized (L2DatabaseFactoryConnectionWrapper.class)
				{
					final StackTraceElement caller = getCaller();
					
					final Integer prevValue = CALLS.get(caller);
					
					CALLS.put(caller, Math.max(size, prevValue == null ? 0 : prevValue.intValue()));
				}
			}
		}
		
		@Override
		public void close() throws SQLException
		{
			super.close();
			
			final List<Connection> list = CONNECTIONS.get();
			
			list.remove(this);
		}
		
		private static StackTraceElement getCaller()
		{
			final StackTraceElement stack[] = new Throwable().getStackTrace();
			
			for (StackTraceElement ste : stack)
			{
				if (ste.getClassName().contains("L2DatabaseFactory"))
					continue;
				
				return ste;
			}
			
			throw new InternalError();
		}
	}*/
}
