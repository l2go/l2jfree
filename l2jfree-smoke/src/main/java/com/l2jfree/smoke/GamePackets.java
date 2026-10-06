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

import java.util.ArrayList;
import java.util.List;

/**
 * The packets of the game protocol (port 7777) that the smoke test uses.
 */
final class GamePackets
{
	// server to client
	static final int OP_CHAR_SELECTION_INFO = 0x09;
	static final int OP_LOGIN_FAIL = 0x0a;
	static final int OP_CHAR_SELECTED = 0x0b;
	static final int OP_NEW_CHARACTER_SUCCESS = 0x0d;
	static final int OP_CHARACTER_CREATE_SUCCESS = 0x0f;
	static final int OP_CHARACTER_CREATE_FAIL = 0x10;
	static final int OP_SERVER_CLOSE = 0x20;
	static final int OP_USER_INFO = 0x32;
	static final int OP_KEY_PACKET = 0x2e;
	static final int OP_LEAVE_WORLD = 0x84;

	// client to server
	static final int OP_LOGOUT = 0x00;
	static final int OP_NEW_CHARACTER = 0x0c;
	static final int OP_PROTOCOL_VERSION = 0x0e;
	static final int OP_ENTER_WORLD = 0x11;
	static final int OP_CHARACTER_SELECTED = 0x12;
	static final int OP_NEW_CHARACTER_INIT = 0x13;
	static final int OP_AUTH_LOGIN = 0x2b;

	/** Bytes the client appends to ProtocolVersion and the server skips when StrictFinal is set. */
	private static final int PROTOCOL_VERSION_PADDING = 260;
	private static final int AUTH_LOGIN_PADDING = 16;
	private static final int ENTER_WORLD_PADDING = 104;

	/** Size of one template in NewCharacterSuccess: 20 words. */
	private static final int TEMPLATE_SIZE = 20 * 4;

	private GamePackets()
	{
	}

	/** KeyPacket: whether the protocol revision is accepted and the first eight bytes of the key. */
	record KeyPacket(boolean protocolOk, byte[] key)
	{
	}

	/**
	 * The first entry of CharSelectionInfo is read for its name, the account, and the session id; the
	 * rest of the list is not parsed.
	 */
	record CharacterList(int count, String firstName, String firstAccount, int firstSessionId)
	{
	}

	/** One character template of NewCharacterSuccess. */
	record Template(int race, int classId, int str, int dex, int con, int intelligence, int wit, int men)
	{
	}

	/** 0x0e: the protocol revision, then 260 bytes the server skips in strict mode. Never encrypted. */
	static byte[] protocolVersion(int revision)
	{
		return new PacketWriter().writeC(OP_PROTOCOL_VERSION).writeD(revision).writeZeros(PROTOCOL_VERSION_PADDING)
				.toByteArray();
	}

	/**
	 * 0x2b: the account name, the two play-ok words in the order play 2, play 1, then the two login-ok
	 * words, then 16 bytes the server skips in strict mode.
	 */
	static byte[] authLogin(String account, int playOk1, int playOk2, int loginOk1, int loginOk2)
	{
		return new PacketWriter().writeC(OP_AUTH_LOGIN).writeS(account).writeD(playOk2).writeD(playOk1)
				.writeD(loginOk1).writeD(loginOk2).writeZeros(AUTH_LOGIN_PADDING).toByteArray();
	}

	/** 0x13: asks for the character templates. */
	static byte[] newCharacterInit()
	{
		return new PacketWriter().writeC(OP_NEW_CHARACTER_INIT).toByteArray();
	}

	/**
	 * 0x0c: the name, race, sex, class, the six base stats in the order INT, STR, CON, MEN, DEX, WIT,
	 * hair style, hair color, and face. The server takes the name, sex, class, and the three look values.
	 */
	static byte[] newCharacter(String name, Template template, int sex, int hairStyle, int hairColor, int face)
	{
		return new PacketWriter().writeC(OP_NEW_CHARACTER).writeS(name).writeD(template.race()).writeD(sex)
				.writeD(template.classId()).writeD(template.intelligence()).writeD(template.str())
				.writeD(template.con()).writeD(template.men()).writeD(template.dex()).writeD(template.wit())
				.writeD(hairStyle).writeD(hairColor).writeD(face).toByteArray();
	}

	/** 0x12: the slot, then a short and three words the server reads and ignores. */
	static byte[] characterSelected(int slot)
	{
		return new PacketWriter().writeC(OP_CHARACTER_SELECTED).writeD(slot).writeH(0).writeD(0).writeD(0).writeD(0)
				.toByteArray();
	}

	/** 0x11: 104 bytes the server skips. */
	static byte[] enterWorld()
	{
		return new PacketWriter().writeC(OP_ENTER_WORLD).writeZeros(ENTER_WORLD_PADDING).toByteArray();
	}

	/** 0x00. */
	static byte[] logout()
	{
		return new PacketWriter().writeC(OP_LOGOUT).toByteArray();
	}

	/** Reads KeyPacket after its opcode: the revision flag, eight key bytes, and more the client ignores. */
	static KeyPacket parseKeyPacket(PacketReader in) throws SmokeException
	{
		boolean ok = in.readC() == 1;
		byte[] key = in.readB(8);
		return new KeyPacket(ok, key);
	}

	/**
	 * Reads CharSelectionInfo after its opcode: the number of characters, a word with the value 7, a zero
	 * byte, then per character the name, the character id, the account name, and the session id.
	 */
	static CharacterList parseCharacterList(PacketReader in) throws SmokeException
	{
		int count = in.readD();
		in.skip(5);
		if (count <= 0)
			return new CharacterList(Math.max(count, 0), null, null, 0);

		String name = in.readS();
		in.skip(4);
		String account = in.readS();
		int session = in.readD();
		return new CharacterList(count, name, account, session);
	}

	/**
	 * Reads NewCharacterSuccess after its opcode: a count, then per template 20 words: race, class, and
	 * for each of STR, DEX, CON, INT, WIT, MEN the three words 0x46, value, 0x0a.
	 */
	static List<Template> parseTemplates(PacketReader in) throws SmokeException
	{
		int count = in.readD();
		List<Template> templates = new ArrayList<>();
		while (templates.size() < count && in.remaining() >= TEMPLATE_SIZE)
		{
			int race = in.readD();
			int classId = in.readD();
			int[] stats = new int[6];
			for (int i = 0; i < stats.length; i++)
			{
				in.skip(4);
				stats[i] = in.readD();
				in.skip(4);
			}
			templates.add(new Template(race, classId, stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]));
		}
		return templates;
	}

	/** @return a text for the reason code of CharacterCreateFail */
	static String describeCreateFail(int reason)
	{
		return switch (reason)
		{
			case 0 -> "creation failed";
			case 1 -> "too many characters";
			case 2 -> "the name already exists";
			case 3 -> "the name is longer than 16 characters";
			case 4 -> "incorrect name";
			case 5 -> "creation is not allowed on this server";
			case 6 -> "choose another server";
			default -> "unknown reason";
		};
	}
}
