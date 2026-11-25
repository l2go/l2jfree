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
package com.l2jfree.gameserver.gameobjects.effects;

import java.sql.SQLException;
import java.util.ArrayList;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.persistence.player.PlayerRepository;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.EffectRow;
import com.l2jfree.util.LookupTable;
import com.l2jfree.util.concurrent.ForEachExecutable;

/**
 * @author NB4L1
 */
public final class PlayerEffects extends CreatureEffects
{
	protected static final class StoredEffect
	{
		public final int skillId;
		public final int skillLvl;
		public final int count;
		public final int remaining;
		
		public StoredEffect(L2Effect effect)
		{
			skillId = effect.getSkill().getId();
			skillLvl = effect.getSkill().getLevel();
			count = effect.getCount();
			remaining = effect.getPeriod() - effect.getTime();
		}
		
		public StoredEffect(EffectRow row)
		{
			skillId = row.skillId();
			skillLvl = row.skillLevel();
			count = row.remainingCount();
			remaining = row.remainingSeconds();
		}
	}

	private final LookupTable<ArrayList<StoredEffect>> _storedEffects = new LookupTable<ArrayList<StoredEffect>>();
	
	public PlayerEffects(L2Player owner)
	{
		super(owner);
	}
	
	@Override
	protected L2Player getOwner()
	{
		return (L2Player)_owner;
	}
	
	/** The effects of one class as they were when they were copied. It carries no live effect. */
	public static final class EffectsCopy
	{
		private final int _classIndex;
		private final ArrayList<EffectRow> _rows;
		
		private EffectsCopy(int classIndex, ArrayList<EffectRow> rows)
		{
			_classIndex = classIndex;
			_rows = rows;
		}
	}
	
	public void storeEffects(boolean storeActiveEffects)
	{
		writeEffects(copyEffects(storeActiveEffects));
	}
	
	/**
	 * Copies the effects that are saved. Effects change on the world thread, so a background save copies them there
	 * and writes the copy with {@link #writeEffects(EffectsCopy)} afterwards.
	 * 
	 * @return the copy, or null when effects are not stored
	 */
	public EffectsCopy copyEffects(boolean storeActiveEffects)
	{
		if (!Config.STORE_EFFECTS)
			return null;
		
		final ArrayList<StoredEffect> list = getEffectList();
		
		list.clear();
		
		if (storeActiveEffects)
			for (L2Effect e : getAllEffects())
				if (e != null && e.canBeStoredInDb())
					list.add(new StoredEffect(e));
		
		final ArrayList<EffectRow> rows = new ArrayList<EffectRow>(list.size());
		for (StoredEffect se : list)
			rows.add(new EffectRow(se.skillId, se.skillLvl, Math.max(0, se.count), se.remaining));
		
		return new EffectsCopy(getOwner().classIndexForStoredEffects(), rows);
	}
	
	/** Replaces the stored effects of the copied class. A copy of null, from a server that stores none, is nothing. */
	public void writeEffects(EffectsCopy copy)
	{
		if (copy == null)
			return;
		
		// TODO: delay effect storage
		try
		{
			PlayerRepository.getInstance().replaceEffects(getOwner().getObjectId(), copy._classIndex, copy._rows);
			
			_storedEffects.remove(copy._classIndex);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public void restoreEffects()
	{
		if (!Config.STORE_EFFECTS)
			return;
		
		final ArrayList<StoredEffect> list = getEffectList();
		
		for (final StoredEffect se : list)
		{
			final L2Skill skill = SkillTable.getInstance().getInfo(se.skillId, se.skillLvl);
			if (skill == null)
				continue;
			
			skill.getEffects(getOwner(), getOwner(), new ForEachExecutable<L2Effect>() {
				@Override
				public void execute(L2Effect e)
				{
					e.setTiming(se.count, se.remaining);
				}
			});
		}
	}
	
	public void deleteEffects(int classIndex) throws SQLException
	{
		PlayerRepository.getInstance().deleteEffects(getOwner().getObjectId(), classIndex);
		
		_storedEffects.remove(classIndex);
	}
	
	private ArrayList<StoredEffect> getEffectList()
	{
		ArrayList<StoredEffect> list = _storedEffects.get(getOwner().getClassIndex());
		
		if (list != null)
			return list;
		
		list = new ArrayList<StoredEffect>();
		
		try
		{
			for (EffectRow row : PlayerRepository.getInstance().loadEffects(getOwner().getObjectId(),
					getOwner().getClassIndex()))
			{
				list.add(new StoredEffect(row));
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		_storedEffects.set(getOwner().getClassIndex(), list);
		
		return list;
	}
}
