//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:

package org.cloudi.examples.htmlunit;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;

public class LoggingConfig
{
    private static Logger logger = null;

    public LoggingConfig()
    {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                           "[%4$s] %5$s%6$s%n");
        final ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new SimpleFormatter());
        LoggingConfig.logger = Logger.getLogger("");
        LoggingConfig.logger.setLevel(Level.ALL);
        LoggingConfig.logger.addHandler(consoleHandler);
    }

    public static void initialize()
    {
        System.setProperty("java.util.logging.config.class",
                           "org.cloudi.examples.htmlunit.LoggingConfig");
        try
        {
            LogManager.getLogManager().readConfiguration();
        }
        catch (IOException e)
        {
            e.printStackTrace(Main.err);
            System.exit(1);
        }
    }

    public static Logger getLogger()
    {
        return LoggingConfig.logger;
    }
}

