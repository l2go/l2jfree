/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.tools.dbinstaller.util.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

final class SqlScriptParser
{
	private static final String DEFAULT_DELIMITER = ";";

	private SqlScriptParser()
	{
	}

	static List<String> parse(Scanner scanner)
	{
		List<String> statements = new ArrayList<>();
		StringBuilder currentStatement = new StringBuilder();
		String delimiter = DEFAULT_DELIMITER;

		while (scanner.hasNextLine())
		{
			String line = stripLineComment(scanner.nextLine()).trim();
			if (line.isEmpty())
			{
				continue;
			}

			if (currentStatement.length() == 0 && isDelimiterDirective(line))
			{
				String newDelimiter = line.substring("delimiter".length()).trim();
				if (newDelimiter.isEmpty())
				{
					throw new IllegalArgumentException("DELIMITER directive is missing its delimiter");
				}
				delimiter = newDelimiter;
				continue;
			}

			currentStatement.append(line).append('\n');
			if (line.endsWith(delimiter))
			{
				String statement = currentStatement.toString().trim();
				statement = statement.substring(0, statement.length() - delimiter.length()).trim();
				if (!statement.isEmpty())
				{
					statements.add(statement);
				}
				currentStatement.setLength(0);
			}
		}

		if (currentStatement.length() > 0)
		{
			if (!DEFAULT_DELIMITER.equals(delimiter))
			{
				throw new IllegalArgumentException("SQL script ended before delimiter '" + delimiter + "'");
			}
			statements.add(currentStatement.toString().trim());
		}

		return statements;
	}

	private static boolean isDelimiterDirective(String line)
	{
		String lowerCaseLine = line.toLowerCase(Locale.ROOT);
		return lowerCaseLine.startsWith("delimiter ") || lowerCaseLine.startsWith("delimiter\t");
	}

	private static String stripLineComment(String line)
	{
		String trimmed = line.trim();
		if (trimmed.startsWith("--") || trimmed.startsWith("#"))
		{
			return "";
		}

		int commentStart = line.indexOf("--");
		if (commentStart >= 0)
		{
			return line.substring(0, commentStart);
		}
		return line;
	}
}
