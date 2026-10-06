/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;

/**
 * Runs the work that belongs together, such as saving a player, in one database transaction.
 * <p>
 * While the work runs, every connection that {@link L2DatabaseFactory#getConnection()} hands out on the same thread
 * is the connection of the transaction. The work cannot close it, change its auto-commit mode, or end the transaction;
 * this class commits when the work returns and rolls back when it throws. A call made inside another transaction joins
 * the outer one.
 */
public final class WorldTransaction
{
	private static final Logger _log = LoggerFactory.getLogger(WorldTransaction.class);
	
	/** The connection of the transaction that runs on this thread. */
	private static final ScopedValue<Connection> CURRENT = ScopedValue.newInstance();
	
	private WorldTransaction()
	{
	}
	
	/** The connection of the transaction that runs on this thread, or null when there is none. */
	public static Connection current()
	{
		return CURRENT.isBound() ? CURRENT.get() : null;
	}
	
	/**
	 * @param what a short description for the log
	 * @return true if the work was committed
	 */
	public static boolean run(String what, Runnable work)
	{
		return run(what, () -> L2DatabaseFactory.getInstance().getPoolConnection(), work);
	}
	
	/** Like {@link #run(String, Runnable)} with a connection from the given source. */
	public static boolean run(String what, Supplier<Connection> source, Runnable work)
	{
		if (CURRENT.isBound())
		{
			work.run();
			return true;
		}
		
		try (Connection connection = source.get())
		{
			connection.setAutoCommit(false);
			try
			{
				ScopedValue.where(CURRENT, shared(connection)).run(work);
				// After a failed statement PostgreSQL refuses every other one until the transaction ends. The work may
				// have caught the error, so a probe makes sure the transaction is still usable before it commits.
				try (Statement probe = connection.createStatement())
				{
					probe.execute("SELECT 1");
				}
				connection.commit();
				return true;
			}
			catch (SQLException | RuntimeException e)
			{
				connection.rollback();
				_log.error(what + " failed and was rolled back.", e);
				return false;
			}
			catch (Error e)
			{
				// Switching auto-commit on would commit what the work did so far, so the rollback comes first.
				connection.rollback();
				_log.error(what + " failed with an error and was rolled back.", e);
				throw e;
			}
			finally
			{
				try
				{
					connection.setAutoCommit(true);
				}
				catch (SQLException e)
				{
					// The transaction is over, committed or rolled back: a failure here must not change the outcome.
					_log.warn(what + ": could not switch auto-commit back on.", e);
				}
			}
		}
		catch (SQLException e)
		{
			_log.error(what + " could not run.", e);
			return false;
		}
	}
	
	/** The connection as the work sees it: it cannot close the connection or end the transaction. */
	private static Connection shared(Connection connection)
	{
		return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					String name = method.getName();
					boolean noArguments = args == null || args.length == 0;
					if (name.equals("close") || name.equals("setAutoCommit")
							|| (noArguments && (name.equals("commit") || name.equals("rollback"))))
					{
						return null;
					}
					try
					{
						return method.invoke(connection, args);
					}
					catch (InvocationTargetException e)
					{
						throw e.getCause();
					}
				});
	}
}
