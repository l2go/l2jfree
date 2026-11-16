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
package com.l2jfree.gameserver.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.Channel;

/** One load owner per session. Disconnect and a second selection drop the first result. */
class CharacterLoadTest
{
	@Test
	void aDisconnectedLoadIsNotEntered()
	{
		L2Client client = client();
		int token = client.beginCharacterLoad();
		client.invalidateCharacterLoad();
		
		assertThat(client.characterLoadOpen(token)).isFalse();
		assertThat(client.claimCharacterLoad(token)).isFalse();
		assertThat(client.getActiveChar()).isNull();
	}
	
	@Test
	void aSecondSelectionReplacesTheFirstAndTheSameSlotCanBeChosenAgain()
	{
		L2Client client = client();
		int first = client.beginCharacterLoad();
		int second = client.beginCharacterLoad();
		
		assertThat(client.claimCharacterLoad(first)).isFalse();
		assertThat(client.claimCharacterLoad(second)).isTrue();
		assertThat(client.claimCharacterLoad(second)).isFalse();
		
		client.abandonCharacterLoad(second);
		int again = client.beginCharacterLoad();
		assertThat(again).isNotZero();
		assertThat(client.claimCharacterLoad(again)).isTrue();
	}
	
	@Test
	void aFailedLoadLetsTheSessionChooseAgain()
	{
		L2Client client = client();
		int failed = client.beginCharacterLoad();
		client.abandonCharacterLoad(failed);
		
		assertThat(client.claimCharacterLoad(failed)).isFalse();
		int next = client.beginCharacterLoad();
		assertThat(client.claimCharacterLoad(next)).isTrue();
	}
	
	@Test
	void aNewSessionOfTheSameAccountDoesNotKeepTheOldLoad()
	{
		L2Client oldSession = client();
		L2Client newSession = client();
		int oldToken = oldSession.beginCharacterLoad();
		oldSession.invalidateCharacterLoad();
		int newToken = newSession.beginCharacterLoad();
		
		assertThat(oldSession.characterLoadOpen(oldToken)).isFalse();
		assertThat(oldSession.claimCharacterLoad(oldToken)).isFalse();
		assertThat(newSession.claimCharacterLoad(newToken)).isTrue();
	}
	
	private static L2Client client()
	{
		Channel channel = mock(Channel.class);
		when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 7777));
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		return new L2Client(server, channel);
	}
}
