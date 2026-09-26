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
package com.l2jfree.gameserver.communitybbs.Manager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javolution.util.FastList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.GMAudit;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExMailArrived;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.L2TextBuilder;

/**
 * @author Vital
 */
public class AuctionBBSManager extends BaseBBSManager
{
	private final static Log _log = LogFactory.getLog(AuctionBBSManager.class);
	
	private static FastList<Integer> _lotsBidded = new FastList<Integer>();
	
	public static AuctionBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private class LotList
	{
		private Integer lotId;
		private Integer ownerId;
		private Integer itemId;
		private Integer objectId;
		private Long count;
		private Integer enchantLevel;
		private Integer currency;
		private Integer startingBid;
		private Integer bidIncrement;
		private Integer buyNow;
		private Long endDate;
		private String endDateFormated;
		private Boolean isProcessed;
	}
	
	private class BidList
	{
		private Integer bidderId;
		private Long bidAmount;
		private String bidDateFormated;
	}
	
	public FastList<LotList> getLots()
	{
		FastList<LotList> _lots = new FastList<LotList>();
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT * FROM auction_lots ORDER BY endDate");
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				LotList lot = new LotList();
				lot.lotId = result.getInt("lotId");
				lot.ownerId = result.getInt("ownerId");
				lot.itemId = result.getInt("itemId");
				lot.objectId = result.getInt("objectId");
				lot.count = result.getLong("count");
				lot.enchantLevel = result.getInt("enchantLevel");
				lot.currency = result.getInt("currency");
				lot.startingBid = result.getInt("startingBid");
				lot.bidIncrement = result.getInt("bidIncrement");
				lot.buyNow = result.getInt("buyNow");
				lot.endDate = result.getLong("endDate");
				lot.endDateFormated = new SimpleDateFormat("MMM dd, HH:mm").format(new Date(result.getLong("endDate")));
				lot.isProcessed = result.getBoolean("processed");
				_lots.add(lot);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return _lots;
	}
	
	public FastList<BidList> getBids(int lotId)
	{
		FastList<BidList> _bids = new FastList<BidList>();
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT * FROM auction_bids WHERE lotId = ? ORDER BY bidDate DESC");
			statement.setInt(1, lotId);
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				BidList bid = new BidList();
				bid.bidderId = result.getInt("bidderId");
				bid.bidAmount = result.getLong("bidAmount");
				bid.bidDateFormated =
						new SimpleDateFormat("MMM dd, HH:mm:ss").format(new Date(result.getLong("bidDate")));
				_bids.add(bid);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return _bids;
	}
	
	private LotList getLot(int lotId)
	{
		for (LotList temp : getLots())
			if (temp.lotId == lotId)
				return temp;
		
		return new LotList();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsecmd(java
	 * .lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		if (command.equals("_bbsauction"))
		{
			showAuctionPage(activeChar, 1, "All", false);
		}
		else if (command.startsWith("_bbsauction;"))
		{
			String[] params;
			params = command.split(";");
			showAuctionPage(activeChar, Integer.parseInt(params[1]), params[2], Boolean.parseBoolean(params[3]));
		}
		else if (command.startsWith("_bbsauction_view "))
		{
			showLotPage(activeChar, Integer.parseInt(command.substring(17)));
		}
		else if (command.startsWith("_bbsauction_bid "))
		{
			String[] params;
			params = command.split(" ");
			if (params.length == 5)
			{
				try
				{
					addBid(activeChar, Integer.parseInt(params[1]), Long.valueOf(params[2]),
							Integer.parseInt(params[3]), Long.valueOf(params[4].replaceAll(",", "")));
				}
				catch (NumberFormatException e)
				{
					activeChar.sendMessage("Your must enter a number to bid!");
				}
			}
			showLotPage(activeChar, Integer.parseInt(params[1]));
			showBidPage(activeChar, Integer.parseInt(params[1]));
		}
		else if (command.equals("_bbsauction_new"))
		{
			showNewAuctionPage(activeChar);
		}
		else if (command.startsWith("_bbsauction_new "))
		{
			int hours, currency, objectId;
			Long startingBid, increment, buyNow, count;
			String[] params;
			params = command.split(" ");
			if (params.length != 8)
				return;
			
			hours = Integer.parseInt(params[1]);
			currency = Integer.parseInt(params[2]);
			startingBid = Long.parseLong(params[3]);
			increment = Long.parseLong(params[4]);
			buyNow = Long.parseLong(params[5]);
			try
			{
				count = Long.parseLong(params[6].replaceAll(",", ""));
				objectId = Integer.parseInt(params[7]);
			}
			catch (NumberFormatException e)
			{
				activeChar.sendMessage("Error: Please enter how many of the item you would like to auction!");
				showInventoryPage(activeChar, 1, hours, currency, startingBid, increment, buyNow);
				return;
			}
			if (hours > 168 || startingBid > Long.valueOf("500000000") || increment > Long.valueOf("1000000")
					|| buyNow > Long.valueOf("2000000000") || count > Long.valueOf("2000000000") || hours < 1
					|| startingBid < Long.valueOf("0") || increment < Long.valueOf("0") || buyNow < Long.valueOf("0")
					|| count < Long.valueOf("1") || (buyNow <= startingBid && buyNow > Long.valueOf("0")))
			{
				activeChar.sendMessage("Error: One of your fields was incorrect!");
				return;
			}
			addAuction(activeChar, hours, currency, startingBid, increment, buyNow, count, objectId);
			
			showAuctionPage(activeChar, 1, "All", false);
			showComfirmationPage(activeChar);
		}
		else if (command.startsWith("_bbsauction_new_next "))
		{
			int hours, currency, page;
			Long startingBid, increment, buyNow;
			String[] params;
			params = command.split(" ");
			
			try
			{
				page = Integer.parseInt(params[1].replaceAll(",", ""));
				hours = Integer.parseInt(params[2].replaceAll(",", ""));
				currency =
						(params[3].equals("Adena") ? PlayerInventory.ADENA_ID : (params[3].equals("Ancient_Adena")
								? PlayerInventory.ANCIENT_ADENA_ID : (params[3].equals(ItemTable.getInstance()
										.getTemplate(Config.AUCTION_SPECIAL_CURRENCY).getName().replace(' ', '_'))
										? Config.AUCTION_SPECIAL_CURRENCY : Integer.parseInt(params[3]))));
				startingBid = Long.parseLong(params[4].replaceAll(",", ""));
				increment = Long.parseLong(params[5].replaceAll(",", ""));
				buyNow = Long.parseLong(params[6].replaceAll(",", ""));
				if (hours > 168 || startingBid > Long.valueOf("500000000") || increment > Long.valueOf("1000000")
						|| buyNow > Long.valueOf("2000000000") || hours < 1 || startingBid < Long.valueOf("0")
						|| increment < Long.valueOf("0") || buyNow < Long.valueOf("0")
						|| (buyNow <= startingBid && buyNow > Long.valueOf("0")))
				{
					activeChar.sendMessage("Error: One of your fields was incorrect!");
					return;
				}
				
				showInventoryPage(activeChar, page, hours, currency, startingBid, increment, buyNow);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Error: One of your fields was incorrect!");
				_log.error("", e);
			}
		}
		else if (command.startsWith("_bbsauction_buy_now "))
		{
			showBuyNowPage(activeChar, Integer.parseInt(command.substring(20)));
		}
		else if (command.startsWith("_bbsauction_buy_now_confirm "))
		{
			LotList lot = getLot(Integer.parseInt(command.substring(28)));
			if (addBid(activeChar, lot.lotId, 0, lot.currency, lot.buyNow))
			{
				endAuction(lot.lotId);
				showBuyNowComfirmationPage(activeChar);
			}
		}
		else
		{
			notImplementedYet(activeChar, command);
		}
	}
	
	private void showAuctionPage(L2Player activeChar, int page, String viewOnly, boolean showEnded)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 9 : (page * 10) - 1);
		minIndex = maxIndex - 9;
		getLotsBiddedOn(activeChar);
		
		final L2TextBuilder html = L2TextBuilder.newInstance();
		html.append("<html>");
		html.append("<body><br><br>");
		
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _bbsauction;1;"
				+ viewOnly + ";" + showEnded + "\">" + (showEnded ? "Ended" : "Active") + " Auctions</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770 bgcolor=808080>");
		html.append("<tr><td height=10></td>");
		html.append("</tr><tr>");
		html.append("<td fixWIDTH=5></td>");
		html.append("<td><a action=\"bypass _bbsauction_new\">[New Auction]</a></td>");
		html.append("<td><a action=\"bypass _bbsauction;1;" + viewOnly + ";" + (showEnded ? "false" : "true") + "\">["
				+ (showEnded ? "Active" : "Ended") + " Auctions]</a></td>");
		html.append("<td fixWIDTH=350></td>");
		html.append("<td>View Only:</td>");
		html.append("<td fixWIDTH=125><combobox width=115 var=Combo list=\"All;Ancient;Big Blunt;Big Sword;Blunt;Bow;Crossbow;Dagger;Dual Fist;Dual Sword;Etc;Fist;Heavy Armor;Light Armor;Robe Armor;Material;Other;Other Armor;PetCollar;Pet;Pole;Potion;Quest;Rapier;Receipe;Scroll;Seed;Shield;Shot;Sword\"></td>");
		html.append("<td><button value=\"Go\" action=\"Write Auction Sort _ Combo Combo Combo\" width=20 height=16 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td fixWIDTH=5></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td>");
		html.append("</tr></table>");
		html.append("<br>");
		html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=380 align=center>Item Up For Auction</td>");
		html.append("<td FIXWIDTH=100 align=center>Current Bid</td>");
		html.append("<td FIXWIDTH=100 align=center>Buy Now</td>");
		html.append("<td FIXWIDTH=60 align=center>Currency</td>");
		html.append("<td FIXWIDTH=120 align=center>Ending Date</td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr></table>");
		
		for (LotList lot : getLots())
		{
			long highestBid = getHighestBid(lot.lotId);
			long currentBid = (lot.startingBid > highestBid ? lot.startingBid : highestBid);
			L2Item item = ItemTable.getInstance().getTemplate(lot.itemId);
			if (viewOnly.equals("All") || viewOnly.equals(item.getItemType().toString()))
			{
				if ((lot.endDate > System.currentTimeMillis() && !showEnded)
						|| (lot.endDate < System.currentTimeMillis() && showEnded))
				{
					if (index < minIndex)
					{
						index++;
						continue;
					}
					if (index > maxIndex)
						break;
					html.append("<table border=0 cellspacing=0 cellpadding=2 width=770 "
							+ (_lotsBidded.contains(lot.lotId) ? "bgcolor=\"333388\"" : (activeChar.getObjectId()
									.equals(lot.ownerId) ? "bgcolor=\"337722\"" : "")) + ">");
					html.append("<tr>");
					html.append("<td FIXWIDTH=5></td>");
					if (item.isEquipable())
						html.append("<td FIXWIDTH=380 align=left valign=top><a action=\"bypass _bbsauction_view "
								+ lot.lotId + "\">" + "+" + lot.enchantLevel + " " + item.getName() + "</a></td>");
					else if (!lot.enchantLevel.equals(0))
						html.append("<td FIXWIDTH=380 align=left valign=top><a action=\"bypass _bbsauction_view "
								+ lot.lotId + "\">" + "Level " + lot.enchantLevel + " " + item.getName() + "</a></td>");
					else if (!lot.count.equals(Long.valueOf("1")))
						html.append("<td FIXWIDTH=380 align=left valign=top><a action=\"bypass _bbsauction_view "
								+ lot.lotId + "\">" + "(" + Util.formatAdena(lot.count.intValue()) + ") "
								+ item.getName() + "</a></td>");
					else
						html.append("<td FIXWIDTH=380 align=left valign=top><a action=\"bypass _bbsauction_view "
								+ lot.lotId + "\">" + item.getName() + "</a></td>");
					html.append("<td FIXWIDTH=100 align=center valign=center>" + Util.formatAdena(currentBid)
							+ "</td>");
					html.append("<td FIXWIDTH=100 align=center valign=center>"
							+ (lot.buyNow.equals(0) || currentBid >= lot.buyNow ? "-" : (lot.ownerId.equals(activeChar
									.getObjectId()) || showEnded ? "Buy Now!"
									: "<a action=\"bypass _bbsauction_buy_now " + lot.lotId + "\">Buy Now!</a>"))
							+ "</td>");
					html.append("<td FIXWIDTH=60 align=center valign=center><img src=icon."
							+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "etc_adena_i00" : (lot.currency
									.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "etc_ancient_adena_i00"
									: Config.AUCTION_SPECIAL_CURRENCY_ICON)) + " width=32 height=20></td>");
					html.append("<td FIXWIDTH=120 align=center valign=center>" + lot.endDateFormated + "</td>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("</tr></table>");
					html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
					html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
					index++;
				}
			}
		}
		html.append("<center><table width=770><tr>");
		html.append("<td align=right><button action=\"bypass _bbsauction;" + (page == 1 ? page : page - 1) + ";"
				+ viewOnly + ";" + showEnded
				+ "\" width=16 height=16 back=\"L2UI_ct1.button_df_left_down\" fore=\"L2UI_ct1.button_df_left\"></td>");
		for (int i = 1; i <= 10; i++)
			html.append("<td align=center fixedwidth=10><a action=\"bypass _bbsauction;" + i + ";" + viewOnly + ";"
					+ showEnded + "\">" + i + "</a></td>");
		html.append("<td align=left><button action=\"bypass _bbsauction;"
				+ (page + 1)
				+ ";"
				+ viewOnly
				+ ";"
				+ showEnded
				+ "\" width=16 height=16 back=\"L2UI_ct1.button_df_right_down\" fore=\"L2UI_ct1.button_df_right\"></td>");
		html.append("</tr></table>");
		
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showLotPage(L2Player activeChar, int lotId)
	{
		int count = 0;
		long currentBid;
		int bidCount;
		LotList lot;
		try
		{
			currentBid = getHighestBid(lotId);
			lot = getLot(lotId);
		}
		catch (Exception e)
		{
			activeChar.sendMessage("This auction lot does not exist anymore.");
			_log.warn("", e);
			return;
		}
		bidCount = countBids(lot.lotId);
		L2Item item = ItemTable.getInstance().getTemplate(lot.itemId);
		
		if (currentBid == 0)
			currentBid = lot.startingBid;
		
		final L2TextBuilder html = L2TextBuilder.newInstance();
		html.append("<html>");
		html.append("<body><br><br>");
		
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _bbsauction\">Active Auctions</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Item:&nbsp;</td>");
		if (item.isEquipable())
			html.append("<td FIXWIDTH=360 height=20 align=left>" + "+" + lot.enchantLevel + " " + item.getName()
					+ "</td>");
		else if (!lot.enchantLevel.equals(0))
			html.append("<td FIXWIDTH=360 height=20 align=left>" + "Level " + lot.enchantLevel + " " + item.getName()
					+ "</td>");
		else if (!lot.count.equals(Long.valueOf("1")))
			html.append("<td FIXWIDTH=360 height=20 align=left>(" + Util.formatAdena(lot.count.intValue()) + ") "
					+ item.getName() + "</td>");
		else
			html.append("<td FIXWIDTH=360 height=20 align=left>" + item.getName() + "</td>");
		html.append("<td FIXWIDTH=150 height=20 align=right>End Date:&nbsp;</td>");
		html.append("<td FIXWIDTH=150 height=20 align=left>" + lot.endDateFormated + "</td>");//
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Owner:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>" + getCharName(lot.ownerId) + "</td>");//
		html.append("<td FIXWIDTH=150 height=20 align=right>Status:&nbsp;</td>");
		if (lot.endDate > System.currentTimeMillis())
			html.append("<td FIXWIDTH=150 height=20 align=left><font color=00FF00>Active</font></td>");//
		else
			html.append("<td FIXWIDTH=150 height=20 align=left><font color=FF0000>Ended</font></td>");//
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Bids:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>" + Util.formatAdena(bidCount) + "</td>");//
		html.append("<td FIXWIDTH=150 height=20 align=right>Buy Now Price:&nbsp;</td>");
		if (lot.buyNow.equals(0))
			html.append("<td FIXWIDTH=150 height=20 align=left>None</td>");
		else
			html.append("<td FIXWIDTH=150 height=20 align=left>" + lot.buyNow + "</td>");
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Current Bid:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>" + Util.formatAdena(getHighestBid(lotId)) + "</td>");//
		html.append("<td FIXWIDTH=150 height=20 align=right>Currency:&nbsp;</td>");
		html.append("<td FIXWIDTH=150 height=20 align=left><img src=icon."
				+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "etc_adena_i00" : (lot.currency
						.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "etc_ancient_adena_i00"
						: Config.AUCTION_SPECIAL_CURRENCY_ICON)) + " width=32 height=16></td>");
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr>");
		html.append("</table>");
		html.append("<br>&nbsp;&nbsp;&nbsp;Last 10 bids<br>");
		html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=85></td>");
		html.append("<td FIXWIDTH=200 align=center>Bidder</td>");
		html.append("<td FIXWIDTH=200 align=center>Bid</td>");
		html.append("<td FIXWIDTH=200 align=center>Date</td>");
		html.append("<td FIXWIDTH=85></td>");
		html.append("</tr></table>");
		
		for (BidList bid : getBids(lot.lotId))
		{
			count++;
			if (count > 10)
				break;
			html.append("<table border=0 cellspacing=0 cellpadding=2 width=770><tr>");
			html.append("<td FIXWIDTH=85></td>");
			html.append("<td FIXWIDTH=200 align=center>" + getCharName(bid.bidderId) + "</td>");
			html.append("<td FIXWIDTH=200 align=center>("
					+ Util.formatAdena(bid.bidAmount)
					+ ") "
					+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "Adena" : (lot.currency
							.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "Ancient Adena" : ItemTable.getInstance()
							.getTemplate(Config.AUCTION_SPECIAL_CURRENCY).getName())) + "</td>");
			html.append("<td FIXWIDTH=200 align=center>" + bid.bidDateFormated + "</td>");
			html.append("<td FIXWIDTH=85></td>");
			html.append("</tr></table>");
			html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
			html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
		}
		html.append("<table border=0 cellspacing=0 cellpadding=2 width=770><tr>");
		if (lot.ownerId.equals(activeChar.getObjectId()))
			html.append("<td>You may not place a bid on your own auction lot.</td>");
		else if (lot.endDate < System.currentTimeMillis())
			html.append("<td>This auction lot has ended.</td>");
		else
		{
			html.append("<td>Please enter a bid more than "
					+ Util.formatAdena((bidCount != 0 ? (currentBid + lot.bidIncrement) : currentBid))
					+ " "
					+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "Adena" : (lot.currency
							.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "Ancient Adena" : ItemTable.getInstance()
							.getTemplate(Config.AUCTION_SPECIAL_CURRENCY).getName())) + "</td>");
			html.append("</tr><tr>");
			html.append("<td><button value=\"Bid Window\" action=\"bypass _bbsauction_bid " + lot.lotId
					+ "\" width=100 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr></table>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showBidPage(L2Player activeChar, int lotId)
	{
		LotList lot = null;
		try
		{
			lot = getLot(lotId);
		}
		catch (Exception e)
		{
			_log.warn("Cannot show auction bid page!", e);
			return;
		}
		L2Item item = ItemTable.getInstance().getTemplate(lot.itemId);
		long currentBid = getHighestBid(lotId);
		int bidCount = countBids(lot.lotId);
		
		if (currentBid == 0)
			currentBid = lot.startingBid;
		
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance("<html><body>");
		
		html.append("<title>Bidding Window</title>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280 bgcolor=808080>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=100 align=right>Item:</td>");
		html.append("<td FIXWIDTH=180>" + item.getName() + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Enchant Level:</td>");
		html.append("<td FIXWIDTH=180 valign=top>" + lot.enchantLevel + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Count:</td>");
		html.append("<td FIXWIDTH=180>" + Util.formatAdena(lot.count.intValue()) + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Currency:</td>");
		html.append("<td FIXWIDTH=180><img src=icon."
				+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "etc_adena_i00" : (lot.currency
						.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "etc_ancient_adena_i00"
						: Config.AUCTION_SPECIAL_CURRENCY_ICON)) + " width=32 height=16></td>");
		html.append("</tr></table>");
		html.append("<br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280><tr><td FIXWIDTH=280 height=20></td></tr></table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280 bgcolor=808080>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=100 align=right>Current Bid:</td>");
		html.append("<td FIXWIDTH=180>" + Util.formatAdena(currentBid) + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Buy Now Price:</td>");
		html.append("<td FIXWIDTH=180 valign=top>" + lot.buyNow + "</td>");
		html.append("</tr></table>");
		html.append("<br><br><br>");
		if (lot.ownerId.equals(activeChar.getObjectId()))
			html.append("You may not place a bid on your own auction lot.");
		else if (lot.endDate < System.currentTimeMillis())
			html.append("The auction has ended.");
		else
		{
			html.append("<td>Please enter a bid more then "
					+ Util.formatAdena((bidCount != 0 ? (currentBid + lot.bidIncrement) : currentBid))
					+ " "
					+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "Adena" : (lot.currency
							.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "Ancient Adena" : ItemTable.getInstance()
							.getTemplate(Config.AUCTION_SPECIAL_CURRENCY).getName())) + "</td>");
			html.append("<edit var=\"Value\" width=150 height=11 length=\"13\"><br>");
			html.append("<button value=\"Place Bid\" action=\"bypass -h _bbsauction_bid " + lot.lotId + " "
					+ lot.bidIncrement + " " + lot.currency
					+ " $Value\" width=80 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
		}
		
		html.append("</body></html>");
		
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showNewAuctionPage(L2Player activeChar)
	{
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance("<html><body>");
		
		html.append("<title>New Auction Lot Window</title>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=280 height=30>Please enter how many hours you want the auction to last. (1 - 168)</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><edit var=\"Time\" width=50 height=11 length=\"3\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30>Please select a currency.</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><combobox width=200 var=Currency list=\"Adena;Ancient_Adena;"
				+ ItemTable.getInstance().getTemplate(Config.AUCTION_SPECIAL_CURRENCY).getName().replace(' ', '_')
				+ "\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30>Please enter a minimum starting bid price. (0 - 500,000,000)</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><edit var=\"StartingBid\" width=150 height=11 length=\"11\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30>Please enter a bid increment. (1 - 1,000,000)</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><edit var=\"Increment\" width=150 height=11 length=\"9\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30>Please enter a buy now price. (0 = Off , 1 - 2,000,000,000)</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><edit var=\"BuyNow\" width=150 height=11 length=\"13\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30 align=right><button value=\"Continue to next page\" action=\"bypass _bbsauction_new_next 1 $Time $Currency $StartingBid $Increment $BuyNow\" width=175 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=20></td>");
		html.append("</tr>");
		html.append("</table>");
		
		html.append("</body></html>");
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showInventoryPage(L2Player activeChar, int page, int time, int currency, long startingBid,
			long increment, long buyNow)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 19 : (page * 20) - 1);
		minIndex = maxIndex - 19;
		
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance("<html><body>");
		
		html.append("<title>New Auction Lot Window</title>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280><tr>");
		html.append("<td FIXWIDTH=280 height=20 align=left><button value=\"<< Prev\" action=\"bypass _bbsauction_new_next "
				+ (page == 1 ? page : page - 1)
				+ " "
				+ time
				+ " "
				+ currency
				+ " "
				+ startingBid
				+ " "
				+ increment
				+ " " + buyNow + "\" width=75 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td FIXWIDTH=280 height=20 align=right><button value=\"Next >>\" action=\"bypass _bbsauction_new_next "
				+ (page + 1)
				+ " "
				+ time
				+ " "
				+ currency
				+ " "
				+ startingBid
				+ " "
				+ increment
				+ " "
				+ buyNow
				+ "\" width=75 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=280 height=30>Please enter how many of the item your going to put up for auction. If the item is not stackable, this field will not be read. (1 - 2,000,000,000)</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30><edit var=\"Count\" width=150 height=11 length=\"13\"></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=280 height=30>Please select an item that you wish to put up for auction. If items are not listed on this page, please use the buttons at the top to help find them.</td>");
		html.append("</tr>");
		for (L2ItemInstance item : activeChar.getInventory().getItems())
		{
			if (((item.isTradeable() && !item.isAugmented() && !item.isShadowItem() && !Config.AUCTION_EXCLUDED_ITEMS_LIST
					.contains(item.getItemId())) || Config.AUCTION_INCLUDED_ITEMS_LIST.contains(item.getItemId()))
					&& !item.isEquipped())
			{
				
				if (index < minIndex)
				{
					index++;
					continue;
				}
				if (index > maxIndex)
					break;
				if (item.isStackable())
				{
					html.append("<tr>");
					html.append("<td FIXWIDTH=280><a action=\"bypass _bbsauction_new " + time + " " + currency + " "
							+ startingBid + " " + increment + " " + buyNow + " $Count " + item.getObjectId() + "\">("
							+ Util.formatAdena(item.getCount()) + ") " + item.getName() + "</a></td>");
					html.append("</tr>");
				}
				else
				{
					html.append("<tr>");
					if (item.getEnchantLevel() > 0)
						html.append("<td FIXWIDTH=280><a action=\"bypass _bbsauction_new " + time + " " + currency
								+ " " + startingBid + " " + increment + " " + buyNow + " " + 1 + " "
								+ item.getObjectId() + "\">" + (item.isEquipable() ? "+" : "Level ")
								+ item.getEnchantLevel() + " " + item.getName() + "</a></td>");
					else
						html.append("<td FIXWIDTH=280><a action=\"bypass _bbsauction_new " + time + " " + currency
								+ " " + startingBid + " " + increment + " " + buyNow + " " + 1 + " "
								+ item.getObjectId() + "\">" + item.getName() + "</a></td>");
					html.append("</tr>");
				}
				html.append("<tr>");
				html.append("<td FIXWIDTH=280 height=15></td>");
				html.append("</tr>");
				index++;
			}
		}
		html.append("<tr>");
		html.append("<td FIXWIDTH=280 height=20></td>");
		html.append("</tr>");
		html.append("</table>");
		
		html.append("</body></html>");
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showBuyNowPage(L2Player activeChar, int lotId)
	{
		LotList lot = null;
		try
		{
			lot = getLot(lotId);
		}
		catch (Exception e)
		{
			_log.warn("Cannot show auction BuyNow page!", e);
			return;
		}
		L2Item item = ItemTable.getInstance().getTemplate(lot.itemId);
		long currentBid = getHighestBid(lotId);
		
		if (currentBid == 0)
			currentBid = lot.startingBid;
		
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance("<html><body>");
		
		html.append("<title>Buy Now Comfirmation Window</title>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280 bgcolor=808080>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=100 align=right>Item:</td>");
		html.append("<td FIXWIDTH=180>" + item.getName() + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Enchant Level:</td>");
		html.append("<td FIXWIDTH=180 valign=top>" + lot.enchantLevel + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Count:</td>");
		html.append("<td FIXWIDTH=180>" + Util.formatAdena(lot.count.intValue()) + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Currency:</td>");
		html.append("<td FIXWIDTH=180><img src=icon."
				+ (lot.currency.equals(PlayerInventory.ADENA_ID) ? "etc_adena_i00" : (lot.currency
						.equals(PlayerInventory.ANCIENT_ADENA_ID) ? "etc_ancient_adena_i00"
						: Config.AUCTION_SPECIAL_CURRENCY_ICON)) + " width=32 height=16></td>");
		html.append("</tr></table>");
		html.append("<br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280><tr><td FIXWIDTH=280 height=20></td></tr></table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280 bgcolor=808080>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=100 align=right>Current Bid:</td>");
		html.append("<td FIXWIDTH=180>" + Util.formatAdena(currentBid) + "</td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100 align=right>Buy Now Price:</td>");
		html.append("<td FIXWIDTH=180 valign=top>" + Util.formatAdena(lot.buyNow) + "</td>");
		html.append("</tr></table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=280>");
		html.append("<tr>");
		if (lot.ownerId.equals(activeChar.getObjectId()))
			html.append("<td>You may not place a bid on your own auction lot.</td>");
		else if (lot.endDate < System.currentTimeMillis())
			html.append("<td>This auction lot has ended.</td>");
		else
		{
			html.append("<td FIXWIDTH=280>Are you sure you wish to buy now?</td>");
			html.append("</tr><tr>");
			html.append("<td FIXWIDTH=280 align=center><button value=\"BUY NOW!\" action=\"bypass _bbsauction_buy_now_confirm "
					+ lot.lotId
					+ "\" width=100 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		}
		html.append("</tr>");
		html.append("</table>");
		
		html.append("</body></html>");
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showComfirmationPage(L2Player activeChar)
	{
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance();
		html.append("<html><body>");
		html.append("<title>Auction Comfirmation Window</title>");
		html.append("Congrats! Your item is now up for auction!");
		html.append("</body></html>");
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showBuyNowComfirmationPage(L2Player activeChar)
	{
		NpcHtmlMessage nhm = new NpcHtmlMessage(5);
		L2TextBuilder html = L2TextBuilder.newInstance();
		html.append("<html><body>");
		html.append("<title>Auction Buy Now Comfirmation Window</title>");
		html.append("Congrats! You have won the auction by default, the auction will soon be processed.");
		html.append("</body></html>");
		nhm.setHtml(html.moveToString());
		activeChar.sendPacket(nhm);
		
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		activeChar.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void addAuction(L2Player activeChar, int hours, int currency, long statingBid, long increment,
			long buyNow, long count, int objectId)
	{
		long epochHours = hours * 60 * 60 * 1000;
		L2ItemInstance item = activeChar.getInventory().getItemByObjectId(objectId);
		
		if (activeChar.destroyItem("Auction New Lot", objectId, (int)count, activeChar, true))
		{
			java.sql.Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement =
						con.prepareStatement("INSERT INTO auction_lots (ownerId, itemId, objectId, count, enchantLevel, currency, startingBid, bidIncrement, buyNow, endDate) VALUES (?,?,?,?,?,?,?,?,?,?)");
				statement.setInt(1, activeChar.getObjectId());
				statement.setInt(2, item.getItemId());
				statement.setInt(3, (count == 1 ? objectId : IdFactory.getInstance().getNextId()));
				statement.setLong(4, count);
				statement.setInt(5, item.getEnchantLevel());
				statement.setInt(6, currency);
				statement.setLong(7, statingBid);
				statement.setLong(8, increment);
				statement.setLong(9, buyNow);
				statement.setLong(10, System.currentTimeMillis() + epochHours);
				statement.execute();
				statement.close();
				
				if (activeChar.isGM() && Config.GM_AUDIT)
					GMAudit.auditGMAction(
							activeChar,
							"auction",
							"_bbsauction_new",
							count + " - " + item.getEnchantLevel() + " - " + item.getItemId() + " - "
									+ item.getItemName() + " - " + objectId);
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	public void processAuctions()
	{
		boolean playerWasOnline = false;
		BidList bid = null;
		
		for (LotList lot : getLots())
		{
			if (lot.endDate < System.currentTimeMillis() && !lot.isProcessed)
			{
				bid = getHighestBidder(lot.lotId);
				L2Item itemWon = ItemTable.getInstance().getTemplate(lot.itemId);
				if (countBids(lot.lotId) != 0)
				{
					if (bid != null)
					{
						java.sql.Connection con = null;
						try
						{
							con = L2DatabaseFactory.getInstance().getConnection(con);
							for (L2Player player : L2World.getInstance().getAllPlayers())
							{
								if (player.getObjectId().equals(lot.ownerId))
								{
									L2ItemInstance item =
											player.getInventory().addItem("Auction Give Bid", lot.currency,
													bid.bidAmount, null, null);
									InventoryUpdate iu = new InventoryUpdate();
									iu.addItem(item);
									player.sendPacket(iu);
									SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
									sm.addItemName(item);
									sm.addItemNumber(bid.bidAmount);
									player.sendPacket(sm);
									player.sendPacket(SystemMessageId.NEW_MAIL);
									player.sendPacket(ExMailArrived.STATIC_PACKET);
									playerWasOnline = true;
									break;
								}
							}
							if (!playerWasOnline)
								addItemToInventory(con, lot.ownerId, IdFactory.getInstance().getNextId(), lot.currency,
										bid.bidAmount, 0);
							sendMail(
									con,
									lot.ownerId,
									"Auctioneer",
									"Your auction was success!",
									"Item Sold: "
											+ (!lot.count.equals(1) ? "(" + Util.formatAdena(lot.count.intValue())
													+ ")" : (itemWon.isEquipable() ? "+" : "Level ") + lot.enchantLevel)
											+ " " + itemWon.getName() + "<br>Check your inventory for "
											+ Util.formatAdena(bid.bidAmount) + " "
											+ ItemTable.getInstance().getTemplate(lot.currency));
							
							for (L2Player player : L2World.getInstance().getAllPlayers())
								if (player.getObjectId().equals(bid.bidderId))
								{
									player.sendPacket(SystemMessageId.NEW_MAIL);
									player.sendPacket(ExMailArrived.STATIC_PACKET);
									break;
								}
							addItemToInventory(con, bid.bidderId, (lot.count.equals(1) ? lot.objectId : IdFactory
									.getInstance().getNextId()), lot.itemId, lot.count, lot.enchantLevel);
							
							sendMail(con, bid.bidderId, "Bidder", "You won an auction!!", "Please relog to see your "
									+ (!lot.count.equals(1) ? "(" + Util.formatAdena(lot.count.intValue()) + ")"
											: (itemWon.isEquipable() ? "+" : "Level ") + lot.enchantLevel) + " "
									+ itemWon.getName());
							PreparedStatement statement =
									con.prepareStatement("UPDATE auction_lots SET processed = 'true' WHERE lotId = ?");
							statement.setInt(1, lot.lotId);
							statement.execute();
							statement.close();
						}
						catch (Exception e)
						{
							_log.warn("", e);
						}
						finally
						{
							L2DatabaseFactory.close(con);
						}
					}
				}
				else
				{
					java.sql.Connection con = null;
					try
					{
						con = L2DatabaseFactory.getInstance().getConnection(con);
						for (L2Player player : L2World.getInstance().getAllPlayers())
							if (player.getObjectId().equals(bid.bidderId))
							{
								player.sendPacket(SystemMessageId.NEW_MAIL);
								player.sendPacket(ExMailArrived.STATIC_PACKET);
								break;
							}
						
						addItemToInventory(con, lot.ownerId, (lot.count.equals(1) ? lot.objectId : IdFactory
								.getInstance().getNextId()), lot.itemId, lot.count, lot.enchantLevel);
						
						sendMail(
								con,
								lot.ownerId,
								"Auctioneer",
								"Your auction did not have any bids",
								"Please relog to see your "
										+ (!lot.count.equals(1) ? "(" + Util.formatAdena(lot.count.intValue()) + ")"
												: (itemWon.isEquipable() ? "+" : "Level ") + lot.enchantLevel) + " "
										+ itemWon.getName());
						
						PreparedStatement statement =
								con.prepareStatement("UPDATE auction_lots SET processed = 'true' WHERE lotId = ?");
						statement.setInt(1, lot.lotId);
						statement.execute();
						statement.close();
					}
					catch (Exception e)
					{
						_log.warn("", e);
					}
					finally
					{
						L2DatabaseFactory.close(con);
					}
				}
			}
		}
	}
	
	public void removeOldAuctions()
	{
		for (LotList lot : getLots())
		{
			if ((lot.endDate + Long.valueOf("604800000")) < System.currentTimeMillis() && lot.isProcessed)
			{
				java.sql.Connection con = null;
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					PreparedStatement statement = con.prepareStatement("DELETE FROM auction_bids WHERE lotId = ?");
					statement.setInt(1, lot.lotId);
					statement.execute();
					statement.close();
					
					statement = con.prepareStatement("DELETE FROM auction_lots WHERE lotId = ?");
					statement.setInt(1, lot.lotId);
					statement.execute();
					statement.close();
				}
				catch (Exception e)
				{
					_log.warn("", e);
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
			}
		}
		
	}
	
	private boolean addBid(L2Player activeChar, int lotId, long bidIncrement, int currency, long bidAmount)
	{
		boolean playerWasOnline = false;
		long charged = AuctionBidAmount.transferable(bidAmount);
		if (charged == 0)
		{
			activeChar.sendMessage("Your bid must be a positive amount.");
			return false;
		}
		
		long currentBid = getHighestBid(lotId);
		int prevBidderId;
		long prevBidAmount;
		LotList lot = getLot(lotId);
		int bidCount = countBids(lot.lotId);
		
		if (currentBid == 0)
			currentBid = lot.startingBid;
		
		if (lot.endDate < System.currentTimeMillis())
		{
			activeChar.sendMessage("This auction lot has already ended and is no longer taking bids.");
			return false;
		}
		
		if (charged <= activeChar.getInventory().getInventoryItemCount(currency, 0))
		{
			if (charged > (bidCount != 0 ? currentBid + bidIncrement : currentBid))
			{
				java.sql.Connection con = null;
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					PreparedStatement statement =
							con.prepareStatement("SELECT * FROM auction_bids WHERE lotId = ? ORDER BY bidId DESC");
					statement.setInt(1, lotId);
					ResultSet result = statement.executeQuery();
					if (result.next())
					{
						prevBidderId = result.getInt("bidderId");
						prevBidAmount = result.getLong("bidAmount");
						statement.close();
						
						for (L2Player player : L2World.getInstance().getAllPlayers())
						{
							if (player.getObjectId().equals(prevBidderId))
							{
								L2ItemInstance item =
										player.getInventory().addItem("Auction", currency, prevBidAmount, null, null);
								InventoryUpdate iu = new InventoryUpdate();
								iu.addItem(item);
								player.sendPacket(iu);
								SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
								sm.addItemName(item);
								sm.addItemNumber(prevBidAmount);
								player.sendPacket(sm);
								player.sendPacket(SystemMessageId.NEW_MAIL);
								player.sendPacket(ExMailArrived.STATIC_PACKET);
								playerWasOnline = true;
								break;
							}
						}
						if (!playerWasOnline)
							addItemToInventory(con, prevBidderId, IdFactory.getInstance().getNextId(), currency,
									prevBidAmount, 0);
						sendMail(con, prevBidderId, "Bidder", "You've been out bidded!",
								"Someone has out bidded you!<br1><a action=\"bypass _bbsauction_bid " + lotId
										+ "\">Click here to bid fast!</a>");
					}
					
					con = L2DatabaseFactory.getInstance().getConnection(con);
					statement =
							con.prepareStatement("INSERT INTO auction_bids (lotId, bidderId, bidAmount, bidDate) VALUES (?,?,?,?)");
					statement.setInt(1, lotId);
					statement.setInt(2, activeChar.getObjectId());
					statement.setLong(3, charged);
					statement.setLong(4, System.currentTimeMillis());
					statement.execute();
					statement.close();
					if (currency == PlayerInventory.ADENA_ID)
						activeChar.reduceAdena("Auction Bid", charged, activeChar, true);
					else if (currency == PlayerInventory.ANCIENT_ADENA_ID)
						activeChar.reduceAncientAdena("Auction Bid", charged, activeChar, true);
					else
						activeChar.destroyItemByItemId("Auction Bid", currency, charged, activeChar, true);
					activeChar.sendMessage("Your bid has been placed.");
					
					if (activeChar.isGM() && Config.GM_AUDIT)
						GMAudit.auditGMAction(activeChar, "auction", "_bbsauction_bid", lotId + " - " + currency
								+ " - " + charged);
					
				}
				catch (Exception e)
				{
					_log.warn("", e);
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
			}
			else
			{
				activeChar.sendMessage("Your bid is too low! Try again!");
				return false;
			}
		}
		else
		{
			activeChar.sendMessage("You do not have enough " + ItemTable.getInstance().getTemplate(currency).getName()
					+ " to bid!");
			return false;
		}
		return true;
	}
	
	private void addItemToInventory(java.sql.Connection con, int charId, int objectId, int currency, long count,
			int enchantLevel) throws SQLException
	{
		PreparedStatement statement =
				con.prepareStatement("INSERT INTO items (owner_id, object_id, item_id, count, enchant_level, loc, loc_data) VALUES (?,?,?,?,?,?,?)");
		statement.setInt(1, charId);
		statement.setInt(2, objectId);
		statement.setInt(3, currency);
		statement.setLong(4, count);
		statement.setInt(5, enchantLevel);
		statement.setString(6, "INVENTORY");
		statement.setInt(7, 0);
		statement.execute();
		statement.close();
	}
	
	private void sendMail(java.sql.Connection con, int charId, String recipient, String subject, String message)
			throws SQLException
	{
		PreparedStatement statement =
				con.prepareStatement("INSERT INTO character_mail (charId, senderId, location, recipientNames, subject, message, sentDate, deleteDate, unread) VALUES (?,?,?,?,?,?,?,?,?)");
		statement.setInt(1, charId);
		statement.setInt(2, 100100);
		statement.setString(3, "inbox");
		statement.setString(4, recipient);
		statement.setString(5, subject);
		statement.setString(6, message);
		statement.setLong(7, System.currentTimeMillis());
		statement.setLong(8, System.currentTimeMillis() + Long.valueOf("7948804000"));
		statement.setString(9, "true");
		statement.execute();
		statement.close();
	}
	
	private void endAuction(int lotId)
	{
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("UPDATE auction_lots SET endDate = ? WHERE lotId = ?");
			statement.setLong(1, System.currentTimeMillis());
			statement.setInt(2, lotId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private int countBids(int lotId)
	{
		int count = 0;
		
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT COUNT(*) FROM auction_bids WHERE lotId = ?");
			statement.setInt(1, lotId);
			ResultSet result = statement.executeQuery();
			result.next();
			count = result.getInt(1);
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return count;
	}
	
	private long getHighestBid(int lotId)
	{
		long bidAmount = 0;
		
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT MAX(bidAmount) FROM auction_bids WHERE lotId = ?");
			statement.setInt(1, lotId);
			ResultSet result = statement.executeQuery();
			result.next();
			bidAmount = result.getLong(1);
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return bidAmount;
	}
	
	private BidList getHighestBidder(int lotId)
	{
		BidList bid = new BidList();
		
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT * FROM auction_bids WHERE lotId = ? ORDER BY bidId DESC");
			statement.setInt(1, lotId);
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				bid.bidderId = result.getInt("bidderId");
				bid.bidAmount = result.getLong("bidAmount");
				bid.bidDateFormated = "";
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return bid;
	}
	
	private void getLotsBiddedOn(L2Player activeChar)
	{
		java.sql.Connection con = null;
		try
		{
			_lotsBidded = new FastList<Integer>();
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT lotId FROM auction_bids WHERE bidderId = ? GROUP BY 1");
			statement.setInt(1, activeChar.getObjectId());
			ResultSet result = statement.executeQuery();
			while (result.next())
				_lotsBidded.add(result.getInt("lotId"));
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private String getCharName(int charId)
	{
		String charName = "No Name";
		
		java.sql.Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT char_name FROM characters WHERE charId = ?");
			statement.setInt(1, charId);
			ResultSet result = statement.executeQuery();
			result.next();
			charName = result.getString("char_name");
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("couldnt get char name for " + charId, e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return charName;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsewrite
	 * (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String,
	 * com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		if (ar1.equals("Sort"))
		{
			String viewOnly;
			if (ar4.endsWith(" Armor"))
			{
				if (ar4.equals("Other Armor"))
					viewOnly = "None";
				else if (ar4.equals("Robe Armor"))
					viewOnly = "Magic";
				else
					viewOnly = ar4.replace(" Armor", "");
			}
			else
				viewOnly = ar4;
			
			showAuctionPage(activeChar, 1, viewOnly, false);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AuctionBBSManager _instance = new AuctionBBSManager();
	}
}
