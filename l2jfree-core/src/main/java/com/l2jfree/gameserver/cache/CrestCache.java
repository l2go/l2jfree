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
package com.l2jfree.gameserver.cache;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.clan.L2Clan;

/**
 * @author Layane
 */
public class CrestCache
{
	private static final Logger _log = LoggerFactory.getLogger(CrestCache.class);
	
	public static CrestCache getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final FastMap<Integer, byte[]> _cachePledge = new FastMap<Integer, byte[]>().setShared(true);
	private final FastMap<Integer, byte[]> _cachePledgeLarge = new FastMap<Integer, byte[]>().setShared(true);
	private final FastMap<Integer, byte[]> _cacheAlly = new FastMap<Integer, byte[]>().setShared(true);
	
	private int _loadedFiles;
	private long _bytesBuffLen;
	
	private CrestCache()
	{
		new File("data/crests").mkdirs();
		
		convertOldPedgeFiles();
		reload();
	}
	
	public synchronized void reload()
	{
		FileFilter filter = new BmpFilter();
		
		File dir = new File(Config.DATAPACK_ROOT, "data/crests/");
		
		File[] files = dir.listFiles(filter);
		if (files == null)
			files = new File[0];
		byte[] content;
		
		_loadedFiles = 0;
		_bytesBuffLen = 0;
		
		_cachePledge.clear();
		_cachePledgeLarge.clear();
		_cacheAlly.clear();
		
		for (File file : files)
		{
			RandomAccessFile f = null;
			try
			{
				f = new RandomAccessFile(file, "r");
				content = new byte[(int)f.length()];
				f.readFully(content);
				
				if (file.getName().startsWith("Crest_Large_"))
				{
					_cachePledgeLarge.put(Integer.valueOf(file.getName().substring(12, file.getName().length() - 4)),
							content);
				}
				else if (file.getName().startsWith("Crest_"))
				{
					_cachePledge
							.put(Integer.valueOf(file.getName().substring(6, file.getName().length() - 4)), content);
				}
				else if (file.getName().startsWith("AllyCrest_"))
				{
					_cacheAlly.put(Integer.valueOf(file.getName().substring(10, file.getName().length() - 4)), content);
				}
				_loadedFiles++;
				_bytesBuffLen += content.length;
			}
			catch (Exception e)
			{
				_log.warn("Problem with loading crest bmp file: " + file, e);
			}
			finally
			{
				try
				{
					if (f != null)
						f.close();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
		
		_log.info(String.valueOf(this));
	}
	
	public void convertOldPedgeFiles()
	{
		File dir = new File(Config.DATAPACK_ROOT, "data/crests/");
		
		File[] files = dir.listFiles(new OldPledgeFilter());
		
		if (files == null)
			files = new File[0];
		
		for (File file : files)
		{
			int clanId = Integer.parseInt(file.getName().substring(7, file.getName().length() - 4));
			
			_log.info("Found old crest file \"" + file.getName() + "\" for clanId " + clanId);
			
			int newId = IdFactory.getInstance().getNextId();
			
			L2Clan clan = ClanTable.getInstance().getClan(clanId);
			
			if (clan != null)
			{
				removeOldPledgeCrest(clan.getCrestId());
				
				file.renameTo(new File(Config.DATAPACK_ROOT, "data/crests/Crest_" + newId + ".bmp"));
				_log.info("Renamed Clan crest to new format: Crest_" + newId + ".bmp");
				
				Connection con = null;
				
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					PreparedStatement statement =
							con.prepareStatement("UPDATE clan_data SET crest_id = ? WHERE clan_id = ?");
					statement.setInt(1, newId);
					statement.setInt(2, clan.getClanId());
					statement.executeUpdate();
					statement.close();
				}
				catch (SQLException e)
				{
					_log.warn("Could not update the crest id:", e);
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
				
				clan.setCrestId(newId);
				clan.setHasCrest(true);
			}
			else
			{
				_log.info("Clan Id: " + clanId + " does not exist in table.. deleting.");
				file.delete();
			}
		}
	}
	
	@Override
	public String toString()
	{
		return "Cache[Crest]: " + String.format("%.3f", (float)_bytesBuffLen / 1048576) + " megabytes on "
				+ _loadedFiles + " file(s) loaded.";
	}
	
	public int getLoadedFiles()
	{
		return _loadedFiles;
	}
	
	public byte[] getPledgeCrest(int id)
	{
		return _cachePledge.get(id);
	}
	
	public byte[] getPledgeCrestLarge(int id)
	{
		return _cachePledgeLarge.get(id);
	}
	
	public byte[] getAllyCrest(int id)
	{
		return _cacheAlly.get(id);
	}
	
	public void removePledgeCrest(int id)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/Crest_" + id + ".bmp");
		_cachePledge.remove(id);
		try
		{
			crestFile.delete();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public boolean removePledgeCrestLarge(int id)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/Crest_Large_" + id + ".bmp");
		try
		{
			crestFile.delete();
		}
		catch (Exception e)
		{
			_log.warn("", e);
			return false;
		}
		return _cachePledgeLarge.remove(id) != null;
	}
	
	public void removeOldPledgeCrest(int id)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/Pledge_" + id + ".bmp");
		try
		{
			crestFile.delete();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public void removeAllyCrest(int id)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/AllyCrest_" + id + ".bmp");
		_cacheAlly.remove(id);
		try
		{
			crestFile.delete();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public boolean savePledgeCrest(int newId, byte[] data)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/Crest_" + newId + ".bmp");
		try
		{
			FileOutputStream out = new FileOutputStream(crestFile);
			out.write(data);
			out.close();
			_cachePledge.put(newId, data);
			return true;
		}
		catch (IOException e)
		{
			_log.info("Error saving pledge crest" + crestFile + ":", e);
			return false;
		}
	}
	
	public boolean savePledgeCrestLarge(int newId, byte[] data)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/Crest_Large_" + newId + ".bmp");
		try
		{
			FileOutputStream out = new FileOutputStream(crestFile);
			out.write(data);
			out.close();
			_cachePledgeLarge.put(newId, data);
			return true;
		}
		catch (IOException e)
		{
			_log.info("Error saving Large pledge crest" + crestFile + ":", e);
			return false;
		}
	}
	
	public boolean saveAllyCrest(int newId, byte[] data)
	{
		File crestFile = new File(Config.DATAPACK_ROOT, "data/crests/AllyCrest_" + newId + ".bmp");
		try
		{
			FileOutputStream out = new FileOutputStream(crestFile);
			out.write(data);
			out.close();
			_cacheAlly.put(newId, data);
			return true;
		}
		catch (IOException e)
		{
			_log.info("Error saving ally crest" + crestFile + ":", e);
			return false;
		}
	}
	
	class BmpFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return (file.getName().endsWith(".bmp"));
		}
	}
	
	class OldPledgeFilter implements FileFilter
	{
		@Override
		public boolean accept(File file)
		{
			return (file.getName().startsWith("Pledge_"));
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CrestCache _instance = new CrestCache();
	}
}
