/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.tools.dbinstaller.util.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

class SqlScriptParserTest
{
	@Test
	void keepsStoredFunctionBodyTogetherWhenDelimiterChanges()
	{
		String script = "CREATE TABLE sample (id INT);\n"
				+ "DELIMITER //\n"
				+ "CREATE FUNCTION sampleFunction() RETURNS TEXT\n"
				+ "BEGIN\n"
				+ "  RETURN 'value; still in the function';\n"
				+ "END//\n"
				+ "delimiter ;\n"
				+ "SELECT sampleFunction(); -- trailing comment\n";

		List<String> statements = SqlScriptParser.parse(new Scanner(script));

		assertThat(statements).hasSize(3);
		assertThat(statements.get(0)).isEqualTo("CREATE TABLE sample (id INT)");
		assertThat(statements.get(1)).contains("RETURN 'value; still in the function';").endsWith("END");
		assertThat(statements.get(1)).doesNotContain("DELIMITER", "//");
		assertThat(statements.get(2)).isEqualTo("SELECT sampleFunction()");
	}
}
