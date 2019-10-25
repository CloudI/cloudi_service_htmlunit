//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:

package org.cloudi.examples.htmlunit;

import com.beust.jcommander.Parameter;
import com.gargoylesoftware.htmlunit.BrowserVersion;

public class Arguments
{
    @Parameter(names = "-browser", description = "HtmlUnit browser")
    private String browser = "default";

    public BrowserVersion getBrowser()
    {
        switch (this.browser.toLowerCase())
        {
            case "default":
            case "best_supported":
                return BrowserVersion.BEST_SUPPORTED;
            case "chrome":
                return BrowserVersion.CHROME;
            case "firefox":
            case "firefox_60":
                return BrowserVersion.FIREFOX_60;
            case "ie":
            case "internet_explorer":
                return BrowserVersion.INTERNET_EXPLORER;
            default:
                Main.error(this, "-browser %s is invalid!", this.browser);
                System.exit(1);
                return null;
        }
    }
}

