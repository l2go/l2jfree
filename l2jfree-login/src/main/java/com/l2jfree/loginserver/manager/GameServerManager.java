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
package com.l2jfree.loginserver.manager;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.List;
import java.util.Map;

import javolution.util.FastMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.l2jfree.L2Registry;
import com.l2jfree.loginserver.beans.GameServerInfo;
import com.l2jfree.loginserver.beans.Gameservers;
import com.l2jfree.loginserver.services.GameserversServices;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.tools.util.HexUtil;

/**
 * Manager servers
 * Servers come from server.xml file and database.
 * For each server in database, an instance of Gameserver is launch. The manager controls each gameserver threads.
 */
public class GameServerManager
{
	private static final Log _log = LogFactory.getLog(GameServerManager.class);
	private static GameServerManager __instance = null;
	
	// Game Server from database
	private final Map<Integer, GameServerInfo> _gameServers = new FastMap<Integer, GameServerInfo>().setShared(true);
	
	// RSA Config
	private static final int KEYS_SIZE = 10;
	private KeyPair[] _keyPairs;
	
	private GameserversServices _gsServices = null;
	private GameserversServices _gsServicesXml = null;
	
	/**
	 * Return singleton
	 * exit the program if we didn't succeed to load the instance
	 * @return  GameServerManager
	 */
	public static GameServerManager getInstance()
	{
		if (__instance == null)
		{
			try
			{
				__instance = new GameServerManager();
			}
			catch (NoSuchAlgorithmException e)
			{
				_log.fatal("FATAL: Failed loading GameServerManager. Reason: " + e.getMessage(), e);
				System.exit(1);
			}
			catch (InvalidAlgorithmParameterException e)
			{
				_log.fatal("FATAL: Failed loading GameServerManager. Reason: " + e.getMessage(), e);
				System.exit(1);
			}
		}
		return __instance;
	}
	
	/**
	 * Initialize keypairs
	 * Initialize servers list from xml and db
	 * @throws InvalidAlgorithmParameterException
	 * @throws NoSuchAlgorithmException
	 */
	private GameServerManager() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException
	{
		// o Load DAO
		// ---------
		_gsServices = L2Registry.getGameserversServices();
		_gsServicesXml = L2Registry.getGameserversServicesXml();
		
		// o Load Servers
		// --------------
		load();
		
		// o Load RSA keys
		// ---------------
		loadRSAKeys();
	}
	
	/**
	 * Load RSA keys
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidAlgorithmParameterException
	 */
	private void loadRSAKeys() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException
	{
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4);
		keyGen.initialize(spec);
		
		_keyPairs = new KeyPair[KEYS_SIZE];
		for (int i = 0; i < KEYS_SIZE; i++)
		{
			_keyPairs[i] = keyGen.genKeyPair();
		}
		_log.info("GameServerManager: Cached " + _keyPairs.length + " RSA keys for Game Server communication.");
	}
	
	public Map<Integer, GameServerInfo> getRegisteredGameServers()
	{
		return _gameServers;
	}
	
	public GameServerInfo getRegisteredGameServerById(int id)
	{
		return _gameServers.get(id);
	}
	
	public boolean hasRegisteredGameServerOnId(int id)
	{
		return _gameServers.containsKey(id);
	}
	
	public boolean registerWithFirstAvailableId(GameServerInfo gsi)
	{
		// avoid two servers registering with the same "free" id
		synchronized (_gameServers)
		{
			List<Gameservers> serverNames = _gsServicesXml.getAllGameservers();
			for (Gameservers entry : serverNames)
			{
				if (!_gameServers.containsKey(entry.getServerId()))
				{
					_gameServers.put(entry.getServerId(), gsi);
					gsi.setId(entry.getServerId());
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean register(int id, GameServerInfo gsi)
	{
		// avoid two servers registering with the same id
		synchronized (_gameServers)
		{
			if (!_gameServers.containsKey(id))
			{
				_gameServers.put(id, gsi);
				gsi.setId(id);
				return true;
			}
		}
		return false;
	}
	
	public void registerServerOnDB(GameServerInfo gsi)
	{
		this.registerServerOnDB(gsi.getHexId(), gsi.getId(), gsi.getIp());
	}
	
	public void registerServerOnDB(byte[] hexId, int id, String externalHost)
	{
		Gameservers gs = new Gameservers(id, HexUtil.hexToString(hexId), externalHost);
		if (_gsServices.createGameserver(gs) != id)
		{
			throw new IllegalStateException("Unable to persist registered gameserver " + id);
		}
	}
	
	public String getServerNameById(int id)
	{
		return _gsServicesXml.getGameserverName(id);
	}
	
	/**
	 * Load Gameserver from DAO
	 * For each gameserver, instantiate a GameServer, (a container that hold a thread)
	 */
	private void load()
	{
		List<Gameservers> listGs = _gsServices.getAllGameservers();
		GameServerInfo gsi;
		for (Gameservers gsFromDAO : listGs)
		{
			gsi = new GameServerInfo(gsFromDAO.getServerId(), HexUtil.stringToHex(gsFromDAO.getHexid()));
			_gameServers.put(gsFromDAO.getServerId(), gsi);
		}
		_log.info("GameServerManager: Loaded " + listGs.size());
	}
	
	/**
	* 
	* @param id - the server id
	*/
	public void deleteServer(int id)
	{
		_gsServices.deleteGameserver(id);
	}
	
	/**
	 * 
	 * @param id - the server id
	 */
	public void deleteAllServer()
	{
		_gsServices.removeAll();
	}
	
	/**
	 * 
	 * @return
	 */
	public KeyPair getKeyPair()
	{
		return _keyPairs[Rnd.nextInt(10)];
	}
	
	/**
	 * 
	 * @param id
	 * @return
	 */
	public String getServerName(int id)
	{
		return _gsServicesXml.getGameserverName(id);
	}
	
	/**
	 * 
	 * @param id
	 * @return
	 */
	public List<Gameservers> getServers()
	{
		return _gsServicesXml.getAllGameservers();
	}
}
