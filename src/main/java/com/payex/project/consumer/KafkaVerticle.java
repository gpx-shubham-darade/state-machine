package com.payex.project.consumer;

import com.payex.project.models.KafkaMessage;
import com.payex.project.models.StateMachineDB;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import com.payex.project.constant.AppConstant;
import com.payex.project.repository.RepoUtil;
import com.payex.project.service.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.config.ConfigLoader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class KafkaVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger(KafkaVerticle.class);

    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private final RedisService redisService;
    private final RepoUtil repoUtil;
    private String kafkaTopic ;


    public KafkaVerticle(RedisService redisService, RepoUtil repoUtil) {
        this.redisService = redisService;
        this.repoUtil = repoUtil;
    }

    @Override
    public void start() {

        ConfigLoader config = ConfigLoader.loadConfig();
        kafkaTopic = config.getKafkaTopic();

        // Producer config
        Map<String, String> producerConfig = new HashMap<>();
        producerConfig.put("bootstrap.servers", config.getKafkaBootstrapServers());
        producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("acks", "1");

        producer = KafkaProducer.create(vertx, producerConfig);

        // Consumer config
        Map<String, String> consumerConfig = new HashMap<>();
        consumerConfig.put("bootstrap.servers", config.getKafkaBootstrapServers());
        consumerConfig.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("group.id", config.getKafkaGroupId());
//        consumerConfig.put("auto.offset.reset", "earliest");
//        consumerConfig.put("enable.auto.commit", "false");

        consumer = KafkaConsumer.create(vertx, consumerConfig);

        consumer.handler(record -> {
            try {
            String messageValue = record.value();
            LOGGER.info("Received Kafka message: " + messageValue);

            // Validate JSON format before parsing
            if (!isValidJson(messageValue)) {
                LOGGER.error("Invalid JSON message received: " + messageValue);
                return;
            }
                JsonObject message = new JsonObject(messageValue);
                KafkaMessage kafkaMessage = message.mapTo(KafkaMessage.class);
                LOGGER.info(kafkaMessage);

            LOGGER.info("Received Kafka message for stateMachine: " + kafkaMessage.getStateMachineId());

                // Fetch state machine from MongoDB
                fetchStateMachine(kafkaMessage.getStateMachineId(), res -> {
                    if (res != null) {
                        StateMachine<String, String> stateMachine = buildStateMachine(res);

                        // Get current state from Redis
                        String currentState = redisService.getState(kafkaMessage.getProcessId());
                        if (currentState == null) {
                            currentState = res.getJsonArray("states").getString(0);
                        }

                        // Reset state machine
                        stateMachine.stop();
                        String finalCurrentState = currentState;

                        stateMachine.getStateMachineAccessor()
                                .doWithAllRegions(accessor -> accessor.resetStateMachine(
                                        new DefaultStateMachineContext<>(finalCurrentState, null, null, null)
                                ));

                        stateMachine.start();

                        boolean accepted = stateMachine.sendEvent(kafkaMessage.getEvent());

                        if (accepted) {
                            // Store new state in Redis
                            String newState = stateMachine.getState().getId();
                            redisService.saveState(kafkaMessage.getProcessId(), newState);
                            LOGGER.info("Order " + kafkaMessage.getProcessId() + " transitioned to " + newState);
                        } else {
                            LOGGER.warn("Invalid event " + kafkaMessage.getEvent() + " for order " + kafkaMessage.getProcessId());
                        }
                    } else {
                        LOGGER.error("State machine not found for ID: " + kafkaMessage.getStateMachineId());
                    }
                });
            }
            catch (Exception e) {
                LOGGER.error("Invalid message received");
            }
        });

        consumer.subscribe(config.getKafkaTopic());
    }

    public Future<JsonObject> sendEventToKafka(JsonObject reqJO) {
        KafkaMessage kafkaMessage = KafkaMessage.builder()
                .stateMachineId(reqJO.getString("stateMachineId"))
                .processId(reqJO.getString("processId"))
                .event(reqJO.getString("event"))
                .build();

        return repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES,
                        new JsonObject().put("_id", kafkaMessage.getStateMachineId()), new JsonObject())
                .compose(res -> {
                    if (res == null) {
                        return Future.succeededFuture(new JsonObject()
                                .put("success", false)
                                .put("message", "State machine not found"));
                    }

                    StateMachineDB stateMachineDB = res.mapTo(StateMachineDB.class);
                    int partition = stateMachineDB.getPartition();

                    JsonObject message = JsonObject.mapFrom(kafkaMessage);

                    LOGGER.info("Sending message to Kafka: {}", message.encodePrettily());

                    String key = String.valueOf(partition);
                    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(kafkaTopic, key, message.encode(), partition);

                    return producer.send(record)
                            .map(meta -> {
                                LOGGER.info("Message sent successfully");
                                return new JsonObject()
                                        .put("success", true)
                                        .put("message", "Message sent successfully");
                            })
                            .recover(err -> {
                                LOGGER.error("Message failed", err);
                                return Future.succeededFuture(new JsonObject()
                                        .put("success", false)
                                        .put("message", "Message failed")
                                        .put("error", err.getMessage()));
                            });
                })
                .recover(err -> {
                    LOGGER.error("Unexpected error while sending message", err);
                    return Future.succeededFuture(new JsonObject()
                            .put("success", false)
                            .put("message", "Unexpected error: " + err.getMessage()));
                });
    }


    private boolean isValidJson(String input) {
        try {
            new JsonObject(input);
            return true;
        } catch (DecodeException e) {
            return false;
        }
    }

    private void fetchStateMachine(String id, java.util.function.Consumer<JsonObject> callback) {

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id), new JsonObject())
                .onSuccess(result -> callback.accept(result))
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch state machine for ID: " + id + " - " + err.getMessage());
                    callback.accept(null);
                });
    }

    private StateMachine<String, String> buildStateMachine(JsonObject definition) {
        try {
            StateMachineBuilder.Builder<String, String> builder = StateMachineBuilder.builder();

            JsonArray statesArray = definition.getJsonArray("states");
            JsonArray eventsArray = definition.getJsonArray("events");
            JsonObject transitions = definition.getJsonObject("transitions");

            Set<String> states = new HashSet<>(statesArray.getList());
            Set<String> events = new HashSet<>(eventsArray.getList());

            // Configure States
            builder.configureStates()
                    .withStates()
                    .initial(statesArray.getString(0))
                    .states(states);

            // Configure Transitions Dynamically
            for (String state : transitions.fieldNames()) {
                JsonObject stateTransitions = transitions.getJsonObject(state);

                for (String event : stateTransitions.fieldNames()) {
                    String targetState = stateTransitions.getString(event);

                    builder.configureTransitions()
                            .withExternal()
                            .source(state)
                            .target(targetState)
                            .event(event);
                }
            }

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build state machine: " + e.getMessage(), e);
        }
    }


}