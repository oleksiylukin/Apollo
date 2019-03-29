/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.http;

import static org.slf4j.LoggerFactory.getLogger;

import com.apollocurrency.aplwallet.apl.core.app.AplCoreRuntime;
import com.apollocurrency.aplwallet.apl.util.Constants;
import com.apollocurrency.aplwallet.apl.core.peer.Peers;
import com.apollocurrency.aplwallet.apl.core.rest.exception.ConstraintViolationExceptionMapper;
import com.apollocurrency.aplwallet.apl.core.rest.exception.ParameterExceptionMapper;
import com.apollocurrency.aplwallet.apl.core.rest.exception.RestParameterExceptionMapper;
import com.apollocurrency.aplwallet.apl.core.rest.filters.ApiProtectionFilter;
import com.apollocurrency.aplwallet.apl.core.rest.filters.ApiSplitFilter;
import com.apollocurrency.aplwallet.apl.util.UPnP;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.security.Constraint;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.MultipartConfigElement;

@Vetoed
public final class API {
    private static final Logger LOG = getLogger(API.class);

    // TODO: YL remove static instance later
    private static PropertiesHolder propertiesHolder = CDI.current().select(PropertiesHolder.class).get();

    private static final String[] DISABLED_HTTP_METHODS = {"TRACE", "OPTIONS", "HEAD"};
  
    public static int openAPIPort;
    public static int openAPISSLPort;
    public static boolean isOpenAPI;
    public static List<String> disabledAPIs;
    public static List<APITag> disabledAPITags;

    private static Set<String> allowedBotHosts;
    private static List<NetworkAddress> allowedBotNets;
    public static final int maxRecords = propertiesHolder.getIntProperty("apl.maxAPIRecords");
    static final boolean enableAPIUPnP = propertiesHolder.getBooleanProperty("apl.enableAPIUPnP");
    public static final int apiServerIdleTimeout = propertiesHolder.getIntProperty("apl.apiServerIdleTimeout");
    public static final boolean apiServerCORS = propertiesHolder.getBooleanProperty("apl.apiServerCORS");

    private static Server apiServer;

    private static URI welcomePageUri;
    private static URI serverRootUri;
    //TODO: remove static context
    private static final UPnP upnp = CDI.current().select(UPnP.class).get();
    private static final JettyConnectorCreator jettyConnectorCreator = CDI.current().select(JettyConnectorCreator.class).get();


    private API() {} // never
    
    public static void init() {
//    static {

        List<String> disabled = new ArrayList<>(propertiesHolder.getStringListProperty("apl.disabledAPIs"));
        Collections.sort(disabled);
        disabledAPIs = Collections.unmodifiableList(disabled);
        disabled = propertiesHolder.getStringListProperty("apl.disabledAPITags");
        Collections.sort(disabled);
        List<APITag> apiTags = new ArrayList<>(disabled.size());
        disabled.forEach(tagName -> apiTags.add(APITag.fromDisplayName(tagName)));
        disabledAPITags = Collections.unmodifiableList(apiTags);
        List<String> allowedBotHostsList = propertiesHolder.getStringListProperty("apl.allowedBotHosts");
        if (! allowedBotHostsList.contains("*")) {
            Set<String> hosts = new HashSet<>();
            List<NetworkAddress> nets = new ArrayList<>();
            for (String host : allowedBotHostsList) {
                if (host.contains("/")) {
                    try {
                        nets.add(new NetworkAddress(host));
                    } catch (UnknownHostException e) {
                        LOG.error("Unknown network " + host, e);
                        throw new RuntimeException(e.toString(), e);
                    }
                } else {
                    hosts.add(host);
                }
            }
            allowedBotHosts = Collections.unmodifiableSet(hosts);
            allowedBotNets = Collections.unmodifiableList(nets);
        } else {
            allowedBotHosts = null;
            allowedBotNets = null;
        }

        boolean enableAPIServer = propertiesHolder.getBooleanProperty("apl.enableAPIServer");

        if (enableAPIServer) {

            final int port = propertiesHolder.getIntProperty("apl.apiServerPort");
            final int sslPort = propertiesHolder.getIntProperty("apl.apiServerSSLPort");
            final String host = propertiesHolder.getStringProperty("apl.apiServerHost");
            int maxThreadPoolSize = propertiesHolder.getIntProperty("apl.threadPoolMaxSize");
            int minThreadPoolSize = propertiesHolder.getIntProperty("apl.threadPoolMinSize");
            org.eclipse.jetty.util.thread.QueuedThreadPool threadPool = new org.eclipse.jetty.util.thread.QueuedThreadPool();
            threadPool.setMaxThreads(Math.max(maxThreadPoolSize, 200));
            threadPool.setMinThreads(Math.max(minThreadPoolSize, 8));
            threadPool.setName("APIThreadPool");
            apiServer = new Server(threadPool);
            boolean enableSSL = propertiesHolder.getBooleanProperty("apl.apiSSL");
            
            //
            // Create the HTTP connector
            //
            if (!enableSSL || port != sslPort) {
                jettyConnectorCreator.addHttpConnector(host, port, apiServer);
                LOG.info("API server using HTTP port " + port);
            }
            //
            // Create the HTTPS connector
            //

            if (enableSSL) {
                jettyConnectorCreator.addHttpSConnector(host, port, apiServer);
            }
            
            String localhost = "0.0.0.0".equals(host) || "127.0.0.1".equals(host) ? "localhost" : host;
            try {
                welcomePageUri = new URI(enableSSL ? "https" : "http", null, localhost, enableSSL ? sslPort : port, "/", null, null);
                serverRootUri = new URI(enableSSL ? "https" : "http", null, localhost, enableSSL ? sslPort : port, "", null, null);
            } catch (URISyntaxException e) {
                LOG.info("Cannot resolve browser URI", e);
            }
            openAPIPort = !propertiesHolder.isLightClient() && "0.0.0.0".equals(host) && allowedBotHosts == null && (!enableSSL || port != sslPort) ? port : 0;
            openAPISSLPort = !propertiesHolder.isLightClient() && "0.0.0.0".equals(host) && allowedBotHosts == null && enableSSL ? sslPort : 0;
            isOpenAPI = openAPIPort > 0 || openAPISSLPort > 0;

            HandlerList apiHandlers = new HandlerList();

            ServletContextHandler apiHandler = new ServletContextHandler();
            String apiResourceBase = AplCoreRuntime.getInstance().findWebUiDir();
            if (apiResourceBase != null && !apiResourceBase.isEmpty()) {
                ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
                defaultServletHolder.setInitParameter("dirAllowed", "false");
                defaultServletHolder.setInitParameter("resourceBase", apiResourceBase);
                defaultServletHolder.setInitParameter("welcomeServlets", "true");
                defaultServletHolder.setInitParameter("redirectWelcome", "true");
                defaultServletHolder.setInitParameter("gzip", "true");
                defaultServletHolder.setInitParameter("etags", "true");
                apiHandler.addServlet(defaultServletHolder, "/*");
                String[] wellcome = {propertiesHolder.getStringProperty("apl.apiWelcomeFile")};
                apiHandler.setWelcomeFiles(wellcome);
            }

            ServletHolder servletHolder = apiHandler.addServlet(APIServlet.class, "/apl");
            servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(
                    null, Math.max(propertiesHolder.getIntProperty("apl.maxUploadFileSize"), Constants.MAX_TAGGED_DATA_DATA_LENGTH), -1L, 0));

            servletHolder = apiHandler.addServlet(APIProxyServlet.class, "/apl-proxy");
            servletHolder.setInitParameters(Collections.singletonMap("idleTimeout",
                    "" + Math.max(apiServerIdleTimeout - APIProxyServlet.PROXY_IDLE_TIMEOUT_DELTA, 0)));
            servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(
                    null, Math.max(propertiesHolder.getIntProperty("apl.maxUploadFileSize"), Constants.MAX_TAGGED_DATA_DATA_LENGTH), -1L, 0));

            GzipHandler gzipHandler = new GzipHandler();
            if (!propertiesHolder.getBooleanProperty("apl.enableAPIServerGZIPFilter", isOpenAPI)) {
                gzipHandler.setExcludedPaths("/apl", "/apl-proxy");
            }
            gzipHandler.setIncludedMethods("GET", "POST");
            gzipHandler.setMinGzipSize(Peers.MIN_COMPRESS_SIZE);
            gzipHandler.addExcludedPaths("/blocks");
            apiHandler.setGzipHandler(gzipHandler);

            apiHandler.addServlet(APITestServlet.class, "/test");
            apiHandler.addServlet(APITestServlet.class, "/test-proxy");

            apiHandler.addServlet(BlockEventSourceServlet.class, "/blocks").setAsyncSupported(true);

//TODO: do we need it at all?
//            apiHandler.addServlet(DbShellServlet.class, "/dbshell");

            apiHandler.addEventListener(new ApiContextListener());
            // Filter to forward requests to new API
            {
              FilterHolder filterHolder = apiHandler.addFilter(ApiSplitFilter.class, "/*", null);
              filterHolder.setAsyncSupported(true);
              filterHolder = apiHandler.addFilter(ApiProtectionFilter.class, "/*", null);
              filterHolder.setAsyncSupported(true);
            }
            if (apiServerCORS) {
                FilterHolder filterHolder = apiHandler.addFilter(CrossOriginFilter.class, "/*", null);
                filterHolder.setInitParameter("allowedHeaders", "*");
                filterHolder.setAsyncSupported(true);
            }

            if (propertiesHolder.getBooleanProperty("apl.apiFrameOptionsSameOrigin")) {
                FilterHolder filterHolder = apiHandler.addFilter(XFrameOptionsFilter.class, "/*", null);
                filterHolder.setAsyncSupported(true);
            }
            disableHttpMethods(apiHandler);

            // --------- ADD REST support servlet (RESTEasy)
            ServletHolder restEasyServletHolder = new ServletHolder(new HttpServletDispatcher());
            restEasyServletHolder.setInitParameter("resteasy.servlet.mapping.prefix", "/rest");
            restEasyServletHolder.setInitParameter("resteasy.injector.factory", "org.jboss.resteasy.cdi.CdiInjectorFactory");

            restEasyServletHolder.setInitParameter(ResteasyContextParameters.RESTEASY_PROVIDERS,
                    new StringJoiner(",")
                            .add(ConstraintViolationExceptionMapper.class.getName())
                            .add(ParameterExceptionMapper.class.getName())
                            .add(RestParameterExceptionMapper.class.getName())
                            .toString()
            );

            String restEasyAppClassName = RestEasyApplication.class.getName();
            restEasyServletHolder.setInitParameter("javax.ws.rs.Application", restEasyAppClassName);
            apiHandler.addServlet(restEasyServletHolder, "/rest/*");
            // init Weld here
            apiHandler.addEventListener(new org.jboss.weld.module.web.servlet.WeldInitialListener());
            //need this listener to support scopes properly
            apiHandler.addEventListener( new org.jboss.weld.environment.servlet.Listener());

            //--------- ADD swagger generated docs and API test page
            // Set the path to our static (Swagger UI) resources

            URL su =  API.class.getResource("/swaggerui");
            if(su!=null){
                String resourceBasePath = su.toExternalForm();
                ContextHandler contextHandler = new ContextHandler("/swagger");
                ResourceHandler swFileHandler = new ResourceHandler();
                swFileHandler.setDirectoriesListed(false);
                swFileHandler.setWelcomeFiles(new String[]{"index.html"});
                swFileHandler.setResourceBase(resourceBasePath);
                contextHandler.setHandler(swFileHandler);
                apiHandlers.addHandler(contextHandler);
            }else{
                LOG.warn("Swagger html/js resources not found, swagger UI is off.");
            }


            apiHandlers.addHandler(apiHandler);
            apiHandlers.addHandler(new DefaultHandler());

            apiServer.setHandler(apiHandlers);
            apiServer.addBean(new APIErrorHandler());
            apiServer.setStopAtShutdown(true);
//            Log.getRootLogger().setDebugEnabled(true);

                try {

                    if (enableAPIUPnP) {
                        Connector[] apiConnectors = apiServer.getConnectors();
                        for (Connector apiConnector : apiConnectors) {
                            if (apiConnector instanceof ServerConnector)
                                upnp.addPort(((ServerConnector)apiConnector).getPort());
                        }
                    }

                    apiServer.start();

                    LOG.info("Started API server at " + host + ":" + port + (enableSSL && port != sslPort ? ", " + host + ":" + sslPort : ""));
                } catch (Exception e) {
                    LOG.error("Failed to start API server", e);
                    throw new RuntimeException(e.toString(), e);
                }


        } else {
            apiServer = null;
            openAPIPort = 0;
            openAPISSLPort = 0;
            isOpenAPI = false;
            LOG.info("API server not enabled");
        }

    }

//    public static void init() {}

    public static void shutdown() {
        if (apiServer != null) {
            try {
                apiServer.stop();
                if (enableAPIUPnP) {
                    Connector[] apiConnectors = apiServer.getConnectors();
                    for (Connector apiConnector : apiConnectors) {
                        if (apiConnector instanceof ServerConnector)
                            upnp.deletePort(((ServerConnector)apiConnector).getPort());
                    }
                }
            } catch (Exception e) {
                LOG.info("Failed to stop API server", e);
            }
        }
    }


    static boolean isAllowed(String remoteHost) {
        if (API.allowedBotHosts == null || API.allowedBotHosts.contains(remoteHost)) {
            return true;
        }
        try {
            BigInteger hostAddressToCheck = new BigInteger(InetAddress.getByName(remoteHost).getAddress());
            for (NetworkAddress network : allowedBotNets) {
                if (network.contains(hostAddressToCheck)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            // can't resolve, disallow
            LOG.info("Unknown remote host " + remoteHost);
        }
        return false;

    }

    private static void disableHttpMethods(ServletContextHandler servletContext) {
        SecurityHandler securityHandler = servletContext.getSecurityHandler();
        if (securityHandler == null) {
            securityHandler = new ConstraintSecurityHandler();
            servletContext.setSecurityHandler(securityHandler);
        }
        disableHttpMethods(securityHandler);
    }

    private static void disableHttpMethods(SecurityHandler securityHandler) {
        if (securityHandler instanceof ConstraintSecurityHandler) {
            ConstraintSecurityHandler constraintSecurityHandler = (ConstraintSecurityHandler) securityHandler;
            for (String method : DISABLED_HTTP_METHODS) {
                disableHttpMethod(constraintSecurityHandler, method);
            }
            ConstraintMapping enableEverythingButTraceMapping = new ConstraintMapping();
            Constraint enableEverythingButTraceConstraint = new Constraint();
            enableEverythingButTraceConstraint.setName("Enable everything but TRACE");
            enableEverythingButTraceMapping.setConstraint(enableEverythingButTraceConstraint);
            enableEverythingButTraceMapping.setMethodOmissions(DISABLED_HTTP_METHODS);
            enableEverythingButTraceMapping.setPathSpec("/");
            constraintSecurityHandler.addConstraintMapping(enableEverythingButTraceMapping);
        }
    }

    private static void disableHttpMethod(ConstraintSecurityHandler securityHandler, String httpMethod) {
        ConstraintMapping mapping = new ConstraintMapping();
        Constraint constraint = new Constraint();
        constraint.setName("Disable " + httpMethod);
        constraint.setAuthenticate(true);
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/");
        mapping.setMethod(httpMethod);
        securityHandler.addConstraintMapping(mapping);
    }



    public static URI getWelcomePageUri() {
        return welcomePageUri;
    }

    public static URI getServerRootUri() {
        return serverRootUri;
    }


}
