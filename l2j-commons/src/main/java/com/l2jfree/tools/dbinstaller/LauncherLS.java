/*
 * Copyright (C) 2004-2015 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.tools.dbinstaller;

import java.awt.HeadlessException;

import javax.swing.UIManager;

import com.l2jfree.L2AutoInitialization;
import com.l2jfree.tools.dbinstaller.console.DBInstallerConsole;
import com.l2jfree.tools.dbinstaller.gui.DBConfigGUI;

/**
 * Contains main class for Database Installer If system doesn't support the graphical UI, start the installer in console mode.
 * @author mrTJO
 */
public class LauncherLS extends AbstractDBLauncher
{
	public static void main(String[] args)
	{
		final String defDatabase = "l2jfree_ls";
		
		final String dir;
		final String cleanUpScript;
		
		if (L2AutoInitialization.isIDEMode())
		{
			dir = "../l2jfree-login/sql/";
			cleanUpScript = "../l2jfree-login/tools/ls_cleanup.sql";
		}
		else
		{
			dir = "./sql/";
			cleanUpScript = "./tools/ls_cleanup.sql";
		}
		
		if ((args != null) && (args.length > 0))
		{
			new DBInstallerConsole(defDatabase, dir, cleanUpScript, getArg("-h", args), getArg("-p", args), getArg(
					"-u", args), getArg("-pw", args), getArg("-d", args), getArg("-m", args),
					getArg("-confirm-clean", args));
			return;
		}
		
		try
		{
			// Set OS Look And Feel
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e)
		{
		}
		
		try
		{
			new DBConfigGUI(defDatabase, dir, cleanUpScript);
		}
		catch (HeadlessException e)
		{
			new DBInstallerConsole(defDatabase, dir, cleanUpScript);
		}
	}
}
