

package org.apache.ignite.console.agent;

import static org.apache.ignite.events.EventType.EVTS_DISCOVERY;
import static org.apache.ignite.internal.IgniteNodeAttributes.ATTR_REST_JETTY_ADDRS;
import static org.apache.ignite.internal.IgniteNodeAttributes.ATTR_REST_JETTY_PORT;
import static org.apache.ignite.internal.IgniteNodeAttributes.ATTR_REST_TCP_ADDRS;
import static org.apache.ignite.internal.IgniteNodeAttributes.ATTR_REST_TCP_PORT;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteIllegalStateException;
import org.apache.ignite.IgniteServices;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryBasicNameMapper;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.cluster.ClusterStartNodeResult;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;

import org.apache.ignite.console.agent.db.DataSourceManager;
import org.apache.ignite.console.agent.handlers.RestClusterHandler;
import org.apache.ignite.console.agent.handlers.StringStreamHandler;
import org.apache.ignite.console.agent.service.*;
import org.apache.ignite.console.json.JsonBinarySerializer;
import org.apache.ignite.console.utils.BeanMerger;
import org.apache.ignite.console.utils.Utils;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.failure.StopNodeFailureHandler;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgnitionEx;
import org.apache.ignite.internal.cluster.ClusterStartNodeResultImpl;
import org.apache.ignite.internal.commandline.CommandHandler;
import org.apache.ignite.internal.management.IgniteCommandRegistry;
import org.apache.ignite.internal.management.api.Command;
import org.apache.ignite.internal.processors.resource.GridSpringResourceContext;
import org.apache.ignite.internal.util.nodestart.IgniteRemoteStartSpecification;
import org.apache.ignite.internal.util.nodestart.StartNodeCallable;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.logger.java.JavaLogger;

import org.apache.ignite.spi.discovery.isolated.IsolatedDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryWebConsoleServerIpFinder;
import org.apache.ignite.spi.eventstorage.memory.MemoryEventStorageSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Launcher for cluster features like SQL and Monitoring.
 *
 * Cache will be created and populated with data to query.
 */
public class IgniteClusterLauncher implements StartNodeCallable{
    /** */
    private static final Logger log = LoggerFactory.getLogger(IgniteClusterLauncher.class);

    /** */
    private static final AtomicBoolean initGuard = new AtomicBoolean();

    /** */
    private static final int WAL_SEGMENTS = 5;

    /** WAL file segment size, 16MBytes. */
    private static final int WAL_SEGMENT_SZ = 16 * 1024 * 1024;
    
    /** Specification. */
    private final IgniteRemoteStartSpecification spec;
    
    /** Connection timeout. */
    private final int timeout;    
    
    
    /**
     * Required by Externalizable.
     */
    public IgniteClusterLauncher() {
        spec = null;
        timeout = 0;        
    }	
    
    /**
     * Constructor.
     *
     * @param spec Specification.
     * @param timeout Connection timeout.
     */
    public IgniteClusterLauncher(IgniteRemoteStartSpecification spec, int timeout) {
        assert spec != null;

        this.spec = spec;

        this.timeout = timeout;
    }
    
    public static IgniteConfiguration mergeIgniteConfiguration(IgniteConfiguration cfg, IgniteConfiguration from){
    	if(from!=null && cfg!=from) {
    		BeanMerger.mergeBeans(from,cfg);
    	}
        return cfg;
    }
    
    public static IgniteConfiguration singleIgniteConfiguration(IgniteConfiguration cfg, IgniteConfiguration preCfg) {
    	
    	cfg = mergeIgniteConfiguration(cfg,preCfg);
        
        if(cfg.getLocalHost()==null) {
        	cfg.setLocalHost("127.0.0.1");
        }       
        
        if(cfg.getIncludeEventTypes()==null) {
        	int[] evts = new int[EVTS_DISCOVERY.length];
            System.arraycopy(EVTS_DISCOVERY, 0, evts, 0, EVTS_DISCOVERY.length);
        	cfg.setIncludeEventTypes(evts);
        	cfg.setEventStorageSpi(new MemoryEventStorageSpi());
        }
        
        if(cfg.getBinaryConfiguration()==null) {
        	BinaryConfiguration binConf = new BinaryConfiguration();
			binConf.setNameMapper(new BinaryBasicNameMapper());
        	cfg.setBinaryConfiguration(binConf);
        }

		// disable@byron
		if(false && cfg.getBinaryConfiguration()!=null) {
			// add@byron
			if(cfg.getBinaryConfiguration().getTypeConfigurations()==null) {
				cfg.getBinaryConfiguration().setTypeConfigurations(new ArrayList<>());
			}
			// Custom ClusterSerializable
			BinaryTypeConfiguration jsonBinCfg = new BinaryTypeConfiguration();
			jsonBinCfg.setTypeName(JsonObject.class.getName());
			jsonBinCfg.setSerializer(new JsonBinarySerializer());

			BinaryTypeConfiguration arrayBinCfg = new BinaryTypeConfiguration();
			arrayBinCfg.setTypeName(JsonArray.class.getName());
			arrayBinCfg.setSerializer(new JsonBinarySerializer());

			cfg.getBinaryConfiguration().getTypeConfigurations().add(jsonBinCfg);
			cfg.getBinaryConfiguration().getTypeConfigurations().add(arrayBinCfg);
		}
        
        // Configure discovery SPI.
        if(cfg.getDiscoverySpi()==null) {
	        IsolatedDiscoverySpi discoSpi = new IsolatedDiscoverySpi(); 
	        cfg.setDiscoverySpi(discoSpi);
        }
		else if(cfg.getDiscoverySpi() instanceof TcpDiscoverySpi){
			TcpDiscoverySpi disco = (TcpDiscoverySpi)cfg.getDiscoverySpi();
			TcpDiscoveryWebConsoleServerIpFinder ipFinder = null;
			if(disco.getIpFinder()==null){
				ipFinder = new TcpDiscoveryWebConsoleServerIpFinder();
				ipFinder.setMasterUrl(DataSourceManager.getAdminServerUrl());
				ipFinder.setAccountToken(DataSourceManager.getAccountToken());
				disco.setIpFinder(ipFinder);
			}
			else if(disco.getIpFinder() instanceof TcpDiscoveryWebConsoleServerIpFinder){
				ipFinder = (TcpDiscoveryWebConsoleServerIpFinder)disco.getIpFinder();
				ipFinder.setMasterUrl(DataSourceManager.getAdminServerUrl());
				ipFinder.setAccountToken(DataSourceManager.getAccountToken());
			}
		}
        
        cfg.setFailureHandler(new NoOpFailureHandler());
        cfg.setMetricsLogFrequency(0);
        return cfg;
    }


	public static String getNodeRestUrl(Ignite ignite) {
		ClusterNode node = ignite.cluster().localNode();
		if(node.isClient()){
			node = ignite.cluster().forServers().node();
		}

		Collection<String> jettyAddrs = node.attribute(ATTR_REST_JETTY_ADDRS);

		if (jettyAddrs == null) {
			if(ignite.configuration().isClientMode()) {
				return null;
			}
			throw new IgniteException("Cluster: Failed to start Jetty REST server on embedded node");
		}

		String jettyHost = "127.0.0.1";
		for(String host: jettyAddrs) {
			if(host.indexOf(":")<0) {
				jettyHost = host;
				break;
			}
		}

		Integer jettyPort = node.attribute(ATTR_REST_JETTY_PORT);

		if (F.isEmpty(jettyHost) || jettyPort == null)
			throw new IgniteException("Cluster: Failed to start Jetty REST handler on embedded node");

		log.info("Cluster: Started embedded node for data analysis purpose [TCP binary port={}, Jetty REST port={}]", ignite.configuration().getConnectorConfiguration().getPort(), jettyPort);

		String nodeUrl = String.format("http://%s:%d", jettyHost, jettyPort);
		return nodeUrl;
	}

    /** */
    public static void stopIgnite(String clusterName,String clusterId) {  
    	if(clusterId!=null) {
    		try {	    		
	    		String gridName = RestClusterHandler.clusterNameMap.get(clusterId);
	    		if(gridName!=null) {
		    		Ignition.stop(gridName,true);
		    		clusterName = null;
	    		}	    		
    		}
	    	catch(IgniteIllegalStateException | IllegalArgumentException  e) {
	    		//-log.error("Failed to stop cluster node: "+nodeId,e);
	    	}
    	}
    	
    	if(clusterName!=null) {
    		Ignition.stop(clusterName,true);
    	}
    }
    
    /**
     * Start ignite node with cacheEmployee and populate it with data.
     * @throws IgniteCheckedException 
     */
    public static Ignite trySingleStart(String clusterId,String clusterName,int nodeIndex,int isLastNode,String cfgFile) throws IgniteCheckedException {
    	
        return trySingleStart(clusterId,clusterName,nodeIndex,isLastNode,cfgFile,null);
    }
    
    /**
     * Start ignite node with cacheEmployee and populate it with data.
     * @throws IgniteCheckedException 
     */
    public static Ignite trySingleStart(String clusterId,String clusterName,int nodeIndex,int isLastNode,String cfgFile,String preCfgFile) throws IgniteCheckedException {
    	Ignite ignite = null;    	
    	
    	// 基于Instance Name 查找ignite
    	try {
    		ignite = Ignition.ignite(clusterName);
    		return ignite;
    	}
    	catch(IgniteIllegalStateException e) {
    		
    	}
    	
    	// 基于配置文件 启动 实例
    	IgniteConfiguration preCfg = null;
    	IgniteBiTuple<Collection<IgniteConfiguration>, ? extends GridSpringResourceContext> cfgMap=null;
    	if(ignite==null && preCfgFile!=null) {
			URL springPreCfgUrl = U.resolveSpringUrl(preCfgFile);			
			cfgMap = IgnitionEx.loadConfigurations(springPreCfgUrl);
			
			Collection<IgniteConfiguration> cfgList = cfgMap.get1();
			for(IgniteConfiguration cfg: cfgList) {
				if(clusterName.equals(cfg.getIgniteInstanceName()) || cfgList.size()==1){
					preCfg = cfg;
				}				
			}
		}
		
        if(ignite==null && cfgFile!=null) {
        	
			URL springCfgUrl = U.resolveSpringUrl(cfgFile);
			
			IgniteBiTuple<Collection<IgniteConfiguration>, ? extends GridSpringResourceContext> cfgWorkMap;

			cfgWorkMap = IgnitionEx.loadConfigurations(springCfgUrl);			
			//only on node per jvm.					
			IgniteConfiguration cfg = cfgWorkMap.get1().iterator().next();			

			// 最后一个节点： clusterID和nodeID相同
			if(isLastNode>0) {
				if(cfg.getConsistentId()==null)
					cfg.setConsistentId(clusterId+"_"+nodeIndex);
				
			}
			else {
				cfg.setClusterStateOnStart(ClusterState.INACTIVE);
				if(cfg.getConsistentId()==null)
					cfg.setConsistentId(clusterId+"_"+nodeIndex);		
			}
			
			if(cfg.getIgniteInstanceName()==null)
				cfg.setIgniteInstanceName(clusterName);
			
			singleIgniteConfiguration(cfg,preCfg);
			
			ignite = IgnitionEx.start(cfg,cfgWorkMap.get2());
			
        }
        
        if(cfgMap!=null && !cfgMap.get1().isEmpty()) {
			// other ignite instance
			Collection<IgniteConfiguration> cfgList = cfgMap.get1();
			for(IgniteConfiguration cfg: cfgList) {
				if(clusterName.equals(cfg.getIgniteInstanceName())){
					if(ignite==null) {
						ignite = IgnitionEx.start(cfg,cfgMap.get2());
					}
				}
				else {
					try {
						IgnitionEx.start(cfg,cfgMap.get2());
					}
					catch(IgniteIllegalStateException | IllegalArgumentException  e) {
			    		
			    	}
				}							
			}
		}
        
        if(isLastNode>0 && ignite!=null) {
        	try {
				while(ignite.cluster().nodes().size()<isLastNode) {
            		Thread.sleep(1000L);
            	}
			} catch (InterruptedException e) {				
				e.printStackTrace();
			}
        	
			ignite.cluster().state(ClusterState.ACTIVE);
			ServiceDeployment.deployBuildinServices(ignite);

		}
        return ignite;
    }
    

    /**
     * Start ignite node with cacheEmployee and populate it with data.
     */
    public static boolean saveBlobToFile(JsonObject json,String destPath,List<String> messages) {

		String clusterName = json.getString("name");
    	String base64 = json.getString("blob");    	 
        String prefix = "data:application/octet-stream;base64,";
        if (base64!=null && base64.startsWith(prefix)) {
        	String fileName = Utils.escapeFileName(clusterName);
			U.mkdirs(new File(destPath));
			
			byte[] zip = Base64.decodeBase64(base64.substring(prefix.length()));
			File zipFile = new File(destPath, fileName+".zip");
			String descDir = destPath + fileName+"/";
			
			try {
				FileOutputStream writer = new FileOutputStream(zipFile);
				writer.write(zip);
				writer.close();
				
				AgentUtils.unZip(zipFile, descDir);
				
				return true;
				
			} catch (IOException e) {
				log.error("Failed to save zip blob data!",e);
				messages.add(e.getMessage());
			}
        }
        return false;
        
    } 
    
    /**
     * Start ignite node with cacheEmployee and populate it with data.
     * @throws IgniteCheckedException 
     */
    public static File saveDataToFile(String fileName,String json) throws IgniteCheckedException {
    	
    	String work = U.workDirectory(null, null)+ "/config/";
		U.mkdirs(new File(work));
		
		File outFile = new File(work, fileName);			
		
		try {
			FileOutputStream writer = new FileOutputStream(outFile);
			writer.write(json.getBytes());
			writer.close();				
			
		} catch (IOException e) {
			log.error("Failed to save ini data!",e);
			throw new IgniteCheckedException(e);
		}
		return outFile;	
        
    }
    
    /**
     * @param json Token to revoke.
     */
    public static JsonObject callClusterCommand(Ignite ignite,String cmdName,JsonObject json) throws IgniteCheckedException {
        log.info("Cluster cmd is invoking: " + cmdName);
        
        JsonArray args = json.getJsonArray("args");        
        JsonObject stat = new JsonObject();
        
        List<String> argsList = new ArrayList<>();
		argsList.add(cmdName);

        if(ignite!=null) {
	        
        	ClusterNode node = ignite.cluster().localNode();
	        Collection<String> tcpAddrs = node.addresses();
	        String host = "127.0.0.1";
			for(String ip: tcpAddrs) {
				if(!ip.contains(":")) {
					host = ip;
					break;
				}
			}
			argsList.add("--host");
			argsList.add(host);
	        argsList.add("--port");
	        Object tcpPort = node.attribute("clientListenerPort");
	        argsList.add(""+tcpPort);
        }
        
        if(args!=null) {
        	args.forEach(e-> argsList.addAll(Arrays.asList(e.toString().split("\\s"))));
        }
        StringStreamHandler outHandder = new StringStreamHandler();        
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(CommandHandler.class.getName() + "Log");
        logger.addHandler(outHandder);
        JavaLogger javaLogger = new JavaLogger(logger,!JavaLogger.isConfigured());
		CommandHandler hnd = new CommandHandler(javaLogger);
		hnd.console = null;
        if(cmdName.equals("commandList")) {
        	IgniteCommandRegistry cmdReg = null;
        	if(ignite==null) {        		
				try {
					Field registry = hnd.getClass().getDeclaredField("registry");
					registry.setAccessible(true);
	            	cmdReg = (IgniteCommandRegistry)registry.get(hnd);
	            	
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
        	}
        	else {
        		cmdReg = ((IgniteEx)ignite).commandsRegistry();
        	}
        	
        	List<JsonObject> results = new ArrayList<>(10);
        	Iterator<Entry<String, Command<?, ?>>> it = cmdReg.commands();
        	while (it.hasNext()) {                    
        		Entry<String, Command<?, ?>> pair = it.next();
        		Command<?, ?> c = pair.getValue();
        		try{
	        		hnd.printUsage(javaLogger,c);
	        		javaLogger.flush();
	        		String usage = outHandder.getOutput();
	        		String desc = "";
					try {
						desc = c.description();
					}catch (Exception ignored){
						// ignore
					}
	        		
	            	JsonObject cmd = new JsonObject();
	            	cmd.put("name", pair.getKey());
	            	cmd.put("text", desc);
	            	cmd.put("usage", usage);
	            	cmd.put("experimental", c.argClass().getSimpleName());
	            	results.add(cmd);
        		}catch(Exception ignore) {
					// ignore
        		}
            }
        	
        	stat.put("result", results);
        	return stat;
        }
		if(cmdName.equals("startUI")) {
			int code = startClusterCrudUICommand(ignite.name(),argsList,stat);
			stat.put("code",code);
			javaLogger.flush();
			if(code==CommandHandler.EXIT_CODE_OK) {
				stat.put("message", outHandder.getOutput());
			}
			else {
				stat.put("message", outHandder.getOutput());
			}
			return stat;
		}
		if(cmdName.equals("stopUI")) {
			int code = stopClusterCrudUICommand(ignite.name(),argsList,stat);
			stat.put("code",code);
			javaLogger.flush();
			if(code==CommandHandler.EXIT_CODE_OK) {
				stat.put("message", outHandder.getOutput());
			}
			else {
				stat.put("message", outHandder.getOutput());
			}
			return stat;
		}

        int code = hnd.execute(argsList);
        stat.put("code",code);
        javaLogger.flush();
        if(code==CommandHandler.EXIT_CODE_OK) {
        	stat.put("message", outHandder.getOutput());
        }
        else {
        	stat.put("message", outHandder.getOutput());
        }
        return stat;
    }

	/** 从 ProcessHandle 抽取可持久化信息。 */
	public static ProcessSnapshot toSnapshot(ProcessHandle handle) {
		ProcessHandle.Info info = handle.info();

		ProcessSnapshot s = new ProcessSnapshot();
		s.setPid(handle.pid());

		// 启动时间：Instant -> epochMillis（可能缺失）
		s.setStartEpochMillis(info.startInstant().map(Instant::toEpochMilli).orElse(null));

		// 命令行 / 参数 / 可执行路径 / 用户，均可能为空
		s.setCommand(info.command().orElse(null));
		s.setArguments(info.arguments().map(List::of).orElse(null)); // List.of(array)
		s.setCommandLine(info.commandLine().orElse(null));
		s.setUser(info.user().orElse(null));
		s.setSavedAtEpochMillis(System.currentTimeMillis());
		return s;
	}

	/**
	 * @param args
	 */
	public static int startClusterCrudUICommand(String clusterName,List<String> args,JsonObject stat) throws IgniteCheckedException {
		log.info("Cluster {} ui is start...",clusterName);
		clusterName = Utils.escapeFileName(clusterName);
		String cwd = U.workDirectory(null, null)+ "/config/"+clusterName;
		// 指定工作目录
		File workDir = new File(cwd);

		// 构建命令（注意：命令和参数要分开传入）
		ProcessBuilder pb = new ProcessBuilder(args.toArray(new String[args.size()]));
		// 设置工作目录
		pb.directory(workDir);

		// 继承当前进程的 IO（输出直接打印到控制台）
		pb.inheritIO();

		try {
			Process process = pb.start();

			ProcessSnapshot snap = toSnapshot(process.toHandle());
			String info = Utils.toJson(snap);
			saveDataToFile(clusterName+"/process.json",info);
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * @param args
	 */
	public static int stopClusterCrudUICommand(String clusterName,List<String> args,JsonObject stat) throws IgniteCheckedException {
		log.info("Cluster {} ui is stop...",clusterName);
		clusterName = Utils.escapeFileName(clusterName);
		String cwd = U.workDirectory(null, null)+ "/config/"+clusterName;
		File infoFile = new File(cwd,"process.json");
		long pid = 0L;
        try {
            Reader fileReader = new FileReader(infoFile);
			ProcessSnapshot snap = Utils.fromJson(fileReader,ProcessSnapshot.class);
			pid = snap.pid;
        } catch (IOException e) {
			stat.put("message","UI进程没有启动!");
            return 0;
        }
        Duration grace = Duration.ofSeconds(5);
		Optional<ProcessHandle> opt = ProcessHandle.of(pid);
		if (opt.isPresent() && opt.get().isAlive()) {
			ProcessHandle h = opt.get();

			// 2) 先礼：SIGTERM
			h.destroy();
			if (waitDead(h, grace)){
				infoFile.delete();
				return 0;
			}

			// 3) 后兵：SIGKILL
			h.destroyForcibly();
			if (waitDead(h, grace)){
				infoFile.delete();
				return 0;
			}
			return -1;
		}
		infoFile.delete();
		return 0;
	}

	private static boolean waitDead(ProcessHandle h, Duration grace) {
		long deadline = System.nanoTime() + grace.toNanos();
		while (System.nanoTime() < deadline) {
			if (!h.isAlive()) return true;
			try { Thread.sleep(100); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
		}
		return !h.isAlive();
	}

		@Override
	public ClusterStartNodeResult call() throws Exception {
		// start node by agent
    	// 给其他agent发送启动消息
		ClusterStartNodeResult result = new ClusterStartNodeResultImpl(spec.host(),false,"not implement!");
		return result;
	}

	@Data
	public static class ProcessSnapshot {
		private long pid;                 // 关键：重新定位用
		private Long startEpochMillis;    // 关键：防 PID 复用误杀
		private String command;           // 可执行文件路径
		private List<String> arguments;   // 参数（可能为空）
		private String commandLine;       // 完整命令行（可能为空）
		private String user;              // 启动用户（可能为空）
		private long savedAtEpochMillis;  // 记录时间
		// getters/setters ...
	}

}
