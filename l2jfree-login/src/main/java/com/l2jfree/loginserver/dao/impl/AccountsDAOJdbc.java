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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.AccountsDAO;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;

/** JDBC persistence for the login server's accounts table. */
public final class AccountsDAOJdbc implements AccountsDAO
{
	private static final String SELECT_COLUMNS = "login, password, lastactive, accessLevel, lastServerId, "
			+ "birthYear, birthMonth, birthDay, lastIP";

	private final JdbcTransactions transactions;

	public AccountsDAOJdbc(JdbcTransactions transactions)
	{
		this.transactions = transactions;
	}

	@Override
	public List<Accounts> getAllAccounts()
	{
		return transactions.withConnection(connection -> {
			List<Accounts> accounts = new ArrayList<Accounts>();
			try (PreparedStatement statement = connection.prepareStatement("SELECT " + SELECT_COLUMNS + " FROM accounts");
					ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					accounts.add(readAccount(result));
				}
			}
			return accounts;
		});
	}

	@Override
	public String createAccount(Object object)
	{
		Accounts account = requireAccount(object);
		return transactions.withConnection(connection -> {
			List<String> columns = new ArrayList<String>();
			columns.add("login");
			addColumn(columns, "password", account.getPassword());
			addColumn(columns, "lastactive", account.getLastactive());
			addColumn(columns, "accessLevel", account.getAccessLevel());
			addColumn(columns, "lastServerId", account.getLastServerId());
			addColumn(columns, "birthYear", account.getBirthYear());
			addColumn(columns, "birthMonth", account.getBirthMonth());
			addColumn(columns, "birthDay", account.getBirthDay());
			addColumn(columns, "lastIP", account.getLastIp());
			String sql = "INSERT INTO accounts (" + String.join(", ", columns) + ") VALUES ("
					+ placeholders(columns.size()) + ")";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				bindAccountColumns(statement, account, columns);
				statement.executeUpdate();
				return account.getLogin();
			}
		});
	}

	@Override
	public void createOrUpdate(Object object)
	{
		Accounts account = requireAccount(object);
		transactions.withConnection(connection -> {
			upsert(connection, account);
			return null;
		});
	}

	@Override
	public void createOrUpdateAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			for (Object entity : entities)
			{
				upsert(connection, requireAccount(entity));
			}
			return null;
		});
	}

	@Override
	public void update(Object object)
	{
		Accounts account = requireAccount(object);
		transactions.withConnection(connection -> {
			String sql = "UPDATE accounts SET password=?, lastactive=?, accessLevel=?, lastServerId=?, birthYear=?, "
					+ "birthMonth=?, birthDay=?, lastIP=? WHERE login=?";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				statement.setString(1, account.getPassword());
				setBigDecimal(statement, 2, account.getLastactive());
				setInteger(statement, 3, account.getAccessLevel());
				setInteger(statement, 4, account.getLastServerId());
				setInteger(statement, 5, account.getBirthYear());
				setInteger(statement, 6, account.getBirthMonth());
				setInteger(statement, 7, account.getBirthDay());
				statement.setString(8, account.getLastIp());
				statement.setString(9, account.getLogin());
				if (statement.executeUpdate() == 0)
				{
					throw new LoginObjectNotFoundException("Account", account.getLogin());
				}
			}
			return null;
		});
	}

	@Override
	public void removeAccount(Object object)
	{
		removeAccountById(requireAccount(object).getLogin());
	}

	@Override
	public Accounts getAccountById(String id)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("SELECT " + SELECT_COLUMNS
					+ " FROM accounts WHERE login=?"))
			{
				statement.setString(1, id);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new LoginObjectNotFoundException("Account", id);
					}
					return readAccount(result);
				}
			}
		});
	}

	@Override
	public void removeAccountById(String login)
	{
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				statement.setString(1, login);
				if (statement.executeUpdate() == 0)
				{
					throw new LoginObjectNotFoundException("Account", login);
				}
			}
			return null;
		});
	}

	@Override
	public void removeAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				for (Object entity : entities)
				{
					statement.setString(1, requireAccount(entity).getLogin());
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		});
	}

	@Override
	public boolean updateAccessLevel(String login, int accessLevel)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("UPDATE accounts SET accessLevel=? WHERE login=?"))
			{
				statement.setInt(1, accessLevel);
				statement.setString(2, login);
				if (statement.executeUpdate() > 0)
				{
					return true;
				}
			}
			// MySQL reports zero changed rows if the account already had this level.
			try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM accounts WHERE login=?"))
			{
				statement.setString(1, login);
				try (ResultSet result = statement.executeQuery())
				{
					return result.next();
				}
			}
		});
	}

	private void upsert(java.sql.Connection connection, Accounts account) throws SQLException
	{
		List<String> columns = new ArrayList<String>();
		columns.add("login");
		addColumn(columns, "password", account.getPassword());
		addColumn(columns, "lastactive", account.getLastactive());
		addColumn(columns, "accessLevel", account.getAccessLevel());
		addColumn(columns, "lastServerId", account.getLastServerId());
		addColumn(columns, "birthYear", account.getBirthYear());
		addColumn(columns, "birthMonth", account.getBirthMonth());
		addColumn(columns, "birthDay", account.getBirthDay());
		addColumn(columns, "lastIP", account.getLastIp());
		StringBuilder sql = new StringBuilder("INSERT INTO accounts (");
		sql.append(String.join(", ", columns)).append(") VALUES (").append(placeholders(columns.size()))
				.append(") ON DUPLICATE KEY UPDATE ");
		List<String> updates = new ArrayList<String>();
		for (int i = 1; i < columns.size(); i++)
		{
			String column = columns.get(i);
			updates.add(column + " = VALUES(" + column + ")");
		}
		if (updates.isEmpty())
		{
			updates.add("login = VALUES(login)");
		}
		sql.append(String.join(", ", updates));
		try (PreparedStatement statement = connection.prepareStatement(sql.toString()))
		{
			bindAccountColumns(statement, account, columns);
			statement.executeUpdate();
		}
	}

	private static Accounts readAccount(ResultSet result) throws SQLException
	{
		Accounts account = new Accounts();
		account.setLogin(result.getString("login"));
		account.setPassword(result.getString("password"));
		account.setLastactive(result.getBigDecimal("lastactive"));
		account.setAccessLevel(getInteger(result, "accessLevel"));
		account.setLastServerId(getInteger(result, "lastServerId"));
		account.setBirthYear(getInteger(result, "birthYear"));
		account.setBirthMonth(getInteger(result, "birthMonth"));
		account.setBirthDay(getInteger(result, "birthDay"));
		account.setLastIp(result.getString("lastIP"));
		return account;
	}

	private static Integer getInteger(ResultSet result, String column) throws SQLException
	{
		int value = result.getInt(column);
		return result.wasNull() ? null : Integer.valueOf(value);
	}

	private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.INTEGER);
		}
		else
		{
			statement.setInt(index, value.intValue());
		}
	}

	private static void setBigDecimal(PreparedStatement statement, int index, java.math.BigDecimal value)
			throws SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.DECIMAL);
		}
		else
		{
			statement.setBigDecimal(index, value);
		}
	}

	private static Accounts requireAccount(Object object)
	{
		if (!(object instanceof Accounts))
		{
			throw new IllegalArgumentException("Expected an Accounts instance");
		}
		Accounts account = (Accounts)object;
		if (account.getLogin() == null)
		{
			throw new IllegalArgumentException("Account login must not be null");
		}
		return account;
	}

	private static void addColumn(List<String> columns, String name, Object value)
	{
		if (value != null)
		{
			columns.add(name);
		}
	}

	private static String placeholders(int count)
	{
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < count; i++)
		{
			if (i > 0)
			{
				placeholders.append(", ");
			}
			placeholders.append('?');
		}
		return placeholders.toString();
	}

	private static void bindAccountColumns(PreparedStatement statement, Accounts account, List<String> columns)
			throws SQLException
	{
		int index = 1;
		for (String column : columns)
		{
			switch (column)
			{
				case "login":
					statement.setString(index++, account.getLogin());
					break;
				case "password":
					statement.setString(index++, account.getPassword());
					break;
				case "lastactive":
					statement.setBigDecimal(index++, account.getLastactive());
					break;
				case "accessLevel":
					statement.setInt(index++, account.getAccessLevel().intValue());
					break;
				case "lastServerId":
					statement.setInt(index++, account.getLastServerId().intValue());
					break;
				case "birthYear":
					statement.setInt(index++, account.getBirthYear().intValue());
					break;
				case "birthMonth":
					statement.setInt(index++, account.getBirthMonth().intValue());
					break;
				case "birthDay":
					statement.setInt(index++, account.getBirthDay().intValue());
					break;
				case "lastIP":
					statement.setString(index++, account.getLastIp());
					break;
				default:
					throw new IllegalArgumentException("Unknown accounts column " + column);
			}
		}
	}
}
