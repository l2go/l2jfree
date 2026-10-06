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
package ai.individual;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import ai.group_template.L2AttackableAIScript;

import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.zone.L2BossZone;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.model.zone.L2Zone.ZoneType;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExShowScreenMessage;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * Ice Fairy Sirra AI<br>
 * TODO: review because of the differences between the two zone system
 * 
 * @author Kerberos
 */
public class IceFairySirra extends L2AttackableAIScript
{
	private static final int STEWARD = 32029;
	private static final int SILVER_HEMOCYTE = 8057;
	private static L2BossZone _freyasZone;
	private static L2Player _player = null;
	protected List<L2Npc> _allMobs = new ArrayList<L2Npc>();
	protected Future<?> _onDeadEventTask = null;
	
	public IceFairySirra(int id, String name, String descr)
	{
		super(id, name, descr);
		int[] mob = { STEWARD, 22100, 22102, 22104 };
		registerMobs(mob);
		addEventId(STEWARD, Quest.QuestEventType.QUEST_START);
		addEventId(STEWARD, Quest.QuestEventType.ON_TALK);
		addEventId(STEWARD, Quest.QuestEventType.ON_FIRST_TALK);
		init0();
	}
	
	private void init0()
	{
		L2Zone zone = ZoneManager.getInstance().isInsideZone(ZoneType.Boss, 105546, 127892);
		//_freyasZone = GrandBossManager.getInstance().getZone(105546, -127892, -2768);
		//if (_freyasZone == null)
		if (!(zone instanceof L2BossZone))
		{
			_log.warn("IceFairySirraManager: Failed to load zone");
			return;
		}
		_freyasZone = (L2BossZone)zone;
		_freyasZone.setEnabled(false);
		//_freyasZone.setZoneEnabled(false);
		L2Npc steward = findTemplate(STEWARD);
		if (steward != null)
			steward.setBusy(false);
		openGates();
	}
	
	public void cleanUp()
	{
		init0();
		cancelQuestTimer("30MinutesRemaining", null, _player);
		cancelQuestTimer("20MinutesRemaining", null, _player);
		cancelQuestTimer("10MinutesRemaining", null, _player);
		cancelQuestTimer("End", null, _player);
		for (L2Npc mob : _allMobs)
		{
			try
			{
				mob.getSpawn().stopRespawn();
				mob.deleteMe();
			}
			catch (RuntimeException e)
			{
				_log.error("IceFairySirraManager: Failed deleting mob.", e);
			}
		}
		_allMobs.clear();
	}
	
	public L2Npc findTemplate(int npcId)
	{
		L2Npc npc = null;
		for (L2Spawn spawn : SpawnTable.getInstance().getSpawnTable().values())
		{
			if (spawn != null && spawn.getNpcid() == npcId)
			{
				npc = spawn.getLastSpawn();
				break;
			}
		}
		return npc;
	}
	
	protected void openGates()
	{
		for (int i = 23140001; i < 23140003; i++)
		{
			try
			{
				L2DoorInstance door = DoorTable.getInstance().getDoor(i);
				if (door != null)
				{
					door.openMe();
				}
				else
				{
					_log.warn("IceFairySirraManager: Attempted to open undefined door. doorId: " + i);
				}
			}
			catch (RuntimeException e)
			{
				_log.error("IceFairySirraManager: Failed closing door", e);
			}
		}
	}
	
	protected void closeGates()
	{
		for (int i = 23140001; i < 23140003; i++)
		{
			try
			{
				L2DoorInstance door = DoorTable.getInstance().getDoor(i);
				if (door != null)
				{
					door.closeMe();
				}
				else
				{
					_log.warn("IceFairySirraManager: Attempted to close undefined door. doorId: " + i);
				}
			}
			catch (RuntimeException e)
			{
				_log.error("IceFairySirraManager: Failed closing door", e);
			}
		}
	}
	
	public boolean checkItems(L2Player player)
	{
		if (player.getParty() != null)
		{
			for (L2Player pc : player.getParty().getPartyMembers())
			{
				L2ItemInstance i = pc.getInventory().getItemByItemId(SILVER_HEMOCYTE);
				if (i == null || i.getCount() < 10)
				{
					return false;
				}
			}
		}
		else
			return false;
		return true;
	}
	
	public void destroyItems(L2Player player)
	{
		if (player.getParty() != null)
		{
			for (L2Player pc : player.getParty().getPartyMembers())
			{
				L2ItemInstance i = pc.getInventory().getItemByItemId(SILVER_HEMOCYTE);
				pc.destroyItem("Hemocytes", i.getObjectId(), 10, null, false);
			}
		}
		else
			cleanUp();
	}
	
	public void teleportInside(L2Player player)
	{
		if (player.getParty() != null)
		{
			for (L2Player pc : player.getParty().getPartyMembers())
			{
				pc.teleToLocation(113533, -126159, -3488, false);
				if (_freyasZone == null)
				{
					_log.warn("IceFairySirraManager: Failed to load zone");
					cleanUp();
					return;
				}
				// what should be here instead?
				//_freyasZone.allowPlayerEntry(pc, 2103);
			}
		}
		else
			cleanUp();
	}
	
	public void screenMessage(L2Player player, String text, int time)
	{
		if (player.getParty() != null)
		{
			for (L2Player pc : player.getParty().getPartyMembers())
			{
				pc.sendPacket(new ExShowScreenMessage(text, time));
			}
		}
		else
			cleanUp();
	}
	
	public void doSpawns()
	{
		int[][] mobs =
				{ { 29060, 105546, -127892, -2768 }, { 29056, 102779, -125920, -2840 },
						{ 22100, 111719, -126646, -2992 }, { 22102, 109509, -128946, -3216 },
						{ 22104, 109680, -125756, -3136 } };
		L2Spawn spawnDat;
		L2NpcTemplate template;
		try
		{
			for (int i = 0; i < 5; i++)
			{
				template = NpcTable.getInstance().getTemplate(mobs[i][0]);
				if (template != null)
				{
					spawnDat = new L2Spawn(template);
					spawnDat.setAmount(1);
					spawnDat.setLocx(mobs[i][1]);
					spawnDat.setLocy(mobs[i][2]);
					spawnDat.setLocz(mobs[i][3]);
					spawnDat.setHeading(0);
					spawnDat.setRespawnDelay(60);
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_allMobs.add(spawnDat.doSpawn());
					spawnDat.stopRespawn();
				}
				else
				{
					_log.warn("IceFairySirraManager: Data missing in NPC table for ID: " + mobs[i][0]);
				}
			}
		}
		catch (RuntimeException e)
		{
			_log.warn("IceFairySirraManager: Spawns could not be initialized: ", e);
		}
	}
	
	public String getHtmlPath(int val)
	{
		String pom = "";
		
		pom = "32029-" + val;
		if (val == 0)
			pom = "32029";
		
		String temp = "data/html/default/" + pom + ".htm";
		
		if (HtmCache.getInstance().pathExists(temp))
			return temp;
		
		// If the file is not found, the standard message "I have nothing to say to you" is returned
		return "data/html/npcdefault.htm";
	}
	
	public void sendHtml(L2Npc npc, L2Player player, String filename)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(npc.getObjectId()));
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2Player player)
	{
		if (player.getQuestState("IceFairySirra") == null)
			newQuestState(player);
		player.setLastQuestNpcObject(npc.getObjectId());
		String filename = "";
		if (npc.isBusy())
			filename = getHtmlPath(10);
		else
			filename = getHtmlPath(0);
		sendHtml(npc, player, filename);
		return null;
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2Player player)
	{
		if (event.equalsIgnoreCase("check_condition"))
		{
			if (npc.isBusy())//should never happen
				return super.onAdvEvent(event, npc, player);
			else
			{
				String filename = "";
				if (player.isInParty() && player.getParty().getPartyLeaderOID() == player.getObjectId())
				{
					if (checkItems(player) == true)
					{
						startQuestTimer("start", 100000, null, player);
						_player = player;
						destroyItems(player);
						player.getInventory().addItem("Scroll", 8379, 3, player, null);
						npc.setBusy(true);
						screenMessage(player, "Steward: Please wait a moment.", 100000);
						filename = getHtmlPath(3);
					}
					else
					{
						filename = getHtmlPath(2);
					}
				}
				else
				{
					filename = getHtmlPath(1);
				}
				sendHtml(npc, player, filename);
			}
		}
		else if (event.equalsIgnoreCase("start"))
		{
			if (_freyasZone == null)
			{
				_log.warn("IceFairySirraManager: Failed to load zone");
				cleanUp();
				return super.onAdvEvent(event, npc, player);
			}
			_freyasZone.setEnabled(true);
			//_freyasZone.setZoneEnabled(true);
			closeGates();
			doSpawns();
			startQuestTimer("Party_Port", 2000, null, player);
			startQuestTimer("End", 1802000, null, player);
		}
		else if (event.equalsIgnoreCase("Party_Port"))
		{
			teleportInside(player);
			screenMessage(player, "Steward: Please restore the Queen's appearance!", 10000);
			startQuestTimer("30MinutesRemaining", 300000, null, player);
		}
		else if (event.equalsIgnoreCase("30MinutesRemaining"))
		{
			screenMessage(player, "30 minute(s) are remaining.", 10000);
			startQuestTimer("20minutesremaining", 600000, null, player);
		}
		else if (event.equalsIgnoreCase("20MinutesRemaining"))
		{
			screenMessage(player, "20 minute(s) are remaining.", 10000);
			startQuestTimer("10minutesremaining", 600000, null, player);
		}
		else if (event.equalsIgnoreCase("10MinutesRemaining"))
		{
			screenMessage(player, "Steward: Waste no time! Please hurry!", 10000);
		}
		else if (event.equalsIgnoreCase("End"))
		{
			screenMessage(player, "Steward: Was it indeed too much to ask.", 10000);
			cleanUp();
		}
		return super.onAdvEvent(event, npc, player);
	}
	
	public static void main(String[] args)
	{
		new IceFairySirra(-1, "IceFairySirra", "ai");
	}
}
