package com.payex.project.controller;

import com.payex.project.models.StateMachineDB;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.constant.AppConstant;
import com.payex.project.repository.RepoUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


public class ControllerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger(ControllerVerticle.class);
    private final RepoUtil repoUtil;

    public ControllerVerticle(RepoUtil repoUtil) {
        this.repoUtil = repoUtil;
    }

    public Future<JsonObject> createStateMachine(JsonObject reqJO) {
        Promise<JsonObject> promise = Promise.promise();

        String id = reqJO.getString("stateMachineName");
        int partition = ThreadLocalRandom.current()
                .nextInt(AppConstant.MIN_PARTITION, AppConstant.MAX_PARTITION);

        StateMachineDB stateMachineDB =
                StateMachineDB.builder()
                        ._id(id)
                        .stateMachineName(id)
                        .states(reqJO.getJsonArray("states").getList())
                        .events(reqJO.getJsonArray("events").getList())
                        .transitions(reqJO.getJsonObject("transitions").mapTo(Map.class))
                        .partition(partition)
                        .build();

        if (!isValidTransitions(stateMachineDB)) {
            promise.complete(new JsonObject()
                    .put("statusCode", 400)
                    .put("success", false)
                    .put("message", "Invalid transitions: Missing states or events"));
            return promise.future();
        }

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id), new JsonObject())
                .onSuccess(res -> {
                    if (res != null) {
                        JsonObject response = new JsonObject()
                                .put("statusCode", 409)
                                .put("success", false)
                                .put("message", "State machine with Name already exists");
                        LOGGER.warn("State machine with ID {} already exists", id);
                        promise.complete(response);
                        return;
                    }

                    repoUtil.save(AppConstant.COLLECTION_STATE_MACHINES, JsonObject.mapFrom(stateMachineDB))
                            .onSuccess(result -> {
                                JsonObject response = new JsonObject()
                                        .put("statusCode", 201)
                                        .put("success", true)
                                        .put("message", "State machine created successfully")
                                        .put("id", id);
                                LOGGER.info("State machine created: {}", id);
                                promise.complete(response);
                            })
                            .onFailure(err -> {
                                JsonObject errorResponse = new JsonObject()
                                        .put("statusCode", 500)
                                        .put("success", false)
                                        .put("error", "Failed to store state machine: " + err.getMessage());
                                LOGGER.info("Failed to store state machine: {}" + err.getMessage(), id);
                                promise.fail(errorResponse.encode());
                            });

                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("statusCode", 500)
                            .put("success", false)
                            .put("error", "Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();

    }

    public Future<JsonObject>  getStateMachine(String id) {
        Promise<JsonObject> promise = Promise.promise();

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id), new JsonObject())
                .onSuccess(res -> {
                    if (res == null) {
                        JsonObject response = new JsonObject()
                                .put("statusCode", 404)
                                .put("success", false)
                                .put("message", "State machine not found");
                        promise.complete(response);
                    } else {
                        StateMachineDB stateMachineDB = res.mapTo(StateMachineDB.class);
                        JsonObject response = new JsonObject()
                                .put("statusCode", 200)
                                .put("success", true)
                                .put("message", "State machine found")
                                .put("stateMachine", stateMachineDB);
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("statusCode", 500)
                            .put("success", false)
                            .put("error", "Failed to fetch state machine definition for ID: " + id + " - " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();
    }

    public Future<JsonObject>  updateStateMachine(String id, JsonObject reqJO) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject query = new JsonObject().put("_id", id);

        StateMachineDB stateMachineDB =
                StateMachineDB.builder()
                        .states(reqJO.getJsonArray("states").getList())
                        .events(reqJO.getJsonArray("events").getList())
                        .transitions(reqJO.getJsonObject("transitions").mapTo(Map.class))
                        .build();

        if (!isValidTransitions(stateMachineDB)) {
            promise.complete(new JsonObject()
                    .put("statusCode", 400)
                    .put("success", false)
                    .put("message", "Invalid transitions: Missing states or events"));
            return promise.future();
        }

        JsonObject updateData = JsonObject.mapFrom(stateMachineDB);

        updateData.remove("_id");
        updateData.remove("stateMachineName");
        updateData.remove("partition");

        repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, updateData)
                .onSuccess(result -> {
                    if (result== null) {
                        JsonObject response = new JsonObject()
                                .put("statusCode", 404)
                                .put("success", false)
                                .put("message", "State machine not found");
                        promise.complete(response);
                    } else {
                        JsonObject response = new JsonObject()
                                .put("statusCode", 200)
                                .put("success", true)
                                .put("message", "State machine updated successfully");
                        LOGGER.info("State machine {} updated successfully", id);
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to update state machine {}: {}", id, err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("statusCode", 500)
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
                                .put("statusCode", 404)
                                .put("success", false)
                                .put("message", "State machine not found");
                        promise.complete(response);
                    }
                    else {
                        JsonObject response = new JsonObject()
                                .put("statusCode", 200)
                                .put("success", false)
                                .put("message", "State machine deleted successfully");
                        promise.complete(response);
                    }
                })
                .onFailure(err -> {

                    LOGGER.error("Failed to delete state machine for ID: " + id + " - " + err.getMessage());

                    JsonObject errorResponse = new JsonObject()
                            .put("statusCode", 500)
                            .put("success", false)
                            .put("error", "Failed to delete state machine for ID: " + id + " - " + err.getMessage());
                    promise.fail(errorResponse.encode());
                });
        return promise.future();
    }

    private boolean isValidTransitions(StateMachineDB stateMachineDB) {
        Set<String> stateSet = new HashSet<>(stateMachineDB.getStates());
        Set<String> eventSet = new HashSet<>(stateMachineDB.getEvents());
        Map<String, Map<String, String>> transitions = stateMachineDB.getTransitions();

        for (String fromState : transitions.keySet()) {
            if (!stateSet.contains(fromState)) {
                LOGGER.error("Invalid transition: '{}' is not a defined state", fromState);
                return false;
            }

            Map<String, String> stateTransitions = transitions.get(fromState);
            for (String event : stateTransitions.keySet()) {
                if (!eventSet.contains(event)) {
                    LOGGER.error("Invalid transition: Event '{}' is not defined", event);
                    return false;
                }

                String nextState = stateTransitions.get(event);
                if (!stateSet.contains(nextState)) {
                    LOGGER.error("Invalid transition: Target state '{}' is not defined", nextState);
                    return false;
                }
            }
        }
        return true;
    }


}
