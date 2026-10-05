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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClanRankPrivilegeSqlTest
{
	@Test
	@DisplayName("clan privilege statements quote the reserved word rank")
	void statementsQuoteRank()
	{
		assertThat(new String[] {
				L2Clan.RESTORE_RANK_PRIVS_SQL,
				L2Clan.UPDATE_RANK_PRIVS_SQL,
				L2Clan.INSERT_RANK_PRIVS_SQL
		}).allSatisfy(sql -> assertThat(sql).contains("`rank`"));
	}
}
