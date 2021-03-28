/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.console.agent.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.nio.channels.AsynchronousCloseException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.net.ssl.SSLException;

import org.apache.ignite.IgniteLogger;
import org.apache.ignite.Ignition;
import org.apache.ignite.console.agent.AgentConfiguration;
import org.apache.ignite.console.agent.db.DbMetadataReader;
import org.apache.ignite.console.agent.db.DbSchema;
import org.apache.ignite.console.agent.db.DbTable;
import org.apache.ignite.console.agent.handlers.DatabaseListener.DBInfo;
import org.apache.ignite.console.agent.rest.JdbcExecutor;
import org.apache.ignite.console.agent.rest.RestResult;
import org.apache.ignite.console.demo.AgentClusterDemo;
import org.apache.ignite.console.demo.AgentMetadataDemo;
import org.apache.ignite.console.json.JsonObject;
import org.apache.ignite.console.websocket.TopologySnapshot;
import org.apache.ignite.console.websocket.WebSocketRequest;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.client.GridClientCacheMode;
import org.apache.ignite.internal.processors.rest.client.message.GridClientCacheBean;
import org.apache.ignite.internal.processors.rest.client.message.GridClientNodeBean;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.LT;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.eclipse.jetty.client.HttpResponseException;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.ignite.console.agent.AgentUtils.resolvePath;
import static org.apache.ignite.console.utils.Utils.fromJson;
import static org.apache.ignite.internal.IgniteVersionUtils.VER_STR;

/**
 * Handler extract database metadata for "Metadata import" dialog on Web Console.
 */
public class DatabaseHandler{
    /** */
    private static final IgniteLogger log = new Slf4jLogger(LoggerFactory.getLogger(DatabaseHandler.class));

    /** */
    private static final String IMPLEMENTATION_VERSION = "Implementation-Version";

    /** */
    private static final String BUNDLE_VERSION = "Bundle-Version";

    /** */
    private final File driversFolder;

    /** */
    private final DbMetadataReader dbMetaReader;
    
    
    private final JdbcExecutor jdbcExecutor;
    
    DatabaseListener  databaseListener = new DatabaseListener();
    
    /**
     * @param cfg Config.
     */
    public DatabaseHandler(AgentConfiguration cfg) {
    	
        driversFolder = resolvePath(F.isEmpty(cfg.driversFolder()) ? "jdbc-drivers" : cfg.driversFolder());

        dbMetaReader = new DbMetadataReader();
        
        jdbcExecutor = new JdbcExecutor(databaseListener);
    }

    /**
     * @param jdbcDriverJar File name of driver jar file.
     * @param jdbcDriverCls Optional JDBC driver class name.
     * @param jdbcDriverImplVer Optional JDBC driver version.
     * @return JSON for driver info.
     */
    private Map<String, String> driver(
        String jdbcDriverJar,
        String jdbcDriverCls,
        String jdbcDriverImplVer
    ) {
        Map<String, String> map = new LinkedHashMap<>();

        map.put("jdbcDriverJar", jdbcDriverJar);
        map.put("jdbcDriverClass", jdbcDriverCls);
        map.put("jdbcDriverImplVersion", jdbcDriverImplVer);

        return map;
    }

    /**
     * Collect list of JDBC drivers.
     *
     * @return List of JDBC drivers.
     */
    public List<Map<String, String>> collectJdbcDrivers() {
        List<Map<String, String>> drivers = new ArrayList<>();

        if (driversFolder != null) {
            log.info("Collecting JDBC drivers in folder: " + driversFolder.getPath());

            File[] list = driversFolder.listFiles((dir, name) -> name.endsWith(".jar"));

            if (list != null) {
                for (File file : list) {
                    try {
                        boolean win = System.getProperty("os.name").contains("win");

                        URL url = new URL("jar", null,
                            "file:" + (win ? "/" : "") + file.getPath() + "!/META-INF/services/java.sql.Driver");

                        try (
                            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), UTF_8));
                            JarFile jar = new JarFile(file.getPath())
                        ) {
                            Manifest m = jar.getManifest();
                            Object ver = m.getMainAttributes().getValue(IMPLEMENTATION_VERSION);

                            if (ver == null)
                                ver = m.getMainAttributes().getValue(BUNDLE_VERSION);

                            String jdbcDriverCls = reader.readLine();

                            drivers.add(driver(file.getName(), jdbcDriverCls, ver != null ? ver.toString() : null));

                            log.info("Found: [driver=" + file + ", class=" + jdbcDriverCls + "]");
                        }
                    }
                    catch (IOException e) {
                        drivers.add(driver(file.getName(), null, null));

                        log.info("Found: [driver=" + file + "]");
                        log.error("Failed to detect driver class: " + e.getMessage());
                    }
                }
            }
            else
                throw new IllegalStateException("JDBC drivers folder has no files");
        }
        else
            throw new IllegalStateException("JDBC drivers folder not specified");

        return drivers;
    }

    /**
     * Collect DB schemas.
     *
     * @param evt Websocket event.
     * @return DB schemas.
     */
    public DbSchema collectDbSchemas(WebSocketRequest evt) throws SQLException {
        log.info("Collecting database schemas...");

        JsonObject args = fromJson(evt.getPayload());

        try (Connection conn = connect(args)) {
            String catalog = conn.getCatalog();

            if (catalog == null) {
                String jdbcUrl = args.getString("jdbcUrl", "");

                String[] parts = jdbcUrl.split("[/:=]");

                catalog = parts.length > 0 ? parts[parts.length - 1] : "NONE";
            }

            Collection<String> schemas = dbMetaReader.schemas(conn, args.getBoolean("importSamples", false));

            log.info("Collected database schemas:" + schemas.size());            

            return new DbSchema(catalog, schemas);
        }
    }

    /**
     * Collect DB metadata.
     *
     * @param evt Websocket event.
     * @return DB metadata
     */
    @SuppressWarnings("unchecked")
    public Collection<DbTable> collectDbMetadata(WebSocketRequest evt) throws SQLException {
        log.info("Collecting database metadata...");

        JsonObject args = fromJson(evt.getPayload());

        if (!args.containsKey("schemas"))
            throw new IllegalArgumentException("Missing schemas in arguments: " + args);

        List<String> schemas = (List)args.get("schemas");

        if (!args.containsKey("tablesOnly"))
            throw new IllegalArgumentException("Missing tablesOnly in arguments: " + args);

        boolean tblsOnly = args.getBoolean("tablesOnly", false);

        try (Connection conn = connect(args)) {
            Collection<DbTable> metadata = dbMetaReader.metadata(conn, schemas, tblsOnly);

            log.info("Collected database metadata: " + metadata.size());

            return metadata;
        }
    }

    /**
     * @param args Connection arguments.
     * @return Connection to database.
     * @throws SQLException If failed to connect.
     */
    private Connection connect(JsonObject args) throws SQLException {
        String jdbcUrl = args.getString("jdbcUrl", "");

        if (AgentMetadataDemo.isTestDriveUrl(jdbcUrl))
            return AgentMetadataDemo.testDrive();

        String jdbcDriverJarPath = args.getString("jdbcDriverJar", "");

        if (F.isEmpty(jdbcDriverJarPath))
            throw new IllegalArgumentException("Path to JDBC driver not found in arguments");

        String jdbcDriverCls = args.getString("jdbcDriverClass", "");

        if (F.isEmpty(jdbcDriverCls))
            throw new IllegalArgumentException("JDBC driver class not found in arguments");

        if (F.isEmpty(jdbcUrl))
            throw new IllegalArgumentException("JDBC URL not found in arguments");

        if (!args.containsKey("info"))
            throw new IllegalArgumentException("Connection parameters not found in arguments");

        Properties jdbcInfo = new Properties();

        jdbcInfo.putAll((Map)args.get("info"));

        if (!new File(jdbcDriverJarPath).isAbsolute() && driversFolder != null)
            jdbcDriverJarPath = new File(driversFolder, jdbcDriverJarPath).getPath();

        log.info("Connecting to database[drvJar=" + jdbcDriverJarPath +
            ", drvCls=" + jdbcDriverCls + ", jdbcUrl=" + jdbcUrl + "]");

        Connection conn = dbMetaReader.connect(jdbcDriverJarPath, jdbcDriverCls, jdbcUrl, jdbcInfo);
        
        this.databaseListener.addDB(args);
        
        return conn;
    }


	public Collection<GridClientCacheBean> schemas(DBInfo info) {
		if(info==null)
			return Collections.EMPTY_LIST; 
		try (Connection conn = dbMetaReader.connect(null,info.driverCls,info.jdbcUrl,info.jdbcProp)) {
            String catalog = conn.getCatalog();

            if (catalog == null) {
                String jdbcUrl = info.jdbcUrl;

                String[] parts = jdbcUrl.split("[/:=]");

                catalog = parts.length > 0 ? parts[parts.length - 1] : "NONE";
            }

            Collection<String> schemas = dbMetaReader.schemas(conn, true);
            Collection<GridClientCacheBean> caches = new ArrayList<>();
            schemas.forEach((name)->{    
            	GridClientCacheBean cache = new GridClientCacheBean(name,GridClientCacheMode.LOCAL,name);
            	caches.add(cache);
            });
            return caches;
           
        }
		catch(Exception e) {
			e.printStackTrace();
			return Collections.EMPTY_LIST;
		}
	}
	
    public boolean isDBCluster(String clusterId) {
		return clusterId!=null && databaseListener.isDBCluster(clusterId);
	}
	
	public RestResult restCommand(String clusterId, JsonObject params) throws Throwable  {		
		 RestResult res = jdbcExecutor.sendRequest(clusterId, params, params);
         return res;
	}
	

    /**
     * @return Topology snapshot for demo cluster.
     */
    List<TopologySnapshot> topologySnapshot() {        
        List<TopologySnapshot> tops = new LinkedList<>();        
        String deactivedCluster = null;
        for (Entry<String, DBInfo> ent: databaseListener.clusters.entrySet()) {
        	DBInfo info = ent.getValue();
        	try {
	        	if(ent.getValue().top==null) {
		        	List<GridClientNodeBean> nodes = new ArrayList<>();
		        	GridClientNodeBean node = new GridClientNodeBean();
		        	node.setNodeId(UUID.fromString(info.clusterId));
		        	node.setConsistentId(info.clusterId);
		        	node.setAttributes((Map)info.jdbcProp);
		        	node.setTcpAddresses(Arrays.asList(info.jdbcUrl));
		        	node.setTcpPort(0);
		        	Collection<GridClientCacheBean> schemas = schemas(info);
		        	node.setCaches(schemas);		        	
		        	nodes.add(node);
		        	TopologySnapshot top = new TopologySnapshot(nodes);
		            
		            top.setClusterVersion(VER_STR);
		            top.setId(info.clusterId);
		            top.setName(info.jdbcUrl);
		            top.setDemo(false); 
		            top.setActive(true);
		            if(schemas.isEmpty()) {
		            	top.setActive(false);
		            	deactivedCluster = info.clusterId;
		            }
		           
		            info.top = top;
	        	} 
	        	else {
	        		Collection<GridClientCacheBean> schemas = schemas(info);
	        		if(schemas.isEmpty()) {
		            	info.top.setActive(false);
		            	deactivedCluster = info.clusterId;
		            }
	        	}
	            
	            tops.add(info.top);
	            
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}

        }       
        
        if(deactivedCluster!=null) {
        	databaseListener.clusters.remove(deactivedCluster);
        }
        
        return tops;
    }
}
