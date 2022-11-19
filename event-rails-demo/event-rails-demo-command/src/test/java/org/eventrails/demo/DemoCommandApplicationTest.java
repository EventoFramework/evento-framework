package org.eventrails.demo;

import ch.qos.logback.core.encoder.EchoEncoder;
import org.eventrails.bus.rabbitmq.RabbitMqMessageBus;
import org.eventrails.demo.api.command.*;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

class DemoCommandApplicationTest {


}