package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.common.SerializedObject;

/**
 * Legacy v1/v2-compat carrier for a serialized query response.
 *
 * <p>In v2 the transport path no longer uses this class: {@code ProjectionManager}
 * puts the {@link QueryResponse} object directly into {@code EventoResponse.body}
 * and {@code AdminPayloadCodec} CBOR-encodes it with full polymorphic type info.
 * This class is retained so that any receiver that still reads an older wire
 * format (where the body was a {@code SerializedQueryResponse}) can deserialize
 * and call {@link #getObject()} without crashing.
 */
public class SerializedQueryResponse<T extends QueryResponse<?>> extends SerializedObject<T> {

    public SerializedQueryResponse(T object) {
        super(object);
    }

    public SerializedQueryResponse() {
    }
}
