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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Map;

import javolution.util.FastMap;

import org.apache.commons.lang3.StringUtils;

import com.l2jfree.Config;
import com.l2jfree.gameserver.CoreInfo;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.lang.L2TextBuilder;
import com.l2jfree.mmocore.network.FloodManager.ErrorMode;
import com.l2jfree.mmocore.network.IPacketHandler;
import com.l2jfree.mmocore.network.SelectorConfig;
import com.l2jfree.mmocore.network.SelectorThread;
import com.l2jfree.tools.util.HexUtil;

public final class L2ClientSelectorThread extends SelectorThread<L2Client, L2ClientPacket, L2ServerPacket>
{
	private static final class SingletonHolder
	{
		private static final L2ClientSelectorThread INSTANCE;
		
		static
		{
			final SelectorConfig sc = new SelectorConfig();
			sc.setSelectorSleepTime(5);
			
			try
			{
				if (Config.PACKET_FINAL)
					INSTANCE = new L2ClientSelectorThread(sc, new L2ClientPacketHandlerFinal());
				else
					INSTANCE = new L2ClientSelectorThread(sc, new L2ClientPacketHandler());
			}
			catch (Exception e)
			{
				throw new Error(e);
			}
		}
	}
	
	public static L2ClientSelectorThread getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private L2ClientSelectorThread(SelectorConfig sc,
			IPacketHandler<L2Client, L2ClientPacket, L2ServerPacket> packetHandler) throws IOException
	{
		super(sc, packetHandler);
	}
	
	public void printDebug(ByteBuffer buf, L2Client client, int... opcodes)
	{
		report(ErrorMode.INVALID_OPCODE, client, null, null);
		
		if (!Config.PACKET_HANDLER_DEBUG)
			return;
		
		L2TextBuilder sb = L2TextBuilder.newInstance();
		sb.append("Unknown Packet: ");
		
		for (int i = 0; i < opcodes.length; i++)
		{
			if (i != 0)
				sb.append(" : ");
			
			sb.append("0x").append(Integer.toHexString(opcodes[i]));
		}
		sb.append(", Client: ").append(client);
		_log.info(sb.moveToString());
		
		byte[] array = new byte[buf.remaining()];
		buf.get(array);
		for (String line : StringUtils.split(HexUtil.printData(array), "\n"))
			_log.info(line);
	}
	
	// ==============================================
	
	@Override
	protected L2Client createClient(SocketChannel socketChannel) throws ClosedChannelException
	{
		return new L2Client(this, socketChannel);
	}
	
	@Override
	protected void executePacket(L2ClientPacket packet)
	{
		packet.getClient().execute(packet);
	}
	
	// ==============================================
	
	private final Map<String, Integer> _legalConnections = new FastMap<String, Integer>().setShared(true);
	
	@Override
	protected String getVersionInfo()
	{
		return CoreInfo.getVersionInfo();
	}
	
	@Override
	public boolean acceptConnectionFrom(SocketChannel sc)
	{
		if (!super.acceptConnectionFrom(sc))
			return false;
		
		if (!Config.CONNECTION_FILTERING)
			return true;
		
		final String ip = sc.socket().getInetAddress().getHostAddress();
		
		final Integer count = _legalConnections.get(ip);
		
		if (count == null)
			return false;
		
		if (count == 1)
			_legalConnections.remove(ip);
		else
			_legalConnections.put(ip, count - 1);
		return true;
	}
	
	public void legalize(String ip)
	{
		final Integer count = _legalConnections.get(ip);
		
		if (count == null)
			_legalConnections.put(ip, 1);
		else
			_legalConnections.put(ip, count + 1);
	}
	
	@Override
	public boolean canReceivePacketFrom(L2Client client, int opcode)
	{
		if (!super.canReceivePacketFrom(client, opcode))
			return false;
		
		if (client.isDisconnected())
			return false;
		
		return true;
	}
}
