/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.jupiter.api.Test;

import com.sun.script.jython.JythonScriptEngineFactory;

class PythonScriptSyntaxTest
{
	@Test
	void allDatapackPythonScriptsCompileWithThePinnedJythonRuntime() throws IOException
	{
		Path scriptRoot = Path.of("data", "scripts");
		assertThat(scriptRoot).isDirectory();

		List<Path> scripts;
		try (Stream<Path> files = Files.walk(scriptRoot))
		{
			scripts = files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".py")).sorted().toList();
		}

		assertThat(scripts).as("Python datapack scripts").isNotEmpty();

		ScriptEngine engine = new JythonScriptEngineFactory().getScriptEngine();
		assertThat(engine).isInstanceOf(Compilable.class);
		Compilable compiler = (Compilable) engine;
		List<String> failures = new ArrayList<>();
		for (Path script : scripts)
		{
			try (Reader source = Files.newBufferedReader(script, StandardCharsets.UTF_8))
			{
				compiler.compile(source);
			}
			catch (ScriptException e)
			{
				failures.add(scriptRoot.relativize(script) + ": " + e.getMessage());
			}
		}

		assertThat(failures).as("Jython syntax errors in datapack scripts").isEmpty();
	}
}
