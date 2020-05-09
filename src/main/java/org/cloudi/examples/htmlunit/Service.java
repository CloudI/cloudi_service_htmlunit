//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:
package org.cloudi.examples.htmlunit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.ClassNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.gargoylesoftware.htmlunit.Cache;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;
import org.cloudi.API;

public class Service implements Runnable
{
    private API api;
    private Cache cache;
    private CookieManager cookies;

    public Service(final int thread_index)
    {
        try
        {
            this.api = new API(thread_index);
            this.cache = new Cache();
            this.cookies = new CookieManager();
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
            this.api.subscribe("cookies/load/get", this::cookiesLoad);
            this.api.subscribe("cookies/store/get", this::cookiesStore);
            this.api.subscribe("cookies/clear/get", this::cookiesClear);
            this.api.subscribe("cache/clear/get", this::cacheClear);
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
        final WebClient client = new WebClient();
        final WebClientOptions options = client.getOptions();
        options.setTimeout(timeout);
        options.setThrowExceptionOnFailingStatusCode(false);
        options.setThrowExceptionOnScriptError(false);
        options.setPrintContentOnFailingStatusCode(false);
        client.setCssErrorHandler(new SilentCssErrorHandler());
        client.setCache(this.cache);
        client.setCookieManager(this.cookies);
        return client;
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
            try (final WebClient client = createWebClient(timeout))
            {
                final int timeout_js = timeout / 2;
                final URL url_value = new URL(url.get(0));
                final WebRequest client_request = new WebRequest(url_value);
                for (Map.Entry<String, ArrayList<String>> header :
                     request_parameters.entrySet())
                {
                    for (String header_value : header.getValue())
                    {
                        client_request.setAdditionalHeader(header.getKey(),
                                                           header_value);
                    }
                }
                final HtmlPage page = client.getPage(client_request);
                client.waitForBackgroundJavaScriptStartingBefore(timeout_js);

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

    private ObjectInputStream getCookiesFileIn()
        throws FileNotFoundException,
               IOException
    {
        final String file_path = Main.arguments().getCookiesFilePath();
        return new ObjectInputStream(new FileInputStream(file_path));
    }

    private ObjectOutputStream getCookiesFileOut()
        throws FileNotFoundException,
               IOException
    {
        final String file_path = Main.arguments().getCookiesFilePath();
        return new ObjectOutputStream(new FileOutputStream(file_path));
    }

    @SuppressWarnings("unchecked")
    public Object cookiesLoad(Integer request_type,
                              String name, String pattern,
                              byte[] request_info, byte[] request,
                              Integer timeout, Byte priority,
                              byte[] trans_id, OtpErlangPid pid)
    {
        byte[][] response = null;
        Set<Cookie> cookies_loaded = null;
        try (ObjectInputStream in = getCookiesFileIn())
        {
            cookies_loaded = (Set<Cookie>) in.readObject();
        }
        catch (IOException e)
        {
            e.printStackTrace(Main.err);
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace(Main.err);
        }
        if (cookies_loaded != null)
        {
            final CookieManager cookies_new = new CookieManager();
            for (Cookie cookie : cookies_loaded)
            {
                cookies_new.addCookie(cookie);
            }
            this.cookies = cookies_new;
            response = successResponse();
        }
        return response;
    }

    private byte[][] successResponse()
    {
        HashMap<String, ArrayList<String>> info =
            new HashMap<String, ArrayList<String>>();
        ArrayList<String> value = new ArrayList<String>();
        value.add("202");
        info.put("status", value);
        return new byte[][]{this.api.info_key_value_new(info), null};
    }

    public Object cookiesStore(Integer request_type,
                               String name, String pattern,
                               byte[] request_info, byte[] request,
                               Integer timeout, Byte priority,
                               byte[] trans_id, OtpErlangPid pid)
    {
        byte[][] response = null;
        try (ObjectOutputStream out = getCookiesFileOut())
        {
            Set<Cookie> cookies_stored = this.cookies.getCookies();
            out.writeObject(cookies_stored);
            response = successResponse();
        }
        catch (IOException e)
        {
            e.printStackTrace(Main.err);
        }
        return response;
    }

    public Object cookiesClear(Integer request_type,
                               String name, String pattern,
                               byte[] request_info, byte[] request,
                               Integer timeout, Byte priority,
                               byte[] trans_id, OtpErlangPid pid)
    {
        this.cookies.clearCookies();
        return successResponse();
    }

    public Object cacheClear(Integer request_type,
                             String name, String pattern,
                             byte[] request_info, byte[] request,
                             Integer timeout, Byte priority,
                             byte[] trans_id, OtpErlangPid pid)
    {
        this.cache.clear();
        return successResponse();
    }
}
