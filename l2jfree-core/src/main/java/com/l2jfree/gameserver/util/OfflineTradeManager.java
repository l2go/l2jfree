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
package com.l2jfree.gameserver.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.TradeList.TradeItem;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureItem;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.server.RecipeShopMsg;

/**
 * @author hex1r0
 */
public final class OfflineTradeManager
{
	private static final Logger _log = LoggerFactory.getLogger(OfflineTradeManager.class);
	private int _playerCount = 0;
	private int _itemCount = 0;
	private int _recipeCount = 0;
	
	public static OfflineTradeManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public final void restore()
	{
		_log.info("OfflineTradeManager: Restorring...");
		_playerCount = 0;
		_itemCount = 0;
		_recipeCount = 0;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement = con.prepareStatement("SELECT * FROM offline_traders");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				int charId = rset.getInt(1);
				int privateStoreType = Integer.valueOf(rset.getString(2));
				String msg = rset.getString(3);
				
				L2Player p = L2Player.load(charId);
				if (p == null)
					continue;
				
				p.setOnlineStatus(true);
				L2World.getInstance().storeObject(p);
				L2World.getInstance().addOnlinePlayer(p);
				p.spawnMe();
				
				PreparedStatement st2 = con.prepareStatement("SELECT * FROM offline_traders_items WHERE char_id=?");
				st2.setInt(1, charId);
				ResultSet rset2 = st2.executeQuery();
				
				L2ManufactureList manufactureList = new L2ManufactureList();
				while (rset2.next())
				{
					switch (privateStoreType)
					{
						case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						case L2Player.STORE_PRIVATE_SELL:
							p.getSellList().addItem(rset2.getInt(2), rset2.getLong(3), rset2.getLong(4));
							_itemCount++;
							break;
						case L2Player.STORE_PRIVATE_BUY:
							p.getBuyList().addItemByItemId(rset2.getInt(2), rset2.getLong(3), rset2.getLong(4));
							_itemCount++;
							break;
						case L2Player.STORE_PRIVATE_MANUFACTURE:
							manufactureList.add(new L2ManufactureItem(rset2.getInt(2), rset2.getLong(4)));
							_recipeCount++;
							break;
					}
				}
				rset2.close();
				st2.close();
				
				switch (privateStoreType)
				{
					case L2Player.STORE_PRIVATE_PACKAGE_SELL:
						p.getSellList().setPackaged(true);
						//$FALL-THROUGH$
					case L2Player.STORE_PRIVATE_SELL:
						p.getSellList().setTitle(msg);
						p.tryOpenPrivateSellStore(p.getSellList().isPackaged());
						break;
					case L2Player.STORE_PRIVATE_BUY:
						p.getBuyList().setTitle(msg);
						p.tryOpenPrivateBuyStore();
						break;
					case L2Player.STORE_PRIVATE_MANUFACTURE:
						manufactureList.setStoreName(msg);
						p.setCreateList(manufactureList);
						p.broadcastPacket(new RecipeShopMsg(p));
						break;
				}
				
				p.setPrivateStoreType(privateStoreType);
				p.sitDown();
				p.enterOfflineMode();
				p.broadcastUserInfo();
				
				_playerCount++;
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Could not restore char private store list: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		_log.info("OfflineTradeManager: Restored " + _playerCount + " offline traders with " + _itemCount
				+ " items and " + _recipeCount + " recipes!");
	}
	
	public void store()
	{
		_log.info("OfflineTradeManager: Storring...");
		_playerCount = 0;
		_itemCount = 0;
		_recipeCount = 0;
		
		cleanTables();
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			for (L2Player p : L2World.getInstance().getAllPlayers())
			{
				try
				{
					if (p.isInOfflineMode())
					{
						final int privateStoreType = p.getPrivateStoreType();
						TradeList tradeList = null;
						L2ManufactureList manufactureList = null;
						
						switch (privateStoreType)
						{
							case L2Player.STORE_PRIVATE_SELL:
							case L2Player.STORE_PRIVATE_PACKAGE_SELL:
								tradeList = p.getSellList();
								for (TradeItem i : tradeList.getItems())
								{
									PreparedStatement st2 =
											con.prepareStatement("INSERT INTO offline_traders_items VALUES(?,?,?,?)");
									st2.setInt(1, p.getObjectId());
									st2.setInt(2, i.getObjectId());
									st2.setLong(3, i.getCount());
									st2.setLong(4, i.getPrice());
									st2.execute();
									st2.close();
									
									_itemCount++;
								}
								break;
							case L2Player.STORE_PRIVATE_BUY:
								tradeList = p.getBuyList();
								for (TradeItem i : tradeList.getItems())
								{
									PreparedStatement st2 =
											con.prepareStatement("INSERT INTO offline_traders_items VALUES(?,?,?,?)");
									st2.setInt(1, p.getObjectId());
									st2.setInt(2, i.getItem().getItemId());
									st2.setLong(3, i.getCount());
									st2.setLong(4, i.getPrice());
									st2.execute();
									st2.close();
									
									_itemCount++;
								}
								break;
							case L2Player.STORE_PRIVATE_MANUFACTURE:
								manufactureList = p.getCreateList();
								for (L2ManufactureItem i : manufactureList.getList())
								{
									PreparedStatement st2 =
											con.prepareStatement("INSERT INTO offline_traders_items VALUES(?,?,?,?)");
									st2.setInt(1, p.getObjectId());
									st2.setInt(2, i.getRecipeId());
									st2.setLong(3, -1);
									st2.setLong(4, i.getCost());
									st2.execute();
									st2.close();
									
									_recipeCount++;
								}
								break;
						}
						PreparedStatement st = con.prepareStatement("INSERT INTO offline_traders VALUES(?,?,?)");
						st.setInt(1, p.getObjectId());
						st.setString(2, String.valueOf(privateStoreType));
						switch (privateStoreType)
						{
							case L2Player.STORE_PRIVATE_MANUFACTURE:
								if (manufactureList != null)
									st.setString(3, manufactureList.getStoreName());
								else
									st.setString(3, "");
								break;
							case L2Player.STORE_PRIVATE_SELL:
							case L2Player.STORE_PRIVATE_PACKAGE_SELL:
							case L2Player.STORE_PRIVATE_BUY:
								if (tradeList != null)
									st.setString(3, tradeList.getTitle());
								else
									st.setString(3, "");
								break;
							default:
								st.setString(3, "");
						}
						
						st.execute();
						st.close();
						
						_playerCount++;
					}
					//new Disconnection(p).defaultSequence(true);
				}
				catch (Throwable t)
				{
					t.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			_log.error("OfflineTradeManager: Could not store char private store list: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		_log.info("OfflineTradeManager: Stored " + _playerCount + " offline traders with " + _itemCount + " items and "
				+ _recipeCount + " recipes!");
	}
	
	private void cleanTables()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("TRUNCATE TABLE offline_traders");
			statement.execute();
			statement.close();
			statement = con.prepareStatement("TRUNCATE TABLE offline_traders_items");
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("OfflineTradeManager: Could not clear table: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private static final class SingletonHolder
	{
		public static final OfflineTradeManager INSTANCE = new OfflineTradeManager();
	}
}
