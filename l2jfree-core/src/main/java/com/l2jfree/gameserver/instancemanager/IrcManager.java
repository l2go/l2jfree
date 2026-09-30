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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.network.L2IrcClient;

/**
 * @author evill33t
 * 
 */
public class IrcManager
{
	private static final Logger _log = LoggerFactory.getLogger(IrcManager.class);
	
	private static final class SingletonHolder
	{
		private static final IrcManager INSTANCE = new IrcManager();
	}
	
	public static IrcManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private L2IrcClient _IrcConnection;
	
	private IrcManager()
	{
		_log.info("Initializing IrcManager");
		load();
	}
	
	public void reload()
	{
		if (_IrcConnection != null)
		{
			_IrcConnection.disconnect();
			_IrcConnection = null;
		}
		
		try
		{
			_IrcConnection =
					new L2IrcClient(Config.IRC_SERVER, Config.IRC_PORT, Config.IRC_PASS, Config.IRC_NICK,
							Config.IRC_USER, Config.IRC_NAME, Config.IRC_SSL, Config.IRC_CHANNEL);
			_IrcConnection.connect();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public L2IrcClient getConnection()
	{
		return _IrcConnection;
	}
	
	public void removeConnection()
	{
		_IrcConnection.disconnect();
		_IrcConnection = null;
	}
	
	private final void load()
	{
		_IrcConnection =
				new L2IrcClient(Config.IRC_SERVER, Config.IRC_PORT, Config.IRC_PASS, Config.IRC_NICK, Config.IRC_USER,
						Config.IRC_NAME, Config.IRC_SSL, Config.IRC_CHANNEL);
		
		try
		{
			_IrcConnection.connect();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
}
