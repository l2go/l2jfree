/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.tools.dbinstaller.console;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DBInstallerCleanInstallSafetyTest
{
	@Test
	void cleanModeRequiresExactDatabaseNameConfirmation()
	{
		assertThat(DBInstallerConsole.confirmsCleanInstall("l2jfree_gs", "l2jfree_gs")).isTrue();
		assertThat(DBInstallerConsole.confirmsCleanInstall("l2jfree_gs", "l2jfree_ls")).isFalse();
		assertThat(DBInstallerConsole.confirmsCleanInstall("l2jfree_gs", null)).isFalse();
		assertThat(DBInstallerConsole.confirmsCleanInstall(null, "l2jfree_gs")).isFalse();
	}
}
