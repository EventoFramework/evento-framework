package org.eventrails.bus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.time.Instant;

public class EventRailsMessageBus extends MessageBus {

    private static final Logger logger = LogManager.getLogger(EventRailsMessageBus.class);
    private final EventRailsNodeAddress address;
    private final DataOutputStream outputStream;

    protected EventRailsMessageBus(EventRailsNodeAddress address, DataInputStream dataInputStream, DataOutputStream outputStream) {
        super(subscriber -> {
            new Thread(() -> {
                while (true) {
                    try {
                        var message = EventRailsMessage.parse(dataInputStream.readUTF());
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


    public static EventRailsMessageBus create(
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
        var address = new EventRailsNodeAddress(bundleId, bundleVersion, s.getInetAddress().toString(), nodeId);
        dataOutputStream.writeUTF(EventRailsMessage.joinMessage(address));

        return new EventRailsMessageBus(
                address,
                dataInputStream,
                dataOutputStream
        );
    }

    @Override
    public void cast(NodeAddress address, Serializable message) throws Exception {
        outputStream.writeUTF(EventRailsMessage.create(getAddress(), address, message));
    }

    @Override
    public void broadcast(Serializable message) throws Exception {
        outputStream.writeUTF(EventRailsMessage.create(getAddress(), null, message));
    }

    @Override
    public NodeAddress getAddress() {
        return address;
    }
}
