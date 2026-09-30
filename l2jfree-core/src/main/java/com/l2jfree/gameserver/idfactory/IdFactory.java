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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.util.TableOptimizer;
import com.l2jfree.gameserver.util.TableOptimizer.CharacterRelatedTable;
import com.l2jfree.gameserver.util.TableOptimizer.ItemRelatedTable;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.2.1.2.7 $ $Date: 2005/04/11 10:06:12 $
 */
public abstract class IdFactory
{
	private final static Logger _log = LoggerFactory.getLogger(IdFactory.class);
	
	protected static final String[] ID_UPDATES = {
			"UPDATE items                 SET owner_id = ?    WHERE owner_id = ?",
			"UPDATE items                 SET object_id = ?   WHERE object_id = ?",
			"UPDATE character_quests      SET charId = ?      WHERE charId = ?",
			"UPDATE character_blocks      SET charId = ?      WHERE charId = ?",
			"UPDATE character_friends     SET charId1 = ?     WHERE charId1 = ?",
			"UPDATE character_friends     SET charId2 = ?     WHERE charId2 = ?",
			"UPDATE character_hennas      SET charId = ?      WHERE charId = ?",
			"UPDATE character_recipebook  SET charId = ?      WHERE charId = ?",
			"UPDATE character_shortcuts   SET charId = ?      WHERE charId = ?",
			"UPDATE character_shortcuts   SET shortcut_id = ? WHERE shortcut_id = ? AND type = 1", // items
			"UPDATE character_macroses    SET charId = ?      WHERE charId = ?",
			"UPDATE character_skills      SET charId = ?      WHERE charId = ?",
			"UPDATE character_subclasses  SET charId = ?      WHERE charId = ?",
			"UPDATE characters            SET charId = ?      WHERE charId = ?",
			"UPDATE characters            SET clanid = ?      WHERE clanid = ?",
			"UPDATE clan_data             SET clan_id = ?     WHERE clan_id = ?",
			"UPDATE siege_clans           SET clan_id = ?     WHERE clan_id = ?",
			"UPDATE clan_data             SET ally_id = ?     WHERE ally_id = ?",
			"UPDATE clan_data             SET leader_id = ?   WHERE leader_id = ?",
			"UPDATE pets                  SET item_obj_id = ? WHERE item_obj_id = ?",
			// Added by DaDummy
			"UPDATE auction_bid          SET bidderId = ?      WHERE bidderId = ?",
			"UPDATE character_hennas     SET charId = ?        WHERE charId = ?",
			"UPDATE clan_wars            SET clan1 = ?         WHERE clan1 = ?",
			"UPDATE clan_wars            SET clan2 = ?         WHERE clan2 = ?",
			"UPDATE clanhall             SET ownerId = ?       WHERE ownerId = ?",
			"UPDATE petitions            SET charId = ?        WHERE charId = ?",
			"UPDATE posts                SET post_ownerid = ?  WHERE post_ownerid = ?",
			"UPDATE seven_signs          SET charId = ?        WHERE charId = ?",
			"UPDATE topic                SET topic_ownerid = ? WHERE topic_ownerid = ?",
			"UPDATE itemsonground        SET object_id = ?     WHERE object_id = ?",
			// Added by GDL
			"UPDATE olympiad_nobles          SET charId = ?         WHERE charId = ?",
			"UPDATE clan_privs               SET clan_id = ?        WHERE clan_id = ?",
			"UPDATE clan_skills              SET clan_id = ?        WHERE clan_id = ?",
			"UPDATE clan_subpledges          SET clan_id = ?        WHERE clan_id = ?",
			"UPDATE character_effects        SET charId = ?         WHERE charId = ?",
			"UPDATE character_recommends     SET charId = ?         WHERE charId = ?",
			"UPDATE character_recommends     SET target_id = ?      WHERE target_id = ?",
			"UPDATE character_raid_points     SET charId = ?       WHERE charId = ?",
			"UPDATE character_skill_reuses   SET charId = ?         WHERE charId = ?",
			"UPDATE couples                  SET id = ?             WHERE id = ?",
			"UPDATE couples                  SET player1Id = ?      WHERE player1Id = ?",
			"UPDATE couples                  SET player2Id = ?      WHERE player2Id = ?",
			"UPDATE cursed_weapons           SET playerId = ?       WHERE playerId = ?",
			"UPDATE forums                   SET forum_owner_id = ? WHERE forum_owner_id = ?",
			"UPDATE heroes                   SET charId = ?         WHERE charId = ?" };
	
	protected static final String[] ID_CHECKS = {
			"SELECT owner_id    FROM items                 WHERE object_id >= ?   AND object_id < ?",
			"SELECT object_id   FROM items                 WHERE object_id >= ?   AND object_id < ?",
			"SELECT charId      FROM character_quests      WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_blocks      WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_effects     WHERE charId >= ?      AND charId < ?",
			"SELECT charId1     FROM character_friends     WHERE charId1 >= ?     AND charId1 < ?",
			"SELECT charId2     FROM character_friends     WHERE charId2 >= ?     AND charId2 < ?",
			"SELECT charId      FROM character_hennas      WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_recipebook  WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_shortcuts   WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_macroses    WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_skill_reuses WHERE charId >= ?     AND charId < ?",
			"SELECT charId      FROM character_skills      WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM character_subclasses  WHERE charId >= ?      AND charId < ?",
			"SELECT charId      FROM characters            WHERE charId >= ?      AND charId < ?",
			"SELECT clanid      FROM characters            WHERE clanid >= ?      AND clanid < ?",
			"SELECT clan_id     FROM clan_data             WHERE clan_id >= ?     AND clan_id < ?",
			"SELECT clan_id     FROM siege_clans           WHERE clan_id >= ?     AND clan_id < ?",
			"SELECT ally_id     FROM clan_data             WHERE ally_id >= ?     AND ally_id < ?",
			"SELECT leader_id   FROM clan_data             WHERE leader_id >= ?   AND leader_id < ?",
			"SELECT item_obj_id FROM pets                  WHERE item_obj_id >= ? AND item_obj_id < ?",
			// Added by DaDummy
			"SELECT charId      FROM seven_signs           WHERE charId >= ?      AND charId < ?",
			"SELECT object_id   FROM itemsonground         WHERE object_id >= ?   AND object_id < ?" };
	
	private static final String[] TIMESTAMPS_CLEAN = { "DELETE FROM character_instance_time WHERE time <= ?",
			"DELETE FROM character_skill_reuses WHERE expiration <= ?" };
	
	protected boolean _initialized;
	
	public static final int FIRST_OID = 0x10000000;
	public static final int LAST_OID = 0x7FFFFFFF;
	public static final int FREE_OBJECT_ID_SIZE = LAST_OID - FIRST_OID;
	
	protected static IdFactory _instance = null;
	
	protected IdFactory()
	{
		setAllCharacterOffline();
		cleanUpDB();
		cleanUpTimeStamps();
	}
	
	static
	{
		switch (Config.IDFACTORY_TYPE)
		{
			case BitSet:
				_instance = new BitSetIDFactory();
				break;
			case Stack:
				_instance = new StackIDFactory();
				break;
			case Increment:
				_instance = new IncrementIDFactory();
				break;
			case Rebuild:
				_instance = new BitSetRebuildFactory();
				break;
		}
	}
	
	/**
	 * Sets all character offline
	 */
	protected void setAllCharacterOffline()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			Statement s2 = con.createStatement();
			s2.executeUpdate("UPDATE characters SET online = 0;");
			if (_log.isDebugEnabled())
				_log.debug("Updated characters online status.");
			s2.close();
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Cleans up Database
	 */
	protected void cleanUpDB()
	{
		// TODO:
		// Check for more cleanup query
		// Check order
		
		Connection con = null;
		try
		{
			int cleanCount = 0;
			con = L2DatabaseFactory.getInstance().getConnection(con);
			Statement stmt = con.createStatement();
			
			// If a character not exists
			for (CharacterRelatedTable table : TableOptimizer.getCharacterRelatedTables())
			{
				cleanCount += stmt.executeUpdate(table.getCleanQuery());
			}
			
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clan_data WHERE clan_data.leader_id NOT IN (SELECT charId FROM characters) AND clan_data.clan_id != 6619248;");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM items WHERE loc <> 'clanwh' and items.owner_id NOT IN (SELECT charId FROM characters);");
			
			// If a clan not exists
			cleanCount +=
					stmt.executeUpdate("DELETE FROM auction_bid WHERE auction_bid.bidderId NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clan_privs WHERE clan_privs.clan_id NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clan_skills WHERE clan_skills.clan_id NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clan_subpledges WHERE clan_subpledges.clan_id NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clan_wars WHERE clan_wars.clan1 NOT IN (SELECT clan_id FROM clan_data) OR clan_wars.clan2 NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM forums WHERE forum_owner_id <> 0 AND forums.forum_owner_id NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM items WHERE loc = 'clanwh' AND items.owner_id NOT IN (SELECT clan_id FROM clan_data);");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM siege_clans WHERE siege_clans.clan_id NOT IN (SELECT clan_id FROM clan_data);");
			
			stmt.executeUpdate("UPDATE characters SET `clanid`='0', `clan_privs`='0', `clan_join_expiry_time`='0', `clan_create_expiry_time`='0' WHERE characters.clanid NOT IN (SELECT clan_id FROM clan_data);");
			stmt.executeUpdate("UPDATE clan_subpledges SET leader_id=0 WHERE clan_subpledges.leader_id NOT IN (SELECT charId FROM characters) AND leader_id > 0;");
			stmt.executeUpdate("UPDATE clan_data SET ally_id=0 WHERE clan_data.ally_id NOT IN (SELECT clanid FROM characters WHERE clanid!=0 GROUP BY clanid);");
			stmt.executeUpdate("UPDATE clanhall SET ownerId=0, paidUntil=0, paid=0 WHERE clanhall.ownerId NOT IN (SELECT clan_id FROM clan_data);");
			
			// If the clanhall isn't free
			cleanCount +=
					stmt.executeUpdate("DELETE FROM auction WHERE auction.id IN (SELECT id FROM clanhall WHERE ownerId <> 0) AND auction.sellerId = 0;");
			cleanCount +=
					stmt.executeUpdate("DELETE FROM auction_bid WHERE auction_bid.auctionId IN (SELECT id FROM clanhall WHERE ownerId <> 0);");
			stmt.executeUpdate("UPDATE clan_data SET auction_bid_at = 0 WHERE auction_bid_at NOT IN (SELECT auctionId FROM auction_bid);");
			// If the clanhall is free
			cleanCount +=
					stmt.executeUpdate("DELETE FROM clanhall_functions WHERE clanhall_functions.hall_id NOT IN (SELECT id FROM clanhall WHERE ownerId <> 0);");
			
			// If an item not exists
			for (ItemRelatedTable table : TableOptimizer.getItemRelatedTables())
			{
				cleanCount += stmt.executeUpdate(table.getCleanQuery());
			}
			
			stmt.close();
			_log.info("Cleaned " + cleanCount + " elements from database.");
		}
		catch (SQLException e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void cleanUpTimeStamps()
	{
		Connection con = null;
		try
		{
			int cleanCount = 0;
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stmt;
			for (String line : TIMESTAMPS_CLEAN)
			{
				stmt = con.prepareStatement(line);
				stmt.setLong(1, System.currentTimeMillis());
				cleanCount += stmt.executeUpdate();
				stmt.close();
			}
			
			_log.info("Cleaned " + cleanCount + " expired timestamps from database.");
		}
		catch (SQLException e)
		{
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected final int[] extractUsedObjectIDTable() throws SQLException
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			return PersistedObjectIds.read(con);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public boolean isInitialized()
	{
		return _initialized;
	}
	
	public static IdFactory getInstance()
	{
		return _instance;
	}
	
	public abstract int getNextId();
	
	/**
	 * return a used Object ID back to the pool
	 * @param id ID
	 */
	public abstract void releaseId(int id);
	
	public abstract int getCurrentId();
	
	public abstract int size();
}
