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

package org.apache.ignite.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.cache.query.index.IndexProcessor;
import org.apache.ignite.internal.cache.transform.CacheObjectTransformerProcessor;
import org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager;
import org.apache.ignite.internal.managers.collision.GridCollisionManager;
import org.apache.ignite.internal.managers.communication.GridIoManager;
import org.apache.ignite.internal.managers.deployment.GridDeploymentManager;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.managers.encryption.GridEncryptionManager;
import org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager;
import org.apache.ignite.internal.managers.failover.GridFailoverManager;
import org.apache.ignite.internal.managers.indexing.GridIndexingManager;
import org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager;
import org.apache.ignite.internal.managers.systemview.GridSystemViewManager;
import org.apache.ignite.internal.processors.affinity.GridAffinityProcessor;
import org.apache.ignite.internal.processors.cache.GridCacheProcessor;
import org.apache.ignite.internal.processors.cache.persistence.defragmentation.IgniteDefragmentation;
import org.apache.ignite.internal.processors.cache.persistence.filename.PdsFoldersResolver;
import org.apache.ignite.internal.processors.cacheobject.IgniteCacheObjectProcessor;
import org.apache.ignite.internal.processors.closure.GridClosureProcessor;
import org.apache.ignite.internal.processors.cluster.ClusterProcessor;
import org.apache.ignite.internal.processors.cluster.GridClusterStateProcessor;
import org.apache.ignite.internal.processors.compress.CompressionProcessor;
import org.apache.ignite.internal.processors.configuration.distributed.DistributedConfigurationProcessor;
import org.apache.ignite.internal.processors.continuous.GridContinuousProcessor;
import org.apache.ignite.internal.processors.datastreamer.DataStreamProcessor;
import org.apache.ignite.internal.processors.datastructures.DataStructuresProcessor;
import org.apache.ignite.internal.processors.diagnostic.DiagnosticProcessor;
import org.apache.ignite.internal.processors.failure.FailureProcessor;
import org.apache.ignite.internal.processors.igfs.IgfsHelper;
import org.apache.ignite.internal.processors.igfs.IgfsProcessorAdapter;
import org.apache.ignite.internal.processors.job.GridJobProcessor;
import org.apache.ignite.internal.processors.jobmetrics.GridJobMetricsProcessor;
import org.apache.ignite.internal.processors.localtask.DurableBackgroundTasksProcessor;
import org.apache.ignite.internal.processors.marshaller.GridMarshallerMappingProcessor;
import org.apache.ignite.internal.processors.metastorage.DistributedMetaStorage;
import org.apache.ignite.internal.processors.metric.GridMetricManager;
import org.apache.ignite.internal.processors.odbc.ClientListenerProcessor;
import org.apache.ignite.internal.processors.performancestatistics.PerformanceStatisticsProcessor;
import org.apache.ignite.internal.processors.platform.PlatformProcessor;
import org.apache.ignite.internal.processors.plugin.IgnitePluginProcessor;
import org.apache.ignite.internal.processors.pool.PoolProcessor;
import org.apache.ignite.internal.processors.port.GridPortProcessor;
import org.apache.ignite.internal.processors.query.GridQueryProcessor;
import org.apache.ignite.internal.processors.resource.GridResourceProcessor;
import org.apache.ignite.internal.processors.rest.IgniteRestProcessor;
import org.apache.ignite.internal.processors.schedule.IgniteScheduleProcessorAdapter;
import org.apache.ignite.internal.processors.security.IgniteSecurity;
import org.apache.ignite.internal.processors.segmentation.GridSegmentationProcessor;
import org.apache.ignite.internal.processors.service.IgniteServiceProcessor;
import org.apache.ignite.internal.processors.session.GridTaskSessionProcessor;
import org.apache.ignite.internal.processors.subscription.GridInternalSubscriptionProcessor;
import org.apache.ignite.internal.processors.task.GridTaskProcessor;
import org.apache.ignite.internal.processors.timeout.GridTimeoutProcessor;
import org.apache.ignite.internal.processors.tracing.Tracing;
import org.apache.ignite.internal.suggestions.GridPerformanceSuggestions;
import org.apache.ignite.internal.util.IgniteExceptionRegistry;
import org.apache.ignite.internal.util.tostring.GridToStringExclude;
import org.apache.ignite.internal.worker.WorkersRegistry;
import org.apache.ignite.maintenance.MaintenanceRegistry;
import org.apache.ignite.plugin.PluginNotFoundException;
import org.apache.ignite.plugin.PluginProvider;

/**
 *
 */
@GridToStringExclude
public interface GridKernalContext extends Iterable<GridComponent> {
    /**
     * Gets list of all grid components in the order they were added.
     *
     * @return List of all grid components in the order they were added.
     */
    public List<GridComponent> components();

    /**
     * Gets local node ID.
     *
     * @return Local node ID.
     */
    public UUID localNodeId();

    /**
     * Gets Ignite instance name.
     *
     * @return Ignite instance name.
     */
    public String igniteInstanceName();

    /**
     * Gets logger for given category.
     *
     * @param ctgr Category.
     * @return Logger.
     */
    public IgniteLogger log(String ctgr);

    /**
     * Gets logger for given class.
     *
     * @param cls Class to get logger for.
     * @return Logger.
     */
    public IgniteLogger log(Class<?> cls);

    /**
     * @return {@code True} if grid is in the process of stopping.
     */
    public boolean isStopping();

    /**
     * Gets kernal gateway.
     *
     * @return Kernal gateway.
     */
    public GridKernalGateway gateway();

    /**
     * Gets grid instance managed by kernal.
     *
     * @return Grid instance.
     */
    public IgniteEx grid();

    /**
     * Gets grid configuration.
     *
     * @return Grid configuration.
     */
    public IgniteConfiguration config();

    /**
     * Gets task processor.
     *
     * @return Task processor.
     */
    public GridTaskProcessor task();

    /**
     * Gets cache data affinity processor.
     *
     * @return Cache data affinity processor.
     */
    public GridAffinityProcessor affinity();

    /**
     * Gets job processor.
     *
     * @return Job processor
     */
    public GridJobProcessor job();

    /**
     * Gets timeout processor.
     *
     * @return Timeout processor.
     */
    public GridTimeoutProcessor timeout();

    /**
     * Gets resource processor.
     *
     * @return Resource processor.
     */
    public GridResourceProcessor resource();

    /**
     * Gets job metric processor.
     *
     * @return Metrics processor.
     */
    public GridJobMetricsProcessor jobMetric();

    /**
     * Gets metric manager.
     *
     * @return Monitoring manager.
     */
    public GridMetricManager metric();

    /**
     * Gets maintenance registry.
     *
     * @return Maintenance registry.
     */
    public MaintenanceRegistry maintenanceRegistry();

    /**
     * Gets transformation processor.
     *
     * @return Transformation processor.
     */
    public CacheObjectTransformerProcessor transformer();

    /**
     * Gets system view manager.
     *
     * @return Monitoring manager.
     */
    public GridSystemViewManager systemView();

    /**
     * Gets caches processor.
     *
     * @return Cache processor.
     */
    public GridCacheProcessor cache();

    /**
     * Gets cluster state processor.
     *
     * @return Cluster state processor.
     */
    public GridClusterStateProcessor state();

    /**
     * Gets global metastorage.
     *
     * @return Global metastorage.
     */
    public DistributedMetaStorage distributedMetastorage();

    /**
     * Gets distributed configuration processor.
     *
     * @return Distributed configuration processor.
     */
    public DistributedConfigurationProcessor distributedConfiguration();

    /**
     * Gets tracing processor.
     *
     * @return Tracing processor.
     */
    public Tracing tracing();

    /**
     * Gets task session processor.
     *
     * @return Session processor.
     */
    public GridTaskSessionProcessor session();

    /**
     * Gets closure processor.
     *
     * @return Closure processor.
     */
    public GridClosureProcessor closure();

    /**
     * Gets service processor.
     *
     * @return Service processor.
     */
    public IgniteServiceProcessor service();

    /**
     * Gets port processor.
     *
     * @return Port processor.
     */
    public GridPortProcessor ports();

    /**
     * Gets schedule processor.
     *
     * @return Schedule processor.
     */
    public IgniteScheduleProcessorAdapter schedule();

    /**
     * Gets REST processor.
     *
     * @return REST processor.
     */
    public IgniteRestProcessor rest();

    /**
     * Gets segmentation processor.
     *
     * @return Segmentation processor.
     */
    public GridSegmentationProcessor segmentation();

    /**
     * Gets data streamer processor.
     *
     * @return Data streamer processor.
     */
    public <K, V> DataStreamProcessor<K, V> dataStream();

    /**
     * Gets event continuous processor.
     *
     * @return Event continuous processor.
     */
    public GridContinuousProcessor continuous();

    /**
     * Gets pool processor.
     *
     * @return Pool processor.
     */
    public PoolProcessor pools();


    /**
     * Gets file system processor.
     *
     * @return File system processor.
     */
    public IgfsProcessorAdapter igfs();
    
    
    public IgfsHelper igfsHelper();

    /**
     * Gets grid marshaller mapping processor.
     *
     * @return Mapping processor.
     */
    public GridMarshallerMappingProcessor mapping();

    /**
     * Gets cache object processor.
     *
     * @return Cache object processor.
     */
    public IgniteCacheObjectProcessor cacheObjects();

    /**
     * Gets query processor.
     *
     * @return Query processor.
     */
    public GridQueryProcessor query();

    /**
     * Gets client listener processor.
     *
     * @return Client listener processor.
     */
    public ClientListenerProcessor clientListener();

    /**
     * @return Plugin processor.
     */
    public IgnitePluginProcessor plugins();

    /**
     * Gets deployment manager.
     *
     * @return Deployment manager.
     */
    public GridDeploymentManager deploy();

    /**
     * Gets communication manager.
     *
     * @return Communication manager.
     */
    public GridIoManager io();

    /**
     * Gets discovery manager.
     *
     * @return Discovery manager.
     */
    public GridDiscoveryManager discovery();

    /**
     * Gets checkpoint manager.
     *
     * @return Checkpoint manager.
     */
    public GridCheckpointManager checkpoint();

    /**
     * Gets event storage manager.
     *
     * @return Event storage manager.
     */
    public GridEventStorageManager event();

    /**
     * Gets failover manager.
     *
     * @return Failover manager.
     */
    public GridFailoverManager failover();

    /**
     * Gets collision manager.
     *
     * @return Collision manager.
     */
    public GridCollisionManager collision();

    /**
     * Gets instance of {@link IgniteSecurity}.
     *
     * @return Ignite security.
     */
    public IgniteSecurity security();

    /**
     * Gets load balancing manager.
     *
     * @return Load balancing manager.
     */
    public GridLoadBalancerManager loadBalancing();

    /**
     * Gets indexing manager.
     *
     * @return Indexing manager.
     */
    public GridIndexingManager indexing();

    /**
     * Indexes processor.
     *
     * @return Indexes processor.
     */
    public IndexProcessor indexProcessor();

    /**
     * Gets encryption manager.
     *
     * @return Encryption manager.
     */
    public GridEncryptionManager encryption();

    /**
     * Gets defragmentation manager.
     *
     * @return Defragmentation manager.
     */
    public IgniteDefragmentation defragmentation();

    /**
     * Gets workers registry.
     *
     * @return Workers registry.
     */
    public WorkersRegistry workersRegistry();

    /**
     * Gets data structures processor.
     *
     * @return Data structures processor.
     */
    public DataStructuresProcessor dataStructures();

    /**
     * Gets long JVM pause detector.
     *
     * @return Long JVM pause detector.
     */
    public LongJVMPauseDetector longJvmPauseDetector();

    /**
     * Gets diagnostic processor.
     *
     * @return Diagnostic processor.
     */
    public DiagnosticProcessor diagnostic();

    /**
     * Checks whether this node is invalid due to a critical error or not.
     *
     * @return {@code True} if this node is invalid, {@code false} otherwise.
     */
    public boolean invalid();

    /**
     * Checks whether this node detected its segmentation from the rest of the grid.
     *
     * @return {@code True} if this node has segmented, {@code false} otherwise.
     */
    public boolean segmented();

    /**
     * Gets failure processor.
     */
    public FailureProcessor failure();

    /**
     * Print grid kernal memory stats (sizes of internal structures, etc.).
     *
     * NOTE: This method is for testing and profiling purposes only.
     */
    public void printMemoryStats();

    /**
     * @return Performance suggestions object.
     */
    public GridPerformanceSuggestions performance();

    /**
     * Gets user version for given class loader by checking
     * {@code META-INF/ignite.xml} file for {@code userVersion} attribute. If
     * {@code ignite.xml} file is not found, or user version is not specified there,
     * then default version (empty string) is returned.
     *
     * @param ldr Class loader.
     * @return User version for given class loader or empty string if no version
     *      was explicitly specified.
     */
    public String userVersion(ClassLoader ldr);

    /**
     * @param name Plugin name.
     * @return Plugin provider instance.
     * @throws PluginNotFoundException If plugin provider for the given name was not found.
     */
    public PluginProvider pluginProvider(String name) throws PluginNotFoundException;

    /**
     * Creates optional component.
     *
     * @param cls Component class.
     * @return Created component.
     */
    public <T> T createComponent(Class<T> cls);

    /**
     * Gets exception registry.
     *
     * @return Exception registry.
     */
    public IgniteExceptionRegistry exceptionRegistry();

    /**
     * Get node attribute by name.
     *
     * @param key Attribute name.
     * @return Attribute value.
     */
    public Object nodeAttribute(String key);

    /**
     * Check if node has specified attribute.
     *
     * @param key Attribute name.
     * @return {@code true} If node has attribute with specified name.
     */
    public boolean hasNodeAttribute(String key);

    /**
     * Add attribute to node attributes.
     *
     * @param key Attribute name.
     * @param val Attribute value.
     * @return Previous attribute value associated with attribute name.
     */
    public Object addNodeAttribute(String key, Object val);

    /**
     * @return Node attributes.
     */
    public Map<String, Object> nodeAttributes();

    /**
     * Gets Cluster processor.
     *
     * @return Cluster processor.
     */
    public ClusterProcessor cluster();

    /**
     * Gets marshaller context.
     *
     * @return Marshaller context.
     */
    public MarshallerContextImpl marshallerContext();

    /**
     * @return {@code True} if local node is client node (has flag {@link IgniteConfiguration#isClientMode()} set).
     */
    public boolean clientNode();

    /**
     * @return {@code True} if local node in disconnected state.
     */
    public boolean clientDisconnected();

    /**
     * @return Platform processor.
     */
    public PlatformProcessor platform();

    /**
     * @return PDS mode folder name resolver, also generates consistent ID in case new folder naming is used
     */
    public PdsFoldersResolver pdsFolderResolver();

    /**
     * @return subscription processor to manage internal-only (strict node-local) subscriptions between components.
     */
    public GridInternalSubscriptionProcessor internalSubscriptionProcessor();

    /**
     * @return Default uncaught exception handler used by thread pools.
     */
    public Thread.UncaughtExceptionHandler uncaughtExceptionHandler();

    /**
     * @return Compression processor.
     */
    public CompressionProcessor compress();

    /**
     * @return {@code True} if node is in recovery mode (before join to topology).
     */
    public boolean recoveryMode();

    /**
     * @return Local continuous tasks processor.
     */
    public DurableBackgroundTasksProcessor durableBackgroundTask();

    /**
     * Gets Performance statistics processor.
     *
     * @return Performance statistics processor.
     */
    public PerformanceStatisticsProcessor performanceStatistics();

    /**
     * Executor that is in charge of processing user async continuations.
     *
     * @return Executor that is in charge of processing user async continuations.
     */
    public Executor getAsyncContinuationExecutor();

    /**
     * @return Marshaller instance.
     */
    public BinaryMarshaller marshaller();
}
