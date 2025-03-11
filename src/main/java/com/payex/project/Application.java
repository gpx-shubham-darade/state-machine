package com.payex.project;

import io.vertx.core.Vertx;
import com.payex.project.verticles.MainVerticle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Application {
    private static final Logger LOGGER = LogManager.getLogger(Application.class);

    public static void main(String[] args) {

        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new MainVerticle(), result -> {
            if (result.succeeded()) {
                LOGGER.info("MainVerticle deployed successfully!");
            } else {
                LOGGER.error("Failed to deploy MainVerticle: " + result.cause());
            }
        });
    }
}
