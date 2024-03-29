//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:

package org.cloudi.examples.htmlunit;

import com.beust.jcommander.Parameter;
import org.htmlunit.BrowserVersion;

public class Arguments
{
    @Parameter(names = "-browser", description = "HtmlUnit browser")
    private String browser = "default";

    @Parameter(names = "-cookies-file", description = "HtmlUnit cookies file")
    private String cookies_file = "/tmp/htmlunit_cookies.txt";

    @Parameter(names = "-verbose", description = "HtmlUnit logs vomit")
    private boolean verbose = false;

    public BrowserVersion getBrowser()
    {
        switch (this.browser.toLowerCase())
        {
            case "default":
            case "best_supported":
                return BrowserVersion.BEST_SUPPORTED;
            case "chrome":
                return BrowserVersion.CHROME;
            case "edge":
                return BrowserVersion.EDGE;
            case "firefox":
                return BrowserVersion.FIREFOX;
            case "firefox_esr":
                return BrowserVersion.FIREFOX_ESR;
            default:
                Main.error(this, "-browser %s is invalid!", this.browser);
                System.exit(1);
                return null;
        }
    }

    public String getCookiesFilePath()
    {
        return this.cookies_file;
    }

    public boolean getVerbose()
    {
        return this.verbose;
    }
}

