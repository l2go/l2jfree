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
package com.l2jfree.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmokeClientTest
{
	@Test
	void withoutOptionsTheClientTargetsTheLocalPlatform()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[0]);
		
		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.loginPort()).isEqualTo(2106);
		assertThat(config.worldPort()).isEqualTo(7777);
		assertThat(config.account()).isNull();
	}
	
	@Test
	void everyOptionIsRead()
	{
		SmokeClient.Config config = SmokeClient.parse(new String[] { "--host", "example.org", "--login-port", "2107",
				"--world-port", "7778", "--protocol-revision", "83", "--timeout-seconds", "30", "--account", "anna",
				"--password", "secret" });
		
		assertThat(config).isEqualTo(new SmokeClient.Config("example.org", 2107, 7778, 83, 30, "anna", "secret"));
	}
	
	@Test
	void badInputIsRefusedWithAMessage()
	{
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--nope", "1" })).hasMessageContaining("unknown option");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--login-port" })).hasMessageContaining("missing value");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--login-port", "x" })).hasMessageContaining("number");
		assertThatThrownBy(() -> SmokeClient.parse(new String[] { "--account", "anna" })).hasMessageContaining("together");
	}
	
	@Test
	void aRunAgainstNothingFailsWithAReasonAndDoesNotThrow()
	{
		SmokeClient.Config config = new SmokeClient.Config("127.0.0.1", 1, 1, 87, 5, null, null);
		Reporter reporter = new Reporter(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
		
		SmokeClient.Result result = SmokeClient.run(config, reporter);
		
		assertThat(result.passed()).isFalse();
		assertThat(result.failure()).isNotBlank();
		assertThat(result.lines()).anyMatch(line -> line.startsWith("FAIL"));
	}
}
