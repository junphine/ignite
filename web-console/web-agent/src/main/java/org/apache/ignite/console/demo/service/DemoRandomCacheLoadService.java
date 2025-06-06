

package org.apache.ignite.console.demo.service;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.console.demo.AgentDemoUtils;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;

import static org.apache.ignite.internal.processors.query.QueryUtils.DFLT_SCHEMA;

/**
 * Demo service. Create cache and populate it by random int pairs.
 */
public class DemoRandomCacheLoadService implements Service {
    /** Ignite instance. */
    @IgniteInstanceResource
    private Ignite ignite;

    /** Thread pool to execute cache load operations. */
    private ScheduledExecutorService cachePool;

    /** */
    public static final String RANDOM_CACHE_NAME = "RandomCache";

    /** Employees count. */
    private static final int RND_CNT = 1024;

    /** */
    private static final Random rnd = new Random();

    /** Maximum count read/write key. */
    private final int cnt;

    /**
     * @param cnt Maximum count read/write key.
     */
    public DemoRandomCacheLoadService(int cnt) {
        this.cnt = cnt;
    }

    /** {@inheritDoc} */
    @Override public void cancel(ServiceContext ctx) {
        if (cachePool != null)
            cachePool.shutdownNow();
    }

    /** {@inheritDoc} */
    @Override public void init(ServiceContext ctx) {
        ignite.getOrCreateCache(cacheRandom());

        cachePool = AgentDemoUtils.newScheduledThreadPool(2, "demo-sql-random-load-cache-tasks");
    }

    /** {@inheritDoc} */
    @Override public void execute(ServiceContext ctx) {
        cachePool.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() {
                try {
                    for (String cacheName : ignite.cacheNames()) {
                        IgniteCache<Integer, Integer> cache = ignite.cache(cacheName);

                        if (cache != null &&
                            !DemoCachesLoadService.DEMO_CACHES.contains(cacheName) &&
                            !DFLT_SCHEMA.equalsIgnoreCase(cache.getConfiguration(CacheConfiguration.class).getSqlSchema())) {
                            for (int i = 0, n = 1; i < cnt; i++, n++) {
                                Integer key = rnd.nextInt(RND_CNT);
                                Integer val = rnd.nextInt(RND_CNT);

                                cache.put(key, val);

                                if (rnd.nextInt(100) < 30)
                                    cache.remove(key);
                            }
                        }
                    }
                }
                catch (Throwable e) {
                    if (!e.getMessage().contains("cache is stopped"))
                        ignite.log().error("Cache write task execution error", e);
                }
            }
        }, 10, 3, TimeUnit.SECONDS);
    }

    /**
     * Configure cacheCountry.
     */
    private static <K, V> CacheConfiguration<K, V> cacheRandom() {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(RANDOM_CACHE_NAME);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 32));
        ccfg.setQueryDetailMetricsSize(10);
        ccfg.setStatisticsEnabled(true);
        ccfg.setIndexedTypes(Integer.class, Integer.class);

        return ccfg;
    }
}
