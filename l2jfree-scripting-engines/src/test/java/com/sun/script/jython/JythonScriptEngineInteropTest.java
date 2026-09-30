/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.sun.script.jython;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

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
		AtomicReference<String> holder = new AtomicReference<>();
		engine.put("holder", holder);
		engine.put("suffix", " through the L2JFree JSR-223 bridge");

		CompiledScript script = ((Compilable) engine).compile("holder.set(suffix)");
		script.eval();

		assertThat(holder.get()).isEqualTo(" through the L2JFree JSR-223 bridge");
	}
}
