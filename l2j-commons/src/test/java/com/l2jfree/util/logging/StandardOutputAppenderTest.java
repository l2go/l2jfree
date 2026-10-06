/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/** Routes warning and error events to the dedicated error stream. */
public final class ErrorEventFilter extends Filter<ILoggingEvent>
package com.l2jfree.util.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

class StandardOutputAppenderTest
{
	@Test
	void theLogReachesTheOutputOfTheStartEvenAfterSystemOutIsReplaced() throws Exception
	{
		PrintStream original = System.out;
		ByteArrayOutputStream standard = new ByteArrayOutputStream();
		ByteArrayOutputStream replaced = new ByteArrayOutputStream();
		LoggerContext context = new LoggerContext();
		StandardOutputAppender appender = new StandardOutputAppender();
		try
		{
			System.setOut(new PrintStream(standard, true, StandardCharsets.UTF_8));
			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%level %msg%n");
			encoder.start();
			appender.setContext(context);
			appender.setEncoder(encoder);
			appender.start();
			
			// what the server does after it has set logging up
			System.setOut(new PrintStream(replaced, true, StandardCharsets.UTF_8));
			
			Logger logger = context.getLogger("probe");
			logger.setLevel(ch.qos.logback.classic.Level.INFO);
			logger.addAppender(appender);
			logger.info("Platform ready");
			
			assertThat(standard.toString(StandardCharsets.UTF_8)).isEqualTo("INFO Platform ready\n");
			assertThat(replaced.size()).isZero();
		}
		finally
		{
			System.setOut(original);
			appender.stop();
		}
	}
	
	@Test
	void stoppingTheAppenderLeavesTheStandardOutputOpen() throws Exception
	{
		PrintStream original = System.out;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream standard = new PrintStream(bytes, true, StandardCharsets.UTF_8);
		StandardOutputAppender appender = new StandardOutputAppender();
		try
		{
			System.setOut(standard);
			LoggerContext context = new LoggerContext();
			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%msg%n");
			encoder.start();
			appender.setContext(context);
			appender.setEncoder(encoder);
			appender.start();
			
			appender.stop();
			standard.println("still open");
			
			assertThat(standard.checkError()).isFalse();
			assertThat(bytes.toString(StandardCharsets.UTF_8)).isEqualTo("still open\n");
		}
		finally
		{
			System.setOut(original);
		}
	}
}
