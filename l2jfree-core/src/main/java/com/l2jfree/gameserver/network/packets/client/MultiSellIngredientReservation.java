/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

/** An exact, immutable selection of inventory instances for one exchange. */
final class MultiSellIngredientReservation
{
	static final class Debit
	{
		private final L2ItemInstance _item;
		private final long _count;

		Debit(L2ItemInstance item, long count)
		{
			_item = item;
			_count = count;
		}

		L2ItemInstance item()
		{
			return _item;
		}

		long count()
		{
			return _count;
		}
	}

	private final Map<L2ItemInstance, Long> _available = new IdentityHashMap<L2ItemInstance, Long>();
	private final List<Debit> _debits = new ArrayList<Debit>();

	MultiSellIngredientReservation(L2ItemInstance[] inventory)
	{
		for (L2ItemInstance item : inventory)
		{
			if (item != null && item.getCount() > 0 && !item.isEquipped() && !item.isWear())
				_available.put(item, item.isStackable() ? item.getCount() : 1L);
		}
	}

	boolean reserve(int itemId, int enchantment, long count)
	{
		if (count <= 0)
			return false;

		List<L2ItemInstance> candidates = new ArrayList<L2ItemInstance>();
		long remaining = count;
		for (L2ItemInstance item : _available.keySet())
		{
			if (item.getItemId() == itemId && (enchantment < 0 || item.getEnchantLevel() == enchantment))
				candidates.add(item);
		}
		Collections.sort(candidates, new Comparator<L2ItemInstance>()
		{
			@Override
			public int compare(L2ItemInstance left, L2ItemInstance right)
			{
				int byEnchant = Integer.compare(left.getEnchantLevel(), right.getEnchantLevel());
				return byEnchant != 0 ? byEnchant : Integer.compare(left.getObjectId(), right.getObjectId());
			}
		});

		List<Debit> staged = new ArrayList<Debit>();
		for (L2ItemInstance item : candidates)
		{
			long available = _available.get(item);
			long taken = Math.min(remaining, available);
			if (taken > 0)
			{
				staged.add(new Debit(item, taken));
				remaining -= taken;
			}
			if (remaining == 0)
				break;
		}
		if (remaining != 0)
			return false;

		for (Debit debit : staged)
		{
			_available.put(debit.item(), _available.get(debit.item()) - debit.count());
			_debits.add(debit);
		}
		return true;
	}

	List<Debit> debits()
	{
		return Collections.unmodifiableList(_debits);
	}
}
