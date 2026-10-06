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
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.2.1.2.7 $ $Date: 2005/04/11 10:06:12 $
 */
public abstract class IdFactory
{
	private final static Logger _log = LoggerFactory.getLogger(IdFactory.class);
	
	/**
	 * The statements that move one object id to another, for the compaction of the ids: the key column of every entity
	 * and every column that refers to it. The foreign keys of the schema are not deferrable, so the compaction needs
	 * them to be (see {@link BitSetRebuildFactory}).
	 */
	protected static final String[] ID_UPDATES = {
			// Players
			"UPDATE player SET id = ? WHERE id = ?",
			"UPDATE player SET apprentice_player_id = ? WHERE apprentice_player_id = ?",
			"UPDATE player SET sponsor_player_id = ? WHERE sponsor_player_id = ?",
			"UPDATE player_subclass SET player_id = ? WHERE player_id = ?",
			"UPDATE player_subclass_certification SET player_id = ? WHERE player_id = ?",
			"UPDATE player_skill SET player_id = ? WHERE player_id = ?",
			"UPDATE player_skill_reuse SET player_id = ? WHERE player_id = ?",
			"UPDATE player_effect SET player_id = ? WHERE player_id = ?",
			"UPDATE player_henna SET player_id = ? WHERE player_id = ?",
			"UPDATE player_shortcut SET player_id = ? WHERE player_id = ?",
			"UPDATE player_macro SET player_id = ? WHERE player_id = ?",
			"UPDATE player_teleport_bookmark SET player_id = ? WHERE player_id = ?",
			"UPDATE player_recipe SET player_id = ? WHERE player_id = ?",
			"UPDATE player_quest_variable SET player_id = ? WHERE player_id = ?",
			"UPDATE player_quest_global_variable SET player_id = ? WHERE player_id = ?",
			"UPDATE player_instance_reentry SET player_id = ? WHERE player_id = ?",
			"UPDATE player_raid_score SET player_id = ? WHERE player_id = ?",
			"UPDATE player_birthday SET player_id = ? WHERE player_id = ?",
			"UPDATE player_name_title_color SET player_id = ? WHERE player_id = ?",
			"UPDATE player_recommendation_status SET player_id = ? WHERE player_id = ?",
			"UPDATE player_recommendation SET player_id = ? WHERE player_id = ?",
			"UPDATE player_recommendation SET recommended_player_id = ? WHERE recommended_player_id = ?",
			"UPDATE player_friendship SET player_id = ? WHERE player_id = ?",
			"UPDATE player_friendship SET friend_player_id = ? WHERE friend_player_id = ?",
			"UPDATE player_block SET player_id = ? WHERE player_id = ?",
			"UPDATE player_block SET blocked_player_id = ? WHERE blocked_player_id = ?",
			"UPDATE item SET owner_player_id = ? WHERE owner_player_id = ?",
			"UPDATE cursed_weapon SET player_id = ? WHERE player_id = ?",
			"UPDATE player_mail SET player_id = ? WHERE player_id = ?",
			"UPDATE player_mail SET sender_player_id = ? WHERE sender_player_id = ?",
			"UPDATE offline_store SET player_id = ? WHERE player_id = ?",
			"UPDATE offline_store_item SET player_id = ? WHERE player_id = ?",
			"UPDATE seven_signs_player SET player_id = ? WHERE player_id = ?",
			"UPDATE olympiad_noble SET player_id = ? WHERE player_id = ?",
			"UPDATE olympiad_noble_month_end SET player_id = ? WHERE player_id = ?",
			"UPDATE hero SET player_id = ? WHERE player_id = ?",
			"UPDATE couple SET player1_id = ? WHERE player1_id = ?",
			"UPDATE couple SET player2_id = ? WHERE player2_id = ?",
			"UPDATE forum SET owner_player_id = ? WHERE owner_player_id = ?",
			"UPDATE forum_topic SET author_player_id = ? WHERE author_player_id = ?",
			"UPDATE forum_post SET author_player_id = ? WHERE author_player_id = ?",
			"UPDATE player_restriction SET player_id = ? WHERE player_id = ?",
			"UPDATE leaderboard_entry SET player_id = ? WHERE player_id = ?",
			"UPDATE clan SET leader_player_id = ? WHERE leader_player_id = ?",
			"UPDATE clan_subpledge SET leader_player_id = ? WHERE leader_player_id = ?",
			"UPDATE clan_hall_auction SET seller_player_id = ? WHERE seller_player_id = ?",
			// Clans
			"UPDATE clan SET id = ? WHERE id = ?",
			"UPDATE player SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan SET alliance_id = ? WHERE alliance_id = ?",
			"UPDATE clan_notice SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan_rank_privilege SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan_skill SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan_subpledge SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan_war SET declaring_clan_id = ? WHERE declaring_clan_id = ?",
			"UPDATE clan_war SET target_clan_id = ? WHERE target_clan_id = ?",
			"UPDATE item SET owner_clan_id = ? WHERE owner_clan_id = ?",
			"UPDATE castle_siege_clan SET clan_id = ? WHERE clan_id = ?",
			"UPDATE fort_siege_clan SET clan_id = ? WHERE clan_id = ?",
			"UPDATE clan_hall_siege_clan SET clan_id = ? WHERE clan_id = ?",
			"UPDATE fort SET owner_clan_id = ? WHERE owner_clan_id = ?",
			"UPDATE clan_hall SET owner_clan_id = ? WHERE owner_clan_id = ?",
			"UPDATE clan_hall_auction_bid SET clan_id = ? WHERE clan_id = ?",
			"UPDATE forum SET owner_clan_id = ? WHERE owner_clan_id = ?",
			// Items and pets (a shortcut can point to an item)
			"UPDATE item SET id = ? WHERE id = ?",
			"UPDATE item SET owner_pet_id = ? WHERE owner_pet_id = ?",
			"UPDATE pet SET item_id = ? WHERE item_id = ?",
			"UPDATE item_attribute SET item_id = ? WHERE item_id = ?",
			"UPDATE offline_store_item SET item_id = ? WHERE item_id = ?",
			"UPDATE player_shortcut SET target_id = ? WHERE target_id = ? AND shortcut_type_id = 1",
			// Couples, items on the ground, crests
			"UPDATE couple SET id = ? WHERE id = ?",
			"UPDATE ground_item SET id = ? WHERE id = ?",
			"UPDATE clan SET crest_id = ? WHERE crest_id = ?",
			"UPDATE clan SET large_crest_id = ? WHERE large_crest_id = ?",
			"UPDATE clan SET alliance_crest_id = ? WHERE alliance_crest_id = ?",
			"UPDATE crest SET id = ? WHERE id = ?" };
	
	/** The statements that look for a column that holds an id of a given range: the same columns as the updates. */
	protected static final String[] ID_CHECKS = {
			// Players
			"SELECT id FROM player WHERE id >= ? AND id < ?",
			"SELECT apprentice_player_id FROM player WHERE apprentice_player_id >= ? AND apprentice_player_id < ?",
			"SELECT sponsor_player_id FROM player WHERE sponsor_player_id >= ? AND sponsor_player_id < ?",
			"SELECT player_id FROM player_subclass WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_subclass_certification WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_skill WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_skill_reuse WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_effect WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_henna WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_shortcut WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_macro WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_teleport_bookmark WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_recipe WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_quest_variable WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_quest_global_variable WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_instance_reentry WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_raid_score WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_birthday WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_name_title_color WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_recommendation_status WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_recommendation WHERE player_id >= ? AND player_id < ?",
			"SELECT recommended_player_id FROM player_recommendation WHERE recommended_player_id >= ? AND recommended_player_id < ?",
			"SELECT player_id FROM player_friendship WHERE player_id >= ? AND player_id < ?",
			"SELECT friend_player_id FROM player_friendship WHERE friend_player_id >= ? AND friend_player_id < ?",
			"SELECT player_id FROM player_block WHERE player_id >= ? AND player_id < ?",
			"SELECT blocked_player_id FROM player_block WHERE blocked_player_id >= ? AND blocked_player_id < ?",
			"SELECT owner_player_id FROM item WHERE owner_player_id >= ? AND owner_player_id < ?",
			"SELECT player_id FROM cursed_weapon WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM player_mail WHERE player_id >= ? AND player_id < ?",
			"SELECT sender_player_id FROM player_mail WHERE sender_player_id >= ? AND sender_player_id < ?",
			"SELECT player_id FROM offline_store WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM offline_store_item WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM seven_signs_player WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM olympiad_noble WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM olympiad_noble_month_end WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM hero WHERE player_id >= ? AND player_id < ?",
			"SELECT player1_id FROM couple WHERE player1_id >= ? AND player1_id < ?",
			"SELECT player2_id FROM couple WHERE player2_id >= ? AND player2_id < ?",
			"SELECT owner_player_id FROM forum WHERE owner_player_id >= ? AND owner_player_id < ?",
			"SELECT author_player_id FROM forum_topic WHERE author_player_id >= ? AND author_player_id < ?",
			"SELECT author_player_id FROM forum_post WHERE author_player_id >= ? AND author_player_id < ?",
			"SELECT player_id FROM player_restriction WHERE player_id >= ? AND player_id < ?",
			"SELECT player_id FROM leaderboard_entry WHERE player_id >= ? AND player_id < ?",
			"SELECT leader_player_id FROM clan WHERE leader_player_id >= ? AND leader_player_id < ?",
			"SELECT leader_player_id FROM clan_subpledge WHERE leader_player_id >= ? AND leader_player_id < ?",
			"SELECT seller_player_id FROM clan_hall_auction WHERE seller_player_id >= ? AND seller_player_id < ?",
			// Clans
			"SELECT id FROM clan WHERE id >= ? AND id < ?",
			"SELECT clan_id FROM player WHERE clan_id >= ? AND clan_id < ?",
			"SELECT alliance_id FROM clan WHERE alliance_id >= ? AND alliance_id < ?",
			"SELECT clan_id FROM clan_notice WHERE clan_id >= ? AND clan_id < ?",
			"SELECT clan_id FROM clan_rank_privilege WHERE clan_id >= ? AND clan_id < ?",
			"SELECT clan_id FROM clan_skill WHERE clan_id >= ? AND clan_id < ?",
			"SELECT clan_id FROM clan_subpledge WHERE clan_id >= ? AND clan_id < ?",
			"SELECT declaring_clan_id FROM clan_war WHERE declaring_clan_id >= ? AND declaring_clan_id < ?",
			"SELECT target_clan_id FROM clan_war WHERE target_clan_id >= ? AND target_clan_id < ?",
			"SELECT owner_clan_id FROM item WHERE owner_clan_id >= ? AND owner_clan_id < ?",
			"SELECT clan_id FROM castle_siege_clan WHERE clan_id >= ? AND clan_id < ?",
			"SELECT clan_id FROM fort_siege_clan WHERE clan_id >= ? AND clan_id < ?",
			"SELECT clan_id FROM clan_hall_siege_clan WHERE clan_id >= ? AND clan_id < ?",
			"SELECT owner_clan_id FROM fort WHERE owner_clan_id >= ? AND owner_clan_id < ?",
			"SELECT owner_clan_id FROM clan_hall WHERE owner_clan_id >= ? AND owner_clan_id < ?",
			"SELECT clan_id FROM clan_hall_auction_bid WHERE clan_id >= ? AND clan_id < ?",
			"SELECT owner_clan_id FROM forum WHERE owner_clan_id >= ? AND owner_clan_id < ?",
			// Items and pets (a shortcut can point to an item)
			"SELECT id FROM item WHERE id >= ? AND id < ?",
			"SELECT owner_pet_id FROM item WHERE owner_pet_id >= ? AND owner_pet_id < ?",
			"SELECT item_id FROM pet WHERE item_id >= ? AND item_id < ?",
			"SELECT item_id FROM item_attribute WHERE item_id >= ? AND item_id < ?",
			"SELECT item_id FROM offline_store_item WHERE item_id >= ? AND item_id < ?",
			"SELECT target_id FROM player_shortcut WHERE target_id >= ? AND target_id < ? AND shortcut_type_id = 1",
			// Couples, items on the ground, crests
			"SELECT id FROM couple WHERE id >= ? AND id < ?",
			"SELECT id FROM ground_item WHERE id >= ? AND id < ?",
			"SELECT crest_id FROM clan WHERE crest_id >= ? AND crest_id < ?",
			"SELECT large_crest_id FROM clan WHERE large_crest_id >= ? AND large_crest_id < ?",
			"SELECT alliance_crest_id FROM clan WHERE alliance_crest_id >= ? AND alliance_crest_id < ?",
			"SELECT id FROM crest WHERE id >= ? AND id < ?" };
	
	private static final String[] TIMESTAMPS_CLEAN = { "DELETE FROM player_instance_reentry WHERE reenter_at <= ?",
			"DELETE FROM player_skill_reuse WHERE expires_at <= ?" };
	
	protected boolean _initialized;
	
	public static final int FIRST_OID = 0x10000000;
	public static final int LAST_OID = 0x7FFFFFFF;
	public static final int FREE_OBJECT_ID_SIZE = LAST_OID - FIRST_OID;
	
	protected static IdFactory _instance = null;
	
	protected IdFactory()
	{
		setAllCharacterOffline();
		cleanUpTimeStamps();
	}
	
	private static IdFactory createInstance()
	{
		switch (Config.IDFACTORY_TYPE)
		{
			case BitSet:
				return new BitSetIDFactory();
			case Stack:
				return new StackIDFactory();
			case Increment:
				return new IncrementIDFactory();
			case Rebuild:
				return new BitSetRebuildFactory();
			default:
				throw new IllegalStateException("Unsupported id factory type: " + Config.IDFACTORY_TYPE);
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
			s2.executeUpdate("UPDATE player SET is_online = false WHERE is_online");
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
				stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
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
		if (_instance == null)
		{
			synchronized (IdFactory.class)
			{
				if (_instance == null)
					_instance = createInstance();
			}
		}
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
