/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Writes every durable part of one multisell exchange on one JDBC connection. */
final class MultiSellPersistence
{
	interface Change
	{
		void write(Connection connection) throws SQLException;
	}

	private final List<Change> _changes = new ArrayList<Change>();

	void debit(final int objectId, final int ownerId, final long before, final long after)
	{
		if (before <= 0 || after < 0 || after >= before)
			throw new IllegalArgumentException("Invalid item debit");

		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				if (after == 0)
				{
					deleteByItemId(connection, "item_attributes", "itemId", objectId);
					deleteByItemId(connection, "pets", "item_obj_id", objectId);
				}
				String sql = after == 0
						? "DELETE FROM items WHERE object_id=? AND owner_id=? AND count=?"
						: "UPDATE items SET count=? WHERE object_id=? AND owner_id=? AND count=?";
				try (PreparedStatement statement = connection.prepareStatement(sql))
				{
					int offset = 0;
					if (after > 0)
						statement.setLong(++offset, after);
					statement.setInt(++offset, objectId);
					statement.setInt(++offset, ownerId);
					statement.setLong(++offset, before);
					if (statement.executeUpdate() != 1)
						throw new SQLException("Stale multisell ingredient: " + objectId);
				}
			}
		});
	}

	void insert(final int objectId, final int ownerId, final int itemId, final long count,
			final int enchantment)
	{
		if (count <= 0)
			throw new IllegalArgumentException("Invalid product count");
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO items (owner_id,object_id,item_id,count,enchant_level,loc,loc_data) "
								+ "VALUES (?,?,?,?,?,'INVENTORY',0)"))
				{
					statement.setInt(1, ownerId);
					statement.setInt(2, objectId);
					statement.setInt(3, itemId);
					statement.setLong(4, count);
					statement.setInt(5, enchantment);
					statement.executeUpdate();
				}
			}
		});
	}

	void adjustFame(final int playerId, final int before, final int after)
	{
		adjustBalance("characters", "fame", "charId", playerId, before, after);
	}

	void adjustClanReputation(final int clanId, final int before, final int after)
	{
		adjustBalance("clan_data", "reputation_score", "clan_id", clanId, before, after);
	}

	void adjustCastleTreasury(final int castleId, final long before, final long after)
	{
		adjustBalance("castle", "treasury", "id", castleId, before, after);
	}

	private void adjustBalance(final String table, final String column, final String keyColumn,
			final int key, final long before, final long after)
	{
		if (before == after)
			return;
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET " + column
						+ "=? WHERE " + keyColumn + "=? AND " + column + "=?"))
				{
					statement.setLong(1, after);
					statement.setInt(2, key);
					statement.setLong(3, before);
					if (statement.executeUpdate() != 1)
						throw new SQLException("Stale multisell balance in " + table + ": " + key);
				}
			}
		});
	}

	void commit(Connection connection) throws SQLException
	{
		boolean originalAutoCommit = connection.getAutoCommit();
		if (!originalAutoCommit)
			throw new IllegalArgumentException("A dedicated auto-commit connection is required");
		connection.setAutoCommit(false);
		try
		{
			for (Change change : _changes)
				change.write(connection);
			connection.commit();
		}
		catch (SQLException | RuntimeException failure)
		{
			try
			{
				connection.rollback();
			}
			catch (SQLException rollbackFailure)
			{
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
		finally
		{
			connection.setAutoCommit(true);
		}
	}

	private static void deleteByItemId(Connection connection, String table, String column, int objectId)
			throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM " + table + " WHERE " + column + "=?"))
		{
			statement.setInt(1, objectId);
			statement.executeUpdate();
		}
	}
}
