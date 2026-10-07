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
package com.l2jfree.gameserver.instancemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.l2jfree.util.LookupTable;

class InstanceNamesParsingTest
{
	private static final Path INSTANCE_NAMES = Path.of("../l2jfree-datapack/data/instancenames.xml");

	@Test
	void readsIdAndNameOfEveryInstanceElement() throws Exception
	{
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<!-- a comment -->
				<instances_list xmlns="http://www.l2jfree.com">
				  <instance id="1" name="Party Duel"/>
				  <instance name="Tully's Workshop &amp; Co" id="5"></instance>
				  <other id="9" name="ignored"/>
				  <instance id="44" name="Pailaka (Devil's Isle) é"/>
				  <instance id="1" name="Party Duel again"/>
				</instances_list>
				""";

		LookupTable<String> names = parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		assertThat(names.size()).isEqualTo(3);
		assertThat(names.get(1)).isEqualTo("Party Duel again");
		assertThat(names.get(5)).isEqualTo("Tully's Workshop & Co");
		assertThat(names.get(44)).isEqualTo("Pailaka (Devil's Isle) é");
		assertThat(names.get(9)).isNull();
	}

	@Test
	void readsTheDatapackFileLikeADomParser() throws Exception
	{
		Map<Integer, String> expected = new LinkedHashMap<Integer, String>();
		NodeList elements = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(INSTANCE_NAMES.toFile())
				.getElementsByTagName("instance");
		for (int i = 0; i < elements.getLength(); i++)
		{
			Element element = (Element)elements.item(i);
			expected.put(Integer.valueOf(element.getAttribute("id")), element.getAttribute("name"));
		}

		LookupTable<String> names;
		try (InputStream in = Files.newInputStream(INSTANCE_NAMES))
		{
			names = parse(in);
		}

		assertThat(expected).hasSizeGreaterThan(100);
		assertThat(names.size()).isEqualTo(expected.size());
		for (Map.Entry<Integer, String> entry : expected.entrySet())
			assertThat(names.get(entry.getKey())).as("instance %d", entry.getKey()).isEqualTo(entry.getValue());
	}

	private static LookupTable<String> parse(InputStream in) throws Exception
	{
		LookupTable<String> names = new LookupTable<String>();
		InstanceManager.parseInstanceNames(in, names);
		return names;
	}
}
