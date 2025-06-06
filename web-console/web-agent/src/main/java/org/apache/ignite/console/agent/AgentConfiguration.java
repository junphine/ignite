

package org.apache.ignite.console.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import com.beust.jcommander.Parameter;

import org.apache.ignite.console.utils.BeanMerger;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.plugin.PluginConfiguration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.ignite.console.agent.AgentUtils.secured;
import static org.apache.ignite.console.agent.AgentUtils.trim;

/**
 * Agent configuration.
 */
public class AgentConfiguration implements PluginConfiguration {
    /** Default path to properties file. */
    public static final String DFLT_CFG_PATH = "config/default-agent.properties";

    /** Default server URI. */
    private static final String DFLT_SERVER_URI = "http://localhost:3000";

    /** Default Ignite node HTTP URI. */
    private static final String DFLT_NODE_URI = "http://localhost:8080";    


    /** */
    @Parameter(names = {"-t", "--tokens"},
        description = "User's tokens separated by comma used to connect to Ignite Console.")
    private List<String> tokens;

    /** */
    @Parameter(names = {"-s", "--server-uri"},
        description = "URI of Ignite Console server" +
            "           " +
            "      Default value: " + DFLT_SERVER_URI)
    private String srvUri;

    /** */
    @Parameter(names = {"-n", "--node-uri"},
        description = "Comma-separated list of URIs for connect to Ignite node via REST" +
            "                        " +
            "      Default value: " + DFLT_NODE_URI)
    private List<String> nodeURIs;

    /** */
    @Parameter(names = {"-nl", "--node-login"},
        description = "User name that will be used to connect to secured cluster")
    private String nodeLogin;

    /** */
    @Parameter(names = {"-np", "--node-password"},
        description = "Password that will be used to connect to secured cluster")
    private String nodePwd;
    
    /** agent ident no */
    @Parameter(names = {"-id", "--server-id"},
        description = "Server Ident that will be used to ident node for cluster")
    private int serverId = 0;

   
	/** URI for connect to Ignite demo node REST server */
    private String demoNodeUri;

    /** */
    @Parameter(names = {"-c", "--config"}, description = "Path to properties file" +
        "                                  " +
        "      Default value: " + DFLT_CFG_PATH)
    private String cfgPath;

    /** */
    @Parameter(names = {"-d", "--driver-folder"}, description = "Path to folder with JDBC drivers" +
        "                             " +
        "      Default value: ./jdbc-drivers")
    private String driversFolder;

    /** */
    @Parameter(names = {"-dd", "--disable-demo"}, description = "Disable demo mode on this agent")
    private boolean disableDemo;
    
    /** */
    @Parameter(names = {"-dv", "--disable-vertx"}, description = "Disable vertx on this agent")
    private boolean disableVertx;

    /** */
    @Parameter(names = {"-nks", "--node-key-store"},
        description = "Path to key store that will be used to connect to cluster")
    private String nodeKeyStore;

    /** */
    @Parameter(names = {"-nksp", "--node-key-store-password"},
        description = "Optional password for node key store")
    private String nodeKeyStorePass;

    /** */
    @Parameter(names = {"-nts", "--node-trust-store"},
        description = "Path to trust store that will be used to connect to cluster")
    private String nodeTrustStore;

    /** */
    @Parameter(names = {"-ntsp", "--node-trust-store-password"},
        description = "Optional password for node trust store")
    private String nodeTrustStorePass;

    /** */
    @Parameter(names = {"-sks", "--server-key-store"},
        description = "Path to key store that will be used to connect to Web server")
    private String srvKeyStore;

    /** */
    @Parameter(names = {"-sksp", "--server-key-store-password"},
        description = "Optional password for server key store")
    private String srvKeyStorePass;

    /** */
    @Parameter(names = {"-sts", "--server-trust-store"},
        description = "Path to trust store that will be used to connect to Web server")
    private String srvTrustStore;

    /** */
    @Parameter(names = {"-stsp", "--server-trust-store-password"},
        description = "Optional password for server trust store")
    private String srvTrustStorePass;

    /** */
    @Parameter(names = {"-cs", "--cipher-suites"},
        description = "Optional comma-separated list of SSL cipher suites to be used to connect to server and cluster")
    private List<String> cipherSuites;

    /** */
    @Parameter(names = {"-pks", "--passwords-key-store"},
            description = "Path to key store that keeps encrypted passwords")
    private String passwordsStore;

    /** */
    @Parameter(names = {"-pksp", "--passwords-key-store-password"},
            description = "Password for passwords key store")
    private String passwordsStorePass;
    
    /** */
    @Parameter(names = {"-gmsp", "--gremlin-server-port"},
            description = "Port for Gremlin server, default 8182")
    private int gremlinPort = 8182;    

	/** */
    @Parameter(names = {"-h", "--help"}, help = true, description = "Print this help message")
    private boolean help;

    /**
     * @return Tokens.
     */
    public List<String> tokens() {
        return tokens;
    }

    /**
     * @param tokens Tokens.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration tokens(List<String> tokens) {
        this.tokens = tokens;

        return this;
    }

    /**
     * @return Server URI.
     */
    public String serverUri() {
        return srvUri;
    }

    /**
     * @param srvUri URI.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration serverUri(String srvUri) {
        this.srvUri = srvUri;

        return this;
    }

    /**
     * @return Node URIs.
     */
    public List<String> nodeURIs() {
        return nodeURIs;
    }

    /**
     * @param nodeURIs Node URIs.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeURIs(List<String> nodeURIs) {
        this.nodeURIs = nodeURIs;

        return this;
    }

    /**
     * @return User name for agent to authenticate on node.
     */
    public String nodeLogin() {
        return nodeLogin;
    }

    /**
     * @param nodeLogin User name for agent to authenticate on node.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeLogin(String nodeLogin) {
        this.nodeLogin = nodeLogin;

        return this;
    }

    /**
     * @return Agent password to authenticate on node.
     */
    public String nodePassword() {
        return F.isEmpty(nodePwd) ?  getPasswordFromKeyStore("node-password") : nodePwd;
    }

    /**
     * @param nodePwd Agent password to authenticate on node.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodePassword(String nodePwd) {
        this.nodePwd = nodePwd;

        return this;
    }

    /**
     * @return Demo node URI.
     */
    public String demoNodeUri() {
        return demoNodeUri;
    }

    /**
     * @param demoNodeUri Demo node URI.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration demoNodeUri(String demoNodeUri) {
        this.demoNodeUri = demoNodeUri;

        return this;
    }

    /**
     * @return Configuration path.
     */
    public String configPath() {
        return cfgPath == null ? DFLT_CFG_PATH : cfgPath;
    }

    /**
     * @return Configured drivers folder.
     */
    public String driversFolder() {
        return driversFolder;
    }

    /**
     * @param driversFolder Driver folder.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration driversFolder(String driversFolder) {
        this.driversFolder = driversFolder;

        return this;
    }

    /**
     * @return Disable demo mode.
     */
    public boolean disableDemo() {
        return disableDemo;
    }
    
    /**
     * @return Disable vertx mode.
     */
    public boolean disableVertx() {
        return disableVertx;
    }

    /**
     * @param disableDemo Disable demo mode.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration disableDemo(boolean disableDemo) {
        this.disableDemo = disableDemo;

        return this;
    }
    
    /**
     * @param disableVertx Disable vertx mode.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration disableVertx(boolean disableVertx) {
        this.disableVertx = disableVertx;

        return this;
    }

    /**
     * @return Path to node key store.
     */
    public String nodeKeyStore() {
        return nodeKeyStore;
    }

    /**
     * @param nodeKeyStore Path to node key store.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeKeyStore(String nodeKeyStore) {
        this.nodeKeyStore = nodeKeyStore;

        return this;
    }

    /**
     * @return Node key store password.
     */
    public String nodeKeyStorePassword() {
        return F.isEmpty(nodeKeyStorePass) ?  getPasswordFromKeyStore("node-key-store-password") : nodeKeyStorePass;
    }

    /**
     * @param nodeKeyStorePass Node key store password.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeKeyStorePassword(String nodeKeyStorePass) {
        this.nodeKeyStorePass = nodeKeyStorePass;

        return this;
    }

    /**
     * @return Path to node trust store.
     */
    public String nodeTrustStore() {
        return nodeTrustStore;
    }

    /**
     * @param nodeTrustStore Path to node trust store.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeTrustStore(String nodeTrustStore) {
        this.nodeTrustStore = nodeTrustStore;

        return this;
    }

    /**
     * @return Node trust store password.
     */
    public String nodeTrustStorePassword() {
        return F.isEmpty(nodeTrustStorePass) ? getPasswordFromKeyStore("node-trust-store-password") : nodeTrustStorePass;
    }

    /**
     * @param nodeTrustStorePass Node trust store password.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration nodeTrustStorePassword(String nodeTrustStorePass) {
        this.nodeTrustStorePass = nodeTrustStorePass;

        return this;
    }

    /**
     * @return Path to server key store.
     */
    public String serverKeyStore() {
        return srvKeyStore;
    }

    /**
     * @param srvKeyStore Path to server key store.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration serverKeyStore(String srvKeyStore) {
        this.srvKeyStore = srvKeyStore;

        return this;
    }

    /**
     * @return Server key store password.
     */
    public String serverKeyStorePassword() {
        return F.isEmpty(srvKeyStorePass) ? getPasswordFromKeyStore("server-key-store-password") : srvKeyStorePass;
    }

    /**
     * @param srvKeyStorePass Server key store password.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration serverKeyStorePassword(String srvKeyStorePass) {
        this.srvKeyStorePass = srvKeyStorePass;

        return this;
    }

    /**
     * @return Path to server trust store.
     */
    public String serverTrustStore() {
        return srvTrustStore;
    }

    /**
     * @param srvTrustStore Path to server trust store.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration serverTrustStore(String srvTrustStore) {
        this.srvTrustStore = srvTrustStore;

        return this;
    }

    /**
     * @return Server trust store password.
     */
    public String serverTrustStorePassword() {
        return F.isEmpty(srvTrustStorePass) ? getPasswordFromKeyStore("server-trust-store-password") : srvTrustStorePass;
    }

    /**
     * @param srvTrustStorePass Server trust store password.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration serverTrustStorePassword(String srvTrustStorePass) {
        this.srvTrustStorePass = srvTrustStorePass;

        return this;
    }

    /**
     * @return Path to passwords key store.
     */
    public String passwordsStore() {
        return passwordsStore;
    }

    /**
     * @param passwordsStore Passwords store.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration passwordsStore(String passwordsStore) {
        this.passwordsStore = passwordsStore;

        return this;
    }

    /**
     * @return Passwords key store password.
     */
    public String passwordsStorePassword() {
        return passwordsStorePass;
    }

    /**
     * @param passwordsStorePass Passwords store pass.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration passwordsStorePassword(String passwordsStorePass) {
        this.passwordsStorePass = passwordsStorePass;

        return this;
    }

    /**
     * @return SSL cipher suites.
     */
    public List<String> cipherSuites() {
        return cipherSuites;
    }

    public int serverId() {
		return serverId;
	}

	public void serverId(int serverId) {
		this.serverId = serverId;
	}
	
    /**
     * @param cipherSuites SSL cipher suites.
     * @return {@code this} for chaining.
     */
    public AgentConfiguration cipherSuites(List<String> cipherSuites) {
        this.cipherSuites = cipherSuites;

        return this;
    }

    public int gremlinPort() {
		return gremlinPort;
	}

	public void gremlinPort(int gremlinPort) {
		this.gremlinPort = gremlinPort;
	}
	
    /**
     * @return {@code true} If agent options usage should be printed.
     */
    public boolean help() {
        return help;
    }

    /**
     * @param cfgUrl URL.
     */
    public void load(URL cfgUrl) throws IOException {
        Properties props = new Properties();

        try (Reader reader = new InputStreamReader(cfgUrl.openStream(), UTF_8)) {
            props.load(reader);
        }

        String val = props.getProperty("tokens");

        if (val != null)
            tokens(new ArrayList<>(Arrays.asList(val.split(","))));

        val = props.getProperty("server-uri");

        if (val != null)
            serverUri(val);

        val = props.getProperty("node-uri");

        // Intentionally wrapped by ArrayList, for further manipulations.
        if (val != null)
            nodeURIs(new ArrayList<>(Arrays.asList(val.split(","))));

        val = props.getProperty("node-login");

        if (val != null)
            nodeLogin(val);

        val = props.getProperty("node-password");

        if (val != null)
            nodePassword(val);
        
        val = props.getProperty("server-id");
        if (val != null)
        	serverId(Integer.parseInt(val));

        val = props.getProperty("driver-folder");

        if (val != null)
            driversFolder(val);
        
        val = props.getProperty("disable-demo");

        if (val != null)
            disableDemo(val.equals("true"));
        
        val = props.getProperty("disable-vertx");

        if (val != null)
        	disableVertx(val.equals("true"));

        val = props.getProperty("node-key-store");

        if (val != null)
            nodeKeyStore(val);

        val = props.getProperty("node-key-store-password");

        if (val != null)
            nodeKeyStorePassword(val);

        val = props.getProperty("node-trust-store");

        if (val != null)
            nodeTrustStore(val);

        val = props.getProperty("node-trust-store-password");

        if (val != null)
            nodeTrustStorePassword(val);

        val = props.getProperty("server-key-store");

        if (val != null)
            serverKeyStore(val);

        val = props.getProperty("server-key-store-password");

        if (val != null)
            serverKeyStorePassword(val);

        val = props.getProperty("server-trust-store");

        if (val != null)
            serverTrustStore(val);

        val = props.getProperty("server-trust-store-password");

        if (val != null)
            serverTrustStorePassword(val);

        val = props.getProperty("passwords-key-store");

        if (val != null)
            passwordsStore(val);

        val = props.getProperty("cipher-suites");

        if (val != null)
            cipherSuites(Arrays.asList(val.split(",")));
        
        val = props.getProperty("gremlin-server-port");

        if (val != null)
            gremlinPort(Integer.parseInt(val));
    }

    /**
     * @param cfg Config to merge with.
     */
    public void merge(AgentConfiguration cfg) {    	
    	BeanMerger.mergeBeans(cfg,this);
    }


    /** {@inheritDoc} */
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();

        String nl = System.lineSeparator();

        if (!F.isEmpty(tokens)) {
            sb.append("User's security tokens          : ");

            sb.append(secured(tokens)).append(nl);
        }

        sb.append("URI to Ignite node REST server  : ")
            .append(nodeURIs == null ? DFLT_NODE_URI : String.join(", ", nodeURIs)).append(nl);

        if (nodeLogin != null)
            sb.append("Login to Ignite node REST server: ").append(nodeLogin).append(nl);

        sb.append("URI to Ignite Console server    : ").append(srvUri == null ? DFLT_SERVER_URI : srvUri).append(nl);
        sb.append("Path to properties file         : ").append(configPath()).append(nl);

        String drvFld = driversFolder();

        if (drvFld == null) {
            File agentHome = AgentUtils.getAgentHome();

            if (agentHome != null)
                drvFld = new File(agentHome, "jdbc-drivers").getPath();
        }

        sb.append("Path to JDBC drivers folder     : ").append(drvFld).append(nl);
        sb.append("Demo mode                       : ").append(disableDemo() ? "disabled" : "enabled").append(nl);

        if (!F.isEmpty(nodeKeyStore))
            sb.append("Node key store                  : ").append(nodeKeyStore).append(nl);

        if (!F.isEmpty(nodeKeyStorePass))
            sb.append("Node key store password         : ").append(secured(nodeKeyStorePass)).append(nl);

        if (!F.isEmpty(nodeTrustStore))
            sb.append("Node trust store                : ").append(nodeTrustStore).append(nl);

        if (!F.isEmpty(nodeTrustStorePass))
            sb.append("Node trust store password       : ").append(secured(nodeTrustStorePass)).append(nl);

        if (!F.isEmpty(srvKeyStore))
            sb.append("Server key store                : ").append(srvKeyStore).append(nl);

        if (!F.isEmpty(srvKeyStorePass))
            sb.append("Server key store password       : ").append(secured(srvKeyStorePass)).append(nl);

        if (!F.isEmpty(srvTrustStore))
            sb.append("Server trust store              : ").append(srvTrustStore).append(nl);

        if (!F.isEmpty(srvTrustStorePass))
            sb.append("Server trust store password     : ").append(secured(srvTrustStorePass)).append(nl);

        if (!F.isEmpty(passwordsStore))
            sb.append("Passwords key store             : ").append(passwordsStore).append(nl);

        if (!F.isEmpty(cipherSuites))
            sb.append("Cipher suites                   : ").append(String.join(", ", cipherSuites)).append(nl);

        return sb.toString();
    }

    /**
     * @param name Name.
     * @return Decoded password from passwords key store.
     */
    private String getPasswordFromKeyStore(String name) {
        if (!F.isEmpty(passwordsStore) && !F.isEmpty(passwordsStorePass))
            return AgentUtils.getPasswordFromKeyStore(name, passwordsStore, passwordsStorePass);

        return null;
    }
}
