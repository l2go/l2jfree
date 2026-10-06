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
import java.util.Arrays;

import gnu.trove.TIntArrayList;

final class PersistedObjectIds
{
	/** The key columns of the entities and the crest ids, which share the id range (docs/DATABASE-CONVENTIONS.md, section 3). */
	private static final String[] QUERIES = {
		"SELECT id FROM player",
		"SELECT id FROM item",
		"SELECT id FROM clan",
		"SELECT item_id FROM pet",
		"SELECT id FROM ground_item",
		"SELECT id FROM couple",
		"SELECT crest_id FROM clan",
		"SELECT large_crest_id FROM clan",
		"SELECT alliance_crest_id FROM clan",
		"SELECT id FROM crest"
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
		
		// An id can be stored twice: a pet has the id of its control item, and the member clans of an alliance share a crest
		int[] sorted = ids.toNativeArray();
		int distinct = 0;
		for (int i = 0; i < sorted.length; i++)
		{
			if (i == 0 || sorted[i] != sorted[i - 1])
				sorted[distinct++] = sorted[i];
		}
		return Arrays.copyOf(sorted, distinct);
	}
}
