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
package com.l2jfree.gameserver;

import java.util.Map;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.ServerStatusAttributes;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.AuthLoginGuard;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
import com.l2jfree.gameserver.network.L2ClientSelectorThread;
import com.l2jfree.gameserver.network.WorldAddress;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;
import com.l2jfree.gameserver.network.packets.server.LoginFail;

/**
 * The link between the world and the login module of the same process. It implements {@link WorldPort}, which the
 * login module calls, and it calls the {@link LoginPort} that {@link #connect(LoginPort)} hands over.
 * <p>
 * It keeps the bookkeeping of the clients: the clients that wait for an admission and the accounts that are in
 * the world. The login module is never called while the account lock is held.
 */
public final class LoginLink implements WorldPort
{
	private static final Logger _log = LoggerFactory.getLogger(LoginLink.class);

	private static final class SingletonHolder
	{
		private static final LoginLink INSTANCE = new LoginLink();
	}

	public static LoginLink getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static final class WaitingClient
	{
		public final L2Client gameClient;
		public final SessionKey session;

		public WaitingClient(L2Client client, SessionKey key)
		{
			gameClient = client;
			session = key;
		}
	}

	private final Map<String, WaitingClient> _waitingClients = new FastMap<String, WaitingClient>().setShared(true);
	private final Map<String, L2Client> _accountsInGameServer = new FastMap<String, L2Client>().setShared(true);
	private final Object _accountLock = new Object();

	private volatile LoginPort _login;
	private volatile WorldAddress _address;
	private volatile ServerStatus _status = ServerStatus.STATUS_AUTO;

	private LoginLink()
	{
	}

	/**
	 * Hands the login module to the world. It must be called once, before the world port opens.
	 *
	 * @param login the port of the login module
	 */
	public void connect(LoginPort login)
	{
		if (login == null)
			throw new IllegalArgumentException("login");

		synchronized (this)
		{
			if (_login != null)
				throw new IllegalStateException("The world is already connected to a login module");

			_address = new WorldAddress(WorldAddress.netConfig(Config.EXTERNAL_HOSTNAME, Config.INTERNAL_HOSTNAME,
					Config.SUBNETWORKS));
			_status = Config.SERVER_GMONLY ? ServerStatus.STATUS_GM_ONLY : ServerStatus.STATUS_AUTO;
			_login = login;
		}

		_log.info("Connected to the login module, world status is {}", _status);
	}

	private LoginPort login()
	{
		LoginPort login = _login;
		if (login == null)
			throw new IllegalStateException("The world is not connected to a login module");

		return login;
	}

	// ---------------------------------------------------------------------------------------------
	// calls from the world to the login module
	// ---------------------------------------------------------------------------------------------

	/**
	 * Claims the account for the client and asks the login module to admit it. The answer is applied before the
	 * method returns.
	 *
	 * @return false when the account is already waiting or in the world, true when the claim was accepted
	 */
	public boolean addWaitingClientAndSendRequest(String acc, L2Client client, SessionKey key)
	{
		final LoginPort login = login();

		synchronized (_accountLock)
		{
			if (!AuthLoginGuard.canClaim(_waitingClients.containsKey(acc), _accountsInGameServer.containsKey(acc)))
				return false;

			client.setAccountName(acc);
			_waitingClients.put(acc, new WaitingClient(client, key));
		}

		AdmissionResult result;
		try
		{
			result = login.admit(acc, key);
		}
		catch (RuntimeException e)
		{
			_log.warn("The login module failed to answer the admission of " + acc, e);
			result = AdmissionResult.refused();
		}

		boolean claimed = false;
		L2Client previous = null;
		synchronized (_accountLock)
		{
			WaitingClient waiting = _waitingClients.get(acc);
			if (waiting != null && waiting.gameClient == client)
			{
				_waitingClients.remove(acc);
				claimed = true;

				if (result.admitted())
					previous = _accountsInGameServer.put(acc, client);
			}
		}

		if (!claimed)
		{
			// the client went away while the login module decided: the admission must not outlive it
			if (result.admitted())
				leave(login, acc);

			return true;
		}

		if (result.admitted())
		{
			client.setState(GameClientState.AUTHED);
			client.setSessionId(key);
			client.setHostAddress(result.clientHost());

			// executing the sql query on the thread pool
			final L2Client target = client;
			client.getPacketQueue().execute(new Runnable() {
				@Override
				public void run()
				{
					target.sendPacket(new CharSelectionInfo(target));
				}
			});

			if (previous != null && previous != client)
				previous.closeNow();
		}
		else
		{
			_log.warn("session key is not correct. closing connection");
			client.sendPacket(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
			client.closeNow();
		}

		return true;
	}

	public void sendLogout(String account, L2Client client)
	{
		if (account == null || account.isEmpty())
			return;

		boolean notifyLogin = true;
		synchronized (_accountLock)
		{
			WaitingClient waiting = _waitingClients.get(account);
			if (waiting != null && waiting.gameClient == client)
				_waitingClients.remove(account);

			L2Client current = _accountsInGameServer.get(account);
			if (current == client)
				_accountsInGameServer.remove(account);
			else if (current != null || (waiting != null && waiting.gameClient != client))
				notifyLogin = false;
		}

		if (notifyLogin)
			leave(login(), account);
	}

	public void sendAccessLevel(String account, int level)
	{
		final LoginPort login = login();
		try
		{
			login.changeAccessLevel(account, level);
		}
		catch (RuntimeException e)
		{
			_log.warn("Access level of " + account + " could not be changed", e);
		}
	}

	private static void leave(LoginPort login, String account)
	{
		try
		{
			login.leave(account);
		}
		catch (RuntimeException e)
		{
			_log.warn("The login module failed to close the session of " + account, e);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// WorldPort: calls from the login module to the world
	// ---------------------------------------------------------------------------------------------

	@Override
	public WorldStatus status()
	{
		return new WorldStatus(Config.SERVER_ID, _status, Config.PORT_GAME, _accountsInGameServer.size(),
				Config.MAXIMUM_ONLINE_USERS, Config.SERVER_AGE_LIM, Config.SERVER_PVP, Config.SERVER_LIST_CLOCK,
				Config.SERVER_LIST_BRACKET, Config.SERVER_LIST_TESTSERVER, Config.SERVER_BIT_3, Config.SERVER_BIT_1);
	}

	@Override
	public String addressFor(String clientIp)
	{
		WorldAddress address = _address;
		if (address == null)
			throw new IllegalStateException("The world is not connected to a login module");

		return address.addressFor(clientIp);
	}

	@Override
	public void kick(String account)
	{
		L2Client client = _accountsInGameServer.get(account);

		if (client != null)
			client.closeNow();

		WaitingClient wc = _waitingClients.get(account);

		if (wc != null)
			wc.gameClient.closeNow();

		L2Player.disconnectIfOnline(account);
	}

	@Override
	public void expect(String clientIp)
	{
		L2ClientSelectorThread.getInstance().legalize(clientIp);
	}

	// ---------------------------------------------------------------------------------------------
	// server status
	// ---------------------------------------------------------------------------------------------

	public ServerStatus getServerStatus()
	{
		return _status;
	}

	public void setServerStatus(int status)
	{
		changeAttribute(ServerStatusAttributes.SERVER_LIST_STATUS, status);
	}

	public void setServerStatusDown()
	{
		setServerStatus(ServerStatus.STATUS_DOWN.ordinal());
	}

	public int getMaxPlayer()
	{
		return Config.MAXIMUM_ONLINE_USERS;
	}

	public void setMaxPlayers(int maxPlayer)
	{
		changeAttribute(ServerStatusAttributes.SERVER_LIST_MAX_PLAYERS, maxPlayer);
	}

	public void changeAttribute(int attr, int value)
	{
		changeAttribute(ServerStatusAttributes.valueOf(attr), value);
	}

	/**
	 * Changes an attribute of the server list entry. The login module reads the result through {@link #status()}.
	 */
	public void changeAttribute(ServerStatusAttributes attr, int value)
	{
		switch (attr)
		{
			case SERVER_LIST_STATUS:
				_status = ServerStatus.valueOf(value);
				switch (_status)
				{
					case STATUS_DOWN:
						if (!Shutdown.isInProgress())
							kickPlayers();
						break;
					default:
						break;
				}
				break;
			case SERVER_LIST_UNK:
				Config.SERVER_BIT_1 = (value > 0);
				break;
			case SERVER_LIST_CLOCK:
				Config.SERVER_LIST_CLOCK = (value > 0);
				break;
			case SERVER_LIST_HIDE_NAME:
				Config.SERVER_BIT_3 = (value > 0);
				break;
			case TEST_SERVER:
				Config.SERVER_LIST_TESTSERVER = (value > 0);
				break;
			case SERVER_LIST_BRACKETS:
				Config.SERVER_LIST_BRACKET = (value > 0);
				break;
			case SERVER_LIST_MAX_PLAYERS:
				Config.MAXIMUM_ONLINE_USERS = value;
				break;
			case SERVER_LIST_PVP:
				Config.SERVER_PVP = (value > 0);
				break;
			case SERVER_AGE_LIMITATION:
				Config.SERVER_AGE_LIM = value;
				break;
			case NONE:
			default:
				return;
		}
	}

	private void kickPlayers()
	{
		int counter = 0;
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (player.isGM())
				continue;

			try
			{
				new Disconnection(player).defaultSequence(true);
				counter++;
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
		}

		_log.info(counter + " players were auto-kicked.");
	}
}
