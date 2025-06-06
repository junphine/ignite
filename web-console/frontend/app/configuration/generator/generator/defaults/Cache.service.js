

const DFLT_CACHE = {
    cacheMode: {
        clsName: 'org.apache.ignite.cache.CacheMode'
    },
    partitionLossPolicy: {
        clsName: 'org.apache.ignite.cache.PartitionLossPolicy',
        value: 'IGNORE'
    },
    atomicityMode: {
        clsName: 'org.apache.ignite.cache.CacheAtomicityMode'
    },
    memoryMode: {
        clsName: 'org.apache.ignite.cache.CacheMemoryMode',
        value: 'ONHEAP_TIERED'
    },
    onheapCacheEnabled: false,
    offHeapMaxMemory: -1,
    startSize: 1500000,
    swapEnabled: false,
    sqlOnheapRowCacheSize: 10240,
    longQueryWarningTimeout: 3000,
    snapshotableIndex: false,
    sqlEscapeAll: true,
    sqlSchema: 'PUBLIC',
    storeKeepBinary: false,
    loadPreviousValue: false,
    cacheStoreFactory: {
        DocumentLoadOnlyStoreFactory: {
            batchSize: 512,            
            parallelLoadCacheMinimumThreshold: 512,            
            streamerEnabled: true
        },
        CacheJdbcPojoStoreFactory: {
            batchSize: 512,
            maximumWriteAttempts: 2,
            parallelLoadCacheMinimumThreshold: 512,
            sqlEscapeAll: true
        }
    },
    storeConcurrentLoadAllThreshold: 5,
    readThrough: false,
    writeThrough: false,
    writeBehindEnabled: false,
    writeBehindBatchSize: 512,
    writeBehindFlushSize: 10240,
    writeBehindFlushFrequency: 5000,
    writeBehindFlushThreadCount: 1,
    writeBehindCoalescing: true,
    maxConcurrentAsyncOperations: 500,
    defaultLockTimeout: 0,
    atomicWriteOrderMode: {
        clsName: 'org.apache.ignite.cache.CacheAtomicWriteOrderMode'
    },
    writeSynchronizationMode: {
        clsName: 'org.apache.ignite.cache.CacheWriteSynchronizationMode',
        value: 'PRIMARY_SYNC'
    },
    rebalanceMode: {
        clsName: 'org.apache.ignite.cache.CacheRebalanceMode',
        value: 'ASYNC'
    },
    rebalanceBatchSize: 524288,
    rebalanceBatchesPrefetchCount: 2,
    rebalanceOrder: 0,
    rebalanceDelay: 0,
    rebalanceTimeout: 10000,
    rebalanceThrottle: 0,
    statisticsEnabled: false,
    managementEnabled: false,
    nearConfiguration: {
        nearStartSize: 375000
    },
    clientNearConfiguration: {
        nearStartSize: 375000
    },
    evictionPolicy: {
        batchSize: 1,
        maxSize: 100000
    },
    queryMetadata: 'Configuration',
    queryDetailMetricsSize: 0,
    queryParallelism: 1,
    fields: {
        keyClsName: 'java.lang.String',
        valClsName: 'java.lang.String',
        valField: 'className',
        entries: []
    },
    defaultFieldValues: {
        keyClsName: 'java.lang.String',
        valClsName: 'java.lang.Object'
    },
    fieldsPrecision: {
        keyClsName: 'java.lang.String',
        valClsName: 'java.lang.Integer'
    },
    fieldsScale: {
        keyClsName: 'java.lang.String',
        valClsName: 'java.lang.Integer'
    },
    aliases: {
        keyClsName: 'java.lang.String',
        valClsName: 'java.lang.String',
        keyField: 'field',
        valField: 'alias',
        entries: []
    },
    indexes: {
        indexType: {
            clsName: 'org.apache.ignite.cache.QueryIndexType'
        },
        fields: {
            keyClsName: 'java.lang.String',
            valClsName: 'java.lang.Boolean',
            valField: 'direction',
            entries: []
        }
    },
    typeField: {
        databaseFieldType: {
            clsName: 'java.sql.Types'
        }
    },
    memoryPolicyName: 'default',
    diskPageCompression: {
        clsName: 'org.apache.ignite.configuration.DiskPageCompression'
    },
    sqlOnheapCacheEnabled: false,
    sqlOnheapCacheMaxSize: 0,
    storeByValue: false,
    encryptionEnabled: false,
    eventsDisabled: false,
    maxQueryIteratorsCount: 1024
};

export default class IgniteCacheDefaults {
    constructor() {
        Object.assign(this, DFLT_CACHE);
    }
}
