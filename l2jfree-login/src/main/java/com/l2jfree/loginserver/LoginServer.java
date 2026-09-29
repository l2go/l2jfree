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
package com.l2jfree.loginserver;

import java.net.InetAddress;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.L2Registry;
import com.l2jfree.loginserver.manager.BanManager;
import com.l2jfree.loginserver.manager.GameServerManager;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.L2ClientSelectorThread;
import com.l2jfree.loginserver.status.Status;
import com.l2jfree.loginserver.thread.GameServerListener;

public final class LoginServer extends L2AutoInitialization
{
	public static void main(String[] args) throws Throwable
	{
		// Initialize config
		// ------------------
		Config.load();
		
		// Initialize JDBC services
		// ------------------------
		L2Registry.loadRegistry();
		
		// o Initialize LoginManager
		// -------------------------
		LoginManager.getInstance();
		
		// o Initialize GameServer Manager
		// ------------------------------
		GameServerManager.getInstance();
		
		// o Initialize ban list
		// ----------------------
		BanManager.getInstance();
		
		// o Initialize SelectorThread
		// ----------------------------
		L2ClientSelectorThread.getInstance();
		
		// o Initialize GS listener
		// ----------------------------
		GameServerListener.getInstance();
		
		_log.info("Listening for GameServers on " + Config.LOGIN_HOSTNAME + ":" + Config.LOGIN_PORT);
		
		// o Start status telnet server
		// --------------------------
		if (Config.IS_TELNET_ENABLED)
			Status.initInstance();
		else
			_log.info("Telnet server is currently disabled.");
		
		System.gc();
		
		// o Start the server
		// ------------------
		
		L2ClientSelectorThread.getInstance().openServerSocket(InetAddress.getByName(Config.LOGIN_SERVER_HOSTNAME),
				Config.LOGIN_SERVER_PORT);
		L2ClientSelectorThread.getInstance().start();
		
		_log.info("Login Server ready on " + Config.LOGIN_SERVER_HOSTNAME + ":" + Config.LOGIN_SERVER_PORT);
	}
}
