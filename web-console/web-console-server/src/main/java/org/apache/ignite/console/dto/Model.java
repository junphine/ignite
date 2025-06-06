

package org.apache.ignite.console.dto;

import java.util.UUID;

import org.apache.ignite.console.messages.WebConsoleMessageSource;
import org.apache.ignite.internal.util.typedef.F;
import org.springframework.context.support.MessageSourceAccessor;

import io.vertx.core.json.JsonObject;

import static java.lang.Boolean.FALSE;
import static org.apache.ignite.console.utils.Utils.toJson;

/**
 * DTO for cluster model.
 */
public class Model extends DataObject {
    /** */
    private boolean hasIdx;

    /** */
    private String keyType;

    /** */
    private String valType;
    
    private String tableComment;

    /**
     * @param json JSON data.
     * @return New instance of model DTO.
     */
    public static Model fromJson(JsonObject json) {
    	UUID id = getUUID(json,"id");
        MessageSourceAccessor messages = WebConsoleMessageSource.getAccessor();

        if (id == null)
            throw new IllegalStateException(messages.getMessage("err.model-id-not-found"));

        boolean generatePojo = FALSE.equals(json.getBoolean("generatePojo"));
        boolean missingDb = F.isEmpty(json.getString("databaseSchema")) && F.isEmpty(json.getString("databaseTable"));

        boolean hasIdx = !F.isEmpty(json.getJsonArray("keyFields")) ||
            "Annotations".equals(json.getString("queryMetadata")) && (generatePojo || missingDb);

        return new Model(
            id,
            hasIdx,
            json.getString("keyType"),
            json.getString("valueType"),
            json.getString("tableComment"),
            toJson(json)
        );
    }

    /**
     * Full constructor.
     *
     * @param id ID.
     * @param hasIdx Model has at least one index.
     * @param keyType Key type name.
     * @param valType Value type name.
     * @param json JSON payload.
     */
    public Model(UUID id, boolean hasIdx, String keyType, String valType, String tableComment,String json) {
        super(id, json);

        this.hasIdx = hasIdx;
        this.keyType = keyType;
        this.valType = valType;
        this.tableComment = tableComment;
    }

    /**
     * @return {@code true} if model has at least one index.
     */
    public boolean hasIndex() {
        return hasIdx;
    }

    /**
     * @return Key type name.
     */
    public String keyType() {
        return keyType;
    }

    /**
     * @return Value type name.
     */
    public String valueType() {
        return valType;
    }

    /** {@inheritDoc} */
    @Override public JsonObject shortView() {
        return new JsonObject()
            .put("id", getId())
            .put("hasIndex", hasIdx)
            .put("keyType", keyType)
            .put("valueType", valType)
            .put("tableComment", tableComment);
    }
}
