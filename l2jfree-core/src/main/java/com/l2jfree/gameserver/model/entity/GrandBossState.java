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
package com.l2jfree.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;

/**
 * This class is
 * 
 * @author  L2J_JP SANDMAN
 */
public class GrandBossState
{
	public static enum StateEnum
	{
		NOTSPAWN,
		ALIVE,
		DEAD,
		INTERVAL
	}
	
	private int _bossId;
	private long _respawnDate;
	private StateEnum _state;
	
	private static final Logger _log = LoggerFactory.getLogger(GrandBossState.class);
	
	public int getBossId()
	{
		return _bossId;
	}
	
	public void setBossId(int newId)
	{
		_bossId = newId;
	}
	
	public StateEnum getState()
	{
		return _state;
	}
	
	public void setState(StateEnum newState)
	{
		_state = newState;
	}
	
	public long getRespawnDate()
	{
		return _respawnDate;
	}
	
	public void setRespawnDate(long interval)
	{
		_respawnDate = interval + System.currentTimeMillis();
	}
	
	public GrandBossState()
	{
	}
	
	public GrandBossState(int bossId)
	{
		_bossId = bossId;
		load();
	}
	
	public GrandBossState(int bossId, boolean isDoLoad)
	{
		_bossId = bossId;
		if (isDoLoad)
			load();
	}
	
	public void load()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement = con.prepareStatement("SELECT * FROM grandboss_intervallist WHERE bossId = ?");
			statement.setInt(1, _bossId);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				_respawnDate = rset.getLong("respawnDate");
				
				if (_respawnDate - System.currentTimeMillis() <= 0)
				{
					_state = StateEnum.NOTSPAWN;
				}
				else
				{
					int tempState = rset.getInt("state");
					if (tempState == StateEnum.NOTSPAWN.ordinal())
						_state = StateEnum.NOTSPAWN;
					else if (tempState == StateEnum.INTERVAL.ordinal())
						_state = StateEnum.INTERVAL;
					else if (tempState == StateEnum.ALIVE.ordinal())
						_state = StateEnum.ALIVE;
					else if (tempState == StateEnum.DEAD.ordinal())
						_state = StateEnum.DEAD;
					else
						_state = StateEnum.NOTSPAWN;
				}
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void save()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO grandboss_intervallist (bossId,respawnDate,state) VALUES(?,?,?)");
			statement.setInt(1, _bossId);
			statement.setLong(2, _respawnDate);
			statement.setInt(3, _state.ordinal());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void update()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE grandboss_intervallist SET respawnDate = ?,state = ? WHERE bossId = ?");
			statement.setLong(1, _respawnDate);
			statement.setInt(2, _state.ordinal());
			statement.setInt(3, _bossId);
			statement.execute();
			statement.close();
			_log.info("update GrandBossState : ID-" + _bossId + ",RespawnDate-" + _respawnDate + ",State-"
					+ _state.toString());
		}
		catch (Exception e)
		{
			_log.warn("Exeption on update GrandBossState : ID-" + _bossId + ",RespawnDate-" + _respawnDate + ",State-"
					+ _state.toString(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void setNextRespawnDate(long newRespawnDate)
	{
		_respawnDate = newRespawnDate;
	}
	
	public long getInterval()
	{
		long interval = _respawnDate - System.currentTimeMillis();
		
		if (interval < 0)
			return 0;
		
		return interval;
	}
}
