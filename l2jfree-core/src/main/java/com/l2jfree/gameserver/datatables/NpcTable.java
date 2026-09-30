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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.FactionManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.L2MinionData;
import com.l2jfree.gameserver.model.drop.L2DropCategory;
import com.l2jfree.gameserver.model.drop.L2DropData;
import com.l2jfree.gameserver.model.entity.faction.Faction;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.util.LookupTable;

public final class NpcTable
{
	private static final Logger _log = LoggerFactory.getLogger(NpcTable.class);
	
	public static NpcTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final LookupTable<L2NpcTemplate> _npcs = new LookupTable<L2NpcTemplate>();
	
	private NpcTable()
	{
		restoreNpcData();
	}
	
	private void restoreNpcData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			try
			{
				PreparedStatement statement = con.prepareStatement("SELECT * FROM npc");
				ResultSet npcdata = statement.executeQuery();
				fillNpcTable(npcdata);
				npcdata.close();
				statement.close();
				_log.info("NpcTable: Loaded " + _npcs.size() + " Npc Templates.");
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error creating NPC table: ", e);
			}
			
			try
			{
				PreparedStatement statement = con.prepareStatement("SELECT * FROM custom_npc");
				ResultSet npcdata = statement.executeQuery();
				int npc_count = _npcs.size();
				fillNpcTable(npcdata);
				npcdata.close();
				statement.close();
				if (_npcs.size() > npc_count)
					_log.info("NpcTable: Loaded " + (_npcs.size() - npc_count) + " Custom Npc Templates.");
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error creating custom NPC table: ", e);
			}
			
			try
			{
				PreparedStatement statement = con.prepareStatement("SELECT npcid, skillid, level FROM npcskills");
				ResultSet npcskills = statement.executeQuery();
				
				while (npcskills.next())
				{
					int mobId = npcskills.getInt("npcid");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					
					if (npcDat == null)
					{
						if (Config.ALT_DEV_VERIFY_NPC_SKILLS)
							_log.warn("NpcTable: Missing template for npcskills for npc id: " + mobId);
						continue;
					}
					
					int skillId = npcskills.getInt("skillid");
					int level = npcskills.getInt("level");
					
					if (skillId == 4416)
					{
						npcDat.setRace(level);
						continue;
					}
					
					L2Skill npcSkill = SkillTable.getInstance().getInfo(skillId, level);
					
					if (npcSkill == null)
					{
						if (Config.ALT_DEV_VERIFY_NPC_SKILLS)
							_log.warn("NpcTable: Missing skill for npcskills for skill id-lvl: " + skillId + " "
									+ level);
						continue;
					}
					
					npcDat.addSkill(npcSkill);
				}
				
				npcskills.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC skills table: ", e);
			}
			
			try
			{
				PreparedStatement statement =
						con.prepareStatement("SELECT npcid, skillid, level FROM custom_npcskills");
				ResultSet npcskills = statement.executeQuery();
				
				while (npcskills.next())
				{
					int mobId = npcskills.getInt("npcid");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					
					if (npcDat == null)
					{
						_log.warn("NpcTable: Missing template for custom_npcskills for npc id: " + mobId);
						continue;
					}
					
					int skillId = npcskills.getInt("skillid");
					int level = npcskills.getInt("level");
					
					if (skillId == 4416)
					{
						npcDat.setRace(level);
						continue;
					}
					
					L2Skill npcSkill = SkillTable.getInstance().getInfo(skillId, level);
					
					if (npcSkill == null)
					{
						_log.warn("NpcTable: Missing skill for custom_npcskills for skill id-lvl: " + skillId + " "
								+ level);
						continue;
					}
					
					npcDat.addSkill(npcSkill);
				}
				
				npcskills.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading custom NPC skills table: ", e);
			}
			
			try
			{
				PreparedStatement statement2 =
						con.prepareStatement("SELECT * FROM droplist ORDER BY mobId, chance DESC");
				ResultSet dropData = statement2.executeQuery();
				
				while (dropData.next())
				{
					int mobId = dropData.getInt("mobId");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					if (npcDat == null)
					{
						_log.error("NPCTable: Drop data for undefined NPC. npcId: " + mobId);
						continue;
					}
					L2DropData dropDat = new L2DropData();
					
					dropDat.setItemId(dropData.getInt("itemId"));
					dropDat.setMinDrop(dropData.getInt("min"));
					dropDat.setMaxDrop(dropData.getInt("max"));
					dropDat.setChance(dropData.getInt("chance"));
					
					int category = dropData.getInt("category");
					
					npcDat.addDropData(dropDat, category);
				}
				
				dropData.close();
				statement2.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC drop data: ", e);
			}
			
			try
			{
				PreparedStatement statement2 =
						con.prepareStatement("SELECT * FROM custom_droplist ORDER BY mobId, chance DESC");
				ResultSet dropData = statement2.executeQuery();
				
				while (dropData.next())
				{
					int mobId = dropData.getInt("mobId");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					if (npcDat == null)
					{
						_log.error("NPCTable: Custom drop data for undefined NPC. npcId: " + mobId);
						continue;
					}
					L2DropData dropDat = new L2DropData();
					
					dropDat.setItemId(dropData.getInt("itemId"));
					dropDat.setMinDrop(dropData.getInt("min"));
					dropDat.setMaxDrop(dropData.getInt("max"));
					dropDat.setChance(dropData.getInt("chance"));
					
					int category = dropData.getInt("category");
					
					npcDat.addDropData(dropDat, category);
				}
				
				dropData.close();
				statement2.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading custom NPC drop data: ", e);
			}
			
			try
			{
				PreparedStatement statement3 = con.prepareStatement("SELECT * FROM skill_learn");
				ResultSet learndata = statement3.executeQuery();
				
				while (learndata.next())
				{
					int npcId = learndata.getInt("npc_id");
					int classId = learndata.getInt("class_id");
					L2NpcTemplate npc = getTemplate(npcId);
					
					if (npc == null)
					{
						_log.warn("NPCTable: Error getting NPC template ID " + npcId
								+ " while trying to load skill trainer data.");
						continue;
					}
					
					npc.addTeachInfo(ClassId.values()[classId]);
				}
				
				learndata.close();
				statement3.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC trainer data: ", e);
			}
			
			try
			{
				PreparedStatement statement4 = con.prepareStatement("SELECT * FROM minions");
				ResultSet minionData = statement4.executeQuery();
				int cnt = 0;
				
				while (minionData.next())
				{
					int raidId = minionData.getInt("boss_id");
					L2NpcTemplate npcDat = _npcs.get(raidId);
					if (npcDat == null)
					{
						_log.warn("Minion references undefined boss NPC. Boss NpcId: " + raidId);
						continue;
					}
					L2MinionData minionDat = new L2MinionData();
					minionDat.setMinionId(minionData.getInt("minion_id"));
					minionDat.setAmountMin(minionData.getInt("amount_min"));
					minionDat.setAmountMax(minionData.getInt("amount_max"));
					npcDat.addRaidData(minionDat);
					cnt++;
				}
				
				minionData.close();
				statement4.close();
				_log.info("NpcTable: Loaded " + cnt + " Minions.");
			}
			catch (Exception e)
			{
				_log.error("Error loading minion data: ", e);
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private boolean fillNpcTable(ResultSet NpcData) throws Exception
	{
		boolean loaded = false;
		while (NpcData.next())
		{
			StatsSet npcDat = new StatsSet();
			int id = NpcData.getInt("id");
			
			if (Config.ASSERT)
				assert id < 1000000;
			
			npcDat.set("npcId", id);
			npcDat.set("idTemplate", NpcData.getInt("idTemplate"));
			int level = NpcData.getInt("level");
			npcDat.set("level", level);
			npcDat.set("jClass", NpcData.getString("class"));
			
			npcDat.set("baseShldDef", 0);
			npcDat.set("baseShldRate", 0);
			npcDat.set("baseCritRate", 38);
			
			npcDat.set("name", NpcData.getString("name"));
			npcDat.set("serverSideName", NpcData.getBoolean("serverSideName"));
			npcDat.set("title", NpcData.getString("title"));
			npcDat.set("serverSideTitle", NpcData.getBoolean("serverSideTitle"));
			npcDat.set("collision_radius", NpcData.getDouble("collision_radius"));
			npcDat.set("collision_height", NpcData.getDouble("collision_height"));
			npcDat.set("fcollision_radius", NpcData.getDouble("collision_radius"));
			npcDat.set("fcollision_height", NpcData.getDouble("collision_height"));
			npcDat.set("sex", NpcData.getString("sex"));
			if (!Config.ALLOW_NPC_WALKERS && NpcData.getString("type").equalsIgnoreCase("L2NpcWalker"))
				npcDat.set("type", "L2Npc");
			else
				npcDat.set("type", NpcData.getString("type"));
			npcDat.set("baseAtkRange", NpcData.getInt("attackrange"));
			npcDat.set("rewardExp", NpcData.getInt("exp"));
			npcDat.set("rewardSp", NpcData.getInt("sp"));
			npcDat.set("basePAtkSpd", NpcData.getInt("atkspd"));
			npcDat.set("baseMAtkSpd", NpcData.getInt("matkspd"));
			npcDat.set("aggroRange", NpcData.getInt("aggro"));
			npcDat.set("rhand", NpcData.getInt("rhand"));
			npcDat.set("lhand", NpcData.getInt("lhand"));
			npcDat.set("armor", NpcData.getInt("armor"));
			npcDat.set("baseWalkSpd", NpcData.getInt("walkspd"));
			npcDat.set("baseRunSpd", NpcData.getInt("runspd"));
			
			npcDat.safeSet("baseSTR", NpcData.getInt("str"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseCON", NpcData.getInt("con"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseDEX", NpcData.getInt("dex"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseINT", NpcData.getInt("int"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseWIT", NpcData.getInt("wit"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseMEN", NpcData.getInt("men"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			
			npcDat.set("baseHpMax", NpcData.getInt("hp"));
			npcDat.set("baseCpMax", 0);
			npcDat.set("baseMpMax", NpcData.getInt("mp"));
			npcDat.set("baseHpReg", NpcData.getFloat("hpreg") > 0 ? NpcData.getFloat("hpreg")
					: 1.5 + ((level - 1) / 10.0));
			npcDat.set("baseMpReg", NpcData.getFloat("mpreg") > 0 ? NpcData.getFloat("mpreg")
					: 0.9 + 0.3 * ((level - 1) / 10.0));
			npcDat.set("basePAtk", NpcData.getInt("patk"));
			npcDat.set("basePDef", NpcData.getInt("pdef"));
			npcDat.set("baseMAtk", NpcData.getInt("matk"));
			npcDat.set("baseMDef", NpcData.getInt("mdef"));
			
			npcDat.set("factionId", NpcData.getString("faction_id"));
			npcDat.set("factionRange", NpcData.getInt("faction_range"));
			
			npcDat.set("isUndead", NpcData.getString("isUndead"));
			
			npcDat.set("absorb_level", NpcData.getString("absorb_level"));
			npcDat.set("absorb_type", NpcData.getString("absorb_type"));
			
			npcDat.set("ss", NpcData.getInt("ss"));
			npcDat.set("bss", NpcData.getInt("bss"));
			npcDat.set("ssRate", NpcData.getInt("ss_rate"));
			
			npcDat.set("AI", NpcData.getString("AI"));
			npcDat.set("drop_herbs", Boolean.valueOf(NpcData.getString("drop_herbs")));
			
			if (Config.FACTION_ENABLED)
			{
				Faction faction;
				for (int i = 0; i < FactionManager.getInstance().getFactions().size(); i++)
				{
					faction = FactionManager.getInstance().getFactions().get(i);
					if (faction.getNpcList().contains(id))
					{
						npcDat.set("NPCFaction", faction.getId());
						npcDat.set("NPCFactionName", faction.getName());
					}
				}
			}
			
			L2NpcTemplate template = new L2NpcTemplate(npcDat);
			template.addVulnerability(Stats.BOW_WPN_VULN, 1);
			template.addVulnerability(Stats.CROSSBOW_WPN_VULN, 1);
			template.addVulnerability(Stats.BLUNT_WPN_VULN, 1);
			template.addVulnerability(Stats.DAGGER_WPN_VULN, 1);
			
			_npcs.set(id, template);
			
			loaded = true;
		}
		return loaded;
	}
	
	public boolean reloadNpc(int id)
	{
		Connection con = null;
		boolean loaded = false;
		try
		{
			// save a copy of the old data
			L2NpcTemplate old = getTemplate(id);
			FastMap<Integer, L2Skill> skills = null;
			
			// L2NpcTemplate.getSkillS() is unmodifiable, so the entrySet() of it can't be used
			if (old != null && old.getSkills() != null)
			{
				skills = new FastMap<Integer, L2Skill>(old.getSkills().size());
				
				for (Integer key : old.getSkills().keySet())
					skills.put(key, old.getSkills().get(key));
			}
			
			L2DropCategory[] categories = new L2DropCategory[0];
			
			if (old != null && old.getDropData() != null)
				categories = ArrayUtils.addAll(categories, old.getDropData());
			
			FastList<ClassId> classIds = new FastList<ClassId>();
			
			if (old != null && old.getTeachInfo() != null)
				classIds.addAll(old.getTeachInfo());
			
			L2MinionData[] minions = new L2MinionData[0];
			
			if (old != null && old.getMinionData() != null)
				minions = ArrayUtils.addAll(minions, old.getMinionData());
			
			// reload the NPC base data
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement st = con.prepareStatement("SELECT * FROM npc WHERE id=?");
			st.setInt(1, id);
			ResultSet rs = st.executeQuery();
			loaded = fillNpcTable(rs);
			rs.close();
			st.close();
			
			if (!loaded)
			{
				st = con.prepareStatement("SELECT * FROM custom_npc WHERE id=?");
				st.setInt(1, id);
				rs = st.executeQuery();
				loaded = fillNpcTable(rs);
				rs.close();
				st.close();
			}
			
			// restore additional data from saved copy
			L2NpcTemplate created = getTemplate(id);
			
			if (skills != null)
				for (L2Skill skill : skills.values())
					created.addSkill(skill);
			
			for (ClassId classId : classIds)
				created.addTeachInfo(classId);
			
			for (L2MinionData minion : minions)
				created.addRaidData(minion);
		}
		catch (Exception e)
		{
			_log.warn("NPCTable: Could not reload data for NPC " + id + ": " + e, e);
			loaded = false;
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return loaded;
	}
	
	// just wrapper
	public void reloadAll()
	{
		reloadAll(true);
	}
	
	public void reloadAll(boolean reloadQuests)
	{
		restoreNpcData();
		if (reloadQuests)
			QuestManager.getInstance().reloadAllQuests();
	}
	
	public void cleanUp()
	{
		_npcs.clear(false);
	}
	
	public void saveNpc(StatsSet npc)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			Map<String, Object> set = npc.getSet();
			
			String name = "";
			String values = "";
			
			for (Object obj : set.keySet())
			{
				name = (String)obj;
				
				if (name.equalsIgnoreCase("npcId"))
					continue;
				
				if (values != "")
					values += ", ";
				
				values += name + " = '" + set.get(name) + "'";
			}
			
			String query = "UPDATE npc SET " + values + " WHERE id = ?";
			String query_custom = "UPDATE custom_npc SET " + values + " WHERE id = ?";
			
			try
			{
				PreparedStatement statement = con.prepareStatement(query);
				statement.setInt(1, npc.getInteger("npcId"));
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
			
			try
			{
				PreparedStatement statement = con.prepareStatement(query_custom);
				statement.setInt(1, npc.getInteger("npcId"));
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
		}
		catch (Exception e)
		{
			_log.warn("NPCTable: Could not store new NPC data in database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public L2NpcTemplate getTemplate(int id)
	{
		return _npcs.get(id);
	}
	
	public Iterable<L2NpcTemplate> getAllTemplates()
	{
		return _npcs;
	}
	
	public L2NpcTemplate getTemplateByName(String name)
	{
		for (L2NpcTemplate npcTemplate : _npcs)
			if (npcTemplate.getName().equalsIgnoreCase(name))
				return npcTemplate;
		return null;
	}
	
	public L2NpcTemplate[] getAllOfLevel(int lvl)
	{
		FastList<L2NpcTemplate> list = new FastList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getLevel() == lvl)
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	public L2NpcTemplate[] getAllMonstersOfLevel(int lvl)
	{
		FastList<L2NpcTemplate> list = new FastList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getLevel() == lvl && t.isAssignableTo(L2MonsterInstance.class))
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	public L2NpcTemplate[] getAllNpcStartingWith(String letter)
	{
		FastList<L2NpcTemplate> list = new FastList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getName().startsWith(letter) && t.isAssignableTo(L2Npc.class))
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	/**
	 * @param classType
	 * @return
	 */
	public Set<Integer> getAllNpcOfClassType(String classType)
	{
		return null;
	}
	
	/**
	 * @param clazz
	 * @return
	 */
	public Set<Integer> getAllNpcOfL2jClass(Class<?> clazz)
	{
		return null;
	}
	
	/**
	 * @param aiType
	 * @return
	 */
	public Set<Integer> getAllNpcOfAiType(String aiType)
	{
		return null;
	}
	
	public List<L2NpcTemplate> getMobsByDrop(int itemid)
	{
		List<L2NpcTemplate> returnVal = new FastList<L2NpcTemplate>();
		for (L2NpcTemplate tempNpc : _npcs)
		{
			List<L2DropData> dropdata = tempNpc.getAllDropData();
			if (dropdata != null)
			{
				for (L2DropData tempDrop : dropdata)
				{
					if (tempDrop.getItemId() == itemid)
					{
						returnVal.add(tempNpc);
						break;
					}
				}
			}
		}
		return returnVal;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final NpcTable _instance = new NpcTable();
	}
}
