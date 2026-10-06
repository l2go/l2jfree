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

/**
 * @author evill33t
 * 
 */
public class FactionMember
{
	// =========================================================
	// Data Field
	private int _playerId = 0;
	private int _factionId = 0;
	private int _factionPoints = 0;
	private int _contributions = 0;
	private long _joinDate;
	private int _side;
	
	// =========================================================
	// Constructor
	// The faction system has no schema in Platform 3.0, so a member is kept in memory only and nothing is stored.
	public FactionMember(int playerId)
	{
		_playerId = playerId;
	}
	
	public FactionMember(int playerId, int factionId)
	{
		_playerId = playerId;
		_factionId = factionId;
		_factionPoints = 0;
		_contributions = 0;
		_joinDate = System.currentTimeMillis();
	}
	
	public void quitFaction()
	{
		_factionId = 0;
		_factionPoints = 0;
		_contributions = 0;
	}
	
	public void addFactionPoints(int amount)
	{
		_factionPoints += amount;
	}
	
	public void addContributions(int amount)
	{
		_contributions += amount;
	}
	
	public boolean reduceFactionPoints(int amount)
	{
		if (amount < getFactionPoints())
		{
			_factionPoints -= amount;
			return true;
		}
		
		return false;
	}
	
	public void setFactionPoints(int amount)
	{
		_factionPoints = amount;
	}
	
	public void setContribution(int amount)
	{
		_factionPoints = amount;
	}
	
	public void setFactionId(int factionId)
	{
		_factionId = factionId;
	}
	
	public final int getPlayerId()
	{
		return _playerId;
	}
	
	public final int getFactionId()
	{
		return _factionId;
	}
	
	public final int getSide()
	{
		return _side;
	}
	
	public final int getFactionPoints()
	{
		return _factionPoints;
	}
	
	public final int getContributions()
	{
		return _contributions;
	}
	
	public final long getJoinDate()
	{
		return _joinDate;
	}
}
