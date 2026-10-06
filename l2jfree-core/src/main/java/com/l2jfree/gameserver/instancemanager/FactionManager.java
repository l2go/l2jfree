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
package com.l2jfree.gameserver.instancemanager;

import javolution.util.FastList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.model.entity.faction.Faction;

/**
 * @author evill33t
 * 
 */
public class FactionManager
{
	private static final Logger _log = LoggerFactory.getLogger(FactionManager.class);
	
	private static final class SingletonHolder
	{
		private static final FactionManager INSTANCE = new FactionManager();
	}
	
	public static FactionManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private FactionManager()
	{
		_log.info("Initializing FactionManager");
		load();
	}
	
	// =========================================================
	// Data Field
	private FastList<Faction> _factions;
	private FastList<String> _listTitles = new FastList<String>();
	
	// =========================================================
	// Method - Public
	public void reload()
	{
		getFactions().clear();
		getFactionTitles().clear();
		load();
	}
	
	// =========================================================
	// Method - Private
	private final void load()
	{
		// The faction system has no schema in Platform 3.0: there is nothing to load and the list stays empty.
		_log.info("Loaded: " + getFactions().size() + " faction(s)");
	}
	
	// =========================================================
	// Property - Public
	public final Faction getFactions(int FactionId)
	{
		int index = getFactionIndex(FactionId);
		if (index >= 0)
			return getFactions().get(index);
		return null;
	}
	
	public final int getFactionIndex(int FactionId)
	{
		Faction faction;
		for (int i = 0; i < getFactions().size(); i++)
		{
			faction = getFactions().get(i);
			if (faction != null && faction.getId() == FactionId)
				return i;
		}
		return -1;
	}
	
	public final FastList<Faction> getFactions()
	{
		if (_factions == null)
			_factions = new FastList<Faction>();
		return _factions;
	}
	
	public final FastList<String> getFactionTitles()
	{
		if (_listTitles == null)
			_listTitles = new FastList<String>();
		return _listTitles;
	}
}
