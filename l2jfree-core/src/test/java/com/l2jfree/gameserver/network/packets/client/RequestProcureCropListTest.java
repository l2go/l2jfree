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
package com.l2jfree.gameserver.network.packets.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

class RequestProcureCropListTest
{
	@Test
	@DisplayName("a crop claim cannot consume an inventory object with another item ID")
	void cropClaimMustMatchInventoryItem()
	{
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getItemId()).thenReturn(5000);
		when(item.getCount()).thenReturn(10L);

		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5001, 5)).isFalse();
		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5000, 11)).isFalse();
		assertThat(RequestProcureCropList.matchesClaimedCrop(item, 5000, 10)).isTrue();
		assertThat(RequestProcureCropList.matchesClaimedCrop(null, 5000, 5)).isFalse();
	}
}
