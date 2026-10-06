/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.AccountsDAO;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;

/**
 * JDBC persistence for the <code>login.account</code> table.
 * <p>
 * The bean keeps the shape the login code has always used. The table stores the same facts in
 * PostgreSQL types, so this class converts at the boundary:
 * <ul>
 * <li>the last activity is epoch milliseconds in the bean and <code>timestamptz</code> in the table;</li>
 * <li>the last address is text in the bean and <code>inet</code> in the table, and an empty text is stored as NULL;</li>
 * <li>the last world is 0 in the bean when there is none, and NULL in the table;</li>
 * <li>the birth year, month, and day are three numbers in the bean and one <code>date</code> in the table, and an
 * impossible date is stored as 1900-01-01.</li>
 * </ul>
 * The connection sends text parameters untyped (<code>stringtype=unspecified</code>), so a login name is compared
 * as <code>citext</code>, without regard to case.
 */
public final class AccountsDAOJdbc implements AccountsDAO
{
	private static final LocalDate DEFAULT_BIRTHDAY = LocalDate.of(1900, 1, 1);
	
	private static final String SELECT = "SELECT name, password_hash, access_level, last_active_at, "
			+ "host(last_ip) AS last_ip, last_world_id, birthday_on FROM account";
	
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
			try (PreparedStatement statement = connection.prepareStatement(SELECT);
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
			Map<String, Object> columns = columnsOf(account);
			String sql = "INSERT INTO account (" + String.join(", ", columns.keySet()) + ") VALUES ("
					+ placeholders(columns.size()) + ")";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				bind(statement, columns);
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
			String sql = "UPDATE account SET password_hash=?, access_level=?, last_active_at=?, last_ip=?, "
					+ "last_world_id=?, birthday_on=? WHERE name=?";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				int index = 1;
				statement.setString(index++, account.getPassword());
				statement.setInt(index++, account.getAccessLevel() == null ? 0 : account.getAccessLevel().intValue());
				statement.setTimestamp(index++, toTimestamp(account.getLastactive()));
				statement.setString(index++, emptyToNull(account.getLastIp()));
				statement.setObject(index++, zeroToNull(account.getLastServerId()));
				statement.setObject(index++, toBirthday(account));
				statement.setString(index, account.getLogin());
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
			try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE name=?"))
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
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM account WHERE name=?"))
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
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM account WHERE name=?"))
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
			try (PreparedStatement statement = connection.prepareStatement("UPDATE account SET access_level=? WHERE name=?"))
			{
				statement.setInt(1, accessLevel);
				statement.setString(2, login);
				return statement.executeUpdate() > 0;
			}
		});
	}
	
	private static void upsert(Connection connection, Accounts account) throws SQLException
	{
		Map<String, Object> columns = columnsOf(account);
		StringBuilder sql = new StringBuilder("INSERT INTO account (");
		sql.append(String.join(", ", columns.keySet())).append(") VALUES (").append(placeholders(columns.size()))
				.append(") ON CONFLICT (name) DO UPDATE SET ");
		List<String> updates = new ArrayList<String>();
		for (String column : columns.keySet())
		{
			if (!column.equals("name"))
			{
				updates.add(column + " = EXCLUDED." + column);
			}
		}
		if (updates.isEmpty())
		{
			updates.add("name = EXCLUDED.name");
		}
		sql.append(String.join(", ", updates));
		try (PreparedStatement statement = connection.prepareStatement(sql.toString()))
		{
			bind(statement, columns);
			statement.executeUpdate();
		}
	}
	
	/** The columns to write, in order. A column whose value the bean does not carry is left to its default. */
	private static Map<String, Object> columnsOf(Accounts account)
	{
		Map<String, Object> columns = new LinkedHashMap<String, Object>();
		columns.put("name", account.getLogin());
		put(columns, "password_hash", account.getPassword());
		put(columns, "access_level", account.getAccessLevel());
		put(columns, "last_active_at", toTimestamp(account.getLastactive()));
		put(columns, "last_ip", emptyToNull(account.getLastIp()));
		put(columns, "last_world_id", zeroToNull(account.getLastServerId()));
		if (account.getBirthYear() != null && account.getBirthMonth() != null && account.getBirthDay() != null)
		{
			columns.put("birthday_on", toBirthday(account));
		}
		return columns;
	}
	
	private static void put(Map<String, Object> columns, String name, Object value)
	{
		if (value != null)
		{
			columns.put(name, value);
		}
	}
	
	private static void bind(PreparedStatement statement, Map<String, Object> columns) throws SQLException
	{
		int index = 1;
		for (Object value : columns.values())
		{
			if (value instanceof String)
			{
				statement.setString(index++, (String)value);
			}
			else if (value instanceof Timestamp)
			{
				statement.setTimestamp(index++, (Timestamp)value);
			}
			else
			{
				statement.setObject(index++, value);
			}
		}
	}
	
	private static Accounts readAccount(ResultSet result) throws SQLException
	{
		Accounts account = new Accounts();
		account.setLogin(result.getString("name"));
		account.setPassword(result.getString("password_hash"));
		account.setAccessLevel(Integer.valueOf(result.getInt("access_level")));
		Timestamp lastActive = result.getTimestamp("last_active_at");
		account.setLastactive(lastActive == null ? null : BigDecimal.valueOf(lastActive.getTime()));
		account.setLastIp(result.getString("last_ip"));
		account.setLastServerId(Integer.valueOf(result.getInt("last_world_id")));
		LocalDate birthday = result.getObject("birthday_on", LocalDate.class);
		if (birthday == null)
		{
			birthday = DEFAULT_BIRTHDAY;
		}
		account.setBirthYear(Integer.valueOf(birthday.getYear()));
		account.setBirthMonth(Integer.valueOf(birthday.getMonthValue()));
		account.setBirthDay(Integer.valueOf(birthday.getDayOfMonth()));
		return account;
	}
	
	private static Timestamp toTimestamp(BigDecimal epochMillis)
	{
		return epochMillis == null ? null : new Timestamp(epochMillis.longValue());
	}
	
	private static String emptyToNull(String value)
	{
		return value == null || value.isEmpty() ? null : value;
	}
	
	private static Integer zeroToNull(Integer value)
	{
		return value == null || value.intValue() == 0 ? null : value;
	}
	
	/** The birth date from the three numbers; an impossible or missing date becomes 1900-01-01. */
	private static LocalDate toBirthday(Accounts account)
	{
		if (account.getBirthYear() == null || account.getBirthMonth() == null || account.getBirthDay() == null)
		{
			return DEFAULT_BIRTHDAY;
		}
		try
		{
			return LocalDate.of(account.getBirthYear().intValue(), account.getBirthMonth().intValue(),
					account.getBirthDay().intValue());
		}
		catch (DateTimeException e)
		{
			return DEFAULT_BIRTHDAY;
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
}
