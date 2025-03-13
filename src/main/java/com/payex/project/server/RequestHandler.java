package com.payex.project.server;

import com.payex.project.consumer.KafkaVerticle;
import com.payex.project.controller.ControllerVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RequestHandler.class);
    private final ControllerVerticle controllerVerticle;
    private final KafkaVerticle kafkaVerticle;

    public RequestHandler(KafkaVerticle kafkaVerticle, ControllerVerticle controllerVerticle) {
        this.kafkaVerticle = kafkaVerticle;
        this.controllerVerticle = controllerVerticle;
    }

    public void createStateMachine(RoutingContext ctx) {
        try {
            JsonObject reqJO = ctx.body().asJsonObject();
            controllerVerticle
                    .createStateMachine(reqJO)
                    .onSuccess(
                            res -> {
                                int statusCode = res.getInteger("statusCode", 200);
                                ctx.response().setStatusCode(statusCode).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                JsonObject errorResponse = new JsonObject(failed.getMessage());
                                int statusCode = errorResponse.getInteger("statusCode", 500);
                                ctx.response().setStatusCode(statusCode).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }

    public void getStateMachine(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            controllerVerticle
                    .getStateMachine(id)
                    .onSuccess(
                            res -> {
                                int statusCode = res.getInteger("statusCode", 200);
                                ctx.response().setStatusCode(statusCode).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                JsonObject errorResponse = new JsonObject(failed.getMessage());
                                int statusCode = errorResponse.getInteger("statusCode", 500);
                                ctx.response().setStatusCode(statusCode).end(failed.getMessage());
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
            controllerVerticle
                    .updateStateMachine(id, reqJO)
                    .onSuccess(
                            res -> {
                                int statusCode = res.getInteger("statusCode", 200);
                                ctx.response().setStatusCode(statusCode).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                JsonObject errorResponse = new JsonObject(failed.getMessage());
                                int statusCode = errorResponse.getInteger("statusCode", 500);
                                ctx.response().setStatusCode(statusCode).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }

    public void deleteStateMachine(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            controllerVerticle
                    .deleteStateMachine(id)
                    .onSuccess(
                            res -> {
                                int statusCode = res.getInteger("statusCode", 200);
                                ctx.response().setStatusCode(statusCode).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                JsonObject errorResponse = new JsonObject(failed.getMessage());
                                int statusCode = errorResponse.getInteger("statusCode", 500);
                                ctx.response().setStatusCode(statusCode).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }


    public void sendEventToKafka(RoutingContext ctx) {
        try {
            JsonObject reqJO = ctx.body().asJsonObject();
            kafkaVerticle
                    .sendEventToKafka(reqJO)
                    .onSuccess(
                            res -> {
                                int statusCode = res.getInteger("statusCode", 200);
                                ctx.response().setStatusCode(statusCode).end(res.encodePrettily());
                            })
                    .onFailure(
                            failed -> {
                                JsonObject errorResponse = new JsonObject(failed.getMessage());
                                int statusCode = errorResponse.getInteger("statusCode", 500);
                                ctx.response().setStatusCode(statusCode).end(failed.getMessage());
                            });
        } catch (Exception e) {
            LOGGER.error(e);
            ctx.response().setStatusCode(500).end(e.getMessage());
        }
    }
}

