/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.l2jfree.loginserver.beans.Gameservers;
import com.l2jfree.loginserver.dao.GameserversDAO;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;

/** JDBC persistence for the <code>login.game_server</code> table. */
public final class GameserversDAOJdbc implements GameserversDAO
{
	private final JdbcTransactions transactions;

	public GameserversDAOJdbc(JdbcTransactions transactions)
	{
		this.transactions = transactions;
	}

	@Override
	public List<Gameservers> getAllGameservers()
	{
		return transactions.withConnection(connection -> {
			List<Gameservers> servers = new ArrayList<Gameservers>();
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT id, registration_key, host FROM game_server ORDER BY id");
					ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					servers.add(readGameserver(result));
				}
			}
			return servers;
		});
	}

	@Override
	public int createGameserver(Gameservers gameserver)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO game_server (id, registration_key, host) VALUES (?, ?, ?)"))
			{
				bind(statement, gameserver);
				statement.executeUpdate();
				return gameserver.getServerId();
			}
		});
	}

	@Override
	public void createOrUpdate(Gameservers gameserver)
	{
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO game_server "
					+ "(id, registration_key, host) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
					+ "registration_key = EXCLUDED.registration_key, host = EXCLUDED.host"))
			{
				bind(statement, gameserver);
				statement.executeUpdate();
			}
			return null;
		});
	}

	@Override
	public void createOrUpdateAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO game_server "
					+ "(id, registration_key, host) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
					+ "registration_key = EXCLUDED.registration_key, host = EXCLUDED.host"))
			{
				for (Object entity : entities)
				{
					Gameservers gameserver = requireGameserver(entity);
					bind(statement, gameserver);
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		});
	}

	@Override
	public void update(Object object)
	{
		Gameservers gameserver = requireGameserver(object);
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(
					"UPDATE game_server SET registration_key=?, host=? WHERE id=?"))
			{
				statement.setString(1, gameserver.getHexid());
				statement.setString(2, gameserver.getHost());
				statement.setInt(3, gameserver.getServerId());
				if (statement.executeUpdate() == 0)
				{
					throw new LoginObjectNotFoundException("Gameserver", gameserver.getServerId());
				}
			}
			return null;
		});
	}

	@Override
	public void removeGameserver(Gameservers gameserver)
	{
		removeGameserverByServerId(gameserver.getServerId());
	}

	@Override
	public Gameservers getGameserverByServerId(int id)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT id, registration_key, host FROM game_server WHERE id=?"))
			{
				statement.setInt(1, id);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new LoginObjectNotFoundException("Gameserver", id);
					}
					return readGameserver(result);
				}
			}
		});
	}

	@Override
	public void removeGameserverByServerId(int id)
	{
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM game_server WHERE id=?"))
			{
				statement.setInt(1, id);
				statement.executeUpdate();
			}
			return null;
		});
	}

	@Override
	public void removeAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM game_server WHERE id=?"))
			{
				for (Object entity : entities)
				{
					statement.setInt(1, requireGameserver(entity).getServerId());
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		});
	}

	@Override
	public void removeAll()
	{
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM game_server"))
			{
				statement.executeUpdate();
			}
			return null;
		});
	}

	private static Gameservers readGameserver(ResultSet result) throws SQLException
	{
		return new Gameservers(result.getInt("id"), result.getString("registration_key"), result.getString("host"));
	}

	private static void bind(PreparedStatement statement, Gameservers gameserver) throws SQLException
	{
		statement.setInt(1, gameserver.getServerId());
		statement.setString(2, gameserver.getHexid());
		statement.setString(3, gameserver.getHost());
	}

	private static Gameservers requireGameserver(Object object)
	{
		if (!(object instanceof Gameservers))
		{
			throw new IllegalArgumentException("Expected a Gameservers instance");
		}
		return (Gameservers)object;
	}
}
