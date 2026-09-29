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
package com.l2jfree.loginserver.beans;

/**
 * Database row representing a registered game server.
 */
public class Gameservers implements java.io.Serializable
{
	
	// Fields
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2293307012167588040L;
	private String serverName;
	private int serverId;
	private String hexid;
	private String host;
	
	// Constructors
	
	/** default constructor */
	public Gameservers()
	{
	}
	
	/** full constructor */
	public Gameservers(int _serverId, String _hexid, String _host)
	{
		serverId = _serverId;
		hexid = _hexid;
		host = _host;
	}
	
	// Property accessors
	public int getServerId()
	{
		return serverId;
	}
	
	public void setServerId(int _serverId)
	{
		serverId = _serverId;
	}
	
	public String getHexid()
	{
		return hexid;
	}
	
	public void setHexid(String _hexid)
	{
		hexid = _hexid;
	}
	
	public String getHost()
	{
		return host;
	}
	
	public void setHost(String _host)
	{
		host = _host;
	}
	
	public String getServerName()
	{
		return serverName;
	}
	
	public void setServerName(String _serverName)
	{
		serverName = _serverName;
	}
	
}
