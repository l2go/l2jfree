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
package quests.converted;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.quest.State;
import com.l2jfree.gameserver.model.quest.jython.QuestJython;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcSay;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.tools.random.Rnd;

/**
 * Clan quest to advance clan level, 3 -> 4.<BR><BR>
 * Having in mind that both quest and bingo minigame completion is timer-based,
 * there is no straightforward way to enforce quest consistency between server
 * restarts (at least with current quest timers), that's why quest in progress
 * will be forcibly terminated if the server is restarted or shut down.<BR>
 * This has also been confirmed by the NC Customer Support. You must restart
 * the quest if the server has been restarted before you have finished.<BR>
 * Also, yes, it is possible for one player to obtain all three herbs.
 * Usually it's not worth risking due to the time limit and low drop chance.
 * @author savormix
 */
public final class ProofOfClanAlliance extends QuestJython
{
	private static final boolean DEBUG = false;
	// a.k.a. Pledge of Blood
	private static final String PROOF_OF_CLAN_ALLIANCE = "501_ProofOfClanAlliance";
	private static final int NEEDED_MEMBERS = 3;
	private static final int SKILL_POISON = 4082;
	private static final int SKILL_DEATH = 4083;
	private static final int LOYALTY_TIMER = 4 * 1000;
	private static final int CHEST_TIMER = 300 * 1000;
	
	// Quest NPCs
	private static final int SIR_KRISTOF_RODEMAI = 30756;
	private static final int STATUE_OF_OFFERING = 30757;
	private static final int ATHREA = 30758;
	private static final int KALIS = 30759;
	
	// Quest items
	private static final int HERB_OF_HARIT = 3832;
	private static final int HERB_OF_VANOR = 3833;
	private static final int HERB_OF_OEL_MAHUM = 3834;
	private static final int BLOOD_OF_EVA = 3835;
	private static final int SYMBOL_OF_LOYALTY = 3837;
	private static final int ANTIDOTE_RECIPE_LIST = 3872;
	private static final int VOUCHER_OF_FAITH = 3873;
	private static final int ALLIANCE_MANIFESTO = 3874;
	private static final int POTION_OF_RECOVERY = 3889;
	
	// Quest monsters
	private static final int OEL_MAHUM_WITCH_DOCTOR = 20576;
	private static final int HARIT_LIZARDMAN_SHAMAN = 20644;
	private static final int VANOR_SILENOS_SHAMAN = 20685;
	private static final int[] CHEST = { 27173, 27174, 27175, 27176, 27177 };
	private static final String CHEST_KILLED = "##########Bingo!##########";
	private static final Location[] CHEST_POS = { new Location(102273, 103433, -3512),
			new Location(102190, 103379, -3524), new Location(102107, 103325, -3533),
			new Location(102024, 103271, -3500), new Location(102327, 103350, -3511),
			new Location(102244, 103296, -3518), new Location(102161, 103242, -3529),
			new Location(102078, 103188, -3500), new Location(102381, 103267, -3538),
			new Location(102298, 103213, -3532), new Location(102215, 103159, -3520),
			new Location(102132, 103105, -3513), new Location(102435, 103184, -3515),
			new Location(102352, 103130, -3522), new Location(102269, 103076, -3533),
			new Location(102186, 103022, -3541), };
	private static final int NEEDED_WINS = Math.min(4, CHEST_POS.length);
	
	private final Map<Integer, QuestClan> _questers;
	private volatile QuestClan _minigame;
	
	private ProofOfClanAlliance(int questId, String name, String descr)
	{
		super(questId, name, descr);
		questItemIds =
				new int[] {
						// can be reused - verified by NC customer support
						/*HERB_OF_HARIT, HERB_OF_VANOR, HERB_OF_OEL_MAHUM,*/BLOOD_OF_EVA, SYMBOL_OF_LOYALTY,
						ANTIDOTE_RECIPE_LIST, VOUCHER_OF_FAITH, POTION_OF_RECOVERY };
		_questers = new ConcurrentHashMap<Integer, QuestClan>();
		_minigame = null;
		addStartNpc(SIR_KRISTOF_RODEMAI);
		addStartNpc(STATUE_OF_OFFERING);
		addTalkId(SIR_KRISTOF_RODEMAI);
		addTalkId(STATUE_OF_OFFERING);
		addTalkId(ATHREA);
		addTalkId(KALIS);
		addKillId(OEL_MAHUM_WITCH_DOCTOR);
		addKillId(HARIT_LIZARDMAN_SHAMAN);
		addKillId(VANOR_SILENOS_SHAMAN);
		for (int id : CHEST)
			addKillId(id);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2Player player)
	{
		QuestClan qc = _questers.get(player.getClanId());
		if (event.startsWith("loyalty"))
		{
			if (qc == null || !qc.checkLeader())
				return null;
			if (qc.isLoyal(player))
				player.addItem(PROOF_OF_CLAN_ALLIANCE, SYMBOL_OF_LOYALTY, 1, npc, true);
			else if (DEBUG)
				_log.warn("501_PoCA: loyalty timer is up for non-loyal player? " + qc);
			return null;
		}
		else if (event.startsWith("chest"))
		{
			if (qc == null || !qc.checkLeader())
				return null;
			qc.getChests().timeout();
			_minigame = null;
			return null;
		}
		else
		{
			if (player.isClanLeader())
			{
				QuestState qs = player.getQuestState(PROOF_OF_CLAN_ALLIANCE);
				if ("30756-07.htm".equals(event))
				{
					qc = new QuestClan(player.getClan());
					_questers.put(player.getClanId(), qc);
					qs.set(CONDITION, 1);
					qc.setPart(SIR_KRISTOF_RODEMAI);
					qs.setState(State.STARTED);
					player.sendPacket(SND_ACCEPT);
				}
				else if ("30759-03.htm".equals(event))
				{
					qs.set(CONDITION, 2);
					qc.setPart(STATUE_OF_OFFERING);
				}
				else if ("30759-07.htm".equals(event))
				{
					for (int i = 0; i < NEEDED_MEMBERS; i++)
						qs.takeItems(SYMBOL_OF_LOYALTY, 1);
					qs.giveItems(ANTIDOTE_RECIPE_LIST, 1);
					qs.set(CONDITION, 3);
					qc.setPart(KALIS);
					qs.addNotifyOfDeath(player);
					L2Skill skill = SkillTable.getInstance().getInfo(SKILL_POISON, 1);
					if (skill == null)
					{
						_log.error("501_PoCA: Missing skill " + SKILL_POISON + ", terminating quest!");
						qs.exitQuest(true);
					}
					else
						skill.getEffects(/*npc*/player, player);
				}
			}
			else
			{
				if (qc == null || !qc.checkLeader())
					return NO_QUEST;
				else if ("30757-05.htm".equals(event))
				{
					if (!qc.addLoyalMember(player))
						return "30757-07.htm";
					QuestState qs = player.getQuestState(PROOF_OF_CLAN_ALLIANCE);
					qs.takeItems(SYMBOL_OF_LOYALTY, -1);
					qs.takeItems(BLOOD_OF_EVA, -1);
					if (Rnd.get(10) > 5)
					{
						player.addItem(PROOF_OF_CLAN_ALLIANCE, SYMBOL_OF_LOYALTY, 1, npc, true);
						return "30757-06.htm";
					}
					else
					{
						L2Skill skill = SkillTable.getInstance().getInfo(SKILL_DEATH, 1);
						if (skill == null)
						{
							//player.doDie(npc);
							_log.warn("501_PoCA: Missing skill " + SKILL_DEATH);
						}
						else
						{
							//npc.getAI().setIntention(CtrlIntention.AI_INTENTION_CAST,
							//		new SkillUsageRequest(skill));
							npc.broadcastPacket(new MagicSkillUse(npc, player, skill, skill.getHitTime(), skill
									.getReuseDelay()));
							npc.broadcastPacket(new MagicSkillLaunched(npc, skill, player));
						}
						player.doDie(npc);
						startQuestTimer("loyalty_" + player.getObjectId(), LOYALTY_TIMER, npc, player);
						return "30757-05.htm";
					}
				}
				else if ("30758-03.htm".equals(event))
				{
					if (_minigame == null)
					{
						_minigame = qc;
						qc.setPart(ATHREA);
						for (Location loc : CHEST_POS)
							addSpawn(CHEST[Rnd.get(CHEST.length)], loc.getX(), loc.getY(), loc.getZ(),
									loc.getHeading(), false, CHEST_TIMER);
						startQuestTimer("chest_" + qc.getClan().getClanId(), CHEST_TIMER, npc, player);
					}
					else
						return "30758-04.htm";
				}
				else if ("30758-07.htm".equals(event))
				{
					if (_minigame == null)
					{
						if (player.reduceAdena(PROOF_OF_CLAN_ALLIANCE, 10000, npc, true))
						{
							qc.getChests().reset();
							return "30758-08.htm";
						}
					}
					else
						return "30758-04.htm";
				}
			}
		}
		return event;
	}
	
	@Override
	public String onDeath(L2Creature killer, L2Creature victim, QuestState qs)
	{
		if (victim instanceof L2Player)
		{
			L2Player leader = victim.getActingPlayer();
			QuestClan qc = _questers.remove(leader.getClanId());
			qs.exitQuest(true);
			for (L2ClanMember cm : qc._loyal)
			{
				if (cm == null)
					break;
				L2Player member = cm.getPlayerInstance();
				if (member != null)
				{
					QuestState st = member.getQuestState(PROOF_OF_CLAN_ALLIANCE);
					if (st == null)
						st = newQuestState(member);
					st.exitQuest(true);
				}
			}
			if (DEBUG)
				_log.info("501_PoCA: The leader died. " + qc);
		}
		return null;
	}
	
	@Override
	public String onKill(L2Npc npc, L2Player killer, boolean isPet)
	{
		if (ArrayUtils.contains(CHEST, npc.getNpcId()))
		{
			ChestInfo ci = _minigame.getChests();
			synchronized (ci)
			{
				if (ci.getWins() < NEEDED_WINS)
				{
					if ((ci.getKills() >= CHEST_POS.length - NEEDED_WINS && ci.getKills() - ci.getWins() == 12)
							|| Rnd.get(NEEDED_WINS) == 0)
					{
						ci.incWins();
						npc.broadcastPacket(new NpcSay(npc, CHEST_KILLED));
					}
				}
				ci.incKills();
			}
		}
		else if (killer == null)
			return null;
		QuestClan qc = _questers.get(killer.getClanId());
		if (qc == null || !qc.checkLeader() || qc.getPart() != KALIS || !qc.isLoyal(killer))
			return null;
		QuestState qs = killer.getQuestState(PROOF_OF_CLAN_ALLIANCE);
		switch (npc.getNpcId())
		{
			case OEL_MAHUM_WITCH_DOCTOR:
				qs.dropQuestItems(HERB_OF_OEL_MAHUM, 1, 1, 100000, true, false);
				break;
			case HARIT_LIZARDMAN_SHAMAN:
				qs.dropQuestItems(HERB_OF_HARIT, 1, 1, 100000, true, false);
				break;
			case VANOR_SILENOS_SHAMAN:
				qs.dropQuestItems(HERB_OF_VANOR, 1, 1, 100000, true, false);
				break;
		}
		return null;
	}
	
	@Override
	public String onTalk(L2Npc npc, L2Player talker)
	{
		QuestClan qc = _questers.get(talker.getClanId());
		QuestState qs = talker.getQuestState(PROOF_OF_CLAN_ALLIANCE);
		if (qs == null)
			return NO_QUEST;
		else if (qs.isCompleted())
			return QUEST_DONE;
		int state = qs.getState();
		if (qc == null)
		{
			if (state == State.CREATED)
			{
				switch (npc.getNpcId())
				{
				// anyone may speak
					case SIR_KRISTOF_RODEMAI:
					case STATUE_OF_OFFERING:
						break;
					default:
						if (!talker.isClanLeader())
							return NO_QUEST;
				}
			}
			else
			{
				if (!talker.isClanLeader())
				{
					qs.exitQuest(true);
					return NO_QUEST;
				}
				else
				{
					qc = new QuestClan(talker.getClan());
					qc.setPart(SIR_KRISTOF_RODEMAI);
					_questers.put(talker.getClanId(), qc);
					qs.set(CONDITION, 1);
					return onTalk(npc, talker);
				}
			}
		}
		else if (!talker.isClanLeader() && !qc.checkLeader())
			return NO_QUEST;
		switch (npc.getNpcId())
		{
			case SIR_KRISTOF_RODEMAI:
				if (state == State.CREATED)
				{
					if (talker.isClanLeader())
					{
						switch (talker.getClan().getLevel())
						{
							case 0:
							case 1:
							case 2:
								return buildReply(npc, 1);
							case 3:
								if (qs.getQuestItemsCount(ALLIANCE_MANIFESTO) > 0)
									return buildReply(npc, 3);
								else
									return buildReply(npc, 4);
							default:
								return buildReply(npc, 2);
						}
					}
					else
						return buildReply(npc, 5);
				}
				else if (state == State.STARTED)
				{
					if (qs.getQuestItemsCount(VOUCHER_OF_FAITH) > 0)
					{
						qs.takeItems(VOUCHER_OF_FAITH, -1);
						qs.exitQuest(false);
						qs.giveItems(ALLIANCE_MANIFESTO, 1);
						qs.addExpAndSp(0, 120000);
						talker.sendPacket(SND_FINISH);
						talker.broadcastPacket(new SocialAction(talker.getObjectId(), 3));
						return buildReply(npc, 9);
					}
					else
						return buildReply(npc, 10);
				}
				break;
			case KALIS:
				if (qc == null)
					return NO_QUEST;
				else if (state == State.CREATED)
				{
					QuestState l = qc.getClan().getLeader().getPlayerInstance().getQuestState(PROOF_OF_CLAN_ALLIANCE);
					if (l.getState() == State.STARTED)
						return buildReply(npc, 12);
				}
				else if (state == State.STARTED)
				{
					int part = qc.getPart();
					long symbols = qs.getQuestItemsCount(SYMBOL_OF_LOYALTY);
					boolean poisoned = talker.getEffects().hasEffect(SKILL_POISON);
					if (part == SIR_KRISTOF_RODEMAI)
						return buildReply(npc, 1);
					else if (part == STATUE_OF_OFFERING && symbols < NEEDED_MEMBERS)
						return buildReply(npc, 5);
					else if (symbols >= NEEDED_MEMBERS && !poisoned)
						return buildReply(npc, 6);
					else if (part == KALIS * 2 && qs.getQuestItemsCount(HERB_OF_HARIT) > 0
							&& qs.getQuestItemsCount(HERB_OF_OEL_MAHUM) > 0 && qs.getQuestItemsCount(HERB_OF_VANOR) > 0
							&& poisoned)
					{
						qs.takeItems(ANTIDOTE_RECIPE_LIST, -1);
						qs.takeItems(BLOOD_OF_EVA, -1);
						qs.takeItems(HERB_OF_HARIT, -1);
						qs.takeItems(HERB_OF_OEL_MAHUM, -1);
						qs.takeItems(HERB_OF_VANOR, -1);
						qs.giveItems(VOUCHER_OF_FAITH, 1);
						qs.giveItems(POTION_OF_RECOVERY, 1);
						qs.set(CONDITION, 4);
						qc.setPart(SIR_KRISTOF_RODEMAI * 2);
						return buildReply(npc, 8);
					}
					else if (part % KALIS == 0 || part == ATHREA)
					{
						if (!poisoned)
						{
							qc.setPart(SIR_KRISTOF_RODEMAI);
							return buildReply(npc, 9);
						}
						else
							return buildReply(npc, 10);
					}
					else if (part == SIR_KRISTOF_RODEMAI * 2)
						return buildReply(npc, 11);
				}
				break;
			case STATUE_OF_OFFERING:
				if (qc == null)
					return NO_QUEST;
				else if (qc.getPart() != STATUE_OF_OFFERING)
					return buildReply(npc, 8);
				else if (!talker.isClanLeader())
				{
					if (talker.getLevel() > 39)
					{
						if (qc.isLoyal(talker))
							return buildReply(npc, 3);
						else
							return buildReply(npc, 1);
					}
					else
						return buildReply(npc, 4);
				}
				else
					return buildReply(npc, 2);
			case ATHREA:
				if (qc == null)
					return NO_QUEST;
				int part = qc.getPart();
				QuestState l = qc.getClan().getLeader().getPlayerInstance().getQuestState(PROOF_OF_CLAN_ALLIANCE);
				if (part == KALIS && l.getQuestItemsCount(ANTIDOTE_RECIPE_LIST) > 0
						&& l.getQuestItemsCount(BLOOD_OF_EVA) == 0)
					return buildReply(npc, 1);
				else if (part == KALIS * 2)
					return buildReply(npc, 10);
				else if (part == ATHREA)
				{
					switch (qc.getChests().getState())
					{
						case ChestInfo.WON:
							qs.giveItems(BLOOD_OF_EVA, 1);
							qc.setPart(KALIS * 2);
							return buildReply(npc, 9);
						case ChestInfo.FAILED:
							return buildReply(npc, 6);
						default:
							return buildReply(npc, 10);
					}
				}
				break;
		}
		return NO_QUEST;
	}
	
	private static final String buildReply(L2Npc npc, int answer)
	{
		StringBuilder tb = new StringBuilder();
		tb.append(npc.getNpcId());
		tb.append('-');
		if (answer < 10)
			tb.append('0');
		tb.append(answer);
		tb.append(".htm");
		return tb.toString();
	}
	
	public static void main(String[] args)
	{
		new ProofOfClanAlliance(501, PROOF_OF_CLAN_ALLIANCE, "Proof of Clan Alliance");
	}
	
	private static class QuestClan
	{
		private final L2Clan _clan;
		private final L2ClanMember[] _loyal;
		private final ChestInfo _chests;
		private int _part;
		
		public QuestClan(L2Clan clan)
		{
			_clan = clan;
			_loyal = new L2ClanMember[NEEDED_MEMBERS];
			_chests = new ChestInfo();
		}
		
		public synchronized boolean addLoyalMember(L2Player player)
		{
			L2ClanMember cm = getClan().getClanMember(player.getObjectId());
			if (cm == null)
			{
				if (DEBUG)
					_log.warn("A new loyal member " + player + " does not belong to " + this,
							new IllegalArgumentException());
				return false;
			}
			for (int i = 0; i < NEEDED_MEMBERS; i++)
			{
				if (_loyal[i] == null)
				{
					_loyal[i] = cm;
					return true;
				}
			}
			if (DEBUG)
				_log.warn("Excess loyal member (" + player + ") can't be added to " + this, new IllegalStateException());
			return false;
		}
		
		public boolean checkLeader()
		{
			L2Player leader = getClan().getLeader().getPlayerInstance();
			if (leader == null)
				return false;
			QuestState qs = leader.getQuestState(PROOF_OF_CLAN_ALLIANCE);
			if (qs == null || !qs.isStarted())
				return false;
			else
				return true;
		}
		
		public boolean isLoyal(L2Player player)
		{
			for (L2ClanMember cm : _loyal)
				if (cm != null && cm.getPlayerInstance() == player)
					return true;
			return false;
		}
		
		public L2Clan getClan()
		{
			return _clan;
		}
		
		public ChestInfo getChests()
		{
			return _chests;
		}
		
		public int getPart()
		{
			return _part;
		}
		
		public void setPart(int part)
		{
			_part = part;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof QuestClan)
				return hashCode() == obj.hashCode();
			else
				return false;
		}
		
		@Override
		public int hashCode()
		{
			return getClan().getClanId();
		}
		
		@Override
		public String toString()
		{
			StringBuilder tb = new StringBuilder();
			tb.append("Questing clan ");
			tb.append(getClan().getName());
			tb.append(", leader ");
			tb.append(getClan().getLeaderName());
			tb.append(", tasked members:");
			for (L2ClanMember cm : _loyal)
			{
				if (cm == null)
					break;
				tb.append(' ');
				tb.append(cm.getName());
				tb.append('(');
				if (cm.isOnline())
					tb.append("ON");
				else
					tb.append("OFF");
				tb.append(')');
			}
			return tb.toString();
		}
	}
	
	private static class ChestInfo
	{
		private static final int NONE = 0;
		private static final int WON = 1;
		private static final int FAILED = 2;
		private int _kills;
		private int _wins;
		private int _state;
		
		public ChestInfo()
		{
			reset();
		}
		
		public int getKills()
		{
			return _kills;
		}
		
		public void incKills()
		{
			_kills++;
		}
		
		public int getWins()
		{
			return _wins;
		}
		
		public void incWins()
		{
			_wins++;
			if (getWins() == NEEDED_WINS)
				_state = WON;
		}
		
		public void timeout()
		{
			if (getState() == NONE)
				_state = FAILED;
		}
		
		public int getState()
		{
			return _state;
		}
		
		public void reset()
		{
			_kills = 0;
			_wins = 0;
			_state = NONE;
		}
	}
}
