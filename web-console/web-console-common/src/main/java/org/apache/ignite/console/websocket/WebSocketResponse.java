

package org.apache.ignite.console.websocket;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Websocket response POJO.
 */
public class WebSocketResponse implements WebSocketEvent<Object> {
    /** */
    private String reqId;

    /** */
    private String evtType;

    /** */
    @GridToStringInclude
    private Object payload;

    /**
     * Constructor with auto generated ID.
     *
     * @param evtType Event type.
     * @param payload Payload.
     */
    public WebSocketResponse(String evtType, Object payload) {
        this(UUID.randomUUID().toString(), evtType, payload);
    }

    /**
     * Constructor.
     *
     * @param reqId Request ID.
     * @param evtType Event type.
     * @param payload Payload.
     */
    @JsonCreator
    WebSocketResponse(
        @JsonProperty("requestId") String reqId,
        @JsonProperty("eventType") String evtType,
        @JsonProperty("payload") Object payload
    ) {
        this.reqId = reqId;
        this.evtType = evtType;
        this.payload = payload;
    }

    /** {@inheritDoc} */
    @Override public String getRequestId() {
        return reqId;
    }

    /** {@inheritDoc} */
    @Override public void setRequestId(String reqId) {
        this.reqId = reqId;
    }

    /** {@inheritDoc} */
    @Override public String getEventType() {
        return evtType;
    }

    /** {@inheritDoc} */
    @Override public void setEventType(String evtType) {
        this.evtType = evtType;
    }

    /** {@inheritDoc} */
    @Override public Object getPayload() {
        return payload;
    }

    /** {@inheritDoc} */
    @Override public void setPayload(Object payload) {
        this.payload = payload;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(WebSocketResponse.class, this);
    }
}
