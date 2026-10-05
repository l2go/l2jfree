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
package com.l2jfree.loginserver.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;
import com.zaxxer.hikari.HikariConfig;

class LoginPoolBoundsTest
{
	private String driver;
	private String url;
	private String login;
	private String password;
	private int maximum;
	private int idle;
	
	@BeforeEach
	void rememberPoolSettings()
	{
		driver = Config.DATABASE_DRIVER;
		url = Config.DATABASE_URL;
		login = Config.DATABASE_LOGIN;
		password = Config.DATABASE_PASSWORD;
		maximum = Config.DATABASE_MAX_CONNECTIONS;
		idle = Config.DATABASE_MIN_IDLE_CONNECTIONS;
		Config.DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";
		Config.DATABASE_URL = "jdbc:mysql://127.0.0.1/l2jfree_test";
		Config.DATABASE_LOGIN = "test";
		Config.DATABASE_PASSWORD = "test";
	}
	
	@AfterEach
	void restorePoolSettings()
	{
		Config.DATABASE_DRIVER = driver;
		Config.DATABASE_URL = url;
		Config.DATABASE_LOGIN = login;
		Config.DATABASE_PASSWORD = password;
		Config.DATABASE_MAX_CONNECTIONS = maximum;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = idle;
	}
	
	@Test
	@DisplayName("the login pool keeps at least one connection and caps idle at that maximum")
	void maximumHasAFloorOfOne()
	{
		Config.DATABASE_MAX_CONNECTIONS = 0;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = 5;
		
		HikariConfig pool = LoginDataSource.createPoolConfig();
		
		assertThat(pool.getMaximumPoolSize()).isEqualTo(1);
		assertThat(pool.getMinimumIdle()).isEqualTo(1);
	}
	
	@Test
	@DisplayName("a negative login idle floor becomes zero")
	void negativeIdleBecomesZero()
	{
		Config.DATABASE_MAX_CONNECTIONS = 8;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = -1;
		
		HikariConfig pool = LoginDataSource.createPoolConfig();
		
		assertThat(pool.getMaximumPoolSize()).isEqualTo(8);
		assertThat(pool.getMinimumIdle()).isZero();
	}
}
