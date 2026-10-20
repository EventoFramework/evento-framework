package com.evento.server.es.personaldata;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.personaldata.ForgetSubjectRequest;
import com.evento.common.personaldata.ForgetSubjectResponse;
import com.evento.common.personaldata.SubjectKeyRequest;
import com.evento.common.personaldata.SubjectKeyResponse;
import com.evento.server.bus.lifecycle.BusLifecycle;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Answers the bundles' {@link SubjectKeyRequest} and {@link ForgetSubjectRequest} from the
 * {@link SubjectKeyStore}, on this server. Both are straight: no exactly-once slot, so no key is
 * ever kept in the broker's reply cache (asking again is harmless, forgetting twice too).
 */
@Component
public class SubjectKeyRequestHandlers {

    public SubjectKeyRequestHandlers(BusLifecycle lifecycle, SubjectKeyStore keys) {
        var codec = new AdminPayloadCodec();
        lifecycle.registerLocalHandler(SubjectKeyRequest.class.getSimpleName(), payload -> {
            var request = codec.decodeRequest(payload);
            var body = (SubjectKeyRequest) request.getBody();
            return answer(codec, request.getCorrelationId(),
                    new SubjectKeyResponse(keys.key(body.getSubject(), body.isCreate()).orElse(null)));
        });
        lifecycle.registerLocalHandler(ForgetSubjectRequest.class.getSimpleName(), payload -> {
            var request = codec.decodeRequest(payload);
            var body = (ForgetSubjectRequest) request.getBody();
            return answer(codec, request.getCorrelationId(),
                    new ForgetSubjectResponse(keys.forget(body.getSubject())));
        });
        lifecycle.registerStraightPayloadType(SubjectKeyRequest.class.getSimpleName());
        lifecycle.registerStraightPayloadType(ForgetSubjectRequest.class.getSimpleName());
    }

    private static byte[] answer(AdminPayloadCodec codec, String correlationId, Serializable body) throws Exception {
        var response = new EventoResponse();
        response.setCorrelationId(correlationId);
        response.setBody(body);
        response.setTimestamp(System.currentTimeMillis());
        return codec.encodeResponse(response);
    }
}
