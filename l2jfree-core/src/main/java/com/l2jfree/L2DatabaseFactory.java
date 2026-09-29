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
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// import javax.persistence.EntityManager;
// import javax.persistence.EntityManagerFactory;
// import javax.persistence.Persistence;
// import javolution.util.FastMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
// import org.eclipse.persistence.config.PersistenceUnitProperties;

// import com.l2jfree.sql.ConnectionWrapper;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public final class L2DatabaseFactory
{
	private static final Log _log = LogFactory.getLog(L2DatabaseFactory.class);
	
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
	private final ComboPooledDataSource _source;
	
	// private final EntityManagerFactory _entityManagerFactory;
	
	private L2DatabaseFactory()
	{
		try
		{
			if (Config.DATABASE_MAX_CONNECTIONS < 10)
			{
				Config.DATABASE_MAX_CONNECTIONS = 10;
				_log.warn("at least " + Config.DATABASE_MAX_CONNECTIONS + " db connections are required.");
			}
			
			_source = new ComboPooledDataSource();
			_source.setAutoCommitOnClose(true);
			
			_source.setInitialPoolSize(10);
			_source.setMinPoolSize(10);
			_source.setMaxPoolSize(Config.DATABASE_MAX_CONNECTIONS);
			
			_source.setAcquireRetryAttempts(0); // try to obtain connections indefinitely (0 = never quit)
			_source.setAcquireRetryDelay(500); // 500 miliseconds wait before try to acquire connection again
			_source.setCheckoutTimeout(0); // 0 = wait indefinitely for new connection
			// if pool is exhausted
			_source.setAcquireIncrement(5); // if pool is exhausted, get 5 more connections at a time
			// cause there is a "long" delay on acquire connection
			// so taking more than one connection at once will make connection pooling
			// more effective.
			
			// Avoid requiring a test table or CREATE TABLE permissions from the database user.
			_source.setPreferredTestQuery("SELECT 1");
			_source.setTestConnectionOnCheckin(false);
			
			// testing OnCheckin used with IdleConnectionTestPeriod is faster than testing on checkout
			
			_source.setIdleConnectionTestPeriod(3600); // test idle connections every hour
			_source.setMaxIdleTime(1800); // 0 = idle connections never expire
			// *THANKS* to connection testing configured above
			// but I prefer to disconnect all connections not used
			// for more than 1 hour
			
			// enables statement caching, there is a "semi-bug" in c3p0 0.9.0 but in 0.9.0.2 and later it's fixed
			_source.setMaxStatementsPerConnection(100);
			
			_source.setBreakAfterAcquireFailure(false); // never fail if any way possible
			// setting this to true will make
			// c3p0 "crash" and refuse to work
			// till restart thus making acquire
			// errors "FATAL" ... we don't want that
			// it should be possible to recover
			_source.setDriverClass(Config.DATABASE_DRIVER);
			_source.setJdbcUrl(Config.DATABASE_URL);
			_source.setUser(Config.DATABASE_LOGIN);
			_source.setPassword(Config.DATABASE_PASSWORD);
			
			/* Test the connection */
			_source.getConnection().close();
			
			if (Config.DATABASE_DRIVER.toLowerCase().contains("microsoft"))
				_providerType = ProviderType.MsSql;
			else
				_providerType = ProviderType.MySql;
			
			// ====================================================================================
			// initialization of the EclipseLink JPA
			//
			/*final Map<Object, Object> props = new HashMap<Object, Object>();
			
			props.put(PersistenceUnitProperties.NON_JTA_DATASOURCE, _source);
			
			_entityManagerFactory = Persistence.createEntityManagerFactory("default", props);
			
			// test the entity manager
			_entityManagerFactory.createEntityManager().close();*/
		}
		catch (Exception e)
		{
			throw new Error("L2DatabaseFactory: Failed to init database connections: " + e, e);
		}
	}
	
	public void shutdown() throws Exception
	{
		/*try
		{
			_entityManagerFactory.close();
		}
		catch (Throwable t)
		{
			_log.fatal("", t);
		}*/
		
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
		while (con == null)
		{
			try
			{
				con = _source.getConnection();
				//con = new L2DatabaseFactoryConnectionWrapper(_source.getConnection());
			}
			catch (SQLException e)
			{
				_log.fatal("L2DatabaseFactory: Failed to retrieve database connection!", e);
			}
		}
		
		return con;
	}
	
	/*public EntityManager getEntityManager()
	{
		return _entityManagerFactory.createEntityManager();
	}*/
	
	public int getBusyConnectionCount() throws SQLException
	{
		return _source.getNumBusyConnectionsDefaultUser();
	}
	
	public int getIdleConnectionCount() throws SQLException
	{
		return _source.getNumIdleConnectionsDefaultUser();
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
