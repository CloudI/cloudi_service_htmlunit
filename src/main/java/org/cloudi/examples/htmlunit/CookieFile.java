//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:
package org.cloudi.examples.htmlunit;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import com.gargoylesoftware.htmlunit.util.Cookie;

public class CookieFile
{
    private static final int DOMAIN = 0;
    private static final int SUBDOMAINS = 1;
    private static final int PATH = 2;
    private static final int SECURE = 3;
    private static final int EXPIRES = 4;
    private static final int NAME = 5;
    private static final int VALUE = 6;

    private static final char TAB = '\t';

    private static BufferedReader open_read(final String file_path)
        throws IOException
    {
        return new BufferedReader(new FileReader(file_path));
    }

    private static PrintWriter open_write(final String file_path)
        throws IOException
    {
        return new PrintWriter(new FileOutputStream(file_path));
    }

    private static boolean boolean_parse(final String value)
    {
        return Boolean.valueOf(value).booleanValue();
    }

    private static String boolean_format(final boolean value)
    {
        return Boolean.valueOf(value).toString().toUpperCase();
    }

    private static Date epoch_unix_parse(final String value)
    {
        return new Date(Long.parseLong(value) * 1000);
    }

    private static String epoch_unix_format(final Date value)
    {
        return Long.valueOf(value.getTime() / 1000).toString();
    }

    public static Set<Cookie> load()
    {
        final String file_path = Main.arguments().getCookiesFilePath();
        Set<Cookie> cookies = new HashSet<Cookie>();
        try (BufferedReader reader = open_read(file_path))
        {
            String line;
            long line_number = 0;
            while ((line = reader.readLine()) != null)
            {
                line_number++;
                final boolean http_only = line.startsWith("#HttpOnly_");
                boolean comment = false;
                if (http_only)
                {
                    line = line.substring(10);
                }
                else
                {
                    comment = line.startsWith("#") ||
                              (line.trim().length() == 0);
                }
                if (comment)
                    continue;
                String[] tokens = line.split("\\t");
                final String domain = tokens[DOMAIN];
                final boolean subdomains = boolean_parse(tokens[SUBDOMAINS]);
                final String path = tokens[PATH];
                final boolean secure = boolean_parse(tokens[SECURE]);
                final Date expires = epoch_unix_parse(tokens[EXPIRES]);
                final String name = tokens[NAME];
                final String value = tokens[VALUE];
                final boolean added = cookies.add(
                    new Cookie(domain, name, value,
                               path, expires, secure, http_only));
                if (added == false)
                {
                    throw new IOException("Failed to add " + file_path +
                                          ":" + line_number + " cookie!");
                }
            }
        }
        catch (IOException e)
        {
            cookies = null;
            e.printStackTrace(Main.err);
        }
        return cookies;
    }

    public static boolean store(Set<Cookie> cookies)
    {
        final String file_path = Main.arguments().getCookiesFilePath();
        boolean stored = false;
        try (PrintWriter writer = open_write(file_path))
        {
            writer.println("# cloudi_service_htmlunit generated cookie file");
            for (Cookie cookie : cookies)
            {
                final StringBuilder line = new StringBuilder(80);
                if (cookie.isHttpOnly())
                    line.append("#HttpOnly_");
                line.append(cookie.getDomain())
                    .append(TAB)
                    .append(boolean_format(true))
                    .append(TAB)
                    .append(cookie.getPath())
                    .append(TAB)
                    .append(boolean_format(cookie.isSecure()))
                    .append(TAB)
                    .append(epoch_unix_format(cookie.getExpires()))
                    .append(TAB)
                    .append(cookie.getName())
                    .append(TAB)
                    .append(cookie.getValue());
                writer.println(line.toString());
            }
            writer.flush();
            stored = true;
        }
        catch (IOException e)
        {
            e.printStackTrace(Main.err);
        }
        return stored;
    }
}
