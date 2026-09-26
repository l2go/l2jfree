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
package com.l2jfree.gameserver.communitybbs.Manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionBidAmountTest
{
	@Test
	@DisplayName("a bid above Integer.MAX_VALUE stays the long that will be charged and paid")
	void bidAboveIntegerMaxIsNotNarrowed()
	{
		long bid = 3_000_000_000L;
		
		assertThat((int)bid).isNegative();
		assertThat(AuctionBidAmount.transferable(bid)).isEqualTo(bid);
	}
	
	@Test
	@DisplayName("a non-positive bid is rejected before it is stored")
	void nonPositiveBidIsRejected()
	{
		assertThat(AuctionBidAmount.transferable(0L)).isZero();
		assertThat(AuctionBidAmount.transferable(-1L)).isZero();
	}
}
