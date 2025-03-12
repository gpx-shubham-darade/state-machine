package com.payex.project.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.constant.AppConstant;
import com.payex.project.repository.RepoUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


public class StateMachineVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger(StateMachineVerticle.class);
    private final RepoUtil repoUtil;

    public StateMachineVerticle(RepoUtil repoUtil) {
        this.repoUtil = repoUtil;
    }

    public Future<JsonObject> createStateMachine(JsonObject reqJO) {
        Promise<JsonObject> promise = Promise.promise();

        JsonArray states = reqJO.getJsonArray("states");
        JsonArray events = reqJO.getJsonArray("events");
        JsonObject transitions = reqJO.getJsonObject("transitions");

        // Validate transitions
        if (!isValidTransitions(states, events, transitions)) {
            promise.complete(new JsonObject()
                    .put("success", false)
                    .put("message", "Invalid transitions: Missing states or events"));
            return promise.future();
        }

        String id = UUID.randomUUID().toString();
        int partition = ThreadLocalRandom.current()
                .nextInt(AppConstant.MIN_PARTITION, AppConstant.MAX_PARTITION + 1);

        reqJO.put("_id", id).put("partition",partition);

        repoUtil.save(AppConstant.COLLECTION_STATE_MACHINES, reqJO)
                .onSuccess(res -> {
                    JsonObject response = new JsonObject()
                            .put("success", true)
                            .put("message", "State machine created successfully")
                            .put("id", id);
                    LOGGER.info("State machine created: {}", id);
                    promise.complete(response);
                })
                .onFailure(err -> {
                    JsonObject errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", "Failed to store state machine: " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });

        return promise.future();

    }

    public Future<JsonObject>  getStateMachine(String id) {
        Promise<JsonObject> promise = Promise.promise();

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id), new JsonObject())
                .onSuccess(stateMachine -> {
                    if (stateMachine == null) {
                        JsonObject response = new JsonObject()
                                        .put("success", false)
                                        .put("message", "State machine not found");
                        promise.complete(response);
                    } else {
                        JsonObject response = new JsonObject()
                                .put("success", true)
                                .put("message", "State machine found")
                                .put("state-machine", stateMachine);
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", "Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();
    }

    public Future<JsonObject>  updateStateMachine(String id, JsonObject reqJO) {
        Promise<JsonObject> promise = Promise.promise();

        JsonArray states = reqJO.getJsonArray("states");
        JsonArray events = reqJO.getJsonArray("events");
        JsonObject transitions = reqJO.getJsonObject("transitions");

        // Validate transitions before updating
        if (!isValidTransitions(states, events, transitions)) {
            promise.complete( new JsonObject().put("success", false)
                    .put("message", "Invalid transitions: Missing states or events"));
            return promise.future();
        }

        JsonObject query = new JsonObject().put("_id", id);

        repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, reqJO)
                .onSuccess(result -> {
                    if (result== null) {
                        JsonObject response = new JsonObject()
                                        .put("success", false)
                                        .put("message", "State machine not found");
                        promise.complete(response);
                    } else {
                        JsonObject response = new JsonObject()
                                        .put("success", true)
                                        .put("message", "State machine updated successfully");
                        LOGGER.info("State machine {} updated successfully", id);
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to update state machine {}: {}", id, err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", "Failed to update state machine: " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();
    }

    public Future<JsonObject>  deleteStateMachine(String id) {
        Promise<JsonObject> promise = Promise.promise();

        repoUtil.findOneAndDelete(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id",id))
                .onSuccess(res -> {
                    if (res== null) {

                        JsonObject response = new JsonObject()
                                .put("success", false)
                                .put("message", "State machine not found");
                        promise.complete(response);
                    }
                    else {
                        JsonObject response = new JsonObject()
                                .put("success", false)
                                .put("message", "State machine deleted successfully");
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {

                    LOGGER.error("Failed to delete state machine for ID: " + id + " - " + err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", "Failed to delete state machine for ID: " + id + " - " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();
    }



    private boolean isValidTransitions(JsonArray states, JsonArray events, JsonObject transitions) {
        Set<String> stateSet = new HashSet<>(states.getList());
        Set<String> eventSet = new HashSet<>(events.getList());

        for (String fromState : transitions.fieldNames()) {
            if (!stateSet.contains(fromState)) {
                LOGGER.error("Invalid transition: '{}' is not a defined state", fromState);
                return false;
            }

            JsonObject stateTransitions = transitions.getJsonObject(fromState);
            for (String event : stateTransitions.fieldNames()) {
                if (!eventSet.contains(event)) {
                    LOGGER.error("Invalid transition: Event '{}' is not defined", event);
                    return false;
                }

                String nextState = stateTransitions.getString(event);
                if (!stateSet.contains(nextState)) {
                    LOGGER.error("Invalid transition: Target state '{}' is not defined", nextState);
                    return false;
                }
            }
        }
        return true;
    }


}
