/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
	void allDatapackPythonScriptsCompileWithTheSelectedJythonRuntime() throws IOException
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
			// Windows checkout rewrites LF as CRLF. A trailing backslash then
			// escapes the carriage return, so normalize before the syntax check.
			String source = Files.readString(script, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
			try
			{
				compiler.compile(source);
			}
			catch (ScriptException e)
			{
				failures.add(scriptRoot.relativize(script) + ": " + scriptFailure(e));
			}
		}

		assertThat(failures).as("Jython syntax errors in datapack scripts using the selected runtime").isEmpty();
	}

	private static String scriptFailure(ScriptException exception)
	{
		Throwable cause = exception.getCause();
		while (cause != null && cause.getCause() != null && cause.getCause() != cause)
		{
			cause = cause.getCause();
		}
		return cause == null ? exception.getMessage() : cause.toString();
	}
}
