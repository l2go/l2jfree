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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.util.L2Arrays;

/**
 * @author NB4L1
 */
@SuppressWarnings("unused")
public final class ModuleTester extends L2AutoInitialization
{
	public static void main(String[] args) throws Exception
	{
		Config.load();
		Config.DATAPACK_ROOT = new File("../l2jfree-datapack");
		//L2DatabaseFactory.getInstance();
		
		// here comes what you want to test
		//SkillTable.getInstance();
		//HtmCache.getInstance();
		
		//new WeaponSQLConverter().convert();
		//convertSkills();
		
		System.gc();
		System.runFinalization();
		Thread.sleep(1000);
	}
	
	private static abstract class ContentConverter
	{
		private final File _file;
		
		private ContentConverter(File file)
		{
			_file = file;
		}
		
		protected abstract ArrayList<String> convertImpl(ArrayList<String> list);
		
		protected final void convert() throws IOException
		{
			System.out.println("Converting: '" + _file.getCanonicalPath() + "'");
			
			ArrayList<String> list = new ArrayList<String>();
			
			LineNumberReader lnr = null;
			try
			{
				lnr = new LineNumberReader(new FileReader(_file));
				
				for (String line; (line = lnr.readLine()) != null;)
					list.add(line);
			}
			finally
			{
				IOUtils.closeQuietly(lnr);
			}
			
			final List<String> result = convertImpl(list);
			
			PrintStream ps = null;
			try
			{
				ps = new PrintStream(_file);
				
				for (String line : result)
					ps.println(line);
			}
			finally
			{
				IOUtils.closeQuietly(ps);
			}
			
			System.out.println();
			System.out.flush();
		}
	}
	
	private static final class WeaponSQLConverter extends ContentConverter
	{
		private WeaponSQLConverter()
		{
			super(new File("../l2jfree-datapack/sql/weapon.sql"));
		}
		
		@Override
		protected ArrayList<String> convertImpl(ArrayList<String> list)
		{
			final ArrayList<String> result = new ArrayList<String>();
			
			for (String line : list)
			{
				try
				{
					line = line.trim();
					line = line.replace("Rsk., Evasion", "Rsk. Evasion");
					line = line.replace(" -- PvP Bonus Damage Skill Assigned", "");
					
					if (line.matches(".{60,}--.+"))
					{
						System.out.println(line);
						line = line.split("--")[0].trim();
						System.out.println("\tchanged to:");
						System.out.println(line);
					}
					
					String[] array = line.split(",");
					
					if (array.length > 10)
					{
						array[13] = array[13].replaceAll(".00000", "");
						
						{
							int enchant4SkillId = Integer.parseInt(array[29]);
							int enchant4SkillLvl = Integer.parseInt(array[30]);
							
							if (enchant4SkillId == 0 && enchant4SkillLvl == 0)
							{
								array[29] = "''";
							}
							else
							{
								array[29] = "'" + enchant4SkillId + "-" + enchant4SkillLvl + "'";
							}
							array[30] = null;
						}
						{
							int onCastSkillId = Integer.parseInt(array[31]);
							int onCastSkillLvl = Integer.parseInt(array[32]);
							int onCastSkillChance = Integer.parseInt(array[33]);
							
							if (onCastSkillId == 0 && onCastSkillLvl == 0 && onCastSkillChance == 0)
							{
								array[31] = "''";
							}
							else
							{
								array[31] = "'" + onCastSkillId + "-" + onCastSkillLvl + "-" + onCastSkillChance + "'";
							}
							
							array[32] = null;
							array[33] = null;
						}
						{
							int onCritSkillId = Integer.parseInt(array[34]);
							int onCritSkillLvl = Integer.parseInt(array[35]);
							int onCritSkillChance = Integer.parseInt(array[36]);
							
							if (onCritSkillId == 0 && onCritSkillLvl == 0 && onCritSkillChance == 0)
							{
								array[34] = "''";
							}
							else
							{
								array[34] = "'" + onCritSkillId + "-" + onCritSkillLvl + "-" + onCritSkillChance + "'";
							}
							array[35] = null;
							array[36] = null;
						}
						{
							array[38] = array[38].replaceAll(";'", "'").replaceAll("0-0", "");
						}
						
						array = L2Arrays.compact(array);
					}
					
					StringBuilder sb = new StringBuilder();
					
					for (int i = 0; i < array.length; i++)
					{
						if (i != 0)
							sb.append(',');
						sb.append(array[i]);
					}
					
					if (line.endsWith(","))
						sb.append(',');
					
					result.add(sb.toString());
				}
				catch (RuntimeException e)
				{
					System.out.println(line);
					throw e;
				}
			}
			
			return result;
		}
	}
	
	private static void convertSkills() throws IOException
	{
		for (final File f : new File("../l2jfree-datapack/data/stats/skills/").listFiles())
		{
			if (f.isHidden())
				continue;
			
			new SkillXMLConverter(f).convert();
		}
	}
	
	private static final class SkillXMLConverter extends ContentConverter
	{
		private SkillXMLConverter(File file)
		{
			super(file);
		}
		
		private static final String L1_S1 =
				"    <table name=\"#enchantMagicLvl\"> 76 76 76 77 77 77 78 78 78 79 79 79 80 80 80 81 81 81 82 82 82 83 83 83 84 84 84 85 85 85 </table>";
		private static final String L1_S2 =
				"    <table name=\"#enchantMagicLvl\"> 76 76 76 77 77 77 78 78 78 79 79 79 80 80 80 81 81 81 82 82 82 82 83 83 83 84 84 85 85 85 </table>";
		private static final String L1_S3 =
				"    <table name=\"#enchantMagicLvl\"> 81 81 81 82 82 82 83 83 83 84 84 84 85 85 85 </table>";
		
		@Override
		protected ArrayList<String> convertImpl(ArrayList<String> list)
		{
			for (int i = 0; i < list.size(); i++)
			{
				String line = list.get(i);
				StringBuilder sb = new StringBuilder();
				
				for (int k = 0; k < line.length() && line.charAt(k) == ' '; k++)
					sb.append(' ');
				
				line = line.replaceAll("[ \t]+$", "");
				line = line.replaceAll(">", "> ");
				line = line.replaceAll("> +", "> ");
				line = line.replaceAll("<", " <");
				line = line.replaceAll(" +<", " <");
				line = line.trim();
				
				sb.append(line);
				
				list.set(i, sb.toString());
			}
			
			final ArrayList<String> result = new ArrayList<String>(list.size());
			
			ArrayList<String> tmpList = null;
			for (int i = 0; i < list.size(); i++)
			{
				final String line = list.get(i);
				
				if (line.contains("<skill"))
					tmpList = new ArrayList<String>();
				
				if (tmpList != null)
					tmpList.add(line);
				else
					result.add(line);
				
				if (line.contains("</skill"))
				{
					result.addAll(convertSkill(tmpList));
					tmpList = null;
				}
			}
			
			return result;
		}
		
		private ArrayList<String> convertSkill(ArrayList<String> list)
		{
			Map<String, String> map = new HashMap<String, String>();
			
			for (int i = 0; i < list.size(); i++)
			{
				final String line = list.get(i);
				
				if (line.matches(".*<enchant[0-9] .*"))
				{
					final String name = getAttributeValue(line, "name");
					final String val = getAttributeValue(line, "val");
					
					if (val.contains("#"))
					{
						final Matcher m = Pattern.compile("#ench(ant)?[0-9_]*").matcher(val);
						m.find();
						
						final StringBuilder sb = new StringBuilder();
						
						sb.append(val.substring(0, m.start()));
						sb.append(m.group());
						
						if (Character.isDigit(sb.charAt(sb.length() - 1)))
							sb.append(name);
						else
							sb.append(Util.capitalizeFirst(name));
						
						final String expected = sb.toString();
						
						if (!val.equals(expected))
							map.put(val, expected);
					}
				}
			}
			
			for (int i = 0; i < list.size(); i++)
			{
				String line = list.get(i);
				
				for (Map.Entry<String, String> entry : map.entrySet())
				{
					//line = line.replaceAll(entry.getKey(), entry.getValue());
				}
				
				list.set(i, line);
			}
			
			// magicLvl
			for (int i = 0; i < list.size(); i++)
			{
				final String line = list.get(i);
				final String lowerCase = line.toLowerCase();
				
				if (line.contains("<table") && lowerCase.contains("ench") && lowerCase.contains("magicl"))
				{
					if (line.equals(L1_S1) || line.equals(L1_S2) || line.equals(L1_S3))
					{
						list.remove(i);
						i--;
					}
					else if (line.contains("</table>"))
					{
						System.out.println("|" + line + "|");
					}
					else
					{
						final String sum = line + " " + list.get(i + 1).trim();
						
						if (sum.equals(L1_S1) || sum.equals(L1_S2) || sum.equals(L1_S3))
						{
							list.remove(i + 1);
							list.remove(i);
							i--;
						}
						else
						{
							System.out.println("|" + line + "|" + list.get(i + 1) + "|");
						}
					}
				}
				else if (line.contains("<enchant") && lowerCase.contains("magicl"))
				{
					if (line.matches("    <enchant. name=\"magicLvl\" val=\"#enchantMagicLvl\"/>"))
					{
						list.remove(i);
						i--;
					}
					else
					{
						System.out.println("|" + line + "|");
					}
				}
			}
			
			// element
			for (int i = 0; i < list.size(); i++)
			{
				String line = list.get(i);
				
				if (!line.contains("\"element\""))
					continue;
				
				final String name = Elementals.getElementName(Byte.parseByte(getAttributeValue(line, "val")));
				
				if (name.equals("None"))
				{
					System.out.println("|" + line + "|");
				}
				else
				{
					line = line.substring(0, line.indexOf("/>") + 2) + " <!-- " + name + " -->";
					list.set(i, line);
				}
			}
			
			// weaponsAllowed
			for (int i = 0; i < list.size(); i++)
			{
				String line = list.get(i);
				
				if (!line.contains("\"weaponsAllowed\""))
					continue;
				
				final int weaponsAllowed = Integer.parseInt(getAttributeValue(line, "val"));
				
				final TreeSet<L2WeaponType> types = new TreeSet<L2WeaponType>(new Comparator<L2WeaponType>() {
					@Override
					public int compare(L2WeaponType o1, L2WeaponType o2)
					{
						return getOrder(o1).compareTo(getOrder(o2));
					}
					
					private Integer getOrder(L2WeaponType wt)
					{
						switch (wt)
						{
							case ANCIENT_SWORD:
								return 1;
							case RAPIER:
								return 2;
							case DUAL:
								return 3;
							case SWORD:
								return 4;
							case BIGSWORD:
								return 5;
							case BLUNT:
								return 6;
							case BIGBLUNT:
								return 7;
							case DAGGER:
								return 8;
							case DUAL_DAGGER:
								return 9;
							case BOW:
								return 10;
							case CROSSBOW:
								return 11;
							case NONE:
								return 12;
							case POLE:
								return 13;
							case ETC:
								return 14;
							case FIST:
								return 15;
							case DUALFIST:
								return 16;
							case PET:
								return 17;
							case ROD:
							default:
								return 18;
						}
					}
				});
				
				for (L2WeaponType wt : L2WeaponType.VALUES)
				{
					if ((wt.mask() & weaponsAllowed) != 0)
					{
						types.add(wt);
					}
				}
				
				line = line.substring(0, line.indexOf("/>") + 2) + " <!-- " + StringUtils.join(types, '/') + " -->";
				list.set(i, line);
			}
			
			// mpConsume
			boolean toggle = false;
			for (int i = 0; i < list.size(); i++)
			{
				String line = list.get(i);
				
				if ("operateType".equals(getAttributeValue(line, "name")))
				{
					if ("OP_TOGGLE".equals(getAttributeValue(line, "val")))
					{
						toggle = true;
						break;
					}
				}
			}
			
			if (toggle)
			{
				for (int i = 0; i < list.size(); i++)
				{
					String line = list.get(i);
					
					line = line.replaceAll("mpConsume", "mpInitialConsume");
					list.set(i, line);
				}
			}
			
			return list;
		}
	}
	
	private static String getAttributeValue(String line, String name)
	{
		final Matcher m = Pattern.compile("[^ ]+=\"[^\"]+\"").matcher(line);
		
		while (m.find())
		{
			final String[] result = m.group().split("=");
			
			if (result[0].equals(name))
				return result[1].replace("\"", "");
		}
		
		return null;
	}
}
