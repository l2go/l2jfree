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

/**
 * The amount charged for a community-board bid and later paid to the seller.
 * Callers must not narrow the result to {@code int}: every value above
 * {@link Integer#MAX_VALUE} becomes negative, and {@code reduceAdena} then removes nothing.
 */
final class AuctionBidAmount
{
	private AuctionBidAmount()
	{
	}
	
	/**
	 * @return the amount to store, charge, and pay, or {@code 0} when the bid must be rejected
	 */
	static long transferable(long bidAmount)
	{
		if (bidAmount <= 0)
			return 0L;
		
		return bidAmount;
	}
}
