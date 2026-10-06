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
package com.l2jfree.gameserver.model.entity.faction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * @author evill33t
 *
 */
public class FactionQuest
{
	protected static Logger _log = LoggerFactory.getLogger(FactionQuest.class);
	
	private final int _questId;
	private static int _factionId;
	private static String _name;
	private static String _descr;
	private static int _reward;
	private static int _mobId;
	private static int _amount;
	private static int _minLevel;
	
	public FactionQuest(int questId, int factionId, String name, String descr, int reward, int mobId, int amount,
			int minLevel)
	{
		_questId = questId;
		_factionId = factionId;
		_name = name;
		_descr = descr;
		_reward = reward;
		_mobId = mobId;
		_amount = amount;
		_minLevel = minLevel;
	}
	
	public int getId()
	{
		return _questId;
	}
	
	public static String getName()
	{
		return _name;
	}
	
	public static String getDescr()
	{
		return _descr;
	}
	
	public static int getReward()
	{
		return _reward;
	}
	
	public static int getAmount()
	{
		return _amount;
	}
	
	public static int getMobId()
	{
		return _mobId;
	}
	
	public static int getFactionId()
	{
		return _factionId;
	}
	
	public static int getMinLevel()
	{
		return _minLevel;
	}
	
	/** The faction system has no schema in Platform 3.0: nothing is stored for a started faction quest. */
	public static void createFactionQuest(L2Player player, int factionQuestId)
	{
		// Nothing to store.
	}
	
	public static void endFactionQuest(L2Player player, int factionQuestId)
	{
		player.sendMessage(getName() + " completed.");
		player.getNPCFaction().addFactionPoints(getReward() * Config.FACTION_QUEST_RATE);
		deleteFactionQuest(player, factionQuestId);
	}
	
	/** The faction system has no schema in Platform 3.0: there is no stored faction quest to delete. */
	public static void deleteFactionQuest(L2Player player, int factionQuestId)
	{
		// Nothing to delete.
	}
}
