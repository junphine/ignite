

import DFLT_DIALECTS from 'app/data/dialects.json';

import { EmptyBean, Bean } from './Beans';

import IgniteClusterDefaults from './defaults/Cluster.service';
import IgniteEventGroups from './defaults/Event-groups.service';
import IgniteCacheDefaults from './defaults/Cache.service';
import IgniteIGFSDefaults from './defaults/IGFS.service';
import ArtifactVersionChecker from './ArtifactVersionChecker.service';

import JavaTypes from '../../../services/JavaTypes.service';
import VersionService from 'app/services/Version.service';

import _ from 'lodash';
import isNil from 'lodash/isNil';
import {nonNil, nonEmpty} from 'app/utils/lodashMixins';

const clusterDflts = new IgniteClusterDefaults();
const cacheDflts = new IgniteCacheDefaults();
const igfsDflts = new IgniteIGFSDefaults();
const javaTypes = new JavaTypes();
const versionService = new VersionService();

// Pom dependency information.
import POM_DEPENDENCIES from 'app/data/pom-dependencies.json';

export default class IgniteConfigurationGenerator {
    static eventGrps = new IgniteEventGroups();

    static igniteConfigurationBean(cluster) {
        return new Bean('org.apache.ignite.configuration.IgniteConfiguration', 'cfg', cluster, clusterDflts);
    }

    static igfsConfigurationBean(igfs) {
        return new Bean('org.apache.ignite.configuration.FileSystemConfiguration', 'igfs', igfs, igfsDflts);
    }

    static cacheConfigurationBean(cache) {
        return new Bean('org.apache.ignite.configuration.CacheConfiguration', 'ccfg', cache, cacheDflts);
    }

    static domainConfigurationBean(domain) {
        return new Bean('org.apache.ignite.cache.QueryEntity', 'qryEntity', domain, cacheDflts);
    }

    static domainJdbcTypeBean(domain) {
        return new Bean('org.apache.ignite.cache.store.jdbc.JdbcType', 'type', domain);
    }

    static discoveryConfigurationBean(discovery) {
        return new Bean('org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi', 'discovery', discovery, clusterDflts.discovery);
    }

    static discoveryZKConfigurationBean(discovery) {
        return new Bean('org.apache.ignite.spi.discovery.zk.ZookeeperDiscoverySpi', 'discovery', discovery, clusterDflts.discovery.ZooKeeper);
    }

    /**
     * Function to generate ignite configuration.
     *
     * @param {Object} cluster Cluster to process.
     * @param {Object} targetVer Target version of configuration.
     * @param {Boolean} client Is client configuration.
     * @return {Bean} Generated ignite configuration.
     */
    static igniteConfiguration(cluster, targetVer, client) {
        const available = versionService.since.bind(versionService, targetVer.ignite);

        const cfg = this.igniteConfigurationBean(cluster);

        this.clusterGeneral(cluster, available, cfg, client);
        this.clusterAtomics(cluster.atomicConfiguration, available, cfg);
        this.clusterBinary(cluster.binaryConfiguration, cfg);
        this.clusterCacheKeyConfiguration(cluster.cacheKeyConfiguration, cfg);
        this.clusterCheckpoint(cluster, available, cluster.caches, cfg);

        if (available('2.3.0'))
            this.clusterClientConnector(cluster, available, cfg);

        this.clusterCollision(cluster.collision, cfg);
        this.clusterCommunication(cluster, available, cfg);
        this.clusterConnector(cluster.connector, cfg);

        // Since ignite 2.3
        if (available('2.3.0'))
            this.clusterDataStorageConfiguration(cluster, available, cfg);

        this.clusterDeployment(cluster, available, cfg);
        this.clusterEncryption(cluster.encryptionSpi, available, cfg);
        this.clusterEvents(cluster, available, cfg);
        this.clusterFailover(cluster, available, cfg);

        this.clusterLoadBalancing(cluster, cfg);        
        this.clusterMarshaller(cluster, available, cfg);
       

        this.clusterMisc(cluster, available, cfg);
        this.clusterMetrics(cluster, available, cfg);        

        this.clusterRebalance(cluster, available, cfg);
        this.clusterServiceConfiguration(cluster.serviceConfigurations, cluster.caches, cfg);
        this.clusterSsl(cluster, available, cfg);        

        this.clusterPools(cluster, available, cfg);
        this.clusterTime(cluster, available, cfg);
        this.clusterTransactions(cluster.transactionConfiguration, available, cfg);
        this.clusterUserAttributes(cluster, cfg);

        this.clusterCaches(cluster, cluster.caches, available, targetVer, client, cfg);

        if (!client)
            this.clusterIgfss(cluster.igfss, available, cfg);

        return cfg;
    }

    static dialectClsName(dialect) {
        return DFLT_DIALECTS[dialect] || 'Unknown database: ' + (dialect || 'Choose JDBC dialect');
    }

    // use jndi datasource
    static dataSourceBean(id, dialect, available, storeDeps, implementationVersion) {
    	let dsBean = new Bean('org.springframework.jndi.JndiObjectFactoryBean', id, {'jndiName':'java:jdbc/'+id});
    	dsBean.stringProperty("jndiName");    	
    	return dsBean;
    }
    // use datasource pool
    static dataSourceBeanNative(id, dialect, available, storeDeps, implementationVersion) {
        let dsBean;

        switch (dialect) {
            case 'Generic':
            case 'Hive':
                dsBean = new Bean('com.mchange.v2.c3p0.ComboPooledDataSource', id, {})
                    .property('jdbcUrl', `${id}.jdbc.url`, 'jdbc:your_database');

                break;

            case 'Oracle':
                dsBean = new Bean('oracle.jdbc.pool.OracleDataSource', id, {})
                    .property('URL', `${id}.jdbc.url`, 'jdbc:oracle:thin:@[host]:[port]:[database]');

                break;

            case 'DB2':
                dsBean = new Bean('com.ibm.db2.jcc.DB2DataSource', id, {})
                    .property('serverName', `${id}.jdbc.server_name`, 'YOUR_DATABASE_SERVER_NAME')
                    .propertyInt('portNumber', `${id}.jdbc.port_number`, 'YOUR_JDBC_PORT_NUMBER')
                    .property('databaseName', `${id}.jdbc.database_name`, 'YOUR_DATABASE_NAME')
                    .propertyInt('driverType', `${id}.jdbc.driver_type`, 'YOUR_JDBC_DRIVER_TYPE');

                break;

            case 'SQLServer':
                dsBean = new Bean('com.microsoft.sqlserver.jdbc.SQLServerDataSource', id, {})
                    .property('URL', `${id}.jdbc.url`, 'jdbc:sqlserver://[host]:[port][;databaseName=database]');

                break;

            case 'MySQL':
                const dep = storeDeps
                    ? _.find(storeDeps, (d) => d.name === dialect)
                    : _.first(ArtifactVersionChecker.latestVersions(this._getArtifact({dialect, implementationVersion}, available)));

                const ver = parseInt(dep.version.split('.')[0], 10);

                dsBean = new Bean(ver < 8 ? 'com.mysql.jdbc.jdbc2.optional.MysqlDataSource' : 'com.mysql.cj.jdbc.MysqlDataSource', id, {})
                    .property('URL', `${id}.jdbc.url`, 'jdbc:mysql://[host]:[port]/[database]');

                break;

            case 'PostgreSQL':
                dsBean = new Bean('org.postgresql.ds.PGPoolingDataSource', id, {})
                    .property('url', `${id}.jdbc.url`, 'jdbc:postgresql://[host]:[port]/[database]');

                break;

            case 'H2':
                dsBean = new Bean('org.h2.jdbcx.JdbcDataSource', id, {})
                    .property('URL', `${id}.jdbc.url`, 'jdbc:h2:tcp://[host]/[database]');

                break;
            default:
            	dsBean = new Bean('com.mchange.v2.c3p0.ComboPooledDataSource', id, {})
            		.property('jdbcUrl', `${id}.jdbc.url`, 'jdbc:your_database');
        }

        if (dsBean) {
            dsBean.property('user', `${id}.jdbc.username`, 'YOUR_USER_NAME')
                .property('password', `${id}.jdbc.password`, 'YOUR_PASSWORD');
        }

        return dsBean;
    }
    
    static escapeClusterName(name) {
        return name.replace(/[\\\/*\"\[\],\.:;|=<>?]/g, '-').replace(/ /g, '_');
    }

    // Generate general section.
    static clusterGeneral(cluster, available, cfg = this.igniteConfigurationBean(cluster), client = false) {
        if (client)
            cfg.prop('boolean', 'clientMode', true);

        
        cfg.stringProperty('name', 'igniteInstanceName',(name) => IgniteConfigurationGenerator.escapeClusterName(name));
        
        cfg.stringProperty('localHost');

        if (isNil(cluster.discovery))
            return cfg;

        let discovery = IgniteConfigurationGenerator.discoveryConfigurationBean(cluster.discovery);

        let ipFinder = null;

        let zkDiscovery = null;

        switch (discovery.valueOf('kind')) {
            case 'Isolated':
                discovery = new Bean('org.apache.ignite.spi.discovery.isolated.IsolatedDiscoverySpi', 'discovery', discovery, clusterDflts.discovery);
                
                break;
            case 'Vm':
                ipFinder = new Bean('org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder',
                    'ipFinder', cluster.discovery.Vm, clusterDflts.discovery.Vm);

                ipFinder.collectionProperty('addrs', 'addresses', cluster.discovery.Vm.addresses);

                break;
            case 'Multicast':
                ipFinder = new Bean('org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder',
                    'ipFinder', cluster.discovery.Multicast, clusterDflts.discovery.Multicast);

                ipFinder.stringProperty('multicastGroup')
                    .intProperty('multicastPort')
                    .intProperty('responseWaitTime')
                    .intProperty('addressRequestAttempts')
                    .stringProperty('localAddress')
                    .collectionProperty('addrs', 'addresses', cluster.discovery.Multicast.addresses);

                break;

            case 'WebConsoleServer':
                ipFinder = new Bean('org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryWebConsoleServerIpFinder',
                    'ipFinder', cluster.discovery.WebConsoleServer, clusterDflts.discovery.WebConsoleServer);

                ipFinder.stringProperty('masterUrl')
                    .intProperty('responseWaitTime')
                    .stringProperty('accountToken');

                break;

            case 'ZooKeeper':
                
                zkDiscovery = IgniteConfigurationGenerator.discoveryZKConfigurationBean(cluster.discovery.ZooKeeper);
                zkDiscovery.stringProperty('zkConnectionString');
                zkDiscovery.pathProperty('basePath','zkRootPath');
                
                break;

            case 'ZooKeeperIpFinder':
                const src = cluster.discovery.ZooKeeper;
                const dflt = clusterDflts.discovery.ZooKeeper;

                ipFinder = new Bean('org.apache.ignite.spi.discovery.tcp.ipfinder.zk.TcpDiscoveryZookeeperIpFinder',
                    'ipFinder', src, dflt);

                ipFinder.emptyBeanProperty('curator')
                    .stringProperty('zkConnectionString');

                const kind = _.get(src, 'retryPolicy.kind');

                if (kind) {
                    const policy = src.retryPolicy;

                    let retryPolicyBean;

                    switch (kind) {
                        case 'ExponentialBackoff':
                            retryPolicyBean = new Bean('org.apache.curator.retry.ExponentialBackoffRetry', null,
                                policy.ExponentialBackoff, dflt.ExponentialBackoff)
                                .intConstructorArgument('baseSleepTimeMs')
                                .intConstructorArgument('maxRetries')
                                .intConstructorArgument('maxSleepMs');

                            break;
                        case 'BoundedExponentialBackoff':
                            retryPolicyBean = new Bean('org.apache.curator.retry.BoundedExponentialBackoffRetry',
                                null, policy.BoundedExponentialBackoff, dflt.BoundedExponentialBackoffRetry)
                                .intConstructorArgument('baseSleepTimeMs')
                                .intConstructorArgument('maxSleepTimeMs')
                                .intConstructorArgument('maxRetries');

                            break;
                        case 'UntilElapsed':
                            retryPolicyBean = new Bean('org.apache.curator.retry.RetryUntilElapsed', null,
                                policy.UntilElapsed, dflt.UntilElapsed)
                                .intConstructorArgument('maxElapsedTimeMs')
                                .intConstructorArgument('sleepMsBetweenRetries');

                            break;

                        case 'NTimes':
                            retryPolicyBean = new Bean('org.apache.curator.retry.RetryNTimes', null,
                                policy.NTimes, dflt.NTimes)
                                .intConstructorArgument('n')
                                .intConstructorArgument('sleepMsBetweenRetries');

                            break;
                        case 'OneTime':
                            retryPolicyBean = new Bean('org.apache.curator.retry.RetryOneTime', null,
                                policy.OneTime, dflt.OneTime)
                                .intConstructorArgument('sleepMsBetweenRetry');

                            break;
                        case 'Forever':
                            retryPolicyBean = new Bean('org.apache.curator.retry.RetryForever', null,
                                policy.Forever, dflt.Forever)
                                .intConstructorArgument('retryIntervalMs');

                            break;
                        case 'Custom':
                            const className = _.get(policy, 'Custom.className');

                            if (nonEmpty(className))
                                retryPolicyBean = new EmptyBean(className);

                            break;
                        default:
                            // No-op.
                    }

                    if (retryPolicyBean)
                        ipFinder.beanProperty('retryPolicy', retryPolicyBean);
                }

                ipFinder.pathProperty('basePath')
                    .stringProperty('serviceName')
                    .boolProperty('allowDuplicateRegistrations');

                break;

            case 'Kubernetes':
                ipFinder = new Bean('org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder',
                    'ipFinder', cluster.discovery.Kubernetes, clusterDflts.discovery.Kubernetes);

                ipFinder.stringProperty('serviceName')
                    .stringProperty('namespace')
                    .stringProperty('masterUrl')
                    .pathProperty('accountToken');

                break;

            default:
                // No-op.
        }

        if (zkDiscovery){
            this.clusterDiscovery(cluster.discovery, available, cfg, zkDiscovery);
            return cfg;
        }

        if (ipFinder)
            discovery.beanProperty('ipFinder', ipFinder);

        this.clusterDiscovery(cluster.discovery, available, cfg, discovery);

        return cfg;
    }
    

    /**
     * Get dependency artifact for specified datasource.
     *
     * @param source Datasource.
     * @param available Function to check version availability.
     * @return {Array<{{name: String, version: String}}>} Array of accordance datasource artifacts.
     */
    static _getArtifact(source, available) {
        const deps = _.get(POM_DEPENDENCIES, source.dialect);

        if (!deps)
            return [];

        const extractVersion = (version) => {
            return _.isArray(version) ? _.find(version, (v) => available(v.range)).version : version;
        };

        return _.map(_.castArray(deps), ({version}) => {
            return ({
                name: source.dialect,
                version: source.implementationVersion || extractVersion(version)
            });
        });
    }

    static clusterCaches(cluster, caches, available, targetVer, client, cfg = this.igniteConfigurationBean(cluster)) {
        const usedDataSourceVersions = [];

        if (cluster.discovery.kind === 'Jdbc')
            usedDataSourceVersions.push(...this._getArtifact(cluster.discovery.Jdbc, available));

        _.forEach(cluster.checkpointSpi, (spi) => {
            if (spi.kind === 'JDBC')
                usedDataSourceVersions.push(...this._getArtifact(spi.JDBC, available));
        });

        _.forEach(caches, (cache) => {
            if (_.get(cache, 'cacheStoreFactory.kind'))
                usedDataSourceVersions.push(...this._getArtifact(cache.cacheStoreFactory[cache.cacheStoreFactory.kind], available));
        });

        const useDeps = _.uniqWith(ArtifactVersionChecker.latestVersions(usedDataSourceVersions), _.isEqual);

        const ccfgs = _.map(caches, (cache) => this.cacheConfiguration(cache, available, targetVer, useDeps));

        cfg.varArgProperty('ccfgs', 'cacheConfiguration', ccfgs, 'org.apache.ignite.configuration.CacheConfiguration');

        return cfg;
    }

    // Generate atomics group.
    static clusterAtomics(atomics, available, cfg = this.igniteConfigurationBean()) {

        const acfg = new Bean('org.apache.ignite.configuration.AtomicConfiguration', 'atomicCfg',
            atomics, clusterDflts.atomics);

        acfg.enumProperty('cacheMode')
            .intProperty('atomicSequenceReserveSize');

        if (acfg.valueOf('cacheMode') === 'PARTITIONED')
            acfg.intProperty('backups');

        if (nonNil(atomics))
            this.affinity(atomics.affinity, acfg);

 
        acfg.stringProperty('groupName');

        if (acfg.isEmpty())
            return cfg;

        cfg.beanProperty('atomicConfiguration', acfg);

        return cfg;
    }

    // Generate binary group.
    static clusterBinary(binary, cfg = this.igniteConfigurationBean()) {
        const binaryCfg = new Bean('org.apache.ignite.configuration.BinaryConfiguration', 'binaryCfg',
            binary, clusterDflts.binary);

        binaryCfg.emptyBeanProperty('idMapper')
            .emptyBeanProperty('nameMapper')
            .emptyBeanProperty('serializer');

        const typeCfgs = [];

        _.forEach(binary.typeConfigurations, (type) => {
            const typeCfg = new Bean('org.apache.ignite.binary.BinaryTypeConfiguration',
                javaTypes.toJavaName('binaryType', type.typeName), type, clusterDflts.binary.typeConfigurations);

            typeCfg.stringProperty('typeName')
                .emptyBeanProperty('idMapper')
                .emptyBeanProperty('nameMapper')
                .emptyBeanProperty('serializer')
                .boolProperty('enum')
                .mapProperty('enumValues', _.map(type.enumValues, (v, idx) => ({name: v, value: idx})), 'enumValues');

            if (typeCfg.nonEmpty())
                typeCfgs.push(typeCfg);
        });

        binaryCfg.collectionProperty('types', 'typeConfigurations', typeCfgs, 'org.apache.ignite.binary.BinaryTypeConfiguration')
            .boolProperty('compactFooter');

        if (binaryCfg.isEmpty())
            return cfg;

        cfg.beanProperty('binaryConfiguration', binaryCfg);

        return cfg;
    }

    // Generate cache key configurations.
    static clusterCacheKeyConfiguration(keyCfgs, cfg = this.igniteConfigurationBean()) {
        const items = _.reduce(keyCfgs, (acc, keyCfg) => {
            if (keyCfg.typeName && keyCfg.affinityKeyFieldName) {
                acc.push(new Bean('org.apache.ignite.cache.CacheKeyConfiguration', null, keyCfg)
                    .stringConstructorArgument('typeName')
                    .stringConstructorArgument('affinityKeyFieldName'));
            }

            return acc;
        }, []);

        if (_.isEmpty(items))
            return cfg;

        cfg.arrayProperty('cacheKeyConfiguration', 'cacheKeyConfiguration', items,
            'org.apache.ignite.cache.CacheKeyConfiguration');

        return cfg;
    }

    // Generate checkpoint configurations.
    static clusterCheckpoint(cluster, available, caches, cfg = this.igniteConfigurationBean()) {
        const cfgs = _.filter(_.map(cluster.checkpointSpi, (spi) => {
            switch (_.get(spi, 'kind')) {
                case 'FS':
                    const fsBean = new Bean('org.apache.ignite.spi.checkpoint.sharedfs.SharedFsCheckpointSpi',
                        'checkpointSpiFs', spi.FS);

                    fsBean.collectionProperty('directoryPaths', 'directoryPaths', _.get(spi, 'FS.directoryPaths'))
                        .emptyBeanProperty('checkpointListener');

                    return fsBean;

                case 'Cache':
                    const cacheBean = new Bean('org.apache.ignite.spi.checkpoint.cache.CacheCheckpointSpi',
                        'checkpointSpiCache', spi.Cache);

                    const curCache = _.get(spi, 'Cache.cache');

                    const cache = _.find(caches, (c) => curCache && (c.id === curCache || _.get(c, 'cache.id') === curCache));

                    if (cache)
                        cacheBean.prop('java.lang.String', 'cacheName', cache.name || cache.cache.name);

                    cacheBean.stringProperty('cacheName')
                        .emptyBeanProperty('checkpointListener');

                    return cacheBean;

                case 'S3':
                    const s3Bean = new Bean('org.apache.ignite.spi.checkpoint.s3.S3CheckpointSpi',
                        'checkpointSpiS3', spi.S3, clusterDflts.checkpointSpi.S3);

                    let credentialsBean = null;

                    switch (_.get(spi.S3, 'awsCredentials.kind')) {
                        case 'Basic':
                            credentialsBean = new Bean('com.amazonaws.auth.BasicAWSCredentials', 'awsCredentials', {});

                            credentialsBean.propertyConstructorArgument('checkpoint.s3.credentials.accessKey', 'YOUR_S3_ACCESS_KEY')
                                .propertyConstructorArgument('checkpoint.s3.credentials.secretKey', 'YOUR_S3_SECRET_KEY');

                            break;

                        case 'Properties':
                            credentialsBean = new Bean('com.amazonaws.auth.PropertiesCredentials', 'awsCredentials', {});

                            const fileBean = new Bean('java.io.File', '', spi.S3.awsCredentials.Properties)
                                .pathConstructorArgument('path');

                            if (fileBean.nonEmpty())
                                credentialsBean.beanConstructorArgument('file', fileBean);

                            break;

                        case 'Anonymous':
                            credentialsBean = new Bean('com.amazonaws.auth.AnonymousAWSCredentials', 'awsCredentials', {});

                            break;

                        case 'BasicSession':
                            credentialsBean = new Bean('com.amazonaws.auth.BasicSessionCredentials', 'awsCredentials', {});

                            // TODO 2054 Arguments in one line is very long string.
                            credentialsBean.propertyConstructorArgument('checkpoint.s3.credentials.accessKey')
                                .propertyConstructorArgument('checkpoint.s3.credentials.secretKey')
                                .propertyConstructorArgument('checkpoint.s3.credentials.sessionToken');

                            break;

                        case 'Custom':
                            const className = _.get(spi.S3.awsCredentials, 'Custom.className');

                            if (className)
                                credentialsBean = new Bean(className, 'awsCredentials', {});

                            break;

                        default:
                            break;
                    }

                    if (credentialsBean)
                        s3Bean.beanProperty('awsCredentials', credentialsBean);

                    s3Bean.stringProperty('bucketNameSuffix');

                    if (available('2.4.0')) {
                        s3Bean.stringProperty('bucketEndpoint')
                            .stringProperty('SSEAlgorithm');
                    }

                    const clientBean = new Bean('com.amazonaws.ClientConfiguration', 'clientCfg', spi.S3.clientConfiguration,
                        clusterDflts.checkpointSpi.S3.clientConfiguration);

                    clientBean.enumProperty('protocol')
                        .intProperty('maxConnections')
                        .stringProperty('userAgentPrefix')
                        .stringProperty('userAgentSuffix');

                    const locAddr = new Bean('java.net.InetAddress', '', spi.S3.clientConfiguration)
                        .factoryMethod('getByName')
                        .stringConstructorArgument('localAddress');

                    if (locAddr.nonEmpty())
                        clientBean.beanProperty('localAddress', locAddr);

                    clientBean.stringProperty('proxyHost')
                        .intProperty('proxyPort')
                        .stringProperty('proxyUsername');

                    const userName = clientBean.valueOf('proxyUsername');

                    if (userName)
                        clientBean.property('proxyPassword', `checkpoint.s3.proxy.${userName}.password`);

                    clientBean.stringProperty('proxyDomain')
                        .stringProperty('proxyWorkstation')
                        .stringProperty('nonProxyHosts');

                    const retryPolicy = spi.S3.clientConfiguration.retryPolicy;

                    if (retryPolicy) {
                        const kind = retryPolicy.kind;

                        const policy = retryPolicy[kind];

                        let retryBean;

                        switch (kind) {
                            case 'Default':
                                retryBean = new Bean('com.amazonaws.retry.RetryPolicy', 'retryPolicy', {
                                    retryCondition: 'DEFAULT_RETRY_CONDITION',
                                    backoffStrategy: 'DEFAULT_BACKOFF_STRATEGY',
                                    maxErrorRetry: 'DEFAULT_MAX_ERROR_RETRY',
                                    honorMaxErrorRetryInClientConfig: true
                                }, clusterDflts.checkpointSpi.S3.clientConfiguration.retryPolicy);

                                retryBean.constantConstructorArgument('retryCondition')
                                    .constantConstructorArgument('backoffStrategy')
                                    .constantConstructorArgument('maxErrorRetry')
                                    .constructorArgument('java.lang.Boolean', retryBean.valueOf('honorMaxErrorRetryInClientConfig'));

                                break;

                            case 'DefaultMaxRetries':
                                retryBean = new Bean('com.amazonaws.retry.RetryPolicy', 'retryPolicy', {
                                    retryCondition: 'DEFAULT_RETRY_CONDITION',
                                    backoffStrategy: 'DEFAULT_BACKOFF_STRATEGY',
                                    maxErrorRetry: _.get(policy, 'maxErrorRetry') || -1,
                                    honorMaxErrorRetryInClientConfig: false
                                }, clusterDflts.checkpointSpi.S3.clientConfiguration.retryPolicy);

                                retryBean.constantConstructorArgument('retryCondition')
                                    .constantConstructorArgument('backoffStrategy')
                                    .constructorArgument('java.lang.Integer', retryBean.valueOf('maxErrorRetry'))
                                    .constructorArgument('java.lang.Boolean', retryBean.valueOf('honorMaxErrorRetryInClientConfig'));

                                break;

                            case 'DynamoDB':
                                retryBean = new Bean('com.amazonaws.retry.RetryPolicy', 'retryPolicy', {
                                    retryCondition: 'DEFAULT_RETRY_CONDITION',
                                    backoffStrategy: 'DYNAMODB_DEFAULT_BACKOFF_STRATEGY',
                                    maxErrorRetry: 'DYNAMODB_DEFAULT_MAX_ERROR_RETRY',
                                    honorMaxErrorRetryInClientConfig: true
                                }, clusterDflts.checkpointSpi.S3.clientConfiguration.retryPolicy);

                                retryBean.constantConstructorArgument('retryCondition')
                                    .constantConstructorArgument('backoffStrategy')
                                    .constantConstructorArgument('maxErrorRetry')
                                    .constructorArgument('java.lang.Boolean', retryBean.valueOf('honorMaxErrorRetryInClientConfig'));

                                break;

                            case 'DynamoDBMaxRetries':
                                retryBean = new Bean('com.amazonaws.retry.RetryPolicy', 'retryPolicy', {
                                    retryCondition: 'DEFAULT_RETRY_CONDITION',
                                    backoffStrategy: 'DYNAMODB_DEFAULT_BACKOFF_STRATEGY',
                                    maxErrorRetry: _.get(policy, 'maxErrorRetry') || -1,
                                    honorMaxErrorRetryInClientConfig: false
                                }, clusterDflts.checkpointSpi.S3.clientConfiguration.retryPolicy);

                                retryBean.constantConstructorArgument('retryCondition')
                                    .constantConstructorArgument('backoffStrategy')
                                    .constructorArgument('java.lang.Integer', retryBean.valueOf('maxErrorRetry'))
                                    .constructorArgument('java.lang.Boolean', retryBean.valueOf('honorMaxErrorRetryInClientConfig'));

                                break;

                            case 'Custom':
                                retryBean = new Bean('com.amazonaws.retry.RetryPolicy', 'retryPolicy', policy,
                                    clusterDflts.checkpointSpi.S3.clientConfiguration.retryPolicy);

                                retryBean.beanConstructorArgument('retryCondition', retryBean.valueOf('retryCondition') ? new EmptyBean(retryBean.valueOf('retryCondition')) : null)
                                    .beanConstructorArgument('backoffStrategy', retryBean.valueOf('backoffStrategy') ? new EmptyBean(retryBean.valueOf('backoffStrategy')) : null)
                                    .constructorArgument('java.lang.Integer', retryBean.valueOf('maxErrorRetry'))
                                    .constructorArgument('java.lang.Boolean', retryBean.valueOf('honorMaxErrorRetryInClientConfig'));

                                break;

                            default:
                                break;
                        }

                        if (retryBean)
                            clientBean.beanProperty('retryPolicy', retryBean);
                    }

                    clientBean.intProperty('maxErrorRetry')
                        .intProperty('socketTimeout')
                        .intProperty('connectionTimeout')
                        .intProperty('requestTimeout')
                        .stringProperty('signerOverride')
                        .longProperty('connectionTTL')
                        .longProperty('connectionMaxIdleMillis')
                        .emptyBeanProperty('dnsResolver')
                        .intProperty('responseMetadataCacheSize')
                        .emptyBeanProperty('secureRandom')
                        .intProperty('clientExecutionTimeout')
                        .boolProperty('useReaper')
                        .boolProperty('cacheResponseMetadata')
                        .boolProperty('useExpectContinue')
                        .boolProperty('useThrottleRetries')
                        .boolProperty('useGzip')
                        .boolProperty('preemptiveBasicProxyAuth')
                        .boolProperty('useTcpKeepAlive');

                    if (clientBean.nonEmpty())
                        s3Bean.beanProperty('clientConfiguration', clientBean);

                    s3Bean.emptyBeanProperty('checkpointListener');

                    return s3Bean;

                case 'JDBC':
                    const jdbcBean = new Bean('org.apache.ignite.spi.checkpoint.jdbc.JdbcCheckpointSpi',
                        'checkpointSpiJdbc', spi.JDBC, clusterDflts.checkpointSpi.JDBC);

                    const id = jdbcBean.valueOf('dataSourceBean');
                    const dialect = _.get(spi.JDBC, 'dialect');

                    jdbcBean.dataSource(id, 'dataSource', this.dataSourceBean(id, dialect, available));

                    if (!_.isEmpty(jdbcBean.valueOf('user'))) {
                        jdbcBean.stringProperty('user')
                            .property('pwd', `checkpoint.${jdbcBean.valueOf('dataSourceBean')}.${jdbcBean.valueOf('user')}.jdbc.password`, 'YOUR_PASSWORD');
                    }

                    jdbcBean.stringProperty('checkpointTableName')
                        .stringProperty('keyFieldName')
                        .stringProperty('keyFieldType')
                        .stringProperty('valueFieldName')
                        .stringProperty('valueFieldType')
                        .stringProperty('expireDateFieldName')
                        .stringProperty('expireDateFieldType')
                        .intProperty('numberOfRetries')
                        .emptyBeanProperty('checkpointListener');

                    return jdbcBean;

                case 'Custom':
                    const clsName = _.get(spi, 'Custom.className');

                    if (clsName)
                        return new Bean(clsName, 'checkpointSpiCustom', spi.Cache);

                    return null;

                default:
                    return null;
            }
        }), (checkpointBean) => nonNil(checkpointBean));

        cfg.arrayProperty('checkpointSpi', 'checkpointSpi', cfgs, 'org.apache.ignite.spi.checkpoint.CheckpointSpi');

        return cfg;
    }

    // Generate cluster query group.
    static clusterClientConnector(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {        

        cfg.longProperty('longQueryWarningTimeout');

        if (_.get(cluster, 'clientConnectorConfiguration.enabled') !== true)
            return cfg;

        const bean = new Bean('org.apache.ignite.configuration.ClientConnectorConfiguration', 'cliConnCfg',
            cluster.clientConnectorConfiguration, clusterDflts.clientConnectorConfiguration);

        bean.stringProperty('host')
            .intProperty('port')
            .intProperty('portRange')
            .intProperty('socketSendBufferSize')
            .intProperty('socketReceiveBufferSize')
            .intProperty('maxOpenCursorsPerConnection')
            .intProperty('threadPoolSize')
            .boolProperty('tcpNoDelay');

        if (available('2.4.0')) {
            bean.longProperty('idleTimeout')
                .boolProperty('jdbcEnabled')
                .boolProperty('odbcEnabled')
                .boolProperty('thinClientEnabled');
        }

        if (available('2.5.0')) {
            bean.longProperty('handshakeTimeout')
                .boolProperty('sslEnabled')
                .boolProperty('sslClientAuth')
                .boolProperty('useIgniteSslContextFactory')
                .emptyBeanProperty('sslContextFactory');
        }

        cfg.beanProperty('clientConnectorConfiguration', bean);

        return cfg;
    }

    // Generate collision group.
    static clusterCollision(collision, cfg = this.igniteConfigurationBean()) {
        let colSpi;

        switch (_.get(collision, 'kind')) {
            case 'JobStealing':
                colSpi = new Bean('org.apache.ignite.spi.collision.jobstealing.JobStealingCollisionSpi',
                    'colSpi', collision.JobStealing, clusterDflts.collision.JobStealing);

                colSpi.intProperty('activeJobsThreshold')
                    .intProperty('waitJobsThreshold')
                    .longProperty('messageExpireTime')
                    .intProperty('maximumStealingAttempts')
                    .boolProperty('stealingEnabled')
                    .emptyBeanProperty('externalCollisionListener')
                    .mapProperty('stealingAttrs', 'stealingAttributes');

                break;
            case 'FifoQueue':
                colSpi = new Bean('org.apache.ignite.spi.collision.fifoqueue.FifoQueueCollisionSpi',
                    'colSpi', collision.FifoQueue, clusterDflts.collision.FifoQueue);

                colSpi.intProperty('parallelJobsNumber')
                    .intProperty('waitingJobsNumber');

                break;
            case 'PriorityQueue':
                colSpi = new Bean('org.apache.ignite.spi.collision.priorityqueue.PriorityQueueCollisionSpi',
                    'colSpi', collision.PriorityQueue, clusterDflts.collision.PriorityQueue);

                colSpi.intProperty('parallelJobsNumber')
                    .intProperty('waitingJobsNumber')
                    .stringProperty('priorityAttributeKey')
                    .stringProperty('jobPriorityAttributeKey')
                    .intProperty('defaultPriority')
                    .intProperty('starvationIncrement')
                    .boolProperty('starvationPreventionEnabled');

                break;
            case 'Custom':
                if (nonNil(_.get(collision, 'Custom.class')))
                    colSpi = new EmptyBean(collision.Custom.class);

                break;
            default:
                return cfg;
        }

        if (nonNil(colSpi))
            cfg.beanProperty('collisionSpi', colSpi);

        return cfg;
    }

    // Generate communication group.
    static clusterCommunication(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        const commSpi = new Bean('org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi', 'communicationSpi',
            cluster.communication, clusterDflts.communication);

        commSpi.emptyBeanProperty('listener')
            .stringProperty('localAddress')
            .intProperty('localPort')
            .intProperty('localPortRange')            
            .intProperty('directBuffer')
            .intProperty('directSendBuffer')
            .longProperty('idleConnectionTimeout')
            .longProperty('connectTimeout')
            .longProperty('maxConnectTimeout')
            .intProperty('reconnectCount')
            .intProperty('socketSendBuffer')
            .intProperty('socketReceiveBuffer')
            .intProperty('messageQueueLimit')
            .intProperty('slowClientQueueLimit')
            .intProperty('tcpNoDelay')
            .intProperty('ackSendThreshold')
            .intProperty('unacknowledgedMessagesBufferSize')
            .longProperty('socketWriteTimeout')
            .intProperty('selectorsCount')
            .longProperty('selectorSpins')
            .intProperty('connectionsPerNode')
            .emptyBeanProperty('addressResolver')
            .boolProperty('usePairedConnections');

        if (available('2.3.0'))
            commSpi.boolProperty('filterReachableAddresses');

        if (commSpi.nonEmpty())
            cfg.beanProperty('communicationSpi', commSpi);

        cfg.longProperty('networkTimeout')
            .longProperty('networkSendRetryDelay')
            .intProperty('networkSendRetryCount');

        if (available('2.8.0'))
            cfg.intProperty('networkCompressionLevel');

        if (available('2.5.0'))
            cfg.emptyBeanProperty('communicationFailureResolver');

        return cfg;
    }

    // Generate REST access configuration.
    static clusterConnector(connector, cfg = this.igniteConfigurationBean()) {
        const connCfg = new Bean('org.apache.ignite.configuration.ConnectorConfiguration',
            'connectorConfiguration', connector, clusterDflts.connector);

        if (connCfg.valueOf('enabled')) {
            connCfg.pathProperty('jettyPath')
                .stringProperty('host')
                .intProperty('port')
                .intProperty('portRange')
                .longProperty('idleTimeout')
                .longProperty('idleQueryCursorTimeout')
                .longProperty('idleQueryCursorCheckFrequency')
                .intProperty('receiveBufferSize')
                .intProperty('sendBufferSize')
                .intProperty('sendQueueLimit')
                .intProperty('directBuffer')
                .intProperty('noDelay')
                .intProperty('selectorCount')
                .intProperty('threadPoolSize')
                .emptyBeanProperty('messageInterceptor')
                .stringProperty('secretKey');

            if (connCfg.valueOf('sslEnabled')) {
                connCfg.intProperty('sslClientAuth')
                    .emptyBeanProperty('sslFactory');
            }

            if (connCfg.nonEmpty())
                cfg.beanProperty('connectorConfiguration', connCfg);
        }

        return cfg;
    }

    // Generate deployment group.
    static clusterDeployment(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        cfg.enumProperty('deploymentMode')
            .boolProperty('peerClassLoadingEnabled');

        if (cfg.valueOf('peerClassLoadingEnabled')) {
            cfg.intProperty('peerClassLoadingMissedResourcesCacheSize')
                .intProperty('peerClassLoadingThreadPoolSize')
                .varArgProperty('p2pLocClsPathExcl', 'peerClassLoadingLocalClassPathExclude',
                    cluster.peerClassLoadingLocalClassPathExclude);
        }

        cfg.emptyBeanProperty('classLoader');

        let deploymentBean = null;

        switch (_.get(cluster, 'deploymentSpi.kind')) {
            case 'URI':
                const uriDeployment = cluster.deploymentSpi.URI;

                deploymentBean = new Bean('org.apache.ignite.spi.deployment.uri.UriDeploymentSpi', 'deploymentSpi', uriDeployment);

                const scanners = _.map(uriDeployment.scanners, (scanner) => new EmptyBean(scanner));

                deploymentBean.collectionProperty('uriList', 'uriList', uriDeployment.uriList)
                    .stringProperty('temporaryDirectoryPath')
                    .varArgProperty('scanners', 'scanners', scanners,
                        'org.apache.ignite.spi.deployment.uri.scanners.UriDeploymentScanner')
                    .emptyBeanProperty('listener')
                    .boolProperty('checkMd5')
                    .boolProperty('encodeUri');

                cfg.beanProperty('deploymentSpi', deploymentBean);

                break;

            case 'Local':
                deploymentBean = new Bean('org.apache.ignite.spi.deployment.local.LocalDeploymentSpi', 'deploymentSpi', cluster.deploymentSpi.Local);

                deploymentBean.emptyBeanProperty('listener');

                cfg.beanProperty('deploymentSpi', deploymentBean);

                break;

            case 'Custom':
                cfg.emptyBeanProperty('deploymentSpi.Custom.className');

                break;

            default:
                // No-op.
        }

        return cfg;
    }

    // Generate discovery group.
    static clusterDiscovery(discovery, available, cfg = this.igniteConfigurationBean(), discoSpi = this.discoveryConfigurationBean(discovery)) {
        discoSpi.stringProperty('localAddress')
            .intProperty('localPort')
            .intProperty('localPortRange')
            .emptyBeanProperty('addressResolver')
            .longProperty('socketTimeout')
            .longProperty('ackTimeout')
            .longProperty('maxAckTimeout')
            .longProperty('networkTimeout')
            .longProperty('joinTimeout')
            .intProperty('threadPriority');

        discoSpi.longProperty('topHistorySize')
            .emptyBeanProperty('listener')
            .emptyBeanProperty('dataExchange')
            .emptyBeanProperty('metricsProvider')
            .intProperty('reconnectCount')
            .longProperty('statisticsPrintFrequency')
            .longProperty('ipFinderCleanFrequency')
            .emptyBeanProperty('authenticator');

        if (available('2.4.0'))
            discoSpi.longProperty('reconnectDelay');

        if (available('2.7.0'))
            discoSpi.longProperty('connectionRecoveryTimeout');

        if (available('2.8.0'))
            discoSpi.intProperty('soLinger');

        discoSpi.intProperty('forceServerMode')
            .intProperty('clientReconnectDisabled');

        if (discoSpi.nonEmpty())
            cfg.beanProperty('discoverySpi', discoSpi);

        return discoSpi;
    }

    // Execute event filtration in accordance to generated project version.
    static filterEvents(eventGrps, available) {
        if (eventGrps) {
            return _.reduce(eventGrps, (acc, eventGrp) => {
                switch (eventGrp.value) {
                    case 'EVTS_SWAPSPACE':
                        // Removed.

                        break;
                    case 'EVTS_CACHE':
                        const eventGrpX2 = _.cloneDeep(eventGrp);

                        eventGrpX2.events = _.filter(eventGrpX2.events, (ev) =>
                            !_.includes(['EVT_CACHE_OBJECT_SWAPPED', 'EVT_CACHE_OBJECT_UNSWAPPED'], ev));

                        acc.push(eventGrpX2);

                        break;
                    default:
                        acc.push(eventGrp);
                }

                return acc;
            }, []);
        }

        return eventGrps;
    }

    // Generate events group.
    static clusterEncryption(encryption, available, cfg = this.igniteConfigurationBean(cluster)) {
        if (!available('2.7.0'))
            return cfg;

        let bean;

        switch (_.get(encryption, 'kind')) {
            case 'Keystore':
                bean = new Bean('org.apache.ignite.spi.encryption.keystore.KeystoreEncryptionSpi', 'encryptionSpi',
                    encryption.Keystore, clusterDflts.encryptionSpi.Keystore)
                    .stringProperty('keyStorePath');

                if (nonEmpty(bean.valueOf('keyStorePath')))
                    bean.propertyChar('keyStorePassword', 'encryption.key.storage.password', 'YOUR_ENCRYPTION_KEY_STORAGE_PASSWORD');


                bean.intProperty('keySize')
                    .stringProperty('masterKeyName');

                break;

            case 'Custom':
                const clsName = _.get(encryption, 'Custom.className');

                if (clsName)
                    bean = new EmptyBean(clsName);

                break;

            default:
                // No-op.
        }

        if (bean)
            cfg.beanProperty('encryptionSpi', bean);

        return cfg;
    }

    // Generate events group.
    static clusterEvents(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        const eventStorage = cluster.eventStorage;        

        if (nonEmpty(cluster.includeEventTypes)) {
            const eventGrps = _.filter(this.eventGrps, ({value}) => _.includes(cluster.includeEventTypes, value));

            cfg.eventTypes('evts', 'includeEventTypes', this.filterEvents(eventGrps, available));
        }

        cfg.mapProperty('localEventListeners', _.map(cluster.localEventListeners,
            (lnr) => ({className: new EmptyBean(lnr.className), eventTypes: _.map(lnr.eventTypes, (evt) => {
                const grp = _.find(this.eventGrps, ((grp) => grp.events.indexOf(evt) >= 0));

                return {class: grp.class, label: evt};
            })})), 'localEventListeners');

        return cfg;
    }

    // Generate failover group.
    static clusterFailover(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        const spis = [];

        // Since ignite 2.0
        if (available('2.0.0')) {
            cfg.longProperty('failureDetectionTimeout')
                .longProperty('clientFailureDetectionTimeout');

            if (available('2.7.0'))
                cfg.longProperty('systemWorkerBlockedTimeout');
        }

        _.forEach(cluster.failoverSpi, (spi) => {
            let failoverSpi;

            switch (_.get(spi, 'kind')) {
                case 'JobStealing':
                    failoverSpi = new Bean('org.apache.ignite.spi.failover.jobstealing.JobStealingFailoverSpi',
                        'failoverSpi', spi.JobStealing, clusterDflts.failoverSpi.JobStealing);

                    failoverSpi.intProperty('maximumFailoverAttempts');

                    break;
                case 'Never':
                    failoverSpi = new Bean('org.apache.ignite.spi.failover.never.NeverFailoverSpi',
                        'failoverSpi', spi.Never);

                    break;
                case 'Always':
                    failoverSpi = new Bean('org.apache.ignite.spi.failover.always.AlwaysFailoverSpi',
                        'failoverSpi', spi.Always, clusterDflts.failoverSpi.Always);

                    failoverSpi.intProperty('maximumFailoverAttempts');

                    break;
                case 'Custom':
                    const className = _.get(spi, 'Custom.class');

                    if (className)
                        failoverSpi = new EmptyBean(className);

                    break;
                default:
                    // No-op.
            }

            if (failoverSpi)
                spis.push(failoverSpi);
        });

        if (spis.length)
            cfg.arrayProperty('failoverSpi', 'failoverSpi', spis, 'org.apache.ignite.spi.failover.FailoverSpi');

        if (available('2.5.0')) {
            const handler = cluster.failureHandler;
            const kind = _.get(handler, 'kind');

            let bean;

            switch (kind) {
                case 'RestartProcess':
                    bean = new Bean('org.apache.ignite.failure.RestartProcessFailureHandler', 'failureHandler', handler);

                    break;

                case 'StopNodeOnHalt':
                    const failover = handler.StopNodeOnHalt;

                    bean = new Bean('org.apache.ignite.failure.StopNodeOrHaltFailureHandler', 'failureHandler', failover);

                    if (failover && (failover.tryStop || failover.timeout)) {
                        failover.tryStop = failover.tryStop || false;
                        failover.timeout = failover.timeout || 0;

                        bean.boolConstructorArgument('tryStop')
                            .longConstructorArgument('timeout');
                    }

                    break;

                case 'StopNode':
                    bean = new Bean('org.apache.ignite.failure.StopNodeFailureHandler', 'failureHandler', handler);

                    break;

                case 'Noop':
                    bean = new Bean('org.apache.ignite.failure.NoOpFailureHandler', 'failureHandler', handler);

                    break;

                case 'Custom':
                    const clsName = _.get(handler, 'Custom.className');

                    if (clsName)
                        bean = new Bean(clsName, 'failureHandler', handler);

                    break;

                default:
                    // No-op.
            }

            if (bean) {
                if (['RestartProcess', 'StopNodeOnHalt', 'StopNode'].indexOf(kind) >= 0) {
                    bean.collectionProperty('ignoredFailureTypes', 'ignoredFailureTypes', handler.ignoredFailureTypes,
                        'org.apache.ignite.failure.FailureType', 'java.util.HashSet');
                }

                cfg.beanProperty('failureHandler', bean);
            }
        }

        return cfg;
    }

    // Generate load balancing configuration group.
    static clusterLoadBalancing(cluster, cfg = this.igniteConfigurationBean(cluster)) {
        const spis = [];

        _.forEach(cluster.loadBalancingSpi, (spi) => {
            let loadBalancingSpi;

            switch (_.get(spi, 'kind')) {
                case 'RoundRobin':
                    loadBalancingSpi = new Bean('org.apache.ignite.spi.loadbalancing.roundrobin.RoundRobinLoadBalancingSpi', 'loadBalancingSpiRR', spi.RoundRobin, clusterDflts.loadBalancingSpi.RoundRobin);

                    loadBalancingSpi.boolProperty('perTask');

                    break;

                case 'Adaptive':
                    loadBalancingSpi = new Bean('org.apache.ignite.spi.loadbalancing.adaptive.AdaptiveLoadBalancingSpi', 'loadBalancingSpiAdaptive', spi.Adaptive);

                    let probeBean;

                    switch (_.get(spi, 'Adaptive.loadProbe.kind')) {
                        case 'Job':
                            probeBean = new Bean('org.apache.ignite.spi.loadbalancing.adaptive.AdaptiveJobCountLoadProbe', 'jobProbe', spi.Adaptive.loadProbe.Job, clusterDflts.loadBalancingSpi.Adaptive.loadProbe.Job);

                            probeBean.boolProperty('useAverage');

                            break;

                        case 'CPU':
                            probeBean = new Bean('org.apache.ignite.spi.loadbalancing.adaptive.AdaptiveCpuLoadProbe', 'cpuProbe', spi.Adaptive.loadProbe.CPU, clusterDflts.loadBalancingSpi.Adaptive.loadProbe.CPU);

                            probeBean.boolProperty('useAverage')
                                .boolProperty('useProcessors')
                                .intProperty('processorCoefficient');

                            break;

                        case 'ProcessingTime':
                            probeBean = new Bean('org.apache.ignite.spi.loadbalancing.adaptive.AdaptiveProcessingTimeLoadProbe', 'timeProbe', spi.Adaptive.loadProbe.ProcessingTime, clusterDflts.loadBalancingSpi.Adaptive.loadProbe.ProcessingTime);

                            probeBean.boolProperty('useAverage');

                            break;

                        case 'Custom':
                            const className = _.get(spi, 'Adaptive.loadProbe.Custom.className');

                            if (className)
                                probeBean = new Bean(className, 'probe', spi.Adaptive.loadProbe.Job.Custom);

                            break;

                        default:
                            // No-op.
                    }

                    if (probeBean)
                        loadBalancingSpi.beanProperty('loadProbe', probeBean);

                    break;

                case 'WeightedRandom':
                    loadBalancingSpi = new Bean('org.apache.ignite.spi.loadbalancing.weightedrandom.WeightedRandomLoadBalancingSpi', 'loadBalancingSpiRandom', spi.WeightedRandom, clusterDflts.loadBalancingSpi.WeightedRandom);

                    loadBalancingSpi.intProperty('nodeWeight')
                        .boolProperty('useWeights');

                    break;

                case 'Custom':
                    const cusClassName = _.get(spi, 'Custom.className');

                    if (cusClassName)
                        loadBalancingSpi = new Bean(cusClassName, 'loadBalancingSpiCustom', spi.Custom);

                    break;

                default:
                    // No-op.
            }

            if (loadBalancingSpi)
                spis.push(loadBalancingSpi);
        });

        if (spis.length)
            cfg.varArgProperty('loadBalancingSpi', 'loadBalancingSpi', spis, 'org.apache.ignite.spi.loadbalancing.LoadBalancingSpi');

        return cfg;
    }

    static dataRegionConfiguration(dataRegionCfg, available) {
        const plcBean = new Bean('org.apache.ignite.configuration.DataRegionConfiguration', 'dataRegionCfg', dataRegionCfg, clusterDflts.dataStorageConfiguration.dataRegionConfigurations);

        plcBean.stringProperty('name')
            .longProperty('initialSize')
            .longProperty('maxSize')
            .stringProperty('swapPath')
            .enumProperty('pageEvictionMode')
            .doubleProperty('evictionThreshold')
            .intProperty('emptyPagesPoolSize')
            .intProperty('metricsSubIntervalCount')
            .longProperty('metricsRateTimeInterval')
            .longProperty('checkpointPageBufferSize')
            .boolProperty('metricsEnabled');

        if (!plcBean.valueOf('swapPath'))
            plcBean.boolProperty('persistenceEnabled');

        if (available('2.8.0'))
            plcBean.boolProperty('lazyMemoryAllocation');

        return plcBean;
    }

    // Generate data storage configuration.
    static clusterDataStorageConfiguration(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {        

        const available2_4 = available('2.4.0');

        const available2_7 = available('2.7.0');

        const dataStorageCfg = cluster.dataStorageConfiguration;

        const storageBean = new Bean('org.apache.ignite.configuration.DataStorageConfiguration', 'dataStorageCfg', dataStorageCfg, clusterDflts.dataStorageConfiguration);

        storageBean.intProperty('pageSize')
            .intProperty('concurrencyLevel')
            .longProperty('systemRegionInitialSize')
            .longProperty('systemRegionMaxSize');

        const dfltDataRegionCfg = this.dataRegionConfiguration(_.get(dataStorageCfg, 'defaultDataRegionConfiguration'), available);

        if (!dfltDataRegionCfg.isEmpty())
            storageBean.beanProperty('defaultDataRegionConfiguration', dfltDataRegionCfg);

        const dataRegionCfgs = [];

        _.forEach(_.get(dataStorageCfg, 'dataRegionConfigurations'), (dataRegionCfg) => {
            const plcBean = this.dataRegionConfiguration(dataRegionCfg, available);

            if (plcBean.isEmpty())
                return;

            dataRegionCfgs.push(plcBean);
        });

        if (!_.isEmpty(dataRegionCfgs))
            storageBean.varArgProperty('dataRegionConfigurations', 'dataRegionConfigurations', dataRegionCfgs, 'org.apache.ignite.configuration.DataRegionConfiguration');

        storageBean.stringProperty('storagePath')
            .longProperty('checkpointFrequency');

        if (available2_7) {
            storageBean
                .longProperty('checkpointReadLockTimeout');
        }

        storageBean.intProperty('checkpointThreads')
            .enumProperty('checkpointWriteOrder')
            .enumProperty('walMode')
            .stringProperty('walPath')
            .stringProperty('walArchivePath');

        if (available2_7) {
            storageBean.longProperty('maxWalArchiveSize')
                .intProperty('walCompactionLevel');
        }

        if (available('2.8.0')) {
            storageBean.enumProperty('walPageCompression');

            const compression = storageBean.valueOf('walPageCompression');

            if (compression === 'ZSTD' || compression === 'LZ4')
                storageBean.intProperty('walPageCompressionLevel');
        }

        storageBean.longProperty('walAutoArchiveAfterInactivity')
            .intProperty('walSegments')
            .intProperty('walSegmentSize')
            .intProperty('walHistorySize');

        if (available2_4)
            storageBean.intProperty('walBufferSize');

        storageBean.longProperty('walFlushFrequency')
            .longProperty('walFsyncDelayNanos')
            .intProperty('walRecordIteratorBufferSize')
            .longProperty('lockWaitTime')
            .intProperty('walThreadLocalBufferSize')
            .intProperty('metricsSubIntervalCount')
            .longProperty('metricsRateTimeInterval')
            .boolProperty('metricsEnabled')
            .boolProperty('alwaysWriteFullPages')
            .boolProperty('writeThrottlingEnabled');

        if (available2_4)
            storageBean.boolProperty('walCompactionEnabled');

        const fileIOFactory = _.get(dataStorageCfg, 'fileIOFactory');

        let factoryBean;

        if (fileIOFactory === 'RANDOM')
            factoryBean = new Bean('org.apache.ignite.internal.processors.cache.persistence.file.RandomAccessFileIOFactory', 'rndFileIoFactory', {});
        else if (fileIOFactory === 'ASYNC')
            factoryBean = new Bean('org.apache.ignite.internal.processors.cache.persistence.file.AsyncFileIOFactory', 'asyncFileIoFactory', {});

        if (factoryBean)
            storageBean.beanProperty('fileIOFactory', factoryBean);

        if (_.get(dataStorageCfg, 'defaultDataRegionConfiguration.persistenceEnabled')
            || _.find(_.get(dataStorageCfg, 'dataRegionConfigurations'), (storeCfg) => storeCfg.persistenceEnabled))
            cfg.boolProperty('authenticationEnabled');

        if (storageBean.nonEmpty())
            cfg.beanProperty('dataStorageConfiguration', storageBean);

        return cfg;
    }

    // Generate miscellaneous configuration.
    static clusterMisc(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {        

        cfg.pathProperty('workDirectory')
            .pathProperty('igniteHome')
            .varArgProperty('lifecycleBeans', 'lifecycleBeans', _.map(cluster.lifecycleBeans, (bean) => new EmptyBean(bean)), 'org.apache.ignite.lifecycle.LifecycleBean')
            .emptyBeanProperty('addressResolver')
            .emptyBeanProperty('mBeanServer')
            .varArgProperty('includeProperties', 'includeProperties', cluster.includeProperties);

        if (cluster.cacheStoreSessionListenerFactories) {
            const factories = _.map(cluster.cacheStoreSessionListenerFactories, (factory) => new EmptyBean(factory));

            cfg.varArgProperty('cacheStoreSessionListenerFactories', 'cacheStoreSessionListenerFactories', factories, 'javax.cache.configuration.Factory');
        }

        cfg.stringProperty('consistentId')
            .emptyBeanProperty('warmupClosure')
            .boolProperty('activeOnStart')
            .boolProperty('cacheSanityCheckEnabled');

        if (available('2.7.0'))
            cfg.varArgProperty('sqlSchemas', 'sqlSchemas', cluster.sqlSchemas);

        if (available('2.8.0'))
            cfg.intProperty('sqlQueryHistorySize');

        if (available('2.4.0'))
            cfg.boolProperty('autoActivationEnabled');        

        return cfg;
    }    

    // Generate IGFSs configs.
    static clusterIgfss(igfss, available, cfg = this.igniteConfigurationBean()) {
        const igfsCfgs = _.map(igfss, (igfs) => {
            const igfsCfg = this.igfsGeneral(igfs, available);

            this.igfsIPC(igfs, igfsCfg);
            this.igfsFragmentizer(igfs, igfsCfg);           

            this.igfsSecondFS(igfs, igfsCfg);
            this.igfsMisc(igfs, available, igfsCfg);

            return igfsCfg;
        });

        cfg.varArgProperty('igfsCfgs', 'fileSystemConfiguration', igfsCfgs, 'org.apache.ignite.configuration.FileSystemConfiguration');

        return cfg;
    }

    // Generate marshaller group.
    static clusterMarshaller(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        
        cfg.intProperty('marshalLocalJobs');        

        return cfg;
    }

    // Generate metrics group.
    static clusterMetrics(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        cfg.longProperty('metricsExpireTime')
            .intProperty('metricsHistorySize')
            .longProperty('metricsLogFrequency');

        // Since ignite 2.0
        cfg.longProperty('metricsUpdateFrequency');

        return cfg;
    }    

    // Generate cluster query group.
    static clusterQuery(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {

        cfg.longProperty('longQueryWarningTimeout');

        if (_.get(cluster, 'sqlConnectorConfiguration.enabled') !== true)
            return cfg;

        const bean = new Bean('org.apache.ignite.configuration.SqlConnectorConfiguration', 'sqlConnCfg',
            cluster.sqlConnectorConfiguration, clusterDflts.sqlConnectorConfiguration);

        bean.stringProperty('host')
            .intProperty('port')
            .intProperty('portRange')
            .intProperty('socketSendBufferSize')
            .intProperty('socketReceiveBufferSize')
            .intProperty('maxOpenCursorsPerConnection')
            .intProperty('threadPoolSize')
            .boolProperty('tcpNoDelay');

        cfg.beanProperty('sqlConnectorConfiguration', bean);

        return cfg;
    }

    // Generate cluster query group.
    static clusterPersistence(persistence, available, cfg = this.igniteConfigurationBean()) {
        if (_.get(persistence, 'enabled') !== true)
            return cfg;

        const bean = new Bean('org.apache.ignite.configuration.PersistentStoreConfiguration', 'PersistenceCfg',
            persistence, clusterDflts.persistenceStoreConfiguration);

        bean.stringProperty('persistentStorePath')
            .boolProperty('metricsEnabled')
            .boolProperty('alwaysWriteFullPages')
            .longProperty('checkpointingFrequency')
            .longProperty('checkpointingPageBufferSize')
            .intProperty('checkpointingThreads')
            .enumProperty('walMode')
            .stringProperty('walStorePath')
            .stringProperty('walArchivePath')
            .intProperty('walSegments')
            .intProperty('walSegmentSize')
            .intProperty('walHistorySize')
            .longProperty('walFlushFrequency')
            .longProperty('walFsyncDelayNanos')
            .intProperty('walRecordIteratorBufferSize')
            .longProperty('lockWaitTime')
            .longProperty('rateTimeInterval')
            .intProperty('tlbSize')
            .intProperty('subIntervals')
            .longProperty('walAutoArchiveAfterInactivity');

        cfg.beanProperty('persistentStoreConfiguration', bean);

        return cfg;
    }

    // Generate cluster rebalance group.
    static clusterRebalance(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        if (available('2.5.0')) {
            cfg.intProperty('rebalanceBatchSize')
                .longProperty('rebalanceBatchesPrefetchCount')
                .longProperty('rebalanceTimeout')
                .longProperty('rebalanceThrottle');
        }

        return cfg;
    }

    // Java code generator for cluster's service configurations.
    static clusterServiceConfiguration(srvs, caches, cfg = this.igniteConfigurationBean()) {
        const srvBeans = [];

        _.forEach(srvs, (srv) => {
            const bean = new Bean('org.apache.ignite.services.ServiceConfiguration', 'service', srv, clusterDflts.serviceConfigurations);

            bean.stringProperty('name')
                .emptyBeanProperty('service')
                .intProperty('maxPerNodeCount')
                .intProperty('totalCount')
                .stringProperty('cache', 'cacheName', (id) => id ? _.get(_.find(caches, {id}), 'name', null) : null)
                .stringProperty('affinityKey');

            srvBeans.push(bean);
        });

        if (!_.isEmpty(srvBeans))
            cfg.arrayProperty('services', 'serviceConfiguration', srvBeans, 'org.apache.ignite.services.ServiceConfiguration');

        return cfg;
    }

    // Java code generator for cluster's SSL configuration.
    static clusterSsl(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        if (cluster.sslEnabled && nonNil(cluster.sslContextFactory)) {
            const bean = new Bean('org.apache.ignite.ssl.SslContextFactory', 'sslCtxFactory',
                cluster.sslContextFactory);

            bean.intProperty('keyAlgorithm')
                .pathProperty('keyStoreFilePath');

            if (nonEmpty(bean.valueOf('keyStoreFilePath')))
                bean.propertyChar('keyStorePassword', 'ssl.key.storage.password', 'YOUR_SSL_KEY_STORAGE_PASSWORD');

            bean.intProperty('keyStoreType')
                .intProperty('protocol');

            if (nonEmpty(cluster.sslContextFactory.trustManagers)) {
                bean.arrayProperty('trustManagers', 'trustManagers',
                    _.map(cluster.sslContextFactory.trustManagers, (clsName) => new EmptyBean(clsName)),
                    'javax.net.ssl.TrustManager');
            }
            else {
                bean.pathProperty('trustStoreFilePath');

                if (nonEmpty(bean.valueOf('trustStoreFilePath')))
                    bean.propertyChar('trustStorePassword', 'ssl.trust.storage.password', 'YOUR_SSL_TRUST_STORAGE_PASSWORD');

                bean.intProperty('trustStoreType');
            }

            if (available('2.7.0')) {
                bean.varArgProperty('cipherSuites', 'cipherSuites', cluster.sslContextFactory.cipherSuites)
                    .varArgProperty('protocols', 'protocols', cluster.sslContextFactory.protocols);
            }

            cfg.beanProperty('sslContextFactory', bean);
        }

        return cfg;
    }   

    // Generate time group.
    static clusterTime(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        
        cfg.intProperty('timeServerPortBase')
            .intProperty('timeServerPortRange');

        return cfg;
    }

    // Generate thread pools group.
    static clusterPools(cluster, available, cfg = this.igniteConfigurationBean(cluster)) {
        cfg.intProperty('publicThreadPoolSize')
            .intProperty('systemThreadPoolSize')
            .intProperty('serviceThreadPoolSize')
            .intProperty('managementThreadPoolSize')
            .intProperty('rebalanceThreadPoolSize')
            .intProperty('utilityCacheThreadPoolSize', 'utilityCachePoolSize')
            .longProperty('utilityCacheKeepAliveTime')
            .intProperty('asyncCallbackPoolSize')
            .intProperty('stripedPoolSize');

        // Since ignite 2.0
        if (available('2.0.0')) {
            cfg.intProperty('dataStreamerThreadPoolSize')
                .intProperty('queryThreadPoolSize');

            const executors = [];

            _.forEach(cluster.executorConfiguration, (exec) => {
                const execBean = new Bean('org.apache.ignite.configuration.ExecutorConfiguration', 'executor', exec);

                execBean.stringProperty('name')
                    .intProperty('size');

                if (!execBean.isEmpty())
                    executors.push(execBean);
            });

            if (!_.isEmpty(executors))
                cfg.arrayProperty('executors', 'executorConfiguration', executors, 'org.apache.ignite.configuration.ExecutorConfiguration');
        }

        return cfg;
    }

    // Generate transactions group.
    static clusterTransactions(transactionConfiguration, available, cfg = this.igniteConfigurationBean()) {
        const bean = new Bean('org.apache.ignite.configuration.TransactionConfiguration', 'transactionConfiguration',
            transactionConfiguration, clusterDflts.transactionConfiguration);

        bean.enumProperty('defaultTxConcurrency')
            .enumProperty('defaultTxIsolation')
            .longProperty('defaultTxTimeout')
            .intProperty('pessimisticTxLogLinger')
            .intProperty('pessimisticTxLogSize')
            .boolProperty('txSerializableEnabled')
            .emptyBeanProperty('txManagerFactory');

        if (available('2.5.0'))
            bean.longProperty('txTimeoutOnPartitionMapExchange');

        if (available('2.8.0'))
            bean.longProperty('deadlockTimeout');

        bean.boolProperty('useJtaSynchronization');

        if (bean.nonEmpty())
            cfg.beanProperty('transactionConfiguration', bean);

        return cfg;
    }

    // Generate user attributes group.
    static clusterUserAttributes(cluster, cfg = this.igniteConfigurationBean(cluster)) {
        cfg.mapProperty('attrs', 'attributes', 'userAttributes');

        return cfg;
    }

    // Generate domain model for general group.
    static domainModelGeneral(domain, cfg = this.domainConfigurationBean(domain)) {
        switch (cfg.valueOf('queryMetadata')) {
            case 'Annotations':
                if (nonNil(domain.keyType) && nonNil(domain.valueType)) {
                    cfg.varArgProperty('indexedTypes', 'indexedTypes',
                        [javaTypes.fullClassName(domain.keyType), javaTypes.fullClassName(domain.valueType)],
                        'java.lang.Class');
                }

                break;
            case 'Configuration':
                cfg.stringProperty('keyType', 'keyType', (val) => javaTypes.fullClassName(val))
                    .stringProperty('valueType', 'valueType', (val) => javaTypes.fullClassName(val));

                break;
            default:
        }

        return cfg;
    }

    // Generate domain model for query group.
    static domainModelQuery(domain, available, cfg = this.domainConfigurationBean(domain)) {
        if (cfg.valueOf('queryMetadata') === 'Configuration') {
            const notNull = [];
            const precisions = [];
            const scales = [];
            const defaultValues = [];

            const notNullAvailable = available('2.3.0');
            const defaultAvailable = available('2.4.0');
            const precisionAvailable = available('2.7.0');
            const tableCommentAvailable = available('2.16.999');
            const fields = _.filter(_.map(domain.fields,
                (e) => {
                    if (notNullAvailable && e.notNull)
                        notNull.push(e.name);

                    if (defaultAvailable && e.defaultValue) {
                        let value = e.defaultValue;

                        switch (e.className) {
                            case 'String':
                                value = new Bean('java.lang.String', 'value', e).stringConstructorArgument('defaultValue');

                                break;

                            case 'BigDecimal':
                                value = new Bean('java.math.BigDecimal', 'value', e).stringConstructorArgument('defaultValue');

                                break;

                            case 'byte[]':
                                value = null;

                                break;

                            default: // No-op.
                        }

                        defaultValues.push({name: e.name, value});
                    }

                    if (precisionAvailable && e.precision) {
                        precisions.push({name: e.name, value: e.precision});

                        if (e.scale)
                            scales.push({name: e.name, value: e.scale});
                    }

                    return {name: e.name, className: javaTypes.stringClassName(e.className)};
                }), (field) => {
                return field.name !== domain.keyFieldName && field.name !== domain.valueFieldName;
            });

            cfg.stringProperty('tableName');
            
            if (tableCommentAvailable)
                cfg.stringProperty('tableComment');

            if (available('2.0.0')) {
                cfg.stringProperty('keyFieldName')
                    .stringProperty('valueFieldName');

                const keyFieldName = cfg.valueOf('keyFieldName');
                const valFieldName = cfg.valueOf('valueFieldName');

                if (keyFieldName)
                    fields.push({name: keyFieldName, className: javaTypes.stringClassName(domain.keyType)});

                if (valFieldName)
                    fields.push({name: valFieldName, className: javaTypes.stringClassName(domain.valueType)});
            }

            cfg.collectionProperty('keyFields', 'keyFields', domain.queryKeyFields, 'java.lang.String', 'java.util.HashSet')
                .mapProperty('fields', fields, 'fields', true)
                .mapProperty('aliases', 'aliases');

            if (notNullAvailable && notNull)
                cfg.collectionProperty('notNullFields', 'notNullFields', notNull, 'java.lang.String', 'java.util.HashSet');

            if (defaultAvailable && defaultValues)
                cfg.mapProperty('defaultFieldValues', defaultValues, 'defaultFieldValues');

            if (precisionAvailable && precisions) {
                cfg.mapProperty('fieldsPrecision', precisions, 'fieldsPrecision');

                if (scales)
                    cfg.mapProperty('fieldsScale', scales, 'fieldsScale');
            }

            const indexes = _.map(domain.indexes, (index) => {
                const bean = new Bean('org.apache.ignite.cache.QueryIndex', 'index', index, cacheDflts.indexes)
                    .stringProperty('name')
                    .enumProperty('indexType')
                    .mapProperty('indFlds', 'fields', 'fields', true);

                if (available('2.3.0'))
                    bean.intProperty('inlineSize');

                return bean;
            });

            cfg.collectionProperty('indexes', 'indexes', indexes, 'org.apache.ignite.cache.QueryIndex');
        }

        return cfg;
    }

    // Generate domain model db fields.
    static _domainModelDatabaseFields(cfg, propName, domain) {
        const fields = _.map(domain[propName], (field) => {
            return new Bean('org.apache.ignite.cache.store.jdbc.JdbcTypeField', 'typeField', field, cacheDflts.typeField)
                .constantConstructorArgument('databaseFieldType')
                .stringConstructorArgument('databaseFieldName')
                .classConstructorArgument('javaFieldType')
                .stringConstructorArgument('javaFieldName');
        });

        cfg.varArgProperty(propName, propName, fields, 'org.apache.ignite.cache.store.jdbc.JdbcTypeField');

        return cfg;
    }

    // Generate domain model for store group.
    static domainStore(domain, cfg = this.domainJdbcTypeBean(domain)) {
        cfg.stringProperty('databaseSchema').stringProperty('databaseTable');

        this._domainModelDatabaseFields(cfg, 'keyFields', domain);
        this._domainModelDatabaseFields(cfg, 'valueFields', domain);

        return cfg;
    }

    /**
     * Generate eviction policy object.
     * @param {Object} ccfg Parent configuration.
     * @param {Function} available Function to check feature is supported in Ignite current version.
     * @param {Boolean} near Near cache flag.
     * @param {Object} src Source.
     * @param {Object} dflt Default.
     * @returns {Object} Parent configuration.
     * @private
     */
    static _evictionPolicy(ccfg, available, near, src, dflt) {
        let propName;
        let beanProps;

        switch (_.get(src, 'kind')) {
            case 'LRU': beanProps = {cls: 'org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory', src: src.LRU };
                break;

            case 'FIFO': beanProps = {cls: 'org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicyFactory', src: src.FIFO };
                break;

            case 'SORTED': beanProps = {cls: 'org.apache.ignite.cache.eviction.sorted.SortedEvictionPolicyFactory', src: src.SORTED };
                break;

            default:
                return ccfg;
        }

        propName = (near ? 'nearEviction' : 'eviction') + 'PolicyFactory';
       
        const bean = new Bean(beanProps.cls, propName, beanProps.src, dflt);

        bean.intProperty('batchSize')
            .intProperty('maxMemorySize')
            .intProperty('maxSize');

        ccfg.beanProperty(propName, bean);

        return ccfg;
    }

    // Generate cache general group.
    static cacheGeneral(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        ccfg.stringProperty('name');
        
        ccfg.stringProperty('groupName');

        ccfg.enumProperty('cacheMode')
            .enumProperty('atomicityMode');

        if (ccfg.valueOf('cacheMode') === 'PARTITIONED' && ccfg.valueOf('backups')) {
            ccfg.intProperty('backups')
                .intProperty('readFromBackup');
        }

        // Since ignite 2.0        
        ccfg.enumProperty('partitionLossPolicy');

        ccfg.intProperty('copyOnRead');

        if (ccfg.valueOf('cacheMode') === 'PARTITIONED' && ccfg.valueOf('atomicityMode') === 'TRANSACTIONAL')
            ccfg.intProperty('isInvalidate', 'invalidate');

        return ccfg;
    }

    // Generation of constructor for affinity function.
    static affinityFunction(cls, func) {
        const affBean = new Bean(cls, 'affinityFunction', func);

        affBean.boolConstructorArgument('excludeNeighbors')
            .intProperty('partitions')
            .emptyBeanProperty('affinityBackupFilter');

        return affBean;
    }

    // Generate affinity function.
    static affinity(affinity, cfg) {
        switch (_.get(affinity, 'kind')) {
            case 'Rendezvous':
                cfg.beanProperty('affinity', this.affinityFunction('org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction', affinity.Rendezvous));

                break;
            case 'Fair':
                cfg.beanProperty('affinity', this.affinityFunction('org.apache.ignite.cache.affinity.fair.FairAffinityFunction', affinity.Fair));

                break;
            case 'Custom':
                cfg.emptyBeanProperty('affinity.Custom.className', 'affinity');

                break;
            default:
            // No-op.
        }
    }

    // Generate cache memory group.
    static cacheAffinity(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        this.affinity(cache.affinity, ccfg);

        ccfg.emptyBeanProperty('affinityMapper');

        ccfg.emptyBeanProperty('topologyValidator');            

        return ccfg;
    }

    // Generate key configurations of cache.
    static cacheKeyConfiguration(keyCfgs, available, cfg = this.igniteConfigurationBean()) {
        if (available('2.1.0')) {
            const items = _.reduce(keyCfgs, (acc, keyCfg) => {
                if (keyCfg.typeName && keyCfg.affinityKeyFieldName) {
                    acc.push(new Bean('org.apache.ignite.cache.CacheKeyConfiguration', null, keyCfg)
                        .stringConstructorArgument('typeName')
                        .stringConstructorArgument('affinityKeyFieldName'));
                }

                return acc;
            }, []);

            if (_.isEmpty(items))
                return cfg;

            cfg.arrayProperty('keyConfiguration', 'keyConfiguration', items,
                'org.apache.ignite.cache.CacheKeyConfiguration');
        }

        return cfg;
    }

    // Generate cache memory group.
    static cacheMemory(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        
        if (available('2.3.0'))
            ccfg.stringProperty('dataRegionName');

        if (available('2.8.0')) {
            ccfg.enumProperty('diskPageCompression');

            const compression = ccfg.valueOf('diskPageCompression');

            if (compression === 'ZSTD' || compression === 'LZ4')
                ccfg.intProperty('diskPageCompressionLevel');
        }
        

        // Since ignite 2.0
        if (available('2.0.0')) {
            ccfg.boolProperty('onheapCacheEnabled')
                .emptyBeanProperty('evictionFilter');
        }

        this._evictionPolicy(ccfg, available, false, cache.evictionPolicy, cacheDflts.evictionPolicy);


        if (cache.cacheWriterFactory)
            ccfg.beanProperty('cacheWriterFactory', new EmptyBean(cache.cacheWriterFactory));

        if (cache.cacheLoaderFactory)
            ccfg.beanProperty('cacheLoaderFactory', new EmptyBean(cache.cacheLoaderFactory));

        if (cache.expiryPolicyFactory)
            ccfg.beanProperty('expiryPolicyFactory', new EmptyBean(cache.expiryPolicyFactory));

        return ccfg;
    }

    // Generate cache queries & Indexing group.
    static cacheQuery(cache, domains, available, ccfg = this.cacheConfigurationBean(cache)) {
        const indexedTypes = _.reduce(domains, (acc, domain) => {
            if (domain.queryMetadata === 'Annotations')
                acc.push(javaTypes.fullClassName(domain.keyType), javaTypes.fullClassName(domain.valueType));

            return acc;
        }, []);

        ccfg.stringProperty('sqlSchema');

        ccfg.longProperty('longQueryWarningTimeout')
            .arrayProperty('indexedTypes', 'indexedTypes', indexedTypes, 'java.lang.Class')
            .intProperty('queryDetailMetricsSize')
            .arrayProperty('sqlFunctionClasses', 'sqlFunctionClasses', cache.sqlFunctionClasses, 'java.lang.Class');

        ccfg.intProperty('sqlEscapeAll');

        // Since ignite 2.0
        if (available('2.0.0')) {
            ccfg.intProperty('queryParallelism')
                .intProperty('sqlIndexMaxInlineSize');
        }

        if (available('2.4.0') && cache.sqlOnheapCacheEnabled) {
            ccfg.boolProperty('sqlOnheapCacheEnabled')
                .intProperty('sqlOnheapCacheMaxSize');
        }

        ccfg.intProperty('maxQueryIteratorsCount');

        return ccfg;
    }

    static _baseJdbcPojoStoreFactory(storeFactory, bean, cacheName, domains, available, deps) {
        const jdbcId = bean.valueOf('dataSourceBean');

        bean.dataSource(jdbcId, 'dataSourceBean', this.dataSourceBean(jdbcId, storeFactory.dialect, available, deps, storeFactory.implementationVersion))
            .beanProperty('dialect', new EmptyBean(this.dialectClsName(storeFactory.dialect)));

        bean.intProperty('batchSize')
            .intProperty('maximumPoolSize')
            .intProperty('maximumWriteAttempts')
            .intProperty('parallelLoadCacheMinimumThreshold')
            .emptyBeanProperty('hasher')
            .emptyBeanProperty('transformer')
            .boolProperty('sqlEscapeAll');

        const setType = (typeBean, propName) => {
            if (javaTypes.nonBuiltInClass(typeBean.valueOf(propName)))
                typeBean.stringProperty(propName);
            else
                typeBean.classProperty(propName);
        };

        const types = _.reduce(domains, (acc, domain) => {
            if (isNil(domain.databaseTable))
                return acc;

            const typeBean = this.domainJdbcTypeBean(_.merge({}, domain, {cacheName}))
                .stringProperty('cacheName');

            setType(typeBean, 'keyType');
            setType(typeBean, 'valueType');

            this.domainStore(domain, typeBean);

            acc.push(typeBean);

            return acc;
        }, []);

        bean.varArgProperty('types', 'types', types, 'org.apache.ignite.cache.store.jdbc.JdbcType');
    }

    static _baseDocumentStoreFactory(storeFactory, bean, cacheName, domains, available, deps) {        

        bean.stringProperty('dataSrc').stringProperty('idField');
        bean.intProperty('batchSize').intProperty('parallelLoadCacheMinimumThreshold');
        bean.boolProperty('streamerEnabled');

        const setType = (typeBean, propName) => {
            if (javaTypes.nonBuiltInClass(typeBean.valueOf(propName)))
                typeBean.stringProperty(propName);
            else
                typeBean.classProperty(propName);
        };

        const types = _.reduce(domains, (acc, domain) => {
            if (isNil(domain.databaseTable))
                return acc;

            const typeBean = this.domainJdbcTypeBean(_.merge({}, domain, {cacheName}))
                .stringProperty('cacheName');

            setType(typeBean, 'keyType');
            setType(typeBean, 'valueType');

            this.domainStore(domain, typeBean);

            acc.push(typeBean);

            return acc;
        }, []);

        bean.varArgProperty('types', 'types', types, 'org.apache.ignite.cache.store.jdbc.JdbcType');
    }

    // Generate cache store group.
    static cacheStore(cache, domains, available, targetVer, deps, ccfg = this.cacheConfigurationBean(cache)) {
        const kind = _.get(cache, 'cacheStoreFactory.kind');

        if (kind && cache.cacheStoreFactory[kind]) {
            let bean = null;

            const storeFactory = cache.cacheStoreFactory[kind];

            switch (kind) {
                case 'DocumentLoadOnlyStoreFactory':
                    bean = new Bean('org.apache.ignite.cache.store.bson.DocumentLoadOnlyStoreFactory', 'cacheStoreFactory',
                        storeFactory, cacheDflts.cacheStoreFactory.DocumentLoadOnlyStoreFactory);
                    
                    this._baseDocumentStoreFactory(storeFactory, bean, cache.name, domains, available, deps);

                    break;
                case 'CacheJdbcPojoStoreFactory':
                    bean = new Bean('org.apache.ignite.cache.store.jdbc.CacheJdbcPojoStoreFactory', 'cacheStoreFactory',
                        storeFactory, cacheDflts.cacheStoreFactory.CacheJdbcPojoStoreFactory);

                    this._baseJdbcPojoStoreFactory(storeFactory, bean, cache.name, domains, available, deps);

                    break;
                case 'CacheJdbcBlobStoreFactory':
                    bean = new Bean('org.apache.ignite.cache.store.jdbc.CacheJdbcBlobStoreFactory', 'cacheStoreFactory',
                        storeFactory);

                    if (bean.valueOf('connectVia') === 'DataSource') {
                        const blobId = bean.valueOf('dataSourceBean');

                        bean.dataSource(blobId, 'dataSourceBean', this.dataSourceBean(blobId, storeFactory.dialect, available, deps));
                    }
                    else {
                        ccfg.stringProperty('connectionUrl')
                            .stringProperty('user')
                            .property('password', `ds.${storeFactory.user}.password`, 'YOUR_PASSWORD');
                    }

                    bean.boolProperty('initSchema')
                        .stringProperty('createTableQuery')
                        .stringProperty('loadQuery')
                        .stringProperty('insertQuery')
                        .stringProperty('updateQuery')
                        .stringProperty('deleteQuery');

                    break;
                case 'CacheHibernateBlobStoreFactory':
                    bean = new Bean('org.apache.ignite.cache.store.hibernate.CacheHibernateBlobStoreFactory',
                        'cacheStoreFactory', storeFactory);

                    bean.propsProperty('props', 'hibernateProperties');

                    break;
                default:
            }

            if (bean)
                ccfg.beanProperty('cacheStoreFactory', bean);
        }

        ccfg.intProperty('storeConcurrentLoadAllThreshold')
            .boolProperty('storeKeepBinary')
            .boolProperty('loadPreviousValue')
            .boolProperty('readThrough')
            .boolProperty('writeThrough');

        if (ccfg.valueOf('writeBehindEnabled')) {
            ccfg.boolProperty('writeBehindEnabled')
                .intProperty('writeBehindBatchSize')
                .intProperty('writeBehindFlushSize')
                .longProperty('writeBehindFlushFrequency')
                .intProperty('writeBehindFlushThreadCount');

            // Since ignite 2.0
            if (available('2.0.0'))
                ccfg.boolProperty('writeBehindCoalescing');
        }

        return ccfg;
    }

    // Generate cache concurrency control group.
    static cacheConcurrency(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        ccfg.intProperty('maxConcurrentAsyncOperations')
            .longProperty('defaultLockTimeout');

        ccfg.enumProperty('writeSynchronizationMode');

        return ccfg;
    }

    static nodeFilter(filter, igfss) {
        const kind = _.get(filter, 'kind');

        const settings = _.get(filter, kind);

        if (!isNil(settings)) {
            switch (kind) {
                case 'IGFS':
                    const foundIgfs = _.find(igfss, {id: settings.igfs});

                    if (foundIgfs) {
                        return new Bean('org.apache.ignite.internal.processors.igfs.IgfsNodePredicate', 'nodeFilter', foundIgfs)
                            .stringConstructorArgument('name');
                    }

                    break;
                case 'Custom':
                    if (nonEmpty(settings.className))
                        return new EmptyBean(settings.className);

                    break;
                default:
                // No-op.
            }
        }

        return null;
    }

    // Generate cache node filter group.
    static cacheNodeFilter(cache, igfss, ccfg = this.cacheConfigurationBean(cache)) {
        const filter = _.get(cache, 'nodeFilter');

        const filterBean = this.nodeFilter(filter, igfss);

        if (filterBean)
            ccfg.beanProperty('nodeFilter', filterBean);

        return ccfg;
    }

    // Generate cache rebalance group.
    static cacheRebalance(cache, ccfg = this.cacheConfigurationBean(cache)) {
        ccfg.enumProperty('rebalanceMode')
            .intProperty('rebalanceBatchSize')
            .longProperty('rebalanceBatchesPrefetchCount')
            .intProperty('rebalanceOrder')
            .longProperty('rebalanceDelay')
            .longProperty('rebalanceTimeout')
            .longProperty('rebalanceThrottle');

        return ccfg;
    }

    // Generate miscellaneous configuration.
    static cacheMisc(cache, available, cfg = this.cacheConfigurationBean(cache)) {
        if (cache.interceptor)
            cfg.beanProperty('interceptor', new EmptyBean(cache.interceptor));

        if (available('2.0.0'))
            cfg.boolProperty('storeByValue');

        cfg.boolProperty('eagerTtl');

        if (available('2.7.0'))
            cfg.boolProperty('encryptionEnabled');

        if (available('2.5.0'))
            cfg.boolProperty('eventsDisabled');

        if (cache.cacheStoreSessionListenerFactories) {
            const factories = _.map(cache.cacheStoreSessionListenerFactories, (factory) => new EmptyBean(factory));

            cfg.varArgProperty('cacheStoreSessionListenerFactories', 'cacheStoreSessionListenerFactories', factories, 'javax.cache.configuration.Factory');
        }

        return cfg;
    }

    // Generate server near cache group.
    static cacheNearServer(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        if (ccfg.valueOf('cacheMode') === 'PARTITIONED' && _.get(cache, 'nearConfiguration.enabled')) {
            const bean = new Bean('org.apache.ignite.configuration.NearCacheConfiguration', 'nearConfiguration',
                cache.nearConfiguration, cacheDflts.nearConfiguration);

            bean.intProperty('nearStartSize');

            this._evictionPolicy(bean, available, true, bean.valueOf('nearEvictionPolicy'), cacheDflts.evictionPolicy);

            ccfg.beanProperty('nearConfiguration', bean);
        }

        return ccfg;
    }

    // Generate client near cache group.
    static cacheNearClient(cache, available, ccfg = this.cacheConfigurationBean(cache)) {
        if (ccfg.valueOf('cacheMode') === 'PARTITIONED' && _.get(cache, 'clientNearConfiguration.enabled')) {
            const bean = new Bean('org.apache.ignite.configuration.NearCacheConfiguration',
                javaTypes.toJavaName('nearConfiguration', ccfg.valueOf('name')),
                cache.clientNearConfiguration, cacheDflts.clientNearConfiguration);

            bean.intProperty('nearStartSize');

            this._evictionPolicy(bean, available, true, bean.valueOf('nearEvictionPolicy'), cacheDflts.evictionPolicy);

            return bean;
        }

        return ccfg;
    }

    // Generate cache statistics group.
    static cacheStatistics(cache, ccfg = this.cacheConfigurationBean(cache)) {
        ccfg.boolProperty('statisticsEnabled')
            .boolProperty('managementEnabled');

        return ccfg;
    }

    // Generate domain models configs.
    static cacheDomains(domains, available, ccfg) {
        const qryEntities = _.reduce(domains, (acc, domain) => {
            if (isNil(domain.queryMetadata) || domain.queryMetadata === 'Configuration') {
                const qryEntity = this.domainModelGeneral(domain);

                this.domainModelQuery(domain, available, qryEntity);

                acc.push(qryEntity);
            }

            return acc;
        }, []);

        ccfg.collectionProperty('qryEntities', 'queryEntities', qryEntities, 'org.apache.ignite.cache.QueryEntity');
    }

    static cacheConfiguration(cache, available, targetVer, deps = [], ccfg = this.cacheConfigurationBean(cache)) {
        this.cacheGeneral(cache, available, ccfg);
        this.cacheAffinity(cache, available, ccfg);
        this.cacheMemory(cache, available, ccfg);
        this.cacheQuery(cache, cache.domains, available, ccfg);
        this.cacheStore(cache, cache.domains, available, targetVer, deps, ccfg);
        this.cacheKeyConfiguration(cache.keyConfiguration, available, ccfg);
        const igfs = _.get(cache, 'nodeFilter.IGFS.instance');
        this.cacheNodeFilter(cache, igfs ? [igfs] : [], ccfg);
        this.cacheConcurrency(cache, available, ccfg);
        this.cacheRebalance(cache, ccfg);
        this.cacheMisc(cache, available, ccfg);
        this.cacheNearServer(cache, available, ccfg);
        this.cacheStatistics(cache, ccfg);
        this.cacheDomains(cache.domains, available, ccfg);

        return ccfg;
    }

    // Generate IGFS general group.
    static igfsGeneral(igfs, available, cfg = this.igfsConfigurationBean(igfs)) {
        if (_.isEmpty(igfs.name))
            return cfg;

        cfg.stringProperty('name');        

        cfg.enumProperty('defaultMode');

        return cfg;
    }
    // Generate IGFS secondary file system group.
    static igfsSecondFS(igfs, cfg = this.igfsConfigurationBean(igfs)) {
        if (igfs.secondaryFileSystemEnabled) {
            const secondFs = igfs.secondaryFileSystem || {};

            const bean = new Bean('org.apache.ignite.hadoop.fs.IgniteHadoopIgfsSecondaryFileSystem',
                'secondaryFileSystem', secondFs, igfsDflts.secondaryFileSystem);

            bean.stringProperty('userName', 'defaultUserName');

            let factoryBean = null;

            switch (secondFs.kind || 'Caching') {
                case 'Caching':
                    factoryBean = new Bean('org.apache.ignite.hadoop.fs.CachingHadoopFileSystemFactory', 'fac', secondFs);
                    break;

                case 'Kerberos':
                    factoryBean = new Bean('org.apache.ignite.hadoop.fs.KerberosHadoopFileSystemFactory', 'fac', secondFs, igfsDflts.secondaryFileSystem);
                    break;

                case 'Custom':
                    if (_.get(secondFs, 'Custom.className'))
                        factoryBean = new Bean(secondFs.Custom.className, 'fac', null);

                    break;

                default:
            }

            if (!factoryBean)
                return cfg;

            if (secondFs.kind !== 'Custom') {
                factoryBean.stringProperty('uri')
                    .pathArrayProperty('cfgPaths', 'configPaths', secondFs.cfgPaths, true);

                if (secondFs.kind === 'Kerberos') {
                    factoryBean.stringProperty('Kerberos.keyTab', 'keyTab')
                        .stringProperty('Kerberos.keyTabPrincipal', 'keyTabPrincipal')
                        .longProperty('Kerberos.reloginInterval', 'reloginInterval');
                }

                if (_.get(secondFs, 'userNameMapper.kind')) {
                    const mapper = IgniteConfigurationGenerator._userNameMapperBean(secondFs.userNameMapper);

                    if (mapper)
                        factoryBean.beanProperty('userNameMapper', mapper);
                }
            }

            bean.beanProperty('fileSystemFactory', factoryBean);

            cfg.beanProperty('secondaryFileSystem', bean);
        }

        return cfg;
    }

    // Generate IGFS IPC group.
    static igfsIPC(igfs, cfg = this.igfsConfigurationBean(igfs)) {
        if (igfs.ipcEndpointEnabled) {
            const bean = new Bean('org.apache.ignite.igfs.IgfsIpcEndpointConfiguration', 'ipcEndpointConfiguration',
                igfs.ipcEndpointConfiguration, igfsDflts.ipcEndpointConfiguration);

            bean.enumProperty('type')
                .stringProperty('host')
                .intProperty('port')
                .intProperty('memorySize')
                .pathProperty('tokenDirectoryPath')
                .intProperty('threadCount');

            if (bean.nonEmpty())
                cfg.beanProperty('ipcEndpointConfiguration', bean);
        }

        return cfg;
    }

    // Generate IGFS fragmentizer group.
    static igfsFragmentizer(igfs, cfg = this.igfsConfigurationBean(igfs)) {
        if (igfs.fragmentizerEnabled) {
            cfg.intProperty('fragmentizerConcurrentFiles')
                .longProperty('fragmentizerThrottlingBlockLength')
                .longProperty('fragmentizerThrottlingDelay');
        }
        else
            cfg.boolProperty('fragmentizerEnabled');

        return cfg;
    }

    // Generate IGFS miscellaneous group.
    static igfsMisc(igfs, available, cfg = this.igfsConfigurationBean(igfs)) {
        cfg.intProperty('blockSize');

        // Since ignite 2.0
        if (available('2.0.0'))
            cfg.intProperty('streamBufferSize', 'bufferSize');

        cfg.longProperty('maximumTaskRangeLength')
            .intProperty('managementPort')
            .intProperty('perNodeBatchSize')
            .intProperty('perNodeParallelBatchCount')
            .intProperty('prefetchBlocks')
            .intProperty('sequentialReadsBeforePrefetch');

        cfg.intProperty('colocateMetadata')
            .intProperty('relaxedConsistency')
            .mapProperty('pathModes', 'pathModes');

        // Since ignite 2.0
        if (available('2.0.0'))
            cfg.boolProperty('updateFileLengthOnFlush');

        return cfg;
    }
}
