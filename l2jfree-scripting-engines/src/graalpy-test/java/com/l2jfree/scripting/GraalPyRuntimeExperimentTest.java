/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.scripting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

class GraalPyRuntimeExperimentTest
{
	@Test
	void embeddedPythonCanCallAnExplicitlyAllowedJavaType()
	{
		try (Context context = Context.newBuilder("python")
				.allowHostAccess(HostAccess.ALL)
				.allowHostClassLookup(name -> name.equals("java.lang.StringBuilder"))
				.build())
		{
			context.eval("python", "from java.lang import StringBuilder\n"
					+ "builder = StringBuilder()\n"
					+ "builder.append('GraalPy on Microsoft OpenJDK 25')\n");
			Value result = context.eval("python", "str(builder)");

			assertThat(result.asString()).isEqualTo("GraalPy on Microsoft OpenJDK 25");
		}
	}

	@Test
	void reportPythonThreeSyntaxCompatibilityAcrossTheDatapack() throws IOException
	{
		Path scriptRoot = Path.of("../l2jfree-datapack/data/scripts");
		assertThat(scriptRoot).isDirectory();

		List<Path> scripts;
		try (Stream<Path> files = Files.walk(scriptRoot))
		{
			scripts = files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".py")).sorted().toList();
		}
		assertThat(scripts).isNotEmpty();

		List<String> incompatibleScripts = new ArrayList<>();
		try (Context context = Context.newBuilder("python").build())
		{
			for (Path script : scripts)
			{
				try
				{
					context.parse(Source.newBuilder("python", script.toFile()).build());
				}
				catch (PolyglotException e)
				{
					incompatibleScripts.add(scriptRoot.relativize(script) + ": " + e.getMessage());
				}
			}
		}

		System.out.printf("GraalPy Python 3 syntax probe: %d scripts scanned, %d parsed, %d require migration.%n",
				scripts.size(), scripts.size() - incompatibleScripts.size(), incompatibleScripts.size());
		incompatibleScripts.stream().limit(25).forEach(script -> System.out.println("GraalPy migration candidate: " + script));
	}
}
