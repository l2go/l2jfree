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

import java.util.List;
import java.security.SecureRandom;
import java.util.Random;

/**
 * A Lineage II client for the end-to-end check of the platform: it logs in, takes the one world of the server list,
 * creates a character, enters the world, logs out, and logs in again. It needs nothing but the JDK and uses no class
 * of the server, so a defect in the framing, a cipher, or the order of packets shows up here and not only in the
 * tests of the server.
 * <p>
 * It exits with 0 when every step passed and with 1 when one failed. Each step prints a line.
 */
public final class SmokeClient
{
	private static final String USAGE = "usage: SmokeClient [--host HOST] [--login-port N] [--world-port N] "
			+ "[--protocol-revision N] [--timeout-seconds N] [--account NAME] [--password TEXT]";

	/** The settings of a run. */
	record Config(String host, int loginPort, int worldPort, int protocolRevision, int timeoutSeconds, String account,
			String password)
	{
		static Config defaults()
		{
			return new Config("127.0.0.1", 2106, 7777, 87, 120, null, null);
		}
	}

	/** The outcome of a run: whether it passed, the lines it printed, and the reason when it did not. */
	public record Result(boolean passed, List<String> lines, String failure)
	{
	}

	private SmokeClient()
	{
	}

	public static void main(String[] args)
	{
		final Config config;
		try
		{
			config = parse(args);
		}
		catch (IllegalArgumentException e)
		{
			System.err.println(e.getMessage());
			System.err.println(USAGE);
			System.exit(2);
			return;
		}

		final Result result = run(config, new Reporter(System.out));
		System.out.println(result.passed() ? "SMOKE PASSED" : "SMOKE FAILED: " + result.failure());
		System.exit(result.passed() ? 0 : 1);
	}

	/** Runs the whole flow against a server and reports it. Never throws for a failed step. */
	static Result run(Config config, Reporter reporter)
	{
		final Deadline deadline = Deadline.after(config.timeoutSeconds());
		// the password of the test account is random, so it comes from a secure source
		final Random random = new SecureRandom();
		final String account = config.account() != null ? config.account() : "smoke" + letters(random, 6);
		final String password = config.password() != null ? config.password() : "pw" + letters(random, 10);
		final String character = "Smoke" + letters(random, 6);
		final boolean freshAccount = config.account() == null;

		try
		{
			LoginFlow.Session session = LoginFlow.selectWorld(config, deadline, reporter, account, password);
			WorldFlow.play(config, deadline, reporter, session, account, freshAccount, character);
			LoginFlow.loginAgain(config, deadline, reporter, account, password);
			return new Result(true, reporter.lines(), null);
		}
		catch (SmokeException e)
		{
			reporter.fail(e.getMessage());
			return new Result(false, reporter.lines(), e.getMessage());
		}
		catch (RuntimeException e)
		{
			String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
			reporter.fail(reason);
			return new Result(false, reporter.lines(), reason);
		}
	}

	static Config parse(String[] args)
	{
		Config config = Config.defaults();
		for (int i = 0; i < args.length; i++)
		{
			String name = args[i];
			if (i + 1 >= args.length)
				throw new IllegalArgumentException("missing value for " + name);

			String value = args[++i];
			switch (name)
			{
				case "--host":
					config = new Config(value, config.loginPort(), config.worldPort(), config.protocolRevision(),
							config.timeoutSeconds(), config.account(), config.password());
					break;
				case "--login-port":
					config = new Config(config.host(), number(name, value), config.worldPort(),
							config.protocolRevision(), config.timeoutSeconds(), config.account(), config.password());
					break;
				case "--world-port":
					config = new Config(config.host(), config.loginPort(), number(name, value),
							config.protocolRevision(), config.timeoutSeconds(), config.account(), config.password());
					break;
				case "--protocol-revision":
					config = new Config(config.host(), config.loginPort(), config.worldPort(), number(name, value),
							config.timeoutSeconds(), config.account(), config.password());
					break;
				case "--timeout-seconds":
					config = new Config(config.host(), config.loginPort(), config.worldPort(),
							config.protocolRevision(), number(name, value), config.account(), config.password());
					break;
				case "--account":
					config = new Config(config.host(), config.loginPort(), config.worldPort(),
							config.protocolRevision(), config.timeoutSeconds(), value, config.password());
					break;
				case "--password":
					config = new Config(config.host(), config.loginPort(), config.worldPort(),
							config.protocolRevision(), config.timeoutSeconds(), config.account(), value);
					break;
				default:
					throw new IllegalArgumentException("unknown option " + name);
			}
		}

		if ((config.account() == null) != (config.password() == null))
			throw new IllegalArgumentException("--account and --password go together");

		return config;
	}

	private static int number(String name, String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(name + " needs a number, not '" + value + "'");
		}
	}

	private static String letters(Random random, int count)
	{
		StringBuilder text = new StringBuilder(count);
		for (int i = 0; i < count; i++)
			text.append((char)('a' + random.nextInt(26)));
		return text.toString();
	}
}
