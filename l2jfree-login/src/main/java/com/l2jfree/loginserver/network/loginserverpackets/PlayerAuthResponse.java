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
package com.l2jfree.loginserver.network.loginserverpackets;

import com.l2jfree.loginserver.beans.SessionKey;

public final class PlayerAuthResponse extends LoginToGamePacket
{
	public PlayerAuthResponse(String account, boolean response, String host, SessionKey key)
	{
		super(0x03);
		writeS(account);
		writeC(response ? 1 : 0);
		writeS(host);
		// The game server authenticates only the client that presented this key.
		writeD(key.playOkID1);
		writeD(key.playOkID2);
		writeD(key.loginOkID1);
		writeD(key.loginOkID2);
	}
}
