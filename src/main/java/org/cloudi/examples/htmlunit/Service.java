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
import java.lang.StringBuilder;
import java.io.IOException;
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
    private int thread_index;
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
        this.thread_index = thread_index;
        this.cache = new Cache();
    }

    public void run()
    {
        try
        {
            if (this.thread_index == 0)
            {
                Set<Cookie> cookies_loaded = CookieFile.load(true);
                if (cookies_loaded != null)
                {
                    final CookieManager cookies_init = new CookieManager();
                    for (Cookie cookie : cookies_loaded)
                    {
                        cookies_init.addCookie(cookie);
                    }
                    Service.cookies = cookies_init;
                }
                this.api.subscribe("cookies/load/get", Service::cookiesLoad);
                this.api.subscribe("cookies/store/get", Service::cookiesStore);
                this.api.subscribe("cookies/clear/get", Service::cookiesClear);
            }
            this.api.subscribe("render/get", this::render);
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
            API.info_key_value_parse(request);
        final ArrayList<String> url = request_parameters.remove("url");
        if (url != null)
        {
            final ArrayList<String> xpath = request_parameters.remove("xpath");
            HashMap<String, ArrayList<String>> request_info_parameters =
                API.info_key_value_parse(request_info);
            try (final WebClient client = createWebClient(timeout))
            {
                final int timeout_js = timeout / 2;
                final URL url_value = new URL(url.get(0));
                final WebRequest client_request = new WebRequest(url_value);
                for (Map.Entry<String, ArrayList<String>> request_header :
                     request_info_parameters.entrySet())
                {
                    final String header_key = request_header.getKey();
                    if (Service.request_headers_ignored.contains(header_key))
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
                    if (Service.response_headers_ignored.contains(header_key))
                        continue;
                    response_header.put(header_key,
                        Arrays.asList(client_response_header.getValue()));
                }
                response = new byte[][]{API.info_key_value_new(response_header),
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

    private static void cookieDomainStore(Cookie cookie,
                                          Map<String, Integer> domains)
    {
        String domain = cookie.getDomain();
        if (domain == null)
            domain = "";
        Integer count = domains.get(domain);
        if (count == null)
            count = 0;
        domains.put(domain, count + 1);
    }

    private static void logCookieDomains(Map<String, Integer> domains,
                                         boolean stored)
    {
        final String action = stored ? "stored" : "loaded";
        final String direction = stored ? "to" : "from";
        final StringBuilder domains_output = new StringBuilder();
        long count_total = 0;
        for (Map.Entry<String, Integer> domain : domains.entrySet())
        {
            final int count = domain.getValue().intValue();
            domains_output.append(action)
                          .append(" \"")
                          .append(domain.getKey())
                          .append("\" (count = ")
                          .append(count)
                          .append(")\n");
            count_total += count;
        }
        domains_output.append(direction)
                      .append(" cookies-file \"")
                      .append(Main.arguments().getCookiesFilePath())
                      .append("\" (total = ")
                      .append(count_total)
                      .append(")\n");
        Main.out.print(domains_output.toString());
    }

    private static byte[][] successResponse()
    {
        HashMap<String, List<String>> info =
            new HashMap<String, List<String>>();
        info.put("status", Arrays.asList("204"));
        return new byte[][]{API.info_key_value_new(info), null};
    }

    public Object cacheClear(Integer request_type,
                             String name, String pattern,
                             byte[] request_info, byte[] request,
                             Integer timeout, Byte priority,
                             byte[] trans_id, OtpErlangPid pid)
    {
        this.cache.clear();
        return Service.successResponse();
    }

    public static Object cookiesLoad(Integer request_type,
                                     String name, String pattern,
                                     byte[] request_info, byte[] request,
                                     Integer timeout, Byte priority,
                                     byte[] trans_id, OtpErlangPid pid)
    {
        byte[][] response = null;
        Set<Cookie> cookies_loaded = CookieFile.load(false);
        if (cookies_loaded != null)
        {
            final CookieManager cookies_new = new CookieManager();
            final Map<String, Integer> domains_logged =
                new HashMap<String, Integer>();
            for (Cookie cookie : cookies_loaded)
            {
                Service.cookieDomainStore(cookie, domains_logged);
                cookies_new.addCookie(cookie);
            }
            Service.logCookieDomains(domains_logged, false);
            Service.cookies = cookies_new;
            response = Service.successResponse();
        }
        return response;
    }

    public static Object cookiesStore(Integer request_type,
                                      String name, String pattern,
                                      byte[] request_info, byte[] request,
                                      Integer timeout, Byte priority,
                                      byte[] trans_id, OtpErlangPid pid)
    {
        byte[][] response = null;
        Set<Cookie> cookies_current = Service.cookies.getCookies();
        if (CookieFile.store(cookies_current))
        {
            final Map<String, Integer> domains_logged =
                new HashMap<String, Integer>();
            for (Cookie cookie : cookies_current)
            {
                Service.cookieDomainStore(cookie, domains_logged);
            }
            Service.logCookieDomains(domains_logged, true);
            response = Service.successResponse();
        }
        return response;
    }

    public static Object cookiesClear(Integer request_type,
                                      String name, String pattern,
                                      byte[] request_info, byte[] request,
                                      Integer timeout, Byte priority,
                                      byte[] trans_id, OtpErlangPid pid)
    {
        Service.cookies.clearCookies();
        return Service.successResponse();
    }
}
