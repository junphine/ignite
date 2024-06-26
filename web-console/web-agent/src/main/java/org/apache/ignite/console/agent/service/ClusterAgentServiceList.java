package org.apache.ignite.console.agent.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.ignite.Ignite;

import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;
import org.apache.ignite.services.ServiceDescriptor;

import io.swagger.annotations.ApiOperation;
import io.vertx.core.json.JsonObject;

@ApiOperation("get list of servcie of cluster")
public class ClusterAgentServiceList implements ClusterAgentService {
   
	@IgniteInstanceResource
    private Ignite ignite;
	
	@Override
	public Map<String, ? extends Object> call(Map<String,Object> payload) {
		Map<String,Object> result = new HashMap<>();
		Collection<ServiceDescriptor> descs = ignite.services().serviceDescriptors();
		
		JsonObject args = new JsonObject(payload);
		String type = args!=null?args.getString("type"):null;
		
		for(ServiceDescriptor ctx: descs) {
			JsonObject info = new JsonObject();
			info.put("name", ctx.name());			
			
			info.put("cacheName", ctx.cacheName());
			info.put("affinityKey", ctx.affinityKey());
			info.put("totalCount", ctx.totalCount());
			info.put("maxPerNodeCount", ctx.maxPerNodeCount());
			ApiOperation api = ctx.serviceClass().getAnnotation(ApiOperation.class);
			if(api!=null) {
				info.put("description", api.value());
				info.put("notes", api.notes());				
			}			
			else {
				info.put("description", ctx.serviceClass().getSimpleName());
				info.put("notes","");
			}
			
			if(ClusterAgentService.class.isAssignableFrom(ctx.serviceClass())){
				info.put("type","ClusterAgentService");
				// KeyaffinitySingleton,Multiple,NodeSingleton,ClusterSingleton
				info.put("mode", "ClusterSingleton"); 
			}
			else if(CacheAgentService.class.isAssignableFrom(ctx.serviceClass())){
				info.put("type","CacheAgentService");
				// KeyaffinitySingleton,Multiple,NodeSingleton,ClusterSingleton
				info.put("mode", "NodeSingleton"); 
			}
			else {
				info.put("mode", "ClusterSingleton"); 
				info.put("type","Unknown");
			}
			if(type!=null && !info.getString("type").equals(type)) {
				continue;
			}
			result.put(ctx.name(), info);
		}
		return result;
	}
}
