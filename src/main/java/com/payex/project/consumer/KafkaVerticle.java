package com.payex.project.consumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
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
    private final RedisService redisService;
    private final RepoUtil repoUtil;


    public KafkaVerticle(RedisService redisService, RepoUtil repoUtil) {
        this.redisService = redisService;
        this.repoUtil = repoUtil;
    }

    @Override
    public void start() {
        ConfigLoader config = ConfigLoader.loadConfig();

        // Kafka consumer configuration
        Map<String, String> configMap = new HashMap<>();
        configMap.put("bootstrap.servers", config.getKafkaBootstrapServers());
        configMap.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        configMap.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        configMap.put("group.id", config.getKafkaGroupId());

        consumer = KafkaConsumer.create(vertx, configMap);

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

            if (!message.containsKey("stateMachineId") || !message.containsKey("orderId") || !message.containsKey("event")) {
                LOGGER.error("Invalid message format received: " + record.value());
                return;
            }

            String stateMachineId = message.getString("stateMachineId");
            String orderId = message.getString("orderId");
            String eventStr = message.getString("event");

            LOGGER.info("Received Kafka message for stateMachine: " + stateMachineId);

                // Fetch state machine definition from MongoDB
                fetchStateMachineDefinition(stateMachineId, definition -> {
                    if (definition != null) {
                        StateMachine<String, String> stateMachine = buildStateMachine(definition);

                        // Get current state from Redis
                        String currentState = redisService.getState(orderId);
                        if (currentState == null) {
                            currentState = definition.getJsonArray("states").getString(0);
                        }

                        // Reset state machine
                        stateMachine.stop();
                        String finalCurrentState = currentState;

                        stateMachine.getStateMachineAccessor()
                                .doWithAllRegions(accessor -> accessor.resetStateMachine(
                                        new DefaultStateMachineContext<>(finalCurrentState, null, null, null)
                                ));

                        stateMachine.start();

                        boolean accepted = stateMachine.sendEvent(eventStr);

                        if (accepted) {
                            // Store new state in Redis
                            String newState = stateMachine.getState().getId();
                            redisService.saveState(orderId, newState);
                            LOGGER.info("Order " + orderId + " transitioned to " + newState);
                        } else {
                            LOGGER.warn("Invalid event " + eventStr + " for order " + orderId);
                        }
                    } else {
                        LOGGER.error("State machine definition not found for ID: " + stateMachineId);
                    }
                });
            }
            catch (IllegalArgumentException e) {
                LOGGER.error("Invalid event received");
            }
        });

        consumer.subscribe(config.getKafkaTopic());
    }

    private boolean isValidJson(String input) {
        try {
            new JsonObject(input);
            return true;
        } catch (DecodeException e) {
            return false;
        }
    }

    private void fetchStateMachineDefinition(String id, java.util.function.Consumer<JsonObject> callback) {

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id), new JsonObject())
                .onSuccess(result -> callback.accept(result))
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());
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