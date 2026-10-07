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
package com.l2jfree.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Source patterns that bytecode rules cannot see.
 */
class SourcePatternsTest
{
	/** {@code map.remove(key);} directly followed by {@code map.put(key, ...)}. */
	private static final Pattern REMOVE_THEN_PUT =
			Pattern.compile("(\\w+)\\.remove\\(([^;\\n]*?)\\);\\s*\\1\\.put\\(\\2\\s*,");
	
	/**
	 * Javolution's FastMap let a loop over a map remove and re-put the same key. On a JDK map this throws
	 * ConcurrentModificationException, and on a ConcurrentHashMap it can re-append the key behind the iterator forever
	 * (the raid boss save loop on shutdown). {@code put} alone replaces the value in place.
	 */
	@Test
	void mapsAreNotUpdatedByRemoveThenPut() throws IOException
	{
		List<String> found = new ArrayList<String>();
		for (Path root : List.of(Path.of("../l2j-commons/src/main/java"), Path.of("../l2jfree-login/src/main/java"),
				Path.of("../l2jfree-core/src/main/java"), Path.of("../l2jfree-datapack/data/scripts")))
		{
			assertThat(root).isDirectory();
			try (Stream<Path> files = Files.walk(root))
			{
				for (Path file : (Iterable<Path>)files.filter(f -> f.toString().endsWith(".java"))::iterator)
				{
					String source = Files.readString(file).replace("\r", "");
					Matcher matcher = REMOVE_THEN_PUT.matcher(source);
					while (matcher.find())
					{
						int line = source.substring(0, matcher.start()).split("\n", -1).length;
						found.add(file + ":" + line + " " + matcher.group(1));
					}
				}
			}
		}
		
		assertThat(found).as("remove-then-put of the same key").isEmpty();
	}
}
