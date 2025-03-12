package com.payex.project.server;

import com.payex.project.consumer.KafkaHelper;
import com.payex.project.controller.StateMachineVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RequestHandler.class);
    private final StateMachineVerticle stateMachineVerticle;
    private final KafkaHelper kafkaHelper;

    public RequestHandler(StateMachineVerticle stateMachineVerticle, KafkaHelper kafkaHelper) {
        this.stateMachineVerticle = stateMachineVerticle;
        this.kafkaHelper = kafkaHelper;
    }

    public void createStateMachine(RoutingContext ctx) {
        try {
//            JsonObject reqJO = new JsonObject(ctx.getBody());
            JsonObject reqJO = ctx.body().asJsonObject();
            stateMachineVerticle
                    .createStateMachine(reqJO)
                    .onSuccess(
                            res -> {
                                ctx.response().setStatusCode(200).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                ctx.response().setStatusCode(500).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }

    public void getStateMachine(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            stateMachineVerticle
                    .getStateMachine(id)
                    .onSuccess(
                            res -> {
                                ctx.response().setStatusCode(200).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                ctx.response().setStatusCode(500).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }

    public void updateStateMachine(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonObject reqJO = ctx.body().asJsonObject();
            stateMachineVerticle
                    .updateStateMachine(id, reqJO)
                    .onSuccess(
                            res -> {
                                ctx.response().setStatusCode(200).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                ctx.response().setStatusCode(500).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }

    public void deleteStateMachine(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            stateMachineVerticle
                    .deleteStateMachine(id)
                    .onSuccess(
                            res -> {
                                ctx.response().setStatusCode(200).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                ctx.response().setStatusCode(500).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }


    public void sendKafkaMessage(RoutingContext ctx) {
        try {
//            JsonObject reqJO = new JsonObject(ctx.getBody());
            JsonObject reqJO = ctx.body().asJsonObject();
            kafkaHelper
                    .sendStateMachineEvent(reqJO.getString("a"),reqJO.getString("b"),reqJO.getString("c"))
                    .onSuccess(
                            res -> {
                                ctx.response().setStatusCode(200).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                ctx.response().setStatusCode(500).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }
}

