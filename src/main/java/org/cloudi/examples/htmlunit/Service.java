//-*-Mode:java;coding:utf-8;tab-width:4;c-basic-offset:4;indent-tabs-mode:()-*-
// ex: set ft=java fenc=utf-8 sts=4 ts=4 sw=4 et nomod:
package org.cloudi.examples.htmlunit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.ClassNotFoundException;
import java.lang.StringBuilder;
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
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.cloudi.API;

public class Service implements Runnable
{
    private API api;
    private Cache cache;
    private static CookieManager cookies = new CookieManager();
    private static final Set<String> request_headers_ignored =
        new HashSet<String>(Arrays.asList(
            "accept", "accept-encoding", "connection",
            "host", "peer", "peer-port", "source-address", "source-port",
            "url-path", "user-agent"));
    private static final Set<String> response_headers_ignored =
        new HashSet<String>(Arrays.asList(
            "server", "date", "content-length", "content-encoding",
            "keep-alive", "connection", "transfer-encoding"));

    public Service(final int thread_index)
    {
        try
        {
            this.api = new API(thread_index);
            this.cache = new Cache();
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
        byte[][] response = null;
        HashMap<String, ArrayList<String>> request_parameters =
            this.api.info_key_value_parse(request);
        final ArrayList<String> url = request_parameters.remove("url");
        if (url != null)
        {
            final ArrayList<String> xpath = request_parameters.remove("xpath");
            HashMap<String, ArrayList<String>> request_info_parameters =
                this.api.info_key_value_parse(request_info);
            try (final WebClient client = createWebClient(timeout))
            {
                final int timeout_js = timeout / 2;
                final URL url_value = new URL(url.get(0));
                final WebRequest client_request = new WebRequest(url_value);
                for (Map.Entry<String, ArrayList<String>> request_header :
                     request_info_parameters.entrySet())
                {
                    final String header_key = request_header.getKey();
                    if (request_headers_ignored.contains(header_key))
                        continue;
                    for (String header_value : request_header.getValue())
                    {
                        client_request.setAdditionalHeader(header_key,
                                                           header_value);
                    }
                }
                final HtmlPage page = client.getPage(client_request);
                client.waitForBackgroundJavaScriptStartingBefore(timeout_js);

                byte[] response_body = null;
                if (xpath != null)
                {
                    final DomNode element = page.getFirstByXPath(xpath.get(0));
                    if (element != null)
                        response_body = element.asXml().getBytes();
                }
                else
                {
                    response_body = page.asXml().getBytes();
                }

                final WebResponse client_response = page.getWebResponse();
                final Map<String, List<String>> response_header =
                    new HashMap<String, List<String>>();
                for (NameValuePair client_response_header :
                     client_response.getResponseHeaders())
                {
                    final String header_key =
                        client_response_header.getName().toLowerCase();
                    if (response_headers_ignored.contains(header_key))
                        continue;
                    response_header.put(header_key,
                        Arrays.asList(client_response_header.getValue()));
                }
                response = new byte[][]{
                    this.api.info_key_value_new(response_header),
                    response_body};
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

    private void cookieDomainStore(Cookie cookie, Map<String, Integer> domains)
    {
        String domain = cookie.getDomain();
        if (domain == null)
            domain = "";
        Integer count = domains.get(domain);
        if (count == null)
            count = 0;
        domains.put(domain, count + 1);
    }

    private void logCookieDomains(Map<String, Integer> domains, boolean stored)
    {
        final String action = stored ? "stored" : "loaded";
        final String direction = stored ? "to" : "from";
        final StringBuilder domains_output = new StringBuilder();
        for (Map.Entry<String, Integer> domain : domains.entrySet())
        {
            domains_output.append(action);
            domains_output.append(" \"");
            domains_output.append(domain.getKey());
            domains_output.append("\" (count = ");
            domains_output.append(domain.getValue().intValue());
            domains_output.append(")");
            domains_output.append("\n");
        }
        domains_output.append(direction);
        domains_output.append(" cookies-file \"");
        domains_output.append(Main.arguments().getCookiesFilePath());
        domains_output.append("\"\n");
        Main.out.print(domains_output.toString());
    }

    private byte[][] successResponse()
    {
        HashMap<String, List<String>> info =
            new HashMap<String, List<String>>();
        info.put("status", Arrays.asList("204"));
        return new byte[][]{this.api.info_key_value_new(info), null};
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
            final Map<String, Integer> domains_logged =
                new HashMap<String, Integer>();
            for (Cookie cookie : cookies_loaded)
            {
                cookieDomainStore(cookie, domains_logged);
                cookies_new.addCookie(cookie);
            }
            logCookieDomains(domains_logged, false);
            Service.cookies = cookies_new;
            response = successResponse();
        }
        return response;
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
            Set<Cookie> cookies_stored = Service.cookies.getCookies();
            final Map<String, Integer> domains_logged =
                new HashMap<String, Integer>();
            for (Cookie cookie : cookies_stored)
            {
                cookieDomainStore(cookie, domains_logged);
            }
            out.writeObject(cookies_stored);
            logCookieDomains(domains_logged, true);
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
        Service.cookies.clearCookies();
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
