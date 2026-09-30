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
package com.l2jfree.loginserver.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.l2jfree.loginserver.beans.Gameservers;
import com.l2jfree.loginserver.dao.GameserversDAO;
import com.l2jfree.loginserver.dao.LoginDataAccessException;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;

/**
 * Account service to handle gameservers management
 * 
 */
public class GameserversServices
{
	private static Logger _log = LoggerFactory.getLogger(GameserversServices.class);
	
	private GameserversDAO __dao = null;
	
	public void setGameserversDAO(GameserversDAO dao)
	{
		__dao = dao;
	}
	
	/**
	 * Return list of gameservers
	 * @return
	 */
	public List<Gameservers> getAllGameservers()
	{
		return __dao.getAllGameservers();
	}
	
	/**
	 * 
	 * @param id - the server id
	 * @return  the server name or null if no server was found
	 */
	public String getGameserverName(int id)
	{
		try
		{
			return __dao.getGameserverByServerId(id).getServerName();
		}
		catch (LoginObjectNotFoundException e)
		{
			_log.warn("Unable to retrieve gameserver : " + id, e);
			return null;
		}
	}
	
	/**
	 * 
	 * @param gs
	 * @return id of created server or -1 if server was not created.
	 */
	public int createGameserver(Gameservers gs)
	{
		try
		{
			return __dao.createGameserver(gs);
		}
		catch (LoginDataAccessException e)
		{
			_log.warn("Unable to create gameserver.", e);
			return -1;
		}
	}
	
	/**
	 * 
	 * @param id
	 */
	public void deleteGameserver(int id)
	{
		try
		{
			__dao.removeGameserverByServerId(id);
		}
		catch (LoginDataAccessException e)
		{
			_log.warn("Error while deleting gameserver :" + e, e);
		}
	}
	
	/**
	 * @param entities
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#removeAll()
	 */
	public void removeAll()
	{
		__dao.removeAll();
	}
}
