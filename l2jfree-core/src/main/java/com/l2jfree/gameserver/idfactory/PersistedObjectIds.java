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
package com.l2jfree.gameserver.idfactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import gnu.trove.TIntArrayList;

final class PersistedObjectIds
{
	private static final String[] QUERIES = {
		"SELECT charId FROM characters",
		"SELECT object_id FROM items",
		"SELECT clan_id FROM clan_data",
		"SELECT crest_id FROM clan_data",
		"SELECT crest_large_id FROM clan_data",
		"SELECT ally_crest_id FROM clan_data",
		"SELECT id FROM couples",
		"SELECT object_id FROM itemsonground"
	};

	private PersistedObjectIds()
	{
	}

	static int[] read(Connection con) throws SQLException
	{
		TIntArrayList ids = new TIntArrayList();
		try (Statement statement = con.createStatement())
		{
			for (String query : QUERIES)
			{
				try (ResultSet rows = statement.executeQuery(query))
				{
					while (rows.next())
					{
						int id = rows.getInt(1);
						if (id > 0)
							ids.add(id);
					}
				}
			}
		}
		ids.sort();
		return ids.toNativeArray();
	}
}
