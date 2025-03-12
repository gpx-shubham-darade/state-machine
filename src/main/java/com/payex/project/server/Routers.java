package com.payex.project.server;

import com.payex.project.constant.AppConstant;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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

import static io.vertx.json.schema.common.dsl.Keywords.minLength;


public class Routers extends AbstractVerticle {
    private final RequestHandler requestHandler;


    public Routers(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void registerRoutes(Router router) {
        router.post(AppConstant.END_POINT_STATE_MACHINE)
                .handler(fileBatchingValidation)
                .handler(requestHandler::createStateMachine)
                .failureHandler(this::commonFailureHandler);

        router.get(AppConstant.END_POINT_STATE_MACHINE + "/:id")
                .handler(requestHandler::getStateMachine)
                .failureHandler(this::commonFailureHandler);

        router.put(AppConstant.END_POINT_STATE_MACHINE + "/:id")
                .handler(fileBatchingValidation)
                .handler(requestHandler::updateStateMachine)
                .failureHandler(this::commonFailureHandler);

        router.delete(AppConstant.END_POINT_STATE_MACHINE + "/:id")
                .handler(requestHandler::deleteStateMachine)
                .failureHandler(this::commonFailureHandler);

        router.post(AppConstant.END_POINT_KAFKA_MESSAGE)
                .handler(requestHandler::sendKafkaMessage)
                .failureHandler(this::commonFailureHandler);

    }


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

    private void commonFailureHandler(RoutingContext ctx) {
        if (ctx.failure() instanceof BodyProcessorException) {
            BodyProcessorException ex = (BodyProcessorException) ctx.failure();
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("success",false)
                            .put("error", "Validation error: " + ex.getMessage())
                            .encode());
        } else {
            ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("success",false)
                            .put("error", "Internal Server Error")
                            .encode());
        }
    }


}
