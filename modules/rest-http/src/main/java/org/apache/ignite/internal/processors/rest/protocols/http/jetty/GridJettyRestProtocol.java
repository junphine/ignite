/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.rest.protocols.http.jetty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.internal.processors.rest.GridRestProtocolHandler;
import org.apache.ignite.internal.processors.rest.protocols.GridRestProtocolAdapter;
import org.apache.ignite.internal.util.typedef.C1;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.spi.IgniteSpiException;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_JETTY_HOST;
import static org.apache.ignite.IgniteSystemProperties.IGNITE_JETTY_LOG_NO_OVERRIDE;
import static org.apache.ignite.IgniteSystemProperties.IGNITE_JETTY_PORT;
import static org.apache.ignite.spi.IgnitePortProtocol.TCP;

/**
 * Jetty REST protocol implementation.
 * 
 */
public class GridJettyRestProtocol extends GridRestProtocolAdapter {
    /**
     *
     */
    static {
        if (!IgniteSystemProperties.getBoolean(IGNITE_JETTY_LOG_NO_OVERRIDE)) {
            // See also https://www.eclipse.org/jetty/documentation/9.4.x/configuring-logging.html
            // It seems that using system properties should be fine.
            System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
            System.setProperty("org.eclipse.jetty.util.log.LEVEL", "OFF");
            System.setProperty("org.eclipse.jetty.util.component.LEVEL", "OFF");

        }
    }

    private GridJettyRestHandler jettyHnd;

    /** HTTP server. */
    private static Server httpSrv;
    
    private static int handlerCount = 0;

    /**
     * @param ctx Context.
     */
    public GridJettyRestProtocol(GridKernalContext ctx) {
        super(ctx);
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return "Jetty REST for Ignite Intstance "+ ctx.igniteInstanceName();
    }

    /** {@inheritDoc} */
    @Override public void start(GridRestProtocolHandler hnd) throws IgniteCheckedException {
        assert ctx.config().getConnectorConfiguration() != null;        

        String jettyHost = System.getProperty(IGNITE_JETTY_HOST, ctx.config().getLocalHost());

        try {
            System.setProperty(IGNITE_JETTY_HOST, U.resolveLocalHost(jettyHost).getHostAddress());
        }
        catch (IOException e) {
            throw new IgniteCheckedException("Failed to resolve host to bind address: " + jettyHost, e);
        }

        jettyHnd = new GridJettyRestHandler(hnd, new C1<String, Boolean>() {
            @Override public Boolean apply(String tok) {
                return F.isEmpty(secretKey) || authenticate(tok);
            }
        }, ctx);    
        
        
        // first start instance
        if(httpSrv==null) {
        	configSingletonJetty();
        	jettyHnd.index = 0;
     	}
        else {
        	jettyHnd.index = ++handlerCount;
        }
        
        HandlerList handlers = (HandlerList)httpSrv.getHandler();    		
		if(handlers!=null) {			
			handlers.prependHandler(jettyHnd); 
		}
		else {
			httpSrv.setHandler(jettyHnd);
		}
        
        override(getJettyConnector());
    }
    
    /** {@inheritDoc} */
    @Override public void onKernalStart() {    	
		
    	if(!httpSrv.isStarting() && !httpSrv.isStarted()) {
    		
			try {
				AbstractNetworkConnector connector = getJettyConnector();
				
				try {
		            host = InetAddress.getByName(connector.getHost());
		        }
		        catch (UnknownHostException e) {
		            throw new IgniteCheckedException("Failed to resolve Jetty host address: " + connector.getHost(), e);
		        }

	            int initPort = connector.getPort();
	            int portRange = config().getPortRange();
	            int lastPort = portRange == 0 ? initPort : initPort + portRange - 1;

	            for (port = initPort; port <= lastPort; port++) {
	                connector.setPort(port);

	                if (startJetty()) {
	                    if (log.isInfoEnabled())
	                        log.info(startInfo());

	                    return;
	                }
	            }
	            U.warn(log, "Failed to start Jetty REST server (possibly all ports in range are in use) " +
	                    "[firstPort=" + initPort + ", lastPort=" + lastPort + ']');
	            
			} catch (IgniteCheckedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}            
     	}
    }

  
    private void override(AbstractNetworkConnector con) {
        int currPort = con.getPort();
        try {        	
        	this.port = currPort; 
            this.host = InetAddress.getByName(con.getHost());
        }
        catch (UnknownHostException e) {
           
        }
    }

    /**
     * @throws IgniteCheckedException If failed.
     * @return {@code True} if Jetty started.
     */
    private boolean configSingletonJetty() throws IgniteCheckedException {
    	
    	String jettyHost = System.getProperty(IGNITE_JETTY_HOST, ctx.config().getLocalHost());

        try {
            System.setProperty(IGNITE_JETTY_HOST, U.resolveLocalHost(jettyHost).getHostAddress());
        }
        catch (IOException e) {
            throw new IgniteCheckedException("Failed to resolve host to bind address: " + jettyHost, e);
        }
        
    	String jettyPath = config().getJettyPath();

        final URL cfgUrl;

        if (jettyPath == null) {
            cfgUrl = null;

            if (log.isDebugEnabled())
                log.debug("Jetty configuration file is not provided, using defaults.");
        }
        else {
            cfgUrl = U.resolveIgniteUrl(jettyPath);

            if (cfgUrl == null)
                throw new IgniteSpiException("Invalid Jetty configuration file: " + jettyPath);
            else if (log.isDebugEnabled())
                log.debug("Jetty configuration file: " + cfgUrl);
        }

        loadJettyConfiguration(cfgUrl);

        return true;
    }
        
    /**
     * @throws IgniteCheckedException If failed.
     * @return {@code True} if Jetty started.
     */
    private boolean startJetty() throws IgniteCheckedException {
        try {
        	
            httpSrv.start();

            if (httpSrv.isStarted()) {
                for (Connector con : httpSrv.getConnectors()) {
                    int connPort = ((NetworkConnector)con).getPort();

                    if (connPort > 0)
                        ctx.ports().registerPort(connPort, TCP, getClass());
                }

                return true;
            }

            return  false;
        }
        catch (Exception e) {
            boolean failedToBind = e instanceof SocketException;

            if (e instanceof MultiException) {
                if (log.isDebugEnabled())
                    log.debug("Caught multi exception: " + e);

                failedToBind = true;

                for (Object obj : ((MultiException)e).getThrowables())
                    if (!(obj instanceof SocketException))
                        failedToBind = false;
            }

            if (e instanceof IOException && X.hasCause(e, SocketException.class))
                failedToBind = true;

            if (failedToBind) {
                if (log.isDebugEnabled())
                    log.debug("Failed to bind HTTP server to configured port.");

                stopJetty();
            }
            else
                throw new IgniteCheckedException("Failed to start Jetty HTTP server.", e);

            return false;
        }
    }

    /**
     * Loads jetty configuration from the given URL.
     *
     * @param cfgUrl URL to load configuration from.
     * @throws IgniteCheckedException if load failed.
     */
    private void loadJettyConfiguration(@Nullable URL cfgUrl) throws IgniteCheckedException {
        if (cfgUrl == null) {
            HttpConfiguration httpCfg = new HttpConfiguration();

            httpCfg.setSecureScheme("https");
            httpCfg.setSecurePort(8443);
            httpCfg.setSendServerVersion(true);
            httpCfg.setSendDateHeader(true);           

            String srvPortStr = System.getProperty(IGNITE_JETTY_PORT, "8080");

            int srvPort;

            try {
                srvPort = Integer.parseInt(srvPortStr);
            }
            catch (NumberFormatException ignore) {
                throw new IgniteCheckedException("Failed to start Jetty server because IGNITE_JETTY_PORT system property " +
                    "cannot be cast to integer: " + srvPortStr);
            }

            httpSrv = new Server(new QueuedThreadPool(64, 4));

            ServerConnector srvConn = new ServerConnector(httpSrv, new HttpConnectionFactory(httpCfg));

            srvConn.setHost(System.getProperty(IGNITE_JETTY_HOST, "localhost"));
            srvConn.setPort(srvPort);
            srvConn.setIdleTimeout(60000L);
            srvConn.setReuseAddress(true);

            httpSrv.addConnector(srvConn);

            httpSrv.setStopAtShutdown(false);
        }
        else {
            XmlConfiguration cfg;

            try {
                cfg = new XmlConfiguration(Resource.newResource(cfgUrl));
            }
            catch (FileNotFoundException e) {
                throw new IgniteSpiException("Failed to find configuration file: " + cfgUrl, e);
            }
            catch (SAXException e) {
                throw new IgniteSpiException("Failed to parse configuration file: " + cfgUrl, e);
            }
            catch (IOException e) {
                throw new IgniteSpiException("Failed to load configuration file: " + cfgUrl, e);
            }
            catch (Exception e) {
                throw new IgniteSpiException("Failed to start HTTP server with configuration file: " + cfgUrl, e);
            }

            try {
                httpSrv = (Server)cfg.configure();
            }
            catch (Exception e) {
                throw new IgniteCheckedException("Failed to start Jetty HTTP server.", e);
            }
        }

        assert httpSrv != null;
        
        //add@byron support custom rest cmd handler        
        String webAppDirs = "webapps"; 
		if(ctx.config().getIgniteHome()!=null){
			webAppDirs = ctx.config().getIgniteHome()+File.separatorChar+webAppDirs; 
		}
		
		List<Handler> plugins = new ArrayList<>();
		File webPlugins = new File(webAppDirs);
		if(webPlugins.isDirectory()) {
			
			for(File warFile: webPlugins.listFiles()) {
			    String warPath = warFile.getPath();
			    int pos = warFile.getName().indexOf('.');
			    String contextPath =  pos>0? warFile.getName().substring(0,pos): warFile.getName();
			    WebAppContext webApp = new WebAppContext();
			    webApp.setContextPath("/"+contextPath);
			    webApp.setConfigurationDiscovered(true);
			    
			    if (warFile.isDirectory()) {
			        // Development mode, read from FS
			    	webApp.setResourceBase(warFile.getPath());
			        webApp.setDescriptor(warPath+"/WEB-INF/web.xml");
			        webApp.setExtraClasspath(warPath+"/WEB-INF/classes/");		        
			       
		        } else if(warFile.getName().endsWith(".war")) {
			        // use packaged WAR
			        webApp.setWar(warFile.getAbsolutePath());
			        webApp.setExtractWAR(false);
			       
			    }
		        else {
		        	continue;
		        }
			    
			    //webApp.setClassLoader(Thread.currentThread().getContextClassLoader());  
				webApp.setParentLoaderPriority(false);
				webApp.setServer(httpSrv);
				webApp.setErrorHandler(new ErrorHandler());
				webApp.setAttribute("gridKernalContext", ctx);
				
				plugins.add(webApp);	
			
		    }
		}
	    
	 // Create a handler list to store our static and servlet context handlers.
	    Handler hnd = httpSrv.getHandler();
		HandlerList handlers = new HandlerList();
		if(hnd!=null) {
			plugins.add(hnd);
			handlers.setHandlers(plugins.toArray(new Handler[plugins.size()]));	
		}
		else {
			handlers.setHandlers(plugins.toArray(new Handler[plugins.size()]));	
		}
        //-httpSrv.setHandler(jettyHnd);
        httpSrv.setHandler(handlers);
        
        //end@

        
    }

    /**
     * Checks that the only connector configured for the current jetty instance
     * and returns it.
     *
     * @return Connector instance.
     * @throws IgniteCheckedException If no or more than one connectors found.
     */
    private AbstractNetworkConnector getJettyConnector() throws IgniteCheckedException {
        if (httpSrv.getConnectors().length == 1) {
            Connector connector = httpSrv.getConnectors()[0];

            if (!(connector instanceof AbstractNetworkConnector))
                throw new IgniteCheckedException("Error in jetty configuration. Jetty connector should extend " +
                    "AbstractNetworkConnector class." );

            return (AbstractNetworkConnector)connector;
        }
        else
            throw new IgniteCheckedException("Error in jetty configuration [connectorsFound=" +
                httpSrv.getConnectors().length + "connectorsExpected=1]");
    }

    /**
     * Stops Jetty.
     */
    private void stopJetty() {
        // Jetty does not really stop the server if port is busy.
        try {
            if (httpSrv != null) {
                // If server was successfully started, deregister ports.
                if (httpSrv.isStarted())
                    ctx.ports().deregisterPorts(getClass());

                // Record current interrupted status of calling thread.
                boolean interrupted = Thread.interrupted();

                try {
                    httpSrv.stop();
                }
                finally {
                    // Reset interrupted flag on calling thread.
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
            }
        }
        catch (InterruptedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Thread has been interrupted.");

            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            U.error(log, "Failed to stop Jetty HTTP server.", e);
        }
    }

    /** {@inheritDoc} */
    @Override public void stop() {
    	if(httpSrv!=null) {  
    		HandlerList handlers = (HandlerList)httpSrv.getHandler();
    		handlers.removeHandler(jettyHnd);
    		
    		if(jettyHnd.index == 0) {
    	        stopJetty();	
    	        httpSrv = null;
        	}
        	 
        
        	if (log.isInfoEnabled())
                log.info(stopInfo());
    	}  
    	
    }

    /** {@inheritDoc} */
    @Override protected String getAddressPropertyName() {
        return IgniteNodeAttributes.ATTR_REST_JETTY_ADDRS;
    }

    /** {@inheritDoc} */
    @Override protected String getHostNamePropertyName() {
        return IgniteNodeAttributes.ATTR_REST_JETTY_HOST_NAMES;
    }

    /** {@inheritDoc} */
    @Override protected String getPortPropertyName() {
        return IgniteNodeAttributes.ATTR_REST_JETTY_PORT;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJettyRestProtocol.class, this);
    }
}
