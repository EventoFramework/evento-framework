package org.evento.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.time.Instant;

public class EventoMessageBus extends MessageBus {

    private static final Logger logger = LogManager.getLogger(EventoMessageBus.class);
    private final EventoNodeAddress address;
    private final DataOutputStream outputStream;

    protected EventoMessageBus(EventoNodeAddress address, DataInputStream dataInputStream, DataOutputStream outputStream) {
        super(subscriber -> {
            new Thread(() -> {
                while (true) {
                    try {
                        var message = EventoMessage.parse(dataInputStream.readUTF());
                        switch (message.getType()) {
                            case DATA -> subscriber.onMessage(message.getSource(), message.getMessage());
                            case VIEW -> {
                                var viewUpdate = ((ViewUpdate) message.getMessage());
                                subscriber.onViewUpdate(viewUpdate.getView(), viewUpdate.getNewNodes(), viewUpdate.getRemovedNodes());
                            }
                        }
                    } catch (Exception e) {
                        logger.error(e);
                    }

                }
            }).start();
        });
        this.address = address;
        this.outputStream = outputStream;
    }


    public static EventoMessageBus create(
            String bundleId,
            long bundleVersion,
            String host,
            int port) throws Exception {
        logger.info("Creating socket connection to %s:%d".formatted(host, port));
        Socket s = new Socket(host, port);
        logger.info("Connected!");
        DataInputStream dataInputStream = new DataInputStream(s.getInputStream());
        DataOutputStream dataOutputStream = new DataOutputStream(s.getOutputStream());
        var nodeId = bundleId + ":" + bundleVersion + "-" + Instant.now().toEpochMilli();
        var address = new EventoNodeAddress(bundleId, bundleVersion, s.getInetAddress().toString(), nodeId);
        dataOutputStream.writeUTF(EventoMessage.joinMessage(address));

        return new EventoMessageBus(
                address,
                dataInputStream,
                dataOutputStream
        );
    }

    @Override
    public void cast(NodeAddress address, Serializable message) throws Exception {
        outputStream.writeUTF(EventoMessage.create(getAddress(), address, message));
    }

    @Override
    public void broadcast(Serializable message) throws Exception {
        outputStream.writeUTF(EventoMessage.create(getAddress(), null, message));
    }

    @Override
    public NodeAddress getAddress() {
        return address;
    }

    @Override
    protected void disconnect() {

    }
}
