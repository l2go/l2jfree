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
package com.l2jfree.gameserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.script.DateRange;
import com.l2jfree.lang.L2TextBuilder;

/**
 * This class ...
 * 
 * @version $Revision: 1.5.2.1.2.7 $ $Date: 2005/03/29 23:15:14 $
 */
public class Announcements
{
	private final static Logger _log = LoggerFactory.getLogger(Announcements.class);
	
	private final List<String> _announcements = new ArrayList<String>();
	private final List<List<Object>> _eventAnnouncements = new ArrayList<List<Object>>();
	
	private Announcements()
	{
		loadAnnouncements();
	}
	
	public static Announcements getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public void loadAnnouncements()
	{
		_announcements.clear();
		File file = new File(Config.DATAPACK_ROOT, "data/announcements.txt");
		if (file.exists())
		{
			readFromDisk(file);
		}
		else
		{
			_log.info("data/announcements.txt doesn't exist");
		}
	}
	
	public void showAnnouncements(L2Player activeChar)
	{
		for (int i = 0; i < _announcements.size(); i++)
		{
			CreatureSay cs =
					new CreatureSay(0, SystemChatChannelId.Chat_Announce, activeChar.getName(), _announcements.get(i)
							.replace("%name%", activeChar.getName()));
			activeChar.sendPacket(cs);
		}
		
		Date currentDate = new Date();
		for (int i = 0; i < _eventAnnouncements.size(); i++)
		{
			List<Object> entry = _eventAnnouncements.get(i);
			
			DateRange validDateRange = (DateRange)entry.get(0);
			String[] msg = (String[])entry.get(1);
			
			if (validDateRange.isValid() && validDateRange.isWithinRange(currentDate))
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1);
				for (String element : msg)
					sm.addString(element);
				activeChar.sendPacket(sm);
			}
		}
	}
	
	public void addEventAnnouncement(DateRange validDateRange, String[] msg)
	{
		ArrayList<Object> entry = new ArrayList<Object>();
		entry.add(validDateRange);
		entry.add(msg);
		entry.trimToSize();
		_eventAnnouncements.add(entry);
	}
	
	public void listAnnouncements(L2Player activeChar)
	{
		String content = HtmCache.getInstance().getHtmForce("data/html/admin/announce.htm");
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setHtml(content);
		L2TextBuilder replyMSG = L2TextBuilder.newInstance();
		replyMSG.append("<br>");
		for (int i = 0; i < _announcements.size(); i++)
		{
			replyMSG.append("<table width=260><tr><td width=220>");
			replyMSG.append(_announcements.get(i));
			replyMSG.append("</td><td width=40><button value=\"Delete\" action=\"bypass -h admin_del_announcement ");
			replyMSG.append(i);
			replyMSG.append("\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr></table>");
		}
		adminReply.replace("%announces%", replyMSG.moveToString());
		activeChar.sendPacket(adminReply);
	}
	
	public void addAnnouncement(String text)
	{
		_announcements.add(text);
		saveToDisk();
	}
	
	public void delAnnouncement(int line)
	{
		_announcements.remove(line);
		saveToDisk();
	}
	
	private void readFromDisk(File file)
	{
		BufferedReader lnr = null;
		try
		{
			int i = 0;
			String line = null;
			lnr = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			while ((line = lnr.readLine()) != null)
			{
				StringTokenizer st = new StringTokenizer(line, "\n\r");
				if (st.hasMoreTokens())
				{
					String announcement = st.nextToken();
					_announcements.add(announcement);
					
					i++;
				}
			}
			if (_log.isDebugEnabled())
				_log.info("Announcements: Loaded " + i + " Announcements.");
		}
		catch (IOException e1)
		{
			_log.error("Error reading announcements", e1);
		}
		finally
		{
			try
			{
				if (lnr != null)
					lnr.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private void saveToDisk()
	{
		File file = new File("data/announcements.txt");
		FileWriter save = null;
		
		try
		{
			save = new FileWriter(file);
			for (int i = 0; i < _announcements.size(); i++)
			{
				save.write(_announcements.get(i));
				save.write("\r\n");
			}
		}
		catch (IOException e)
		{
			_log.warn("saving the announcements file has failed: ", e);
		}
		finally
		{
			try
			{
				if (save != null)
					save.close();
			}
			catch (Exception e)
			{
			}
		}
	}
	
	public void announceToAll(String text)
	{
		CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Announce, "", text);
		
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(cs);
		}
	}
	
	public void announceToAll(L2ServerPacket gsp)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(gsp);
		}
	}
	
	public void announceToAll(SystemMessageId sm)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(sm);
		}
	}
	
	public void announceToInstance(L2ServerPacket gsp, int instanceId)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (player.isSameInstance(instanceId))
				player.sendPacket(gsp);
		}
	}
	
	// Method fo handling announcements from admin
	public void handleAnnounce(String command, int lengthToTrim)
	{
		try
		{
			// Announce string to everyone on server
			String text = command.substring(lengthToTrim);
			announceToAll(text);
		}
		
		// No body cares!
		catch (StringIndexOutOfBoundsException e)
		{
			// empty message.. ignore
		}
	}
	
	/**
	 * Announce to players.<BR>
	 * <BR>
	 * 
	 * @param message
	 *            The String of the message to send to player
	 */
	public void announceToPlayers(String message)
	{
		// Get all players
		for (L2Player player : L2World.getInstance().getAllPlayers())
			player.sendMessage(message);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final Announcements _instance = new Announcements();
	}
}
