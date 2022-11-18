package org.eventrails.server.bus;

import ch.qos.logback.core.encoder.EchoEncoder;
import org.eventrails.bus.EventRailsMessage;
import org.eventrails.bus.EventRailsMessageType;
import org.eventrails.bus.ViewUpdate;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.serialization.ObjectMapperUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageBroker {

    private ServerSocket serverSocket;
    private Map<NodeAddress, MessageBrokerConnection> connections = new ConcurrentHashMap<>();


    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true)
            new MessageBrokerConnection(serverSocket.accept(), connections).start();
    }

    private static class MessageBrokerConnection extends Thread {
        private final Map<NodeAddress, MessageBrokerConnection> connections;
        private Socket clientSocket;
        private DataOutputStream out;
        private DataInputStream in;
        private NodeAddress nodeAddress;

        public MessageBrokerConnection(Socket socket, Map<NodeAddress, MessageBrokerConnection> connections) {
            this.clientSocket = socket;
            this.connections = connections;
        }

        public void run() {
            try {
                out = new DataOutputStream(clientSocket.getOutputStream());
                in = new DataInputStream(clientSocket.getInputStream());
                while (true) {
                    String ori;
                    try {
                        ori = in.readUTF();
                    }catch (IOException e){
                        onDisconnect(nodeAddress);
                        return;
                    }
                    EventRailsMessage message = EventRailsMessage.parse(ori);
                    if (message.getType() == EventRailsMessageType.KILL) break;
                    else if (message.getType() == EventRailsMessageType.CONNECT) {
                        onConnect(message.getSource());
                    } else if (message.getType() == EventRailsMessageType.DATA) {
                        send(message.getDest(), ori);
                    }
                }
                in.close();
                out.close();
                clientSocket.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void send(NodeAddress dest, String message) {
            try {
                connections.get(dest).down(message);
            } catch (ConnectionInterruptedException e) {
                onDisconnect(dest);
            }
        }

        private void down(String message) throws ConnectionInterruptedException {
            try {
                out.writeUTF(message);
            } catch (IOException e) {
                throw new ConnectionInterruptedException();
            }
        }

        private String up() throws ConnectionInterruptedException {
            try {
                return in.readUTF();
            } catch (IOException e) {
                throw new ConnectionInterruptedException();
            }
        }

        private void onConnect(NodeAddress source) {
            this.nodeAddress = source;
            connections.put(source, this);
            for (Map.Entry<NodeAddress, MessageBrokerConnection> v : connections.entrySet()) {
                send(v.getKey(),
                        EventRailsMessage.viewUpdateMessage(
                                v.getKey(),
                                new ViewUpdate(connections.keySet(),
                                        Set.of(source), Set.of())
                        ));
            }
        }

        private void onDisconnect(NodeAddress nodeAddress) {
            connections.remove(nodeAddress);
            for (Map.Entry<NodeAddress, MessageBrokerConnection> v : connections.entrySet()) {
                send(v.getKey(),
                        EventRailsMessage.viewUpdateMessage(
                                v.getKey(),
                                new ViewUpdate(connections.keySet(),
                                        Set.of(), Set.of(nodeAddress))
                        ));
            }
        }
    }
}
