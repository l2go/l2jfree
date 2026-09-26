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

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Collection;
import java.util.Map;

import javax.crypto.Cipher;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.l2jfree.Config;
import com.l2jfree.L2Registry;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.beans.FailedLoginAttempt;
import com.l2jfree.loginserver.beans.GameServerInfo;
import com.l2jfree.loginserver.beans.SessionKey;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.loginserver.services.exception.AccountBannedException;
import com.l2jfree.loginserver.services.exception.AccountModificationException;
import com.l2jfree.loginserver.services.exception.AccountWrongPasswordException;
import com.l2jfree.loginserver.services.exception.HackingException;
import com.l2jfree.loginserver.services.exception.IPRestrictedException;
import com.l2jfree.loginserver.services.exception.MaintenanceException;
import com.l2jfree.loginserver.services.exception.MaturityException;
import com.l2jfree.loginserver.status.Status;
import com.l2jfree.loginserver.thread.GameServerThread;
import com.l2jfree.network.ServerStatus;
import com.l2jfree.tools.codec.Base64;
import com.l2jfree.tools.math.ScrambledKeyPair;
import com.l2jfree.tools.random.Rnd;

/**
 * This class handles login on loginserver.
 * It store connection for each account.
 * 
 * The ClientThread use LoginManager to :
 *  - store his connection identifier
 *  - retrieve basic information
 *  - delog an account
 */
public class LoginManager
{
	private static final Log _log = LogFactory.getLog(LoginManager.class);
	private static final Log _logLogin = LogFactory.getLog("login");
	private static final Log _logLoginTries = LogFactory.getLog("login.try");
	private static final Log _logLoginFailed = LogFactory.getLog("login.failed");
	
	private static final class SingletonHolder
	{
		private static final LoginManager INSTANCE = new LoginManager();
	}
	
	public static LoginManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	/** Authed Clients on LoginServer*/
	protected Map<String, L2Client> _loginServerClients = new FastMap<String, L2Client>().setShared(true);
	
	/** Keep trace of login attempt for an inetadress*/
	private Map<InetAddress, FailedLoginAttempt> _hackProtection;
	
	private ScrambledKeyPair[] _keyPairs;
	
	protected byte[][] _blowfishKeys;
	
	private static final int BLOWFISH_KEYS = 20;
	
	private AccountsServices _service = null;
	
	public static enum AuthLoginResult
	{
		INVALID_PASSWORD,
		ACCOUNT_BANNED,
		ALREADY_ON_LS,
		ALREADY_ON_GS,
		AUTH_SUCCESS,
		SYSTEM_ERROR
	}
	
	private FastList<L2Client> _connections;
	
	/**
	 * Private constructor to avoid direct instantiation.
	 * Initialize a key generator.
	 */
	private LoginManager()
	{
		try
		{
			_log.info("LoginManager: initializing.");
			
			_hackProtection = new FastMap<InetAddress, FailedLoginAttempt>();
			
			_keyPairs = new ScrambledKeyPair[10];
			
			_service = (AccountsServices)L2Registry.getBean("AccountsServices");
			
			_connections = new FastList<L2Client>();
			
			KeyPairGenerator keygen = null;
			
			try
			{
				keygen = KeyPairGenerator.getInstance("RSA");
				RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
				keygen.initialize(spec);
			}
			catch (GeneralSecurityException e)
			{
				_log.fatal("Error in RSA setup:", e);
				_log.info("Server shutting down now");
				System.exit(1);
				return;
			}
			
			//generate the initial set of keys
			for (int i = 0; i < 10; i++)
			{
				_keyPairs[i] = new ScrambledKeyPair(keygen.generateKeyPair());
			}
			_log.info("LoginManager: Cached 10 KeyPairs for RSA communication");
			
			testCipher((RSAPrivateKey)_keyPairs[0].getPair().getPrivate());
			
			// Store keys for blowfish communication
			generateBlowFishKeys();
		}
		catch (GeneralSecurityException e)
		{
			_log.fatal("FATAL: Failed initializing LoginManager. Reason: " + e.getMessage(), e);
			System.exit(1);
		}
		
	}
	
	/**
	 * This is mostly to force the initialization of the Crypto Implementation, avoiding it being done on runtime when its first needed.<BR>
	 * In short it avoids the worst-case execution time on runtime by doing it on loading.
	 * @param key Any private RSA Key just for testing purposes.
	 * @throws GeneralSecurityException if a underlying exception was thrown by the Cipher
	 */
	private void testCipher(RSAPrivateKey key) throws GeneralSecurityException
	{
		// avoid worst-case execution, KenM
		Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
		rsaCipher.init(Cipher.DECRYPT_MODE, key);
	}
	
	private void generateBlowFishKeys()
	{
		_blowfishKeys = new byte[BLOWFISH_KEYS][16];
		
		for (int i = 0; i < BLOWFISH_KEYS; i++)
		{
			for (int j = 0; j < _blowfishKeys[i].length; j++)
			{
				_blowfishKeys[i][j] = (byte)(Rnd.nextInt(255) + 1);
			}
		}
		_log.info("Stored " + _blowfishKeys.length + " keys for Blowfish communication");
	}
	
	/**
	 * @return Returns a random key
	 */
	public byte[] getBlowfishKey()
	{
		return _blowfishKeys[(int)(Math.random() * BLOWFISH_KEYS)];
	}
	
	/**
	 * 
	 * @param account
	 * @param client
	 * @return a SessionKey
	 */
	public SessionKey assignSessionKeyToLogin(String account, L2Client client)
	{
		SessionKey key;
		
		key =
				new SessionKey(Rnd.nextInt(Integer.MAX_VALUE), Rnd.nextInt(Integer.MAX_VALUE),
						Rnd.nextInt(Integer.MAX_VALUE), Rnd.nextInt(Integer.MAX_VALUE));
		_loginServerClients.put(account, client);
		return key;
	}
	
	public void removeAuthedLoginClient(String account)
	{
		_loginServerClients.remove(account);
	}
	
	public boolean isAccountInLoginServer(String account)
	{
		return _loginServerClients.containsKey(account);
	}
	
	public SessionKey assignSessionKeyToClient(String account, L2Client client)
	{
		SessionKey key;
		
		key =
				new SessionKey(Rnd.nextInt(Integer.MAX_VALUE), Rnd.nextInt(Integer.MAX_VALUE),
						Rnd.nextInt(Integer.MAX_VALUE), Rnd.nextInt(Integer.MAX_VALUE));
		_loginServerClients.put(account, client);
		return key;
	}
	
	public GameServerInfo getAccountOnGameServer(String account)
	{
		Collection<GameServerInfo> serverList = GameServerManager.getInstance().getRegisteredGameServers().values();
		for (GameServerInfo gsi : serverList)
		{
			GameServerThread gst = gsi.getGameServerThread();
			if (gst != null && gst.hasAccountOnGameServer(account))
			{
				return gsi;
			}
		}
		return null;
	}
	
	/**
	 * Publishes {@code account} on {@code gameServerAccounts} and drops the login-server session.
	 * Callers must not send {@code PlayerAuthResponse} until this returns.
	 *
	 * @return {@code false} when the account is already on a game server
	 */
	public boolean beginPlaySession(String account, java.util.Set<String> gameServerAccounts)
	{
		synchronized (_loginServerClients)
		{
			if (!PlaySessionAdmission.reserveGameServer(isAccountInAnyGameServer(account)))
				return false;
			
			gameServerAccounts.add(account);
			_loginServerClients.remove(account);
			return true;
		}
	}
	
	public void confirmPlaySession(String account, java.util.Set<String> gameServerAccounts)
	{
		synchronized (_loginServerClients)
		{
			gameServerAccounts.add(account);
		}
	}
	
	public void endPlaySession(String account, java.util.Set<String> gameServerAccounts)
	{
		synchronized (_loginServerClients)
		{
			gameServerAccounts.remove(account);
		}
	}
	
	public boolean isAccountInAnyGameServer(String account)
	{
		Collection<GameServerInfo> serverList = GameServerManager.getInstance().getRegisteredGameServers().values();
		for (GameServerInfo gsi : serverList)
		{
			GameServerThread gst = gsi.getGameServerThread();
			if (gst != null && gst.hasAccountOnGameServer(account))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 
	 * @param account
	 * @param password
	 * @param client
	 * @return true if validation succeed or false if we have technical problems
	 * @throws HackingException if we detect a hacking attempt
	 * @throws AccountBannedException if the use was banned
	 * @throws AccountWrongPasswordException if the password was wrong
	 */
	public AuthLoginResult tryAuthLogin(String account, String password, L2Client client)
			throws AccountBannedException, AccountWrongPasswordException, IPRestrictedException
	{
		AuthLoginResult ret = AuthLoginResult.INVALID_PASSWORD;
		
		try
		{
			// check auth
			if (loginValid(account, password, client))
			{
				// The game-server check and the login-server map share one lock with beginPlaySession.
				synchronized (_loginServerClients)
				{
					boolean onGameServer = isAccountInAnyGameServer(account);
					boolean onLoginServer = _loginServerClients.containsKey(account);
					if (!PlaySessionAdmission.mayAuthenticate(onGameServer, onLoginServer))
					{
						ret = onGameServer ? AuthLoginResult.ALREADY_ON_GS : AuthLoginResult.ALREADY_ON_LS;
					}
					else
					{
						_loginServerClients.put(account, client);
						ret = AuthLoginResult.AUTH_SUCCESS;
					}
				}
				if (ret == AuthLoginResult.AUTH_SUCCESS || ret == AuthLoginResult.ALREADY_ON_LS)
				{
					Accounts acc = _service.getAccountById(account);
					// keep access level in the L2LoginClient
					client.setAccessLevel(acc.getAccessLevel());
					// keep last server choice
					client.setLastServerId(acc.getLastServerId());
					client.setAge(acc.getBirthYear(), acc.getBirthMonth(), acc.getBirthDay());
				}
			}
		}
		catch (NoSuchAlgorithmException e)
		{
			_log.error("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		catch (UnsupportedEncodingException e)
		{
			_log.error("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		catch (AccountModificationException e)
		{
			_log.warn("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		return ret;
	}
	
	public L2Client getAuthedClient(String account)
	{
		return _loginServerClients.get(account);
	}
	
	public SessionKey getKeyForAccount(String account)
	{
		L2Client client = _loginServerClients.get(account);
		if (client != null)
		{
			return client.getSessionKey();
		}
		return null;
	}
	
	public String getHostForAccount(String account)
	{
		L2Client client = getAuthedClient(account);
		
		return client != null ? client.getIp() : "-1";
	}
	
	/**
	 * Login is possible if number of player < max player for this GS
	 * and the status of the GS != STATUS_GM_ONLY
	 * All those conditions are not applied if the player is a GM
	 * @return
	 */
	public boolean isLoginPossible(int age, int access, int serverId) throws MaintenanceException, MaturityException
	{
		GameServerInfo gsi = GameServerManager.getInstance().getRegisteredGameServerById(serverId);
		if (gsi != null && gsi.isAuthed())
		{
			if (gsi.getStatus() == ServerStatus.STATUS_GM_ONLY && access < Config.GM_MIN)
				throw MaintenanceException.MAINTENANCE;
			//Some accounts, like GM ones, can always connect
			if (age < gsi.getAgeLimitation())
				throw new MaturityException(age, gsi.getAgeLimitation());
			return (gsi.getCurrentPlayerCount() < gsi.getMaxPlayers() || access >= Config.GM_MIN);
		}
		else
			throw MaintenanceException.MAINTENANCE;
	}
	
	/**
	 * 
	 * @param ServerID
	 * @return online player count for a server
	 */
	public int getOnlinePlayerCount(int serverId)
	{
		GameServerInfo gsi = GameServerManager.getInstance().getRegisteredGameServerById(serverId);
		if (gsi != null && gsi.isAuthed())
		{
			return gsi.getCurrentPlayerCount();
		}
		return 0;
	}
	
	/***
	 * 
	 * @param ServerID
	 * @return max allowed online player for a server
	 */
	public int getMaxAllowedOnlinePlayers(int id)
	{
		GameServerInfo gsi = GameServerManager.getInstance().getRegisteredGameServerById(id);
		if (gsi != null)
		{
			return gsi.getMaxPlayers();
		}
		return 0;
	}
	
	/**
	 * 
	 * @param user
	 * @param banLevel
	 */
	public void setAccountAccessLevel(String account, int banLevel)
	{
		try
		{
			_service.changeAccountLevel(account, banLevel);
		}
		catch (AccountModificationException e)
		{
			_log.error("Could not set accessLevel for user: " + account, e);
		}
	}
	
	/**
	 * 
	 * @param user
	 * @param lastServerId
	 */
	public void setAccountLastServerId(String account, int lastServerId)
	{
		try
		{
			Accounts acc = _service.getAccountById(account);
			acc.setLastServerId(lastServerId);
			_service.addOrUpdateAccount(acc);
		}
		catch (AccountModificationException e)
		{
			_log.error("Could not set last server for user: " + account, e);
		}
	}
	
	/**
	 * 
	 * @param user
	 * @return true if a user is a GM account
	 */
	public boolean isGM(Accounts acc)
	{
		if (acc != null)
			return acc.getAccessLevel() >= Config.GM_MIN;
		else
			return false;
	}
	
	/**
	 * 
	 * @param user
	 * @return account if exist, null if not
	 */
	public Accounts getAccount(String user)
	{
		return _service.getAccountById(user);
	}
	
	/**
	 * <p>This method returns one of the 10 {@link ScrambledKeyPair}.</p>
	 * <p>One of them the renewed asynchronously using a {@link UpdateKeyPairTask} if necessary.</p>
	 * @return a scrambled keypair
	 */
	public ScrambledKeyPair getScrambledRSAKeyPair()
	{
		return _keyPairs[Rnd.nextInt(10)];
	}
	
	/**
	 * user name is not case sensitive any more
	 * @param user
	 * @param password
	 * @param address
	 * @return true if all operations succeed
	 * @throws NoSuchAlgorithmException if SHA is not supported
	 * @throws UnsupportedEncodingException if UTF-8 is not supported
	 * @throws AccountModificationException  if we were unable to modify the account
	 * @throws AccountBannedException  if account is banned
	 * @throws AccountWrongPasswordException if the password is wrong
	 */
	public boolean loginValid(String user, String password, L2Client client) throws NoSuchAlgorithmException,
			UnsupportedEncodingException, AccountModificationException, AccountBannedException,
			AccountWrongPasswordException, IPRestrictedException
	{
		InetAddress address = client.getInetAddress();
		if (BanManager.getInstance().isRestrictedAddress(address))
			throw new IPRestrictedException();
		else if (BanManager.getInstance().isBannedAddress(address))
			throw new IPRestrictedException(BanManager.getInstance().getBanExpiry(address));
		// player disconnected meanwhile
		if (address == null)
			return false;
		return loginValid(user, password, address);
	}
	
	/**
	 * user name is not case sensitive any more
	 * @param user
	 * @param password
	 * @param address
	 * @return true if all operations succeed
	 * @throws NoSuchAlgorithmException if SHA is not supported
	 * @throws UnsupportedEncodingException if UTF-8 is not supported
	 * @throws AccountModificationException  if we were unable to modify the account
	 * @throws AccountBannedException  if account is banned
	 * @throws AccountWrongPasswordException if the password is wrong
	 */
	public boolean loginValid(String user, String password, InetAddress address) throws NoSuchAlgorithmException,
			UnsupportedEncodingException, AccountModificationException, AccountBannedException,
			AccountWrongPasswordException
	{
		_logLoginTries.info("User trying to connect  '" + user + "' "
				+ (address == null ? "null" : address.getHostAddress()));
		
		// o Convert password in utf8 byte array
		// ----------------------------------
		MessageDigest md = MessageDigest.getInstance("SHA");
		byte[] raw = password.getBytes("UTF-8");
		byte[] hash = md.digest(raw);
		
		// o find Account
		// -------------
		Accounts acc = _service.getAccountById(user);
		
		// If account is not found
		// try to create it if AUTO_CREATE_ACCOUNTS is activated
		// or return false
		// ------------------------------------------------------
		if (acc == null)
		{
			if (handleAccountNotFound(user, address, hash))
				return true;
			else
				throw new AccountWrongPasswordException(user);
		}
		// If account is found
		// check ban state
		// check password and update last ip/last active
		// ---------------------------------------------
		else
		{
			// check the account is not ban
			if (acc.getAccessLevel() < 0)
			{
				throw new AccountBannedException(user);
			}
			try
			{
				checkPassword(hash, acc);
				acc.setLastactive(new BigDecimal(System.currentTimeMillis()));
				if (address != null)
				{
					acc.setLastIp(address.getHostAddress());
				}
				_service.addOrUpdateAccount(acc);
				handleGoodLogin(user, address);
			}
			// If password are different
			// -------------------------
			catch (AccountWrongPasswordException e)
			{
				handleBadLogin(user, password, address);
				throw e;
			}
		}
		
		return true;
	}
	
	/**
	 * @param user
	 * @param address
	 */
	private void handleGoodLogin(String user, InetAddress address)
	{
		// for long running servers, this should prevent blocking
		// of users that mistype their passwords once every day :)
		if (address != null)
		{
			_hackProtection.remove(address);
		}
		if (_logLogin.isDebugEnabled())
			_logLogin.debug("login successfull for '" + user + "' "
					+ (address == null ? "null" : address.getHostAddress()));
	}
	
	/**
	 * 
	 * If login are different, increment hackProtection counter. It's maybe a hacking attempt
	 * 
	 * @param user
	 * @param password
	 * @param address
	 */
	private void handleBadLogin(String user, String password, InetAddress address)
	{
		_logLoginFailed.info("login failed for user : '" + user + "' "
				+ (address == null ? "null" : address.getHostAddress()));
		
		// In special case, adress is null, so this protection is useless
		if (address != null)
		{
			FailedLoginAttempt failedAttempt = _hackProtection.get(address);
			int failedCount;
			if (failedAttempt == null)
			{
				_hackProtection.put(address, new FailedLoginAttempt(address, password));
				failedCount = 1;
			}
			else
			{
				failedAttempt.increaseCounter(password);
				failedCount = failedAttempt.getCount();
			}
			
			if (failedCount >= Config.LOGIN_TRY_BEFORE_BAN)
			{
				_log.info("Temporary auto-ban for " + address.getHostAddress() + " (" + Config.LOGIN_BLOCK_AFTER_BAN
						+ " seconds, " + failedCount + " login tries)");
				BanManager.getInstance().addBanForAddress(address, Config.LOGIN_BLOCK_AFTER_BAN * 1000);
			}
		}
	}
	
	/**
	 * @param hash
	 * @param acc
	 * @throws AccountWrongPasswordException if password is wrong
	 */
	private void checkPassword(byte[] hash, Accounts acc) throws AccountWrongPasswordException
	{
		if (_log.isDebugEnabled())
			_log.debug("account exists");
		
		byte[] expected = Base64.decode(acc.getPassword());
		
		for (int i = 0; i < expected.length; i++)
		{
			if (hash[i] != expected[i])
			{
				throw new AccountWrongPasswordException(acc.getLogin());
			}
		}
	}
	
	/**
	 * @param user
	 * @param address
	 * @param hash
	 * @return true if accounts was successfully created or false is AUTO_CREATE_ACCOUNTS = false or creation failed
	 * @throws AccountModificationException
	 */
	private boolean handleAccountNotFound(String user, InetAddress address, byte[] hash)
			throws AccountModificationException
	{
		Accounts acc;
		if (Config.AUTO_CREATE_ACCOUNTS)
		{
			if ((user.length() >= 2) && (user.length() <= 14))
			{
				acc =
						new Accounts(user, Base64.encodeBytes(hash), new BigDecimal(System.currentTimeMillis()), 0, 0,
								1900, 1, 1, (address == null ? "null" : address.getHostAddress()));
				_service.addOrUpdateAccount(acc);
				
				_logLogin.info("Account created: " + user);
				_log.info("An account was newly created: " + user);
				Status.tryBroadcast("Account created for player " + user);
				
				return true;
				
			}
			_logLogin.warn("Invalid username creation/use attempt: " + user);
			return false;
		}
		_logLogin.warn("No such account exists: " + user);
		return false;
	}
	
	public void addConnection(L2Client lc)
	{
		_connections.add(lc);
	}
	
	public void remConnection(L2Client lc)
	{
		_connections.remove(lc);
	}
}
