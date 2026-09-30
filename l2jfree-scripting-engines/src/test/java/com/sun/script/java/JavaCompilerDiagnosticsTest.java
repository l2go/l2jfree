/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.sun.script.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class JavaCompilerDiagnosticsTest
{
	@Test
	void compilationErrorsIncludeLineAndColumnForDatapackScriptReports()
	{
		StringWriter diagnostics = new StringWriter();
		JavaCompiler compiler = new JavaCompiler();

		assertThat(compiler.compile("Broken.java", "class Broken {\n void run() {\n  int value = ;\n }\n}", diagnostics))
				.isNull();
		assertThat(diagnostics.toString()).contains("ERROR").contains("line 3").contains("column");
	}
}
