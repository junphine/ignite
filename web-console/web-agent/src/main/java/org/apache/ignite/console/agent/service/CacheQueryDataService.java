package org.apache.ignite.console.agent.service;

import io.swagger.annotations.ApiOperation;
import io.vertx.core.json.JsonObject;
import io.vertx.webmvc.mcp.McpSchema;
import io.vertx.webmvc.mcp.ToolExecutor;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.console.agent.rest.GridTaskExecutor;
import org.apache.ignite.console.agent.rest.RestResult;
import org.apache.ignite.resources.IgniteInstanceResource;

import java.util.List;
import java.util.Map;


@ApiOperation("Query or scan cache data from cluster")
public class CacheQueryDataService implements CacheAgentService,McpService {
   
	 /** Ignite instance. */
    @IgniteInstanceResource
    private Ignite ignite;

	@Override
	public List<ToolExecutor> toolExecutors(){
		McpSchema.JSONSchema queryType = new McpSchema.JSONSchema();
		queryType.setType("string");
		queryType.setDescription("Query type");
		queryType.setEnumValues(List.of("SQL","SQL_FIELDS","SCAN"));

		ToolExecutor toolX = ToolExecutor.builder()
				.name("query-cache")
				.description("Query cache data from cluster use sql or keyword")
				.parameter("cacheName","string","Ignie cache name",true)
				.parameter("type","string","Ignie entity type name",true)
				.parameter("qry","string","SQL or Luence query keyword",false)
				.parameter("arg","array","Query arguments",false)
				.parameter("className","string","Filter className",false)
				.parameter("pageSize","int","pageSize",false)
				.parameter("queryType",queryType,true)
				.executor((Map<String,Object> param)->{
					return this.call((String)param.get("cacheName"),param);
				 })
				.build();

		return List.of(toolX);
	}
    
	@Override
	public ServiceResult call(String cache,Map<String,Object> payload) {
		ServiceResult result = new ServiceResult();
		int count = 0;		
		
		List<String> message = result.getMessages();
		
		try {
			IgniteCache<?,?> igcache = ignite.cache(cache);
			GridTaskExecutor taskExecutor = new GridTaskExecutor();
			JsonObject param = new JsonObject(payload);
			param.put("cacheName",cache);
			param.put("keepBinary",true);

			String queryType = (String)payload.get("queryType");
			String cmd = "qryscanexe";
			if("SQL_FIELDS".equalsIgnoreCase(queryType))
				cmd = "qryfldexe";
			if("SQL".equalsIgnoreCase(queryType))
				cmd = "qryexe";

			RestResult res = taskExecutor.execRequest(ignite,cmd,param);
			if(res.getStatus()==0){
				result.put("data",res.getData());
				result.setStatus("succes");
			}
			else{
				result.setErrorType("GridTaskExecutor");
				message.add(res.getError());
			}
		}
		catch(Exception e) {
			result.setAcknowledged(false);
			message.add(e.getMessage());
		}
		return result;
	}

}
