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

import javolution.util.FastList;
import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.skills.L2Skill;

/**
 * Warning: must be loaded after loading SkillTable
 *
 * @author  DrHouse
 */
public class ResidentialSkillTable
{
	private static final Logger _log = LoggerFactory.getLogger(ResidentialSkillTable.class);
	
	private static final class SingletonHolder
	{
		private static final ResidentialSkillTable INSTANCE = new ResidentialSkillTable();
	}
	
	public static ResidentialSkillTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final FastMap<Integer, L2Skill[]> _list = new FastMap<Integer, L2Skill[]>();
	
	private ResidentialSkillTable()
	{
		load();
	}
	
	public void reload()
	{
		_list.clear();
		load();
	}
	
	private void load()
	{
		FastMap<Integer, FastList<L2Skill>> tempMap = new FastMap<Integer, FastList<L2Skill>>();
		Connection con = null;
		
		int skills = 0;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT * FROM skill_residential ORDER BY entityId");
			ResultSet rs = statement.executeQuery();
			
			while (rs.next())
			{
				int entityId = rs.getInt("entityId");
				int skillId = rs.getInt("skillId");
				int skillLvl = rs.getInt("skillLevel");
				
				L2Skill sk = SkillTable.getInstance().getInfo(skillId, skillLvl);
				
				if (sk == null)
				{
					_log.warn("ResidentialSkillTable: SkillTable has returned null for ID/level: " + skillId + "/"
							+ skillLvl);
					continue;
				}
				if (!tempMap.containsKey(entityId))
				{
					FastList<L2Skill> aux = new FastList<L2Skill>();
					aux.add(sk);
					tempMap.put(entityId, aux);
				}
				else
					tempMap.get(entityId).add(sk);
				++skills;
			}
			statement.close();
			rs.close();
			
			for (Map.Entry<Integer, FastList<L2Skill>> e : tempMap.entrySet())
			{
				_list.put(e.getKey(), e.getValue().toArray(new L2Skill[e.getValue().size()]));
			}
		}
		catch (Exception e)
		{
			_log.error("ResidentialSkillTable: a problem occured while loading skills!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		_log.info("ResidentialSkillTable: Loaded " + _list.size() + " entities with " + skills + " associated skills.");
	}
	
	public L2Skill[] getSkills(int entityId)
	{
		return _list.get(entityId);
	}
}
