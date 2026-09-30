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
package com.l2jfree.gameserver.gameobjects.skills;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.util.LookupTable;

public final class PlayerSkills
{
	private static final Logger _log = LoggerFactory.getLogger(PlayerSkills.class);
	
	private final LookupTable<SkillMap> _storedSkills = new LookupTable<SkillMap>();
	private final L2Player _owner;
	
	public PlayerSkills(L2Player owner)
	{
		_owner = owner;
	}
	
	private L2Player getOwner()
	{
		return _owner;
	}
	
	public void storeSkill(L2Skill skill, int classIndex)
	{
		if (skill == null)
			return;
		
		final SkillMap map = getSkillMap(classIndex);
		
		final Integer oldLevel = map.put(skill);
		
		checkStoredSkill(skill, classIndex);
		
		if (oldLevel != null && oldLevel.intValue() == skill.getLevel())
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			if (oldLevel != null)
			{
				PreparedStatement statement =
						con.prepareStatement("UPDATE character_skills SET skill_level=? WHERE skill_id=? AND charId=? AND class_index=?");
				statement.setInt(1, skill.getLevel());
				statement.setInt(2, skill.getId());
				statement.setInt(3, getOwner().getObjectId());
				statement.setInt(4, classIndex);
				statement.execute();
				statement.close();
			}
			else
			{
				PreparedStatement statement =
						con.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?)");
				statement.setInt(1, getOwner().getObjectId());
				statement.setInt(2, skill.getId());
				statement.setInt(3, skill.getLevel());
				statement.setInt(4, classIndex);
				statement.execute();
				statement.close();
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
	
	public void deleteSkill(L2Skill skill)
	{
		if (skill == null)
			return;
		
		final SkillMap map = getSkillMap();
		
		if (map.remove(skill) == null)
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement =
					con.prepareStatement("DELETE FROM character_skills WHERE skill_id=? AND charId=? AND class_index=?");
			statement.setInt(1, skill.getId());
			statement.setInt(2, getOwner().getObjectId());
			statement.setInt(3, getOwner().getClassIndex());
			statement.execute();
			statement.close();
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
	
	public void restoreSkills()
	{
		final SkillMap map = getSkillMap();
		
		ArrayList<L2Skill> tmp = new ArrayList<L2Skill>();
		
		for (Map.Entry<Integer, Integer> entry : map.entrySet())
		{
			final L2Skill skill = SkillTable.getInstance().getInfo(entry.getKey(), entry.getValue());
			if (skill == null)
				continue;
			
			tmp.add(skill);
		}
		
		L2Skill[] skills = tmp.toArray(new L2Skill[tmp.size()]);
		
		Arrays.sort(skills, getOwner().SKILL_LIST_COMPARATOR);
		
		for (L2Skill skill : skills)
			getOwner().addSkill(skill);
	}
	
	public void deleteSkills(Connection con, int classIndex) throws SQLException
	{
		PreparedStatement statement =
				con.prepareStatement("DELETE FROM character_skills WHERE charId=? AND class_index=?");
		statement.setInt(1, getOwner().getObjectId());
		statement.setInt(2, classIndex);
		statement.execute();
		statement.close();
		
		_storedSkills.remove(classIndex);
	}
	
	private SkillMap getSkillMap()
	{
		return getSkillMap(getOwner().getClassIndex());
	}
	
	private SkillMap getSkillMap(int classIndex)
	{
		SkillMap map = _storedSkills.get(classIndex);
		
		if (map != null)
			return map;
		
		map = new SkillMap();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement =
					con.prepareStatement("SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=?");
			statement.setInt(1, getOwner().getObjectId());
			statement.setInt(2, classIndex);
			
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				final int skillId = rset.getInt("skill_id");
				final int skillLvl = rset.getInt("skill_level");
				
				map.put(skillId, skillLvl);
				
				final L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
				if (skill == null)
					continue;
				
				checkStoredSkill(skill, classIndex);
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_storedSkills.set(classIndex, map);
		
		return map;
	}
	
	private void checkStoredSkill(L2Skill skill, int classIndex)
	{
		if (getOwner().isGM() || Config.ALT_GAME_SKILL_LEARN || getOwner().getClassIndex() != classIndex)
			return;
		
		if (getOwner().isTemporarySkill(skill))
			_log.warn("Temporary skill " + skill + " was saved for " + getOwner());
		
		if (!getOwner().isStoredSkill(skill))
			_log.warn("Non-stored skill " + skill + " was saved for " + getOwner());
	}
	
	private static final class SkillMap extends FastMap<Integer, Integer>
	{
		private static final long serialVersionUID = -222036343002486892L;
		
		public Integer put(L2Skill skill)
		{
			return put(skill.getId(), skill.getLevel());
		}
		
		@SuppressWarnings("unused")
		public Integer get(L2Skill skill)
		{
			return get(skill.getId());
		}
		
		public Integer remove(L2Skill skill)
		{
			return remove(skill.getId());
		}
	}
}
