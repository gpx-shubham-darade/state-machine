package com.payex.project.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.ext.web.validation.builder.Bodies;
import io.vertx.ext.web.validation.builder.ValidationHandlerBuilder;
import io.vertx.json.schema.SchemaParser;
import io.vertx.json.schema.SchemaRouter;
import io.vertx.json.schema.SchemaRouterOptions;
import io.vertx.json.schema.common.dsl.Schemas;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.constant.AppConstant;
import com.payex.project.repository.RepoUtil;

import java.util.*;

import static io.vertx.json.schema.common.dsl.Keywords.minLength;

public class StateMachineVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(StateMachineVerticle.class);

    private MongoClient mongoClient;
    RepoUtil repoUtil;

    public StateMachineVerticle(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.repoUtil = new RepoUtil(mongoClient);
    }

    public void registerRoutes(Router router) {

        ValidationHandler fileBatchingValidation =
                ValidationHandlerBuilder.create(
                                SchemaParser.createDraft201909SchemaParser(
                                        SchemaRouter.create(Vertx.vertx(), new SchemaRouterOptions())))
                        .body(
                                Bodies.json(
                                        Schemas.objectSchema()
                                                .requiredProperty("name", Schemas.stringSchema().with(minLength(2)))
                                                .requiredProperty("states", Schemas.arraySchema().items(Schemas.stringSchema().with(minLength(2))))
                                                .requiredProperty("events", Schemas.arraySchema().items(Schemas.stringSchema().with(minLength(2))))
                                                .requiredProperty(
                                                        "transitions",
                                                        Schemas.objectSchema()
                                                                .additionalProperties(
                                                                        Schemas.objectSchema()
                                                                                .additionalProperties(Schemas.stringSchema().with(minLength(2)))
                                                                )
                                                )
                                )
                        )
                        .build();

        router.post(AppConstant.END_POINT_STATE_MACHINE)
                .handler(fileBatchingValidation)
                .handler(this::createStateMachine)
                .failureHandler(this::commonFailureHandler);

    }

    private void commonFailureHandler(RoutingContext ctx) {
        if (ctx.failure() instanceof BodyProcessorException) {
            BodyProcessorException ex = (BodyProcessorException) ctx.failure();
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Validation error: " + ex.getMessage())
                            .encode());
        } else {
            ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Internal Server Error")
                            .encode());
        }
    }

    private void createStateMachine(RoutingContext context) {

        JsonObject body = new JsonObject(context.getBody());

        if (body == null || !body.containsKey("name") || !body.containsKey("states") || !body.containsKey("events") || !body.containsKey("transitions")) {
            context.response().setStatusCode(400).end("Invalid request payload");
            return;
        }

        String id = UUID.randomUUID().toString();
        body.put("_id", id);

        repoUtil.save(AppConstant.COLLECTION_STATE_MACHINES, body)
                .onSuccess(res -> {
                    context.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("success",true).put("message","state machine created successfully").put("id", id).encode());
                    LOGGER.info("State machine created {}", id);
                })
                .onFailure(err -> {
                    context.response()
                            .setStatusCode(500)
                            .end(new JsonObject().put("success",false).put("error", "Failed to store state machine: " + err.getMessage()).encode());
                });

    }

}
