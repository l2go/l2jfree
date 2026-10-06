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
package com.l2jfree.platform;

import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.gameserver.GameServer;
import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.LoginModule;

/**
 * Starts the login module and the world module in one process (ADR-0003).
 * <p>
 * The order is fixed. The login module prepares itself and opens no port. The world loads everything it needs and
 * opens its port, and it is handed the login module through the contract. Only then the login module opens its
 * port, so a client that reaches the login finds a world. A failure in any step ends the process.
 * <p>
 * The class extends {@link L2AutoInitialization} for its static initializer, which sets up logging, the
 * uncaught-exception handler and the class path check once, as the two former entry points did.
 */
public final class Platform extends L2AutoInitialization
{
	private Platform()
	{
	}
	
	public static void main(String[] args) throws Exception
	{
		final LoginModule login = LoginModule.prepare();
		final WorldPort world = GameServer.start(login);
		login.start(world);
		
		LoggerFactory.getLogger(Platform.class).info("Platform ready: login {}:{}, world {}:{}",
				LoginConfig.LOGIN_SERVER_HOSTNAME, LoginConfig.LOGIN_SERVER_PORT, Config.GAMESERVER_HOSTNAME,
				world.status().port());
	}
}
