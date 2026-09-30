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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;

/**
 * Database backup utility, directly dependent on mysqldump tool.
 * 
 * @author hex1r0
 * @author savormix
 */
public final class DatabaseBackupManager
{
	private static final Logger _log = LoggerFactory.getLogger(DatabaseBackupManager.class);
	
	public static void makeBackup()
	{
		File f = new File(Config.DATAPACK_ROOT, Config.DATABASE_BACKUP_SAVE_PATH);
		if (!f.mkdirs() && !f.exists())
		{
			_log.warn("Could not create folder " + f.getAbsolutePath());
			return;
		}
		
		_log.info("DatabaseBackupManager: backing up `" + Config.DATABASE_BACKUP_DATABASE_NAME + "`...");
		
		Process run = null;
		try
		{
			run =
					Runtime.getRuntime()
							.exec("mysqldump"
									+ " --user="
									+ Config.DATABASE_LOGIN
									+ " --password="
									+ Config.DATABASE_PASSWORD
									+ " --compact --complete-insert --default-character-set=utf8 --extended-insert --lock-tables --quick --skip-triggers "
									+ Config.DATABASE_BACKUP_DATABASE_NAME, null,
									new File(Config.DATABASE_BACKUP_MYSQLDUMP_PATH));
		}
		catch (Exception e)
		{
		}
		finally
		{
			if (run == null)
			{
				_log.warn("Could not execute mysqldump!");
				return;
			}
		}
		
		try
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
			Date time = new Date();
			
			File bf = new File(f, sdf.format(time) + (Config.DATABASE_BACKUP_COMPRESSION ? ".zip" : ".sql"));
			if (!bf.createNewFile())
				throw new IOException("Cannot create backup file: " + bf.getCanonicalPath());
			InputStream input = run.getInputStream();
			OutputStream out = new FileOutputStream(bf);
			if (Config.DATABASE_BACKUP_COMPRESSION)
			{
				ZipOutputStream dflt = new ZipOutputStream(out);
				dflt.setMethod(ZipOutputStream.DEFLATED);
				dflt.setLevel(Deflater.BEST_COMPRESSION);
				dflt.setComment("L2JFree Schema Backup Utility\r\n\r\nBackup date: "
						+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS z").format(time));
				dflt.putNextEntry(new ZipEntry(Config.DATABASE_BACKUP_DATABASE_NAME + ".sql"));
				out = dflt;
			}
			
			byte[] buf = new byte[4096];
			int written = 0;
			for (int read; (read = input.read(buf)) != -1;)
			{
				out.write(buf, 0, read);
				
				written += read;
			}
			input.close();
			out.close();
			
			if (written == 0)
			{
				bf.delete();
				BufferedReader br = new BufferedReader(new InputStreamReader(run.getErrorStream()));
				String line;
				while ((line = br.readLine()) != null)
					_log.warn("DatabaseBackupManager: " + line);
				br.close();
			}
			else
				_log.info("DatabaseBackupManager: Schema `" + Config.DATABASE_BACKUP_DATABASE_NAME
						+ "` backed up successfully in " + (System.currentTimeMillis() - time.getTime()) / 1000 + " s.");
			
			run.waitFor();
		}
		catch (Exception e)
		{
			_log.warn("DatabaseBackupManager: Could not make backup: ", e);
		}
	}
}
