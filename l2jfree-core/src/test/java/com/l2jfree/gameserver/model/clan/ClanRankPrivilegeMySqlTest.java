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
package com.l2jfree.gameserver.model.clan;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("integration")
@Testcontainers
class ClanRankPrivilegeMySqlTest
{
	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
	
	@Test
	void mySql8AcceptsTheQuotedPrivilegeStatements() throws Exception
	{
		try (Connection con = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
				Statement ddl = con.createStatement())
		{
			ddl.execute("CREATE TABLE clan_privs ("
					+ "clan_id INT NOT NULL, `rank` INT NOT NULL, party INT NOT NULL, privilleges INT NOT NULL, "
					+ "PRIMARY KEY (clan_id, `rank`, party))");
			
			try (PreparedStatement insert = con.prepareStatement(L2Clan.INSERT_RANK_PRIVS_SQL))
			{
				insert.setInt(1, 1);
				insert.setInt(2, 3);
				insert.setInt(3, 0);
				insert.setInt(4, 7);
				assertThat(insert.executeUpdate()).isEqualTo(1);
			}
			
			try (PreparedStatement update = con.prepareStatement(L2Clan.UPDATE_RANK_PRIVS_SQL))
			{
				update.setInt(1, 1);
				update.setInt(2, 3);
				update.setInt(3, 0);
				update.setInt(4, 9);
				update.setInt(5, 9);
				assertThat(update.executeUpdate()).isPositive();
			}
			
			try (PreparedStatement select = con.prepareStatement(L2Clan.RESTORE_RANK_PRIVS_SQL))
			{
				select.setInt(1, 1);
				try (ResultSet privileges = select.executeQuery())
				{
					assertThat(privileges.next()).isTrue();
					assertThat(privileges.getInt("rank")).isEqualTo(3);
					assertThat(privileges.getInt("privilleges")).isEqualTo(9);
				}
			}
		}
	}
}
