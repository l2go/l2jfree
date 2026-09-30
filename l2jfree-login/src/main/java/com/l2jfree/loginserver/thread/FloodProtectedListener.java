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
package com.l2jfree.loginserver.thread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;

/**
 * @author -Wooden-
 */
public abstract class FloodProtectedListener extends Thread
{
	protected static final Logger _log = LoggerFactory.getLogger(FloodProtectedListener.class);
	
	private final Map<String, ForeignConnection> _floodProtection = new FastMap<String, ForeignConnection>();
	private final ServerSocket _serverSocket;
	
	public FloodProtectedListener(String listenIp, int port)
	{
		try
		{
			if (listenIp.equals("*"))
				_serverSocket = new ServerSocket(port);
			else
				_serverSocket = new ServerSocket(port, 50, InetAddress.getByName(listenIp));
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error creating ServerSocket: ", e);
		}
	}
	
	@Override
	public void run()
	{
		for (;;)
		{
			Socket connection = null;
			try
			{
				connection = _serverSocket.accept();
				
				if (isFlooding(connection))
					continue;
				
				addClient(connection);
			}
			catch (Exception e)
			{
				_log.warn("", e);
				
				try
				{
					if (connection != null)
						connection.close();
				}
				catch (IOException e2)
				{
				}
			}
			
			if (isInterrupted())
			{
				// shutdown?
				try
				{
					_serverSocket.close();
				}
				catch (IOException e3)
				{
					_log.warn("", e3);
				}
				return;
			}
		}
	}
	
	private boolean isFlooding(Socket connection)
	{
		if (!Config.FLOOD_PROTECTION)
			return false;
		
		final String host = connection.getInetAddress().getHostAddress();
		
		ForeignConnection fConnection = _floodProtection.get(host);
		if (fConnection != null)
		{
			fConnection.connectionNumber += 1;
			
			if ((fConnection.connectionNumber > Config.FAST_CONNECTION_LIMIT && (System.currentTimeMillis() - fConnection.lastConnection) < Config.NORMAL_CONNECTION_TIME)
					|| (System.currentTimeMillis() - fConnection.lastConnection) < Config.FAST_CONNECTION_TIME
					|| fConnection.connectionNumber > Config.MAX_CONNECTION_PER_IP)
			{
				fConnection.connectionNumber -= 1;
				fConnection.lastConnection = System.currentTimeMillis();
				
				try
				{
					connection.close();
				}
				catch (IOException e)
				{
					_log.warn("", e);
				}
				
				if (!fConnection.isFlooding)
					_log.info("Potential Flood from " + host);
				
				fConnection.isFlooding = true;
				return true;
			}
			else if (fConnection.isFlooding) //if connection was flooding server but now passed the check
			{
				fConnection.isFlooding = false;
				
				_log.info(host + " is not considered as flooding anymore.");
			}
			
			fConnection.lastConnection = System.currentTimeMillis();
		}
		else
		{
			_floodProtection.put(host, new ForeignConnection());
		}
		
		return false;
	}
	
	private static final class ForeignConnection
	{
		public int connectionNumber = 1;
		public long lastConnection = System.currentTimeMillis();
		public boolean isFlooding = false;
	}
	
	public abstract void addClient(Socket s);
	
	public void removeFloodProtection(String ip)
	{
		if (!Config.FLOOD_PROTECTION)
			return;
		
		final ForeignConnection fConnection = _floodProtection.get(ip);
		if (fConnection != null)
		{
			fConnection.connectionNumber -= 1;
			
			if (fConnection.connectionNumber == 0)
			{
				_floodProtection.remove(ip);
			}
		}
		else
		{
			_log.warn("Removing a flood protection for a GameServer that was not in the connection map??? :" + ip);
		}
	}
	
	public void close()
	{
		try
		{
			_serverSocket.close();
		}
		catch (IOException e)
		{
			_log.warn("", e);
		}
	}
}
