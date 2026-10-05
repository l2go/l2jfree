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
package com.l2jfree.gameserver.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Files;

import javax.script.ScriptException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;

class JavaScriptBytecodeTest
{
	@Test
	@DisplayName("a Java script without packaged bytecode is not compiled at startup")
	void missingBytecodeNamesTheScript() throws Exception
	{
		File previousRoot = Config.DATAPACK_ROOT;
		File root = Files.createTempDirectory("l2jfree-scripts").toFile();
		Config.DATAPACK_ROOT = root;
		try
		{
			File script = new File(root, "data/scripts/ai/group_template/Missing.java");
			assertThat(script.getParentFile().mkdirs()).isTrue();
			Files.writeString(script.toPath(), "public class Missing {}\n");
			
			L2ScriptEngineManager scripts = mock(L2ScriptEngineManager.class, CALLS_REAL_METHODS);
			assertThatThrownBy(() -> scripts.executeScript(script))
					.isInstanceOf(ScriptException.class)
					.hasMessageContaining(script.getPath())
					.hasMessageContaining("no packaged bytecode");
		}
		finally
		{
			Config.DATAPACK_ROOT = previousRoot;
		}
	}
}
