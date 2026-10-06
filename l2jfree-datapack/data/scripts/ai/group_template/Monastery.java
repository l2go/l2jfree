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
package ai.group_template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.packets.server.NpcSay;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class Monastery extends L2AttackableAIScript
{
	private static final int[] BASIC_MOBS = { 22124, 22125, 22126, 22127, 22129 };
	private static final int[] GUARDIAN_AND_BEHOLDER = { 22134, 22135 };
	private static final String[] TEXT = { "You cannot carry a weapon without authorization!",
			"name, why would you choose the path of darkness?!", "name! How dare you defy the will of Einhasad!" };
	
	public Monastery(int questId, String name, String descr)
	{
		super(questId, name, descr);
		registerMobs(BASIC_MOBS);
		registerMobs(GUARDIAN_AND_BEHOLDER);
	}
	
	@Override
	public String onAggroRangeEnter(L2Npc npc, L2Player player, boolean isPet)
	{
		if (contains(BASIC_MOBS, npc.getNpcId()) && !npc.isInCombat() && !npc.isRunning())// && npc.getTarget() == null)
		{
			if (player.getActiveWeaponInstance() != null)
			{
				npc.setTarget(player);
				npc.broadcastPacket(new NpcSay(npc, TEXT[0]));
				switch (npc.getNpcId())
				{
					case 22124:
					case 22126:
					{
						L2Skill skill = SkillTable.getInstance().getInfo(4589, 8);
						npc.doCast(skill);
						break;
					}
					default:
					{
						npc.setIsRunning(true);
						((L2Attackable)npc).addDamageHate(player, 0, 999);
						//npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, player);
						break;
					}
				}
			}
			else
			// player has no weapon, so npc shouldn't aggro
			{
				L2Attackable actor = (L2Attackable)npc;
				int hate = actor.getHating(player);
				if (hate == 1)
					actor.reduceHate(player, hate);
			}
		}
		return null;
	}
	
	@Override
	public String onSkillSee(L2Npc npc, L2Player caster, L2Skill skill, L2Object[] targets, boolean isPet)
	{
		if (contains(GUARDIAN_AND_BEHOLDER, npc.getNpcId()))
		{
			if (skill.getSkillType() == L2SkillType.AGGDAMAGE && targets.length != 0)
			{
				for (L2Object obj : targets)
				{
					if (obj.equals(npc))
					{
						npc.broadcastPacket(new NpcSay(npc, TEXT[Rnd.get(2) + 1].replace("name", caster.getName())));
						((L2Attackable)npc).addDamageHate(caster, 0, 999);
						//npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, caster);
						break;
					}
				}
			}
		}
		return super.onSkillSee(npc, caster, skill, targets, isPet);
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		if (contains(BASIC_MOBS, npc.getNpcId()))
		{
			List<L2Playable> result = new ArrayList<L2Playable>();
			Collection<L2Object> objs = npc.getKnownList().getKnownObjects().values();
			for (L2Object obj : objs)
			{
				if (obj instanceof L2Player || obj instanceof L2PetInstance)
				{
					if (Util.checkIfInRange(npc.getAggroRange(), npc, obj, true) && !((L2Creature)obj).isDead())
						result.add((L2Playable)obj);
				}
			}
			if (!result.isEmpty())
			{
				for (L2Playable obj : result)
				{
					L2Player target = obj.getActingPlayer();
					if (target.getActiveWeaponInstance() != null && !npc.isInCombat() && npc.getTarget() == null)
					{
						npc.setTarget(target);
						npc.broadcastPacket(new NpcSay(npc, TEXT[0]));
						switch (npc.getNpcId())
						{
							case 22124:
							case 22126:
							case 22127:
							{
								L2Skill skill = SkillTable.getInstance().getInfo(4589, 8);
								npc.doCast(skill);
								break;
							}
							default:
							{
								npc.setIsRunning(true);
								((L2Attackable)npc).addDamageHate(target, 0, 999);
								//npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
								break;
							}
						}
					}
				}
			}
		}
		return super.onSpawn(npc);
	}
	
	@Override
	public String onSpellFinished(L2Npc npc, L2Player player, L2Skill skill)
	{
		if (contains(BASIC_MOBS, npc.getNpcId()) && skill.getId() == 4589)
		{
			npc.setIsRunning(true);
			((L2Attackable)npc).addDamageHate(player, 0, 999);
			//npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, player);
		}
		return super.onSpellFinished(npc, player, skill);
	}
	
	public static void main(String[] args)
	{
		new Monastery(-1, "Monastery", "ai");
	}
}
