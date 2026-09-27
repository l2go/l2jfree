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
package com.l2jfree.gameserver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.ItemRequest;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.world.L2World;

class TradeListPrivateStoreCapacityTest
{
	@Test
	@DisplayName("a private-store buy checks the actual item's weight despite the request's missing item ID")
	void buyerWeightIncludesActualSellerItem()
	{
		try (MockedStatic<L2World> worlds = mockStatic(L2World.class))
		{
			Fixture fixture = new Fixture(worlds);
			when(fixture.buyerInventory.validateWeight(100)).thenReturn(false);

			assertThat(fixture.request.getItemId()).isZero();
			assertThat(fixture.store.privateStoreBuy(fixture.buyer, new ItemRequest[] { fixture.request })).isFalse();
			verify(fixture.buyerInventory).validateWeight(100);
			verify(fixture.buyerInventory, never()).validateCapacity(anyInt());
		}
	}

	@Test
	@DisplayName("a private-store buy checks the actual item's slot when the buyer inventory is full")
	void buyerSlotsIncludeActualSellerItem()
	{
		try (MockedStatic<L2World> worlds = mockStatic(L2World.class))
		{
			Fixture fixture = new Fixture(worlds);
			when(fixture.buyerInventory.validateWeight(100)).thenReturn(true);
			when(fixture.buyerInventory.validateCapacity(1)).thenReturn(false);

			assertThat(fixture.store.privateStoreBuy(fixture.buyer, new ItemRequest[] { fixture.request })).isFalse();
			verify(fixture.buyerInventory).validateWeight(100);
			verify(fixture.buyerInventory).validateCapacity(1);
		}
	}

	@Test
	@DisplayName("large requested quantities cannot wrap capacity requirements")
	void capacityRequirementCannotWrap()
	{
		assertThat(TradeList.addCapacityRequirement(0, 2, 100)).isEqualTo(200);
		assertThat(TradeList.addCapacityRequirement(0, Long.MAX_VALUE, 100)).isEqualTo(-1);
		assertThat(TradeList.addCapacityRequirement(Integer.MAX_VALUE, 1, 1)).isEqualTo(-1);
	}

	private static final class Fixture
	{
		private final L2Player buyer = mock(L2Player.class);
		private final PlayerInventory buyerInventory = mock(PlayerInventory.class);
		private final TradeList store;
		private final ItemRequest request = new ItemRequest(42, 1, 10);

		private Fixture(MockedStatic<L2World> worlds)
		{
			L2Player seller = mock(L2Player.class);
			PlayerInventory sellerInventory = mock(PlayerInventory.class);
			L2ItemInstance sellerItem = mock(L2ItemInstance.class);
			L2Item template = mock(L2Item.class);
			L2World world = mock(L2World.class);

			worlds.when(L2World::getInstance).thenReturn(world);
			when(world.getPlayer(17)).thenReturn(seller);
			when(seller.getObjectId()).thenReturn(17);
			when(seller.getInventory()).thenReturn(sellerInventory);
			when(sellerInventory.getItemByObjectId(42)).thenReturn(sellerItem);
			when(seller.checkItemManipulation(42, 1, "transfer")).thenReturn(sellerItem);
			when(seller.checkItemManipulation(42, 1, "sell")).thenReturn(sellerItem);
			when(sellerItem.getObjectId()).thenReturn(42);
			when(sellerItem.getItemId()).thenReturn(5000);
			when(sellerItem.getItem()).thenReturn(template);
			when(sellerItem.getCount()).thenReturn(1L);
			when(sellerItem.isTradeable()).thenReturn(true);
			when(template.getWeight()).thenReturn(100);

			when(buyer.getInventory()).thenReturn(buyerInventory);
			when(buyer.getMaxLoad()).thenReturn(1000);
			when(buyer.getInventoryLimit()).thenReturn(100);

			store = new TradeList(seller);
			assertThat(store.addItem(42, 1, 10)).isNotNull();
		}
	}
}
