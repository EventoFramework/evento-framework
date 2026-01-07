package com.evento.common.utils;

import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.internal.EventoMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;

import java.io.Closeable;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class BusTestUtils {

    private static final HashMap<Object, Set<String>> done = new HashMap<>();


    public static boolean check(Object message, String check, Closeable buffer){
        var isIgnore = check.contains("Ignore");
        try {
            if (message instanceof EventoRequest m) {
                if (m.getBody() instanceof Message<?> bm) {
                    var ser = bm.getSerializedPayload().getSerializedObject();
                    if (ser.contains(check)) {
                        var test = ser.contains("\"" + check + "\":true");
                        var msgDone = done.computeIfAbsent(message, e -> new HashSet<>());
                        if (test && !msgDone.contains(check)) {
                            if(isIgnore){
                                return false;
                            }
                            buffer.close();
                            msgDone.add(check);
                        }
                    }

                }
            } else if (message instanceof EventoResponse m) {
                if (m.getBody() instanceof Message<?> bm) {
                    var ser = bm.getSerializedPayload().getSerializedObject();
                    if (ser.contains(check)) {
                        var test = ser.contains("\"" + check + "\":true");
                        var msgDone = done.computeIfAbsent(message, e -> new HashSet<>());
                        if (test && !msgDone.contains(check)) {
                            if(isIgnore){
                                return false;
                            }
                            buffer.close();
                            msgDone.add(check);
                        }
                    }
                }
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        return true;

    }
}
