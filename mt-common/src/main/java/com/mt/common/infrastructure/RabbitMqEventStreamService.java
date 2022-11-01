package com.mt.common.infrastructure;

import static com.mt.common.CommonConstant.EXCHANGE_NAME;

import com.mt.common.domain.CommonDomainRegistry;
import com.mt.common.domain.model.clazz.ClassUtility;
import com.mt.common.domain.model.domain_event.MqHelper;
import com.mt.common.domain.model.domain_event.SagaEventStreamService;
import com.mt.common.domain.model.domain_event.StoredEvent;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RabbitMqEventStreamService implements SagaEventStreamService {

    public static final Map<Thread, Channel> pubChannel = new HashMap<>();
    public static final Map<Thread, Channel> subChannel = new HashMap<>();
    private final boolean autoAck = false;
    @Value("${spring.application.name}")
    private String appName;
    @Resource
    private Environment env;
    private final Connection connectionPub;
    private final Connection connectionSub;

    public RabbitMqEventStreamService(@Value("${mt.url.support.mq}") final String url) {
        log.debug("initializing event stream service with url {}", url);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(url);
        try {
            connectionSub = factory.newConnection();
        } catch (IOException | TimeoutException e) {
            log.error("unable to create subscribe connection", e);
            throw new EventStreamException();
        }
        try {
            connectionPub = factory.newConnection();
        } catch (IOException | TimeoutException e) {
            log.error("unable to create publish connection", e);
            try {
                connectionSub.close();
            } catch (IOException ex) {
                log.error("error during close subscribe connection", ex);
            }
            throw new EventStreamException();
        }
        log.debug("event stream service initialize success");
    }

    /**
     * release resource
     */
    public void releaseResource() {
        log.info("closing event stream resource");
        try {
            connectionSub.close();
        } catch (IOException ex) {
            log.error("error during close subscribe connection", ex);
        }
        try {
            connectionPub.close();
        } catch (IOException ex) {
            log.error("error during close publish connection", ex);
        }
        subChannel.values().forEach(e -> {
            try {
                e.close();
            } catch (TimeoutException | IOException ex) {
                log.error("error during close subscribe channel", ex);
            }
        });
        pubChannel.values().forEach(e -> {
            try {
                e.close();
            } catch (TimeoutException | IOException ex) {
                log.error("error during close subscribe channel", ex);
            }
        });
    }

    @Override
    public void subscribe(String subscribedApplicationName, boolean internal,
                          @Nullable String fixedQueueName, Consumer<StoredEvent> consumer,
                          String... topics) {
        String routingKeyWithoutTopic =
            subscribedApplicationName + "." + (internal ? "internal" : "external") + ".";
        String queueName;
        boolean autoDelete = false;
        if (fixedQueueName != null) {
            queueName = fixedQueueName;
        } else {
            //auto delete random generated queue
            autoDelete = true;
            long id = CommonDomainRegistry.getUniqueIdGeneratorService().id();
            String s;
            if (topics.length == 1) {
                s = MqHelper.handlerOf(appName, topics[0]);
            } else {
                s = MqHelper.handlerOf(appName, "combined_events");
            }
            queueName = Long.toString(id, 36) + "_" + s;
        }
        Thread thread = Thread.currentThread();
        Channel channel = subChannel.get(thread);
        if (channel == null) {
            try {
                channel = connectionSub.createChannel();
            } catch (IOException e) {
                log.error(
                    "unable create subscribe channel for {} with routing key {} and queue name {}",
                    subscribedApplicationName, routingKeyWithoutTopic, queueName, e);
                throw new EventStreamException();
            }
            subChannel.put(thread, channel);
        }
        try {
            channel.queueDeclare(queueName, true, false, autoDelete, null);
            //@todo find out proper prefetch value, this requires test in prod env
            channel.basicQos(10);
            checkExchange(channel);
            for (String topic : topics) {
                channel.queueBind(queueName, EXCHANGE_NAME, routingKeyWithoutTopic + topic);
            }
        } catch (IOException e) {
            log.error("unable create queue for {} with routing key {} and queue name {}",
                subscribedApplicationName, routingKeyWithoutTopic, queueName, e);
            throw new EventStreamException();
        }
        Channel finalChannel = channel;
        try {
            channel.basicConsume(queueName, autoAck, (consumerTag, delivery) -> {
                log.trace("mq message received");
                String s = new String(delivery.getBody(), StandardCharsets.UTF_8);
                StoredEvent event =
                    CommonDomainRegistry.getCustomObjectSerializer()
                        .deserialize(s, StoredEvent.class);
                log.debug("handling {} with id {}", ClassUtility.getShortName(event.getName()),
                    event.getId());
                try {
                    consumer.accept(event);
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                    finalChannel.basicAck(deliveryTag, false);
                } catch (Exception ex) {
                    log.error("error during consume, catch error to maintain connection", ex);
                }
                log.trace("mq message consumed");
            }, consumerTag -> {
            });
        } catch (IOException e) {
            log.error("unable consume message for {} with routing key {} and queue name {}",
                subscribedApplicationName, routingKeyWithoutTopic, queueName, e);
            throw new EventStreamException();
        }
    }

    @Override
    public void replyOf(String subscribedApplicationName, boolean internal, String eventName,
                        Consumer<StoredEvent> consumer) {
        subscribe(subscribedApplicationName, internal, MqHelper.handleReplyOf(appName, eventName),
            consumer, MqHelper.replyOf(eventName));
    }

    @Override
    public void replyCancelOf(String subscribedApplicationName, boolean internal, String eventName,
                              Consumer<StoredEvent> consumer) {
        subscribe(subscribedApplicationName, internal,
            MqHelper.handleReplyCancelOf(appName, eventName), consumer,
            MqHelper.replyCancelOf(eventName));
    }

    @Override
    public void cancelOf(String subscribedApplicationName, boolean internal, String eventName,
                         Consumer<StoredEvent> consumer) {
        subscribe(subscribedApplicationName, internal, MqHelper.handleCancelOf(appName, eventName),
            consumer, MqHelper.cancelOf(eventName));
    }

    @Override
    public void of(String subscribedApplicationName, boolean internal, String eventName,
                   Consumer<StoredEvent> consumer) {
        subscribe(subscribedApplicationName, internal, MqHelper.handlerOf(appName, eventName),
            consumer, eventName);
    }

    @Override
    public void next(String appName, boolean internal, String topic, StoredEvent event) {
        String routingKey = appName + "." + (internal ? "internal" : "external") + "." + topic;
        log.debug("publish next event id {} with routing key {}", event.getId(), routingKey);
        Thread thread = Thread.currentThread();

        Channel channel = pubChannel.get(thread);
        if (channel == null) {
            try {
                channel = connectionPub.createChannel();
            } catch (IOException e) {
                log.error("unable create channel for {} with routing key {}",
                    appName, routingKey, e);
                throw new EventStreamException();
            }
            pubChannel.put(thread, channel);
        }
        try {
            checkExchange(channel);
            channel.confirmSelect();
            channel.basicPublish(EXCHANGE_NAME, routingKey,
                null,
                CommonDomainRegistry.getCustomObjectSerializer().serialize(event)
                    .getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(5_000);
        } catch (IOException | TimeoutException | InterruptedException e) {
            log.error("unable publish message for {} with routing key {}",
                appName, routingKey, e);
            throw new EventStreamException();
        }
    }

    @Override
    public void next(StoredEvent event) {
        next(appName, event.isInternal(), event.getTopic(), event);
    }


    private void checkExchange(Channel channel) throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
    }

    private static class EventStreamException extends RuntimeException {
    }
}
