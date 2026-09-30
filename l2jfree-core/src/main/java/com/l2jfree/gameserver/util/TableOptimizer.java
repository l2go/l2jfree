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
package com.l2jfree.gameserver.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;

public final class TableOptimizer
{
	private static final Logger _log = LoggerFactory.getLogger(TableOptimizer.class);
	
	public static void optimize()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			Statement st = con.createStatement();
			
			final ArrayList<String> tables = new ArrayList<String>();
			{
				ResultSet rs = st.executeQuery("SHOW FULL TABLES");
				while (rs.next())
				{
					String tableType = rs.getString(2/*"Table_type"*/);
					
					if (tableType.equals("VIEW"))
						continue;
					
					tables.add(rs.getString(1));
				}
				rs.close();
			}
			
			{
				ResultSet rs = st.executeQuery("CHECK TABLE " + StringUtils.join(tables, ","));
				while (rs.next())
				{
					String table = rs.getString("Table");
					String msgType = rs.getString("Msg_type");
					String msgText = rs.getString("Msg_text");
					
					if (msgType.equals("status"))
						if (msgText.equals("OK"))
							continue;
					
					_log.warn("TableOptimizer: CHECK TABLE " + table + ": " + msgType + " -> " + msgText);
				}
				rs.close();
				
				_log.info("TableOptimizer: Database tables have been checked.");
			}
			
			{
				ResultSet rs = st.executeQuery("ANALYZE TABLE " + StringUtils.join(tables, ","));
				while (rs.next())
				{
					String table = rs.getString("Table");
					String msgType = rs.getString("Msg_type");
					String msgText = rs.getString("Msg_text");
					
					if (msgType.equals("status"))
						if (msgText.equals("OK") || msgText.equals("Table is already up to date"))
							continue;
					
					_log.warn("TableOptimizer: ANALYZE TABLE " + table + ": " + msgType + " -> " + msgText);
				}
				rs.close();
				
				_log.info("TableOptimizer: Database tables have been analyzed.");
			}
			
			{
				ResultSet rs = st.executeQuery("OPTIMIZE TABLE " + StringUtils.join(tables, ","));
				while (rs.next())
				{
					String table = rs.getString("Table");
					String msgType = rs.getString("Msg_type");
					String msgText = rs.getString("Msg_text");
					
					if (msgType.equals("status"))
						if (msgText.equals("OK") || msgText.equals("Table is already up to date"))
							continue;
					
					if (msgType.equals("note"))
						if (msgText.equals("Table does not support optimize, doing recreate + analyze instead"))
							continue;
					
					_log.warn("TableOptimizer: OPTIMIZE TABLE " + table + ": " + msgType + " -> " + msgText);
				}
				rs.close();
				
				_log.info("TableOptimizer: Database tables have been optimized.");
			}
			st.close();
		}
		catch (Exception e)
		{
			_log.warn("TableOptimizer: Cannot optimize database tables!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private static final ArrayList<CharacterRelatedTable> CHARACTER_RELATED_TABLES =
			new ArrayList<CharacterRelatedTable>();
	private static final ArrayList<ItemRelatedTable> ITEM_RELATED_TABLES = new ArrayList<ItemRelatedTable>();
	
	public static Iterable<CharacterRelatedTable> getCharacterRelatedTables()
	{
		return CHARACTER_RELATED_TABLES;
	}
	
	public static Iterable<ItemRelatedTable> getItemRelatedTables()
	{
		return ITEM_RELATED_TABLES;
	}
	
	static
	{
		new CharacterRelatedTable("character_birthdays", "charId");
		new CharacterRelatedTable("character_blocks", "charId");
		new CharacterRelatedTable("character_effects", "charId");
		new CharacterRelatedTable("character_friends", "charId1");
		new CharacterRelatedTable("character_friends", "charId2");
		new CharacterRelatedTable("character_hennas", "charId");
		new CharacterRelatedTable("character_instance_time", "charId");
		new CharacterRelatedTable("character_macroses", "charId");
		new CharacterRelatedTable("character_mail", "charId");
		new CharacterRelatedTable("character_quest_global_data", "charId");
		new CharacterRelatedTable("character_quests", "charId");
		new CharacterRelatedTable("character_raid_points", "charId");
		new CharacterRelatedTable("character_recipebook", "charId");
		new CharacterRelatedTable("character_recommend_data", "charId");
		new CharacterRelatedTable("character_recommends", "charId");
		new CharacterRelatedTable("character_recommends", "target_id");
		new CharacterRelatedTable("character_shortcuts", "charId");
		new CharacterRelatedTable("character_skill_reuses", "charId");
		new CharacterRelatedTable("character_skills", "charId");
		new CharacterRelatedTable("character_subclass_certification", "charId");
		new CharacterRelatedTable("character_subclasses", "charId");
		new CharacterRelatedTable("character_tpbookmark", "charId");
		new CharacterRelatedTable("couples", "player1Id");
		new CharacterRelatedTable("couples", "player2Id");
		new CharacterRelatedTable("heroes", "charId");
		new CharacterRelatedTable("obj_restrictions", "obj_Id");
		new CharacterRelatedTable("olympiad_nobles", "charId");
		new CharacterRelatedTable("olympiad_nobles_eom", "charId");
		new CharacterRelatedTable("seven_signs", "charId");
		
		new ItemRelatedTable("pets", "item_obj_id");
		new ItemRelatedTable("item_attributes", "itemId");
	}
	
	public static final class CharacterRelatedTable
	{
		private final String _deleteQuery;
		private final String _cleanQuery;
		
		public CharacterRelatedTable(String tableName, String charId)
		{
			_deleteQuery = "DELETE FROM " + tableName + " WHERE " + tableName + "." + charId + "=?";
			_cleanQuery =
					"DELETE FROM " + tableName + " WHERE " + tableName + "." + charId
							+ " NOT IN (SELECT charId FROM characters)";
			
			CHARACTER_RELATED_TABLES.add(this);
		}
		
		/**
		 * @return DELETE FROM %tableName% WHERE %tableName%.%charId%=?;
		 */
		public String getDeleteQuery()
		{
			return _deleteQuery;
		}
		
		/**
		 * @return DELETE FROM %tableName% WHERE %tableName%.%charId% NOT IN (SELECT charId FROM characters)
		 */
		public String getCleanQuery()
		{
			return _cleanQuery;
		}
	}
	
	public static final class ItemRelatedTable
	{
		private final String _deleteQuery;
		private final String _cleanQuery;
		
		public ItemRelatedTable(String tableName, String itemObjectId)
		{
			_deleteQuery =
					"DELETE FROM " + tableName + " WHERE " + tableName + "." + itemObjectId
							+ " IN (SELECT object_id FROM items WHERE items.owner_id=?)";
			_cleanQuery =
					"DELETE FROM " + tableName + " WHERE " + tableName + "." + itemObjectId
							+ " NOT IN (SELECT object_id FROM items)";
			
			ITEM_RELATED_TABLES.add(this);
		}
		
		/**
		 * @return DELETE FROM %tableName% WHERE %tableName%.%itemObjectId% IN (SELECT object_id FROM items WHERE items.owner_id=?)
		 */
		public String getDeleteQuery()
		{
			return _deleteQuery;
		}
		
		/**
		 * @return DELETE FROM %tableName% WHERE %tableName%.%itemObjectId% NOT IN (SELECT object_id FROM items)
		 */
		public String getCleanQuery()
		{
			return _cleanQuery;
		}
	}
}
