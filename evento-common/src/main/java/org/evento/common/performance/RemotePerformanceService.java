package org.evento.common.performance;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.application.Message;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class RemotePerformanceService extends PerformanceService {

    private final MessageBus messageBus;
    private final String serverNodeName;

    public RemotePerformanceService(MessageBus messageBus,
                                    String serverNodeName) {
        super();
        this.messageBus = messageBus;
        this.serverNodeName = serverNodeName;
    }

    public RemotePerformanceService(MessageBus messageBus,
                                    String serverNodeName,
                                    double rate) {
        super(rate);
        this.messageBus = messageBus;
        this.serverNodeName = serverNodeName;
    }


    @Override
    public void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) throws Exception {
        messageBus.cast(messageBus.findNodeAddress(serverNodeName), message);
    }

    @Override
    public void sendInvocationMetricMessage(PerformanceInvocationsMessage message) throws Exception {
        messageBus.cast(messageBus.findNodeAddress(serverNodeName), message);
    }


}
