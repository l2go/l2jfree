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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.mmocore.network.ReceivablePacket;

class RequestDropItemTest
{
	@Test
	@DisplayName("an item manipulation failure rejects the drop without accessing or dropping an item")
	void missingItemFailsTheRequest() throws Exception
	{
		L2Client client = mock(L2Client.class);
		L2Player player = mock(L2Player.class);
		when(client.getActiveChar()).thenReturn(player);
		when(player.isDead()).thenReturn(false);
		when(player.isGM()).thenReturn(true);
		when(player.checkItemManipulation(123, 1, "Drop")).thenReturn(null);

		TestableRequestDropItem packet = new TestableRequestDropItem();
		setField(ReceivablePacket.class, packet, "_client", client);
		setField(RequestDropItem.class, packet, "_objectId", 123);
		setField(RequestDropItem.class, packet, "_count", 1L);

		assertDoesNotThrow(packet::execute);
		verify(client).sendPacket(ActionFailed.STATIC_PACKET);
		verify(player, never()).dropItem("Drop", 123, 1, 0, 0, 0, player, false);
	}

	private static void setField(final Class<?> declaringClass, final Object target, final String name,
			final Object value) throws Exception
	{
		Field field = declaringClass.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static final class TestableRequestDropItem extends RequestDropItem
	{
		void execute()
		{
			runImpl();
		}
	}
}
