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
import java.util.concurrent.TimeUnit;

/**
 * The steps of the smoke test on the world port.
 */
final class WorldFlow
{
	/** The class id of the human fighter, a valid first class with base level 1. */
	private static final int FIGHTER_CLASS_ID = 0;
	private static final int ANNOUNCED_CONNECT_MILLIS = 3000;
	/** The client reads what the world still sends after UserInfo until it is quiet for this long. */
	private static final long QUIET_MILLIS = 500L;
	private static final long SETTLE_MILLIS = 3000L;
	private static final int LOGOUT_ATTEMPTS = 3;
	private static final long LOGOUT_WAIT_MILLIS = 5000L;
	private static final long CLOSE_WAIT_MILLIS = 5000L;

	private WorldFlow()
	{
	}

	/**
	 * Plays one session: ProtocolVersion, KeyPacket, AuthLogin, CharSelectionInfo, character creation,
	 * CharacterSelected, EnterWorld, UserInfo, Logout, LeaveWorld.
	 *
	 * @param expectedCharacters how many characters the account has on entry, or {@link SmokeClient.Config#UNCHECKED}
	 */
	static void play(SmokeClient.Config config, Deadline deadline, Reporter reporter, LoginFlow.Session session,
			String account, int expectedCharacters, String characterName) throws SmokeException
	{
		reporter.begin("world: connect");
		try (GameConnection game = connect(config, deadline, reporter, session.world()))
		{
			reporter.begin("world: protocol version");
			game.send(GamePackets.protocolVersion(config.protocolRevision()));
			GamePackets.KeyPacket keyPacket = GamePackets.parseKeyPacket(game.expect(GamePackets.OP_KEY_PACKET,
					"KeyPacket"));
			if (!keyPacket.protocolOk())
			{
				throw new SmokeException("the world refused the protocol revision " + config.protocolRevision()
						+ " (see MinProtocolRevision and MaxProtocolRevision, or pass --protocol-revision)");
			}
			game.cipher().setKey(GameCipher.fullKey(keyPacket.key()));
			game.cipher().enable();
			reporter.ok("revision " + config.protocolRevision() + " accepted, game cipher on");

			reporter.begin("world: authentication");
			game.send(GamePackets.authLogin(account, session.playOk1(), session.playOk2(), session.loginOk1(),
					session.loginOk2()));
			GamePackets.CharacterList before = GamePackets.parseCharacterList(game.expect(
					GamePackets.OP_CHAR_SELECTION_INFO, "CharSelectionInfo"));
			if (expectedCharacters != SmokeClient.Config.UNCHECKED && before.count() != expectedCharacters)
			{
				throw new SmokeException("the character list has " + before.count() + " characters instead of "
						+ expectedCharacters);
			}
			reporter.ok(before.count() + " characters in the list");

			reporter.begin("world: character templates");
			game.send(GamePackets.newCharacterInit());
			List<GamePackets.Template> templates = GamePackets.parseTemplates(game.expect(
					GamePackets.OP_NEW_CHARACTER_SUCCESS, "NewCharacterSuccess"));
			GamePackets.Template fighter = null;
			for (GamePackets.Template template : templates)
			{
				if (template.classId() == FIGHTER_CLASS_ID)
				{
					fighter = template;
					break;
				}
			}
			if (fighter == null)
				throw new SmokeException("the world offers no template for the class " + FIGHTER_CLASS_ID);

			reporter.ok(templates.size() + " templates, class " + fighter.classId() + " of race " + fighter.race());

			reporter.begin("world: character creation");
			game.send(GamePackets.newCharacter(characterName, fighter, 0, 0, 0, 0));
			game.expect(GamePackets.OP_CHARACTER_CREATE_SUCCESS, "CharacterCreateSuccess");
			GamePackets.CharacterList after = GamePackets.parseCharacterList(game.expect(
					GamePackets.OP_CHAR_SELECTION_INFO, "CharSelectionInfo"));
			if (after.count() != before.count() + 1)
			{
				throw new SmokeException("the character list has " + after.count() + " characters after the creation, "
						+ "expected " + (before.count() + 1));
			}
			if (after.count() == 1)
			{
				if (!characterName.equals(after.firstName()))
				{
					throw new SmokeException("the only character of the list is named '" + after.firstName()
							+ "' instead of '" + characterName + "'");
				}
				if (!account.equals(after.firstAccount()))
				{
					throw new SmokeException("the list names the account '" + after.firstAccount() + "' instead of '"
							+ account + "'");
				}
				if (after.firstSessionId() != session.playOk1())
				{
					throw new SmokeException("the list carries the session id 0x"
							+ Integer.toHexString(after.firstSessionId()) + " instead of the play key 0x"
							+ Integer.toHexString(session.playOk1()));
				}
			}
			reporter.ok("character " + characterName + " created, " + after.count() + " in the list");

			reporter.begin("world: character selection");
			game.send(GamePackets.characterSelected(0));
			String selected = game.expect(GamePackets.OP_CHAR_SELECTED, "CharSelected").readS();
			if (after.count() == 1 && !characterName.equals(selected))
			{
				throw new SmokeException("the world selected the character '" + selected + "' instead of '"
						+ characterName + "'");
			}
			reporter.ok("character " + selected + " selected");

			reporter.begin("world: enter world");
			Watcher watcher = new Watcher(game, Boolean.getBoolean("smoke.fight"));
			game.tap(payload -> {
				try
				{
					watcher.see(payload);
				}
				catch (SmokeException e)
				{
					// a packet the watcher cannot read is not its business
				}
			});
			game.send(GamePackets.enterWorld());
			PacketReader userInfo = game.expect(GamePackets.OP_USER_INFO, "UserInfo");
			int x = userInfo.readD();
			int y = userInfo.readD();
			String where = x + " " + y + " " + userInfo.readD();
			userInfo.skip(4); // vehicle id
			int objectId = userInfo.readD();
			String userName = userInfo.readS();
			if (!userName.equals(selected))
			{
				throw new SmokeException("UserInfo describes '" + userName + "' instead of '" + selected + "'");
			}
			int skippedBefore = game.skipped();
			watcher.at(where, x, y);
			int trailing = settle(game, deadline);
			watcher.hold(deadline, Long.getLong("smoke.hold.seconds", 0L) * 1000L, reporter);
			reporter.ok("UserInfo of " + userName + ", object id " + objectId + ", after " + skippedBefore
					+ " other packets and " + trailing + " more");

			reporter.begin("world: logout");
			logout(game, deadline);
			reporter.ok("LeaveWorld received, the server closed the connection");
		}
	}

	/**
	 * Connects to the world. The server list names an address; when the client cannot reach it (the
	 * default 127.0.0.1 is the client's own host when the client does not run on the server host), it
	 * uses its own {@code --host}. The port is {@code --world-port}.
	 */
	private static GameConnection connect(SmokeClient.Config config, Deadline deadline, Reporter reporter,
			LoginPackets.WorldEntry world) throws SmokeException
	{
		String announced = world.host();
		boolean usable = !announced.isEmpty() && !"0.0.0.0".equals(announced);
		if (world.port() != config.worldPort())
		{
			reporter.info("the server list announces the world port " + world.port() + ", the client uses --world-port "
					+ config.worldPort());
		}

		if (usable && !announced.equals(config.host()))
		{
			try
			{
				FramedConnection connection = FramedConnection.open("world", announced, config.worldPort(), deadline,
						false, ANNOUNCED_CONNECT_MILLIS);
				reporter.ok("connected to the announced address " + announced + ":" + config.worldPort());
				return new GameConnection(connection);
			}
			catch (SmokeException e)
			{
				if (deadline.expired())
					throw e;

				reporter.info("the announced world address " + announced + " is not usable (" + e.getMessage()
						+ "), the client uses its own host " + config.host());
			}
		}

		FramedConnection connection = FramedConnection.open("world", config.host(), config.worldPort(), deadline);
		reporter.ok("connected to " + config.host() + ":" + config.worldPort() + (usable && announced.equals(
				config.host()) ? " (the announced address)" : " (the host of the client)"));
		return new GameConnection(connection);
	}

	/**
	 * Reads what the world still sends after UserInfo until the line is quiet.
	 *
	 * @return the number of packets read
	 */
	private static int settle(GameConnection game, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SETTLE_MILLIS);
		int packets = 0;
		while (System.nanoTime() < until)
		{
			try
			{
				game.receive(QUIET_MILLIS);
				packets++;
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					return packets;

				throw e;
			}
		}
		return packets;
	}

	/** Sends Logout until LeaveWorld arrives, then waits for the server to close the connection. */
	private static void logout(GameConnection game, Deadline deadline) throws SmokeException
	{
		boolean left = false;
		for (int attempt = 1; attempt <= LOGOUT_ATTEMPTS && !left; attempt++)
		{
			game.send(GamePackets.logout());
			left = awaitLeave(game, deadline);
		}
		if (!left)
			throw new SmokeException("the world did not answer Logout with LeaveWorld");

		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_WAIT_MILLIS);
		while (true)
		{
			long left2 = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (left2 <= 0L)
				throw new SmokeException("the world did not close the connection after LeaveWorld");

			try
			{
				game.receive(left2);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.CLOSED)
					return;

				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					throw new SmokeException("the world did not close the connection after LeaveWorld");

				throw e;
			}
		}
	}

	private static boolean awaitLeave(GameConnection game, Deadline deadline) throws SmokeException
	{
		long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOGOUT_WAIT_MILLIS);
		while (true)
		{
			long left = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime());
			if (left <= 0L)
				return false;

			byte[] payload;
			try
			{
				payload = game.receive(left);
			}
			catch (SmokeException e)
			{
				if (e.kind() == SmokeException.Kind.TIMEOUT && !deadline.expired())
					return false;

				throw e;
			}

			if ((payload[0] & 0xff) == GamePackets.OP_LEAVE_WORLD)
				return true;
		}
	}

	/**
	 * Measurement branch only. Stays in the world after UserInfo and reads what the world sends. With
	 * {@code smoke.fight} it clicks the first monster the world announces again and again (the second click on a selected target starts the fight) (NpcInfo with the attackable flag),
	 * so the server has a fight with watchers around it.
	 */
	private static final class Watcher
	{
		private static final int OP_NPC_INFO = 0x0c;
		private static final int OP_ACTION = 0x1f;
		private static final long ATTACK_EVERY_MILLIS = 2000L;

		private final GameConnection _game;
		private final boolean _fight;
		private int _target;
		private int _npcs;
		private int _packets;
		private String _where = "?";
		private final java.util.Map<Integer, Integer> _messages = new java.util.TreeMap<>();
		private int _x;
		private int _y;
		private final java.util.Map<Integer, int[]> _candidates = new java.util.LinkedHashMap<>();
		private final java.util.Map<Integer, Integer> _opcodes = new java.util.TreeMap<>();

		Watcher(GameConnection game, boolean fight)
		{
			_game = game;
			_fight = fight;
		}

		void at(String where, int x, int y)
		{
			_where = where;
			_x = x;
			_y = y;
			long best = Long.MAX_VALUE;
			for (java.util.Map.Entry<Integer, int[]> candidate : _candidates.entrySet())
			{
				long dx = candidate.getValue()[0] - _x;
				long dy = candidate.getValue()[1] - _y;
				if (dx * dx + dy * dy < best)
				{
					best = dx * dx + dy * dy;
					_target = candidate.getKey();
				}
			}
		}

		void see(byte[] payload) throws SmokeException
		{
			_packets++;
			PacketReader in = new PacketReader(payload);
			int opcode = in.readC();
			_opcodes.merge(opcode, 1, Integer::sum);
			if (opcode == 0x62)
				_messages.merge(in.readD(), 1, Integer::sum);
			if (opcode != OP_NPC_INFO)
				return;

			int objectId = in.readD();
			in.skip(4);
			boolean attackable = in.readD() == 1;
			_npcs++;
			if (attackable)
				_candidates.put(objectId, new int[] { in.readD(), in.readD() });
		}

		void hold(Deadline deadline, long millis, Reporter reporter) throws SmokeException
		{
			long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
			long nextAttack = 0L;
			while (System.nanoTime() < end && !deadline.expired())
			{
				if (_fight && _target != 0 && System.nanoTime() >= nextAttack)
				{
					_game.send(command(OP_ACTION, _target));
					nextAttack = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ATTACK_EVERY_MILLIS);
				}
				try
				{
					_game.receive(200L);
				}
				catch (SmokeException e)
				{
					if (e.kind() != SmokeException.Kind.TIMEOUT)
						throw e;
				}
			}
			reporter.info((_fight ? "fighter" : "watcher") + ": " + _npcs + " NpcInfo, target "
					+ (_target == 0 ? "none" : Integer.toString(_target)) + ", " + _packets + " packets at " + _where + ", system messages "
					+ _messages + ", opcodes " + _opcodes);
		}

		private static byte[] command(int opcode, int objectId)
		{
			return new PacketWriter().writeC(opcode).writeD(objectId).writeD(0).writeD(0).writeD(0).writeC(0)
					.toByteArray();
		}
	}
}
