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
package com.l2jfree.util.logging;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;

/**
 * Writes the log to the standard output the process had when logging started. The console appender of Logback looks
 * {@code System.out} up at every event, but the server replaces {@code System.out} with a stream that logs what is
 * written to it, so the console appender printed nothing and {@code docker logs} stayed empty.
 */
public final class StandardOutputAppender extends OutputStreamAppender<ILoggingEvent>
{
	@Override
	public void start()
	{
		if (getOutputStream() == null)
			setOutputStream(new KeepOpen(System.out));
		super.start();
	}
	
	/** The standard output outlives the appender. */
	private static final class KeepOpen extends FilterOutputStream
	{
		KeepOpen(OutputStream out)
		{
			super(out);
		}
		
		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException
		{
			out.write(bytes, offset, length);
		}
		
		@Override
		public void close() throws IOException
		{
			flush();
		}
	}
}
