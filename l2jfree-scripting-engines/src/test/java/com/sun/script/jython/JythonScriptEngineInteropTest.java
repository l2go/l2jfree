/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.sun.script.jython;

import static org.assertj.core.api.Assertions.assertThat;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;

import org.junit.jupiter.api.Test;

class JythonScriptEngineInteropTest
{
	@Test
	void compiledScriptReadsBindingsAndCallsBoundJavaObjects() throws Exception
	{
		ScriptEngine engine = new JythonScriptEngineFactory().getScriptEngine();
		StringBuilder builder = new StringBuilder();
		engine.put("builder", builder);
		engine.put("suffix", " through the L2JFree JSR-223 bridge");

		CompiledScript script = ((Compilable) engine).compile("builder.append(suffix)");
		script.eval();

		assertThat(builder.toString()).isEqualTo(" through the L2JFree JSR-223 bridge");
	}
}
