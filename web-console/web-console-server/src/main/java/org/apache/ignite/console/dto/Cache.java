

package org.apache.ignite.console.dto;

import java.util.UUID;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;

import org.apache.ignite.console.messages.WebConsoleMessageSource;
import org.springframework.context.support.MessageSourceAccessor;

import io.vertx.core.json.JsonObject;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.console.utils.Utils.toJson;

/**
 * DTO for cluster cache.
 */
public class Cache extends DataObject {
    /** */
    private String name;

    /** */
    private CacheMode cacheMode;

    /** */
    private CacheAtomicityMode atomicityMode;

    /** */
    private int backups;

    /**
     * @param json JSON data.
     * @return New instance of cache DTO.
     */
    public static Cache fromJson(JsonObject json) {
    	UUID id = getUUID(json,"id");
        MessageSourceAccessor messages = WebConsoleMessageSource.getAccessor();

        if (id == null)
            throw new IllegalStateException(messages.getMessage("err.cache-id-not-found"));

        return new Cache(
            id,
            json.getString("name"),
            CacheMode.valueOf(json.getString("cacheMode", PARTITIONED.name())),
            CacheAtomicityMode.valueOf(json.getString("atomicityMode", ATOMIC.name())),
            json.getInteger("backups", 0),
            toJson(json)
        );
    }

    /**
     * Full constructor.
     *
     * @param id ID.
     * @param name Cache name.
     * @param json JSON payload.
     */
    public Cache(
        UUID id,
        String name,
        CacheMode cacheMode,
        CacheAtomicityMode atomicityMode,
        int backups,
        String json
    ) {
        super(id, json);

        this.name = name;
        this.cacheMode = cacheMode;
        this.atomicityMode = atomicityMode;
        this.backups = backups;
    }

    /**
     * @return Cache name.
     */
    public String name() {
        return name;
    }

    /**
     * @return Cache mode.
     */
    public CacheMode cacheMode() {
        return cacheMode;
    }

    /**
     * @return Cache atomicity mode.
     */
    public CacheAtomicityMode atomicityMode() {
        return atomicityMode;
    }

    /**
     * @return Cache backups.
     */
    public int backups() {
        return backups;
    }

    /** {@inheritDoc} */
    @Override public JsonObject shortView() {
        return new JsonObject()
            .put("id", getId())
            .put("name", name)
            .put("cacheMode", cacheMode)
            .put("atomicityMode", atomicityMode)
            .put("backups", backups);
    }
}
