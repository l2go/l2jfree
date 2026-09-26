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
package com.l2jfree.gameserver.network.loginserverpackets;

/**
 * @author -Wooden-
 */
public final class PlayerAuthResponse extends LoginServerBasePacket
{
	private final String _account;
	private final boolean _authed;
	private final String _host;
	private final boolean _hasKey;
	private final int _playOk1;
	private final int _playOk2;
	private final int _loginOk1;
	private final int _loginOk2;
	
	public PlayerAuthResponse(byte[] decrypt)
	{
		super(decrypt);
		_account = readS();
		_authed = (readC() != 0);
		_host = canRead() ? readS() : null;
		if (canRead(16))
		{
			_hasKey = true;
			_playOk1 = readD();
			_playOk2 = readD();
			_loginOk1 = readD();
			_loginOk2 = readD();
		}
		else
		{
			_hasKey = false;
			_playOk1 = 0;
			_playOk2 = 0;
			_loginOk1 = 0;
			_loginOk2 = 0;
		}
	}
	
	/**
	 * @return Returns the account.
	 */
	public String getAccount()
	{
		return _account;
	}
	
	/**
	 * @return Returns the authed state.
	 */
	public boolean isAuthed()
	{
		return _authed;
	}
	
	public String getHost()
	{
		return _host;
	}
	
	public boolean hasKey()
	{
		return _hasKey;
	}
	
	public int getPlayOk1()
	{
		return _playOk1;
	}
	
	public int getPlayOk2()
	{
		return _playOk2;
	}
	
	public int getLoginOk1()
	{
		return _loginOk1;
	}
	
	public int getLoginOk2()
	{
		return _loginOk2;
	}
}
