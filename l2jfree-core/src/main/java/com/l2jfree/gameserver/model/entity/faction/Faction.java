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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author evill33t
 * 
 */
public class Faction
{
	private int _Id = 0;
	private String _name = null;
	private float _points = 0;
	private int _joinprice = 0;
	private int _side = 0; // 0 = Neutral 1 = Good 2 = Evil
	private final List<Integer> _list_classes = new ArrayList<Integer>();
	private final List<Integer> _list_npcs = new ArrayList<Integer>();
	private final Map<Integer, String> _list_title = new LinkedHashMap<Integer, String>();
	
	/**
	 * The faction system has no schema in Platform 3.0 (the tables factions, faction_members, faction_quests, and
	 * character_faction_quests are gone), so a faction only knows its id and nothing is stored.
	 */
	public Faction(int factionId)
	{
		_Id = factionId;
	}
	
	public void addPoints(int points)
	{
		_points += points;
	}
	
	public void clearPoints()
	{
		_points = 0;
	}
	
	public final int getId()
	{
		return _Id;
	}
	
	public final String getName()
	{
		return _name;
	}
	
	public final float getPoints()
	{
		return _points;
	}
	
	public final List<Integer> getClassList()
	{
		return _list_classes;
	}
	
	public final List<Integer> getNpcList()
	{
		return _list_npcs;
	}
	
	public final Map<Integer, String> getTitle()
	{
		return _list_title;
	}
	
	public final int getPrice()
	{
		return _joinprice;
	}
	
	public final int getSide()
	{
		return _side;
	}
}
