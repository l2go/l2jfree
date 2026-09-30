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
package com.l2jfree.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2SiegeGuard;
import com.l2jfree.gameserver.gameobjects.instance.L2ClassMasterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2WyvernManagerInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.DayNightSpawnManager;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;

/**
 * This class ...
 * 
 * @author Nightmare
 * @version $Revision: 1.5.2.6.2.7 $ $Date: 2005/03/27 15:29:18 $
 */
public class SpawnTable
{
	private final static Logger _log = LoggerFactory.getLogger(SpawnTable.class);
	
	private final FastMap<Integer, L2Spawn> _spawnTable = new FastMap<Integer, L2Spawn>(50000).setShared(true);
	private int _npcSpawnCount;
	private int _cSpawnCount;
	private int _highestDbId;
	private int _highestCustomDbId;
	
	public static SpawnTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private SpawnTable()
	{
		if (!Config.ALT_DEV_NO_SPAWNS)
			fillSpawnTable();
		else
			_log.debug("Spawns Disabled");
	}
	
	public Map<Integer, L2Spawn> getSpawnTable()
	{
		return _spawnTable;
	}
	
	private void fillSpawnTable()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, count, npc_templateid, locx, locy, locz, heading, respawn_delay, loc_id, periodOfDay FROM spawnlist ORDER BY id");
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_templateid"));
				if (template1 != null)
				{
					if (template1.isAssignableTo(L2SiegeGuard.class))
					{
						// Don't spawn siege guards
					}
					else if (template1.isAssignableTo(L2Boss.class))
					{
						// Don't spawn raidbosses
					}
					else if (!Config.ALT_SPAWN_CLASS_MASTER && template1.isAssignableTo(L2ClassMasterInstance.class))
					{
						// Dont' spawn class masters
					}
					else if (!Config.ALT_SPAWN_WYVERN_MANAGER
							&& template1.isAssignableTo(L2WyvernManagerInstance.class))
					{
						// Dont' spawn wyvern managers
					}
					else
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setId(_npcSpawnCount);
						spawnDat.setDbId(rset.getInt("id"));
						spawnDat.setAmount(rset.getInt("count"));
						spawnDat.setLocx(rset.getInt("locx"));
						spawnDat.setLocy(rset.getInt("locy"));
						spawnDat.setLocz(rset.getInt("locz"));
						spawnDat.setHeading(rset.getInt("heading"));
						spawnDat.setRespawnDelay(rset.getInt("respawn_delay"));
						int loc_id = rset.getInt("loc_id");
						spawnDat.setLocation(loc_id);
						
						switch (rset.getInt("periodOfDay"))
						{
							case 0: // default
								_npcSpawnCount += spawnDat.init(true);
								break;
							case 1: // Day
								DayNightSpawnManager.getInstance().addDayCreature(spawnDat);
								_npcSpawnCount++;
								break;
							case 2: // Night
								DayNightSpawnManager.getInstance().addNightCreature(spawnDat);
								_npcSpawnCount++;
								break;
						}
						
						if (spawnDat.getDbId() > _highestDbId)
							_highestDbId = spawnDat.getDbId();
						_spawnTable.put(spawnDat.getId(), spawnDat);
					}
				}
				else
				{
					_log.warn("SpawnTable: Data missing or incorrect in NPC/Custom NPC table for ID: "
							+ rset.getInt("npc_templateid") + ".");
				}
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			// problem with initializing spawn, go to next one
			_log.warn("SpawnTable: Spawn could not be initialized: ", e);
		}
		_log.info("SpawnTable: Loaded " + _spawnTable.size() + " Npc Spawn Locations.");
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, count, npc_templateid, locx, locy, locz, heading, respawn_delay, loc_id, periodOfDay FROM custom_spawnlist ORDER BY id");
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			_cSpawnCount = _spawnTable.size();
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_templateid"));
				if (template1 != null)
				{
					if (template1.isAssignableTo(L2SiegeGuard.class))
					{
						// Don't spawn siege guards
					}
					else if (template1.isAssignableTo(L2Boss.class))
					{
						// Don't spawn raidbosses
					}
					else if (!Config.ALT_SPAWN_CLASS_MASTER && template1.isAssignableTo(L2ClassMasterInstance.class))
					{
						// Dont' spawn class masters
					}
					else if (!Config.ALT_SPAWN_WYVERN_MANAGER
							&& template1.isAssignableTo(L2WyvernManagerInstance.class))
					{
						// Dont' spawn wyvern managers
					}
					else
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setId(_npcSpawnCount);
						spawnDat.setDbId(rset.getInt("id"));
						spawnDat.setAmount(rset.getInt("count"));
						spawnDat.setLocx(rset.getInt("locx"));
						spawnDat.setLocy(rset.getInt("locy"));
						spawnDat.setLocz(rset.getInt("locz"));
						spawnDat.setHeading(rset.getInt("heading"));
						spawnDat.setRespawnDelay(rset.getInt("respawn_delay"));
						spawnDat.setCustom();
						int loc_id = rset.getInt("loc_id");
						spawnDat.setLocation(loc_id);
						
						switch (rset.getInt("periodOfDay"))
						{
							case 0: // default
								_npcSpawnCount += spawnDat.init();
								break;
							case 1: // Day
								DayNightSpawnManager.getInstance().addDayCreature(spawnDat);
								_npcSpawnCount++;
								break;
							case 2: // Night
								DayNightSpawnManager.getInstance().addNightCreature(spawnDat);
								_npcSpawnCount++;
								break;
						}
						
						if (spawnDat.getDbId() > _highestCustomDbId)
							_highestCustomDbId = spawnDat.getDbId();
						_spawnTable.put(spawnDat.getId(), spawnDat);
					}
				}
				else
				{
					_log.warn("SpawnTable: Data missing or incorrect in NPC/Custom NPC table for ID: "
							+ rset.getInt("npc_templateid") + ".");
				}
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			// problem with initializing spawn, go to next one
			_log.warn("SpawnTable: Custom spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_cSpawnCount = _spawnTable.size() - _cSpawnCount;
		if (_cSpawnCount > 0)
			_log.info("SpawnTable: Loaded " + _cSpawnCount + " Custom Spawn Locations.");
		
		if (_log.isDebugEnabled())
			_log.debug("SpawnTable: Spawning completed, total number of NPCs in the world: " + _npcSpawnCount);
	}
	
	public Map<Integer, L2Spawn> getAllTemplates()
	{
		return _spawnTable;
	}
	
	public void addNewSpawn(L2Spawn spawn, boolean storeInDb)
	{
		_npcSpawnCount++;
		if (spawn.isCustom())
		{
			_highestCustomDbId++;
			spawn.setDbId(_highestCustomDbId);
		}
		else
		{
			_highestDbId++;
			spawn.setDbId(_highestDbId);
		}
		
		spawn.setId(_npcSpawnCount);
		
		_spawnTable.put(spawn.getId(), spawn);
		
		if (storeInDb)
		{
			Connection con = null;
			
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement =
						con.prepareStatement("INSERT INTO "
								+ (spawn.isCustom() ? "custom_spawnlist" : "spawnlist")
								+ " (id,count,npc_templateid,locx,locy,locz,heading,respawn_delay,loc_id) values(?,?,?,?,?,?,?,?,?)");
				statement.setInt(1, spawn.getDbId());
				statement.setInt(2, spawn.getAmount());
				statement.setInt(3, spawn.getNpcId());
				statement.setInt(4, spawn.getLocx());
				statement.setInt(5, spawn.getLocy());
				statement.setInt(6, spawn.getLocz());
				statement.setInt(7, spawn.getHeading());
				statement.setInt(8, spawn.getRespawnDelay() / 1000);
				statement.setInt(9, spawn.getLocation());
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				// problem with storing spawn
				_log.warn("SpawnTable: Could not store spawn in the DB:", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	public void updateSpawn(L2Spawn spawn)
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("update "
							+ (spawn.isCustom() ? "custom_spawnlist" : "spawnlist")
							+ " set count=?,npc_templateid=?,locx=?,locy=?,locz=?,heading=?,respawn_delay=?,loc_id=? where id =?");
			statement.setInt(1, spawn.getAmount());
			statement.setInt(2, spawn.getNpcId());
			statement.setInt(3, spawn.getLocx());
			statement.setInt(4, spawn.getLocy());
			statement.setInt(5, spawn.getLocz());
			statement.setInt(6, spawn.getHeading());
			statement.setInt(7, spawn.getRespawnDelay() / 1000);
			statement.setInt(8, spawn.getLocation());
			statement.setInt(9, spawn.getDbId());
			
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			// problem with storing spawn
			_log.warn("SpawnTable: Could not update spawn in the DB:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void deleteSpawn(L2Spawn spawn, boolean updateDb)
	{
		if (_spawnTable.remove(spawn.getId()) == null)
			return;
		
		if (updateDb)
		{
			Connection con = null;
			
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement =
						con.prepareStatement("DELETE FROM " + (spawn.isCustom() ? "custom_spawnlist" : "spawnlist")
								+ " WHERE id=?");
				statement.setInt(1, spawn.getDbId());
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				// problem with deleting spawn
				_log.warn("SpawnTable: Spawn " + spawn.getDbId() + " could not be removed from DB: ", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	public void reloadAll()
	{
		cleanUp();
		fillSpawnTable();
	}
	
	/**
	 * Clear all spawns from the cache
	 */
	private void cleanUp()
	{
		_spawnTable.clear();
	}
	
	/**
	 * @param id the id of the spawn npc
	 * @return the template (description) of this spawn
	 */
	public L2Spawn getTemplate(int id)
	{
		return _spawnTable.get(id);
	}
	
	/**
	 * Get all the spawn of a NPC<BR><BR>
	 * 
	 * @param npcId : ID of the NPC to find.
	 * @return
	 */
	public void findNPCInstances(L2Player activeChar, int npcId, int teleportIndex)
	{
		int index = 0;
		for (FastMap.Entry<Integer, L2Spawn> entry = _spawnTable.head(), end = _spawnTable.tail(); (entry =
				entry.getNext()) != end;)
		{
			L2Spawn spawn = entry.getValue();
			if (npcId == spawn.getNpcId())
			{
				index++;
				if (teleportIndex > -1)
				{
					if (teleportIndex == index)
						activeChar.teleToLocation(spawn.getLocx(), spawn.getLocy(), spawn.getLocz(), true);
				}
				else
				{
					activeChar.sendMessage(index + " - " + spawn.getTemplate().getName() + " (" + spawn.getId() + "): "
							+ spawn.getLocx() + " " + spawn.getLocy() + " " + spawn.getLocz());
				}
			}
		}
		if (index == 0)
			activeChar.sendMessage("No current spawns found.");
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SpawnTable _instance = new SpawnTable();
	}
}
