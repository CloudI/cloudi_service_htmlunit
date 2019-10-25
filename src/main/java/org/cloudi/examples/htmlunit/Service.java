//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:
package org.cloudi.examples.htmlunit;

import java.util.HashMap;
import java.util.ArrayList;
import java.io.IOException;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.DomNode;
import org.cloudi.API;

public class Service implements Runnable
{
    private API api;

    public Service(final int thread_index)
    {
        try
        {
            this.api = new API(thread_index);
        }
        catch (API.InvalidInputException e)
        {
            e.printStackTrace(Main.err);
            System.exit(1);
        }
        catch (API.MessageDecodingException e)
        {
            e.printStackTrace(Main.err);
            System.exit(1);
        }
        catch (API.TerminateException e)
        {
            Main.error(this, "terminate before initialization");
            System.exit(1);
        }
    }

    public void run()
    {
        try
        {
            this.api.subscribe("render/get", this::render);
            this.api.poll();
        }
        catch (API.TerminateException e)
        {
            // service termination
        }
        catch (Exception e)
        {
            e.printStackTrace(Main.err);
        }
    }

    private WebClient createWebClient(int timeout)
    {
        final WebClient webClient = new WebClient();
        final WebClientOptions options = webClient.getOptions();
        options.setTimeout(timeout);
        options.setThrowExceptionOnFailingStatusCode(false);
        options.setThrowExceptionOnScriptError(false);
        options.setPrintContentOnFailingStatusCode(false);
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        return webClient;
    }

    public Object render(Integer request_type, String name, String pattern,
                         byte[] request_info, byte[] request,
                         Integer timeout, Byte priority,
                         byte[] trans_id, OtpErlangPid pid)
    {
        byte[] response = null;
        HashMap<String, ArrayList<String>> request_parameters =
            this.api.info_key_value_parse(request);
        final ArrayList<String> url = request_parameters.remove("url");
        if (url != null)
        {
            final ArrayList<String> xpath = request_parameters.remove("xpath");
            try (final WebClient webClient = createWebClient(timeout))
            {
                final int timeout_js = timeout / 2;
                final HtmlPage page = webClient.getPage(url.get(0));
                webClient.waitForBackgroundJavaScriptStartingBefore(timeout_js);

                if (xpath != null)
                {
                    final DomNode element = page.getFirstByXPath(xpath.get(0));
                    if (element != null)
                        response = element.asXml().getBytes();
                }
                else
                {
                    response = page.asXml().getBytes();
                }
            }
            catch (FailingHttpStatusCodeException e)
            {
                e.printStackTrace(Main.err);
            }
            catch (IOException e)
            {
                e.printStackTrace(Main.err);
            }
        }
        return response;
    }
}
