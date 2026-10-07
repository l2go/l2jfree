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
package com.l2jfree.loginserver.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.beans.FailedLoginAttempt;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.loginserver.services.exception.AccountWrongPasswordException;
import com.l2jfree.tools.codec.Base64;

class LoginFailureResetTest
{
	@Test
	@DisplayName("a valid password resets only its address's failed-login count")
	void successfulLoginResetsFailedAttemptsForItsAddress() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		byte[] hash = MessageDigest.getInstance("SHA").digest("correct".getBytes(StandardCharsets.UTF_8));
		Accounts account = new Accounts("alice", Base64.encodeBytes(hash), BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		setField(manager, "_service", service);

		Map<InetAddress, FailedLoginAttempt> attempts = new HashMap<InetAddress, FailedLoginAttempt>();
		setField(manager, "_hackProtection", attempts);
		InetAddress address = InetAddress.getByName("192.0.2.1");
		InetAddress otherAddress = InetAddress.getByName("198.51.100.2");
		attempts.put(otherAddress, new FailedLoginAttempt(otherAddress, "other-wrong"));

		int oldLimit = LoginConfig.LOGIN_TRY_BEFORE_BAN;
		LoginConfig.LOGIN_TRY_BEFORE_BAN = 3;
		try
		{
			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-one", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-two", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThat(attempts.get(address).getCount()).isEqualTo(2);

			assertThat(manager.loginValid("alice", "correct", address)).isTrue();
			assertThat(attempts).doesNotContainKey(address);
			assertThat(attempts.get(otherAddress).getCount()).isEqualTo(1);

			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-three", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThat(attempts.get(address).getCount()).isEqualTo(1);
		}
		finally
		{
			LoginConfig.LOGIN_TRY_BEFORE_BAN = oldLimit;
		}
	}

	/** The row read for the password check may be stale: a login writes the last login and nothing else. */
	@Test
	@DisplayName("a login writes the time and the address, not the access level or the password")
	void loginWritesOnlyWhatItChanges() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		byte[] hash = MessageDigest.getInstance("SHA").digest("correct".getBytes(StandardCharsets.UTF_8));
		Accounts account = new Accounts("alice", Base64.encodeBytes(hash), BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		setField(manager, "_service", service);
		setField(manager, "_hackProtection", new HashMap<InetAddress, FailedLoginAttempt>());
		
		assertThat(manager.loginValid("alice", "correct", InetAddress.getByName("192.0.2.7"))).isTrue();
		
		ArgumentCaptor<Accounts> written = ArgumentCaptor.forClass(Accounts.class);
		verify(service).updateGivenColumns(written.capture());
		assertThat(written.getValue().getLogin()).isEqualTo("alice");
		assertThat(written.getValue().getLastIp()).isEqualTo("192.0.2.7");
		assertThat(written.getValue().getLastactive()).isNotNull();
		assertThat(written.getValue().getAccessLevel()).isNull();
		assertThat(written.getValue().getPassword()).isNull();
	}
	
	private static void setField(LoginManager manager, String name, Object value) throws Exception
	{
		Field field = LoginManager.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(manager, value);
	}
}
