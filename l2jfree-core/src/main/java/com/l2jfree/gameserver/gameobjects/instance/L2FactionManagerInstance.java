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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.FactionManager;
import com.l2jfree.gameserver.model.entity.faction.Faction;
import com.l2jfree.gameserver.model.entity.faction.FactionMember;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * @author evill33t
 */
public class L2FactionManagerInstance extends L2NpcInstance
{
	public L2FactionManagerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		// Check if the L2Player already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			// Calculate the distance between the L2Player and the L2NpcInstance
			if (!canInteract(player))
			{
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			}
			else
			{
				showMessageWindow(player);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showMessageWindow(L2Player player)
	{
		int factionId = getTemplate().getNpcFaction();
		String filename = "data/html/npcdefault.htm";
		String factionName = getTemplate().getNpcFactionName();
		String replace = null;
		
		if (factionId != 0)
		{
			filename = "data/html/custom/faction/" + String.valueOf(factionId) + "/start.htm";
			replace = getName();
		}
		sendHtmlMessage(player, filename, replace, factionName);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		// Standard msg
		String filename = "data/html/npcdefault.htm";
		String factionName = getTemplate().getNpcFactionName();
		int factionId = getTemplate().getNpcFaction();
		Faction faction = FactionManager.getInstance().getFactions(factionId);
		if (faction == null)
		{
			return; // the faction system has no stored data in Platform 3.0
		}
		int factionPrice = faction.getPrice();
		String replace = null;
		
		if (factionId != 0)
		{
			String path = "data/html/custom/faction/" + String.valueOf(factionId) + "/";
			replace = String.valueOf(factionPrice);
			
			if (player.getNPCFaction() != null)
			{
				if (player.getNPCFaction().getSide() != faction.getSide())
					filename = path + "already.htm";
				else
					filename = path + "switch.htm";
			}
			else if (command.startsWith("Join"))
				filename = path + "join.htm";
			else if (command.startsWith("Accept"))
			{
				if (player.getNPCFaction() == null)
				{
					if (player.getAdena() < factionPrice)
						filename = path + "noadena.htm";
					else
					{
						player.getInventory().reduceAdena("Faction", factionPrice, player, null);
						player.setNPCFaction(new FactionMember(player.getObjectId(), factionId));
						filename = path + "accepted.htm";
					}
				}
				else
				{
					player.getNPCFaction().setFactionId(factionId);
					filename = path + "switched.htm";
				}
			}
			else if (command.startsWith("Decline"))
				filename = path + "declined.htm";
			else if (command.startsWith("AskQuit"))
				filename = path + "askquit.htm";
			else if (command.startsWith("Story"))
				filename = path + "story.htm";
			else if (command.startsWith("Quit"))
			{
				player.quitNPCFaction();
				filename = path + "quited.htm";
			}
			else if (command.startsWith("Quest"))
			{
				filename = path + "quest.htm";
			}
			else if (command.startsWith("FactionShop"))
				filename = path + "shop.htm";
		}
		sendHtmlMessage(player, filename, replace, factionName);
	}
	
	private void sendHtmlMessage(L2Player player, String filename, String replace, String factionName)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%replace%", replace);
		html.replace("%npcname%", getName());
		html.replace("%factionName%", factionName);
		player.sendPacket(html);
	}
}
