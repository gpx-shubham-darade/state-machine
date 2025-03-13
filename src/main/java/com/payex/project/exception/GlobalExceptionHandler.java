package com.payex.project.exception;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalExceptionHandler {

    private static final Logger LOGGER = LogManager.getLogger(GlobalExceptionHandler.class);

    public enum Type {
        VERTX,
        CONTEXT,
        ROUTER
    }

    private static final Map<Vertx, String> vertxMap = new ConcurrentHashMap<>();

    public static void register(Vertx vertx, String name) {
        vertxMap.put(vertx, name);
        if (vertx != null) {
            vertx.exceptionHandler(new ExceptionManager());
        }
    }

    public static void register(Router router, String name) {
        if (router != null) {
            router.errorHandler(400, GlobalExceptionHandler::handleRouterException);
            router.errorHandler(500, GlobalExceptionHandler::handleRouterException);
        }
    }

    public static void handleRouterException(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        int statusCode = ctx.statusCode();

        String errorMessage = (failure != null && failure.getMessage() != null) ? failure.getMessage() : "Unknown error";

        LOGGER.error("Router Exception: Status {} - {}", statusCode, errorMessage);

        JsonObject errorResponse = new JsonObject()
                .put("success", false)
                .put("status", statusCode)
                .put("error", errorMessage);

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encodePrettily());
    }

    public static class ExceptionManager implements Handler<Throwable> {

        @Override
        public void handle(Throwable throwable) {
            if (throwable != null) {
                LOGGER.error("Exception Caught by GlobalExceptionHandler: {}", throwable.getMessage(), throwable);
            } else {
                LOGGER.error("Unknown exception caught by GlobalExceptionHandler.");
            }
        }
    }
}
