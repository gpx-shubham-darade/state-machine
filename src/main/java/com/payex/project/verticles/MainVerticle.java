package com.payex.project.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import com.payex.project.config.ConfigLoader;
import com.payex.project.consumer.KafkaVerticle;
import com.payex.project.controller.StateMachineVerticle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.service.RedisService;

public class MainVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(MainVerticle.class);

    private MongoClient mongoClient;

    @Override
    public void start(Promise<Void> startPromise) {
        ConfigLoader config = ConfigLoader.loadConfig();

        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", config.getMongoUri())
                .put("db_name", config.getMongoDatabase());

        mongoClient = MongoClient.createShared(vertx, mongoConfig);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new StateMachineVerticle(mongoClient).registerRoutes(router);

        RedisService redisService = new RedisService();
        vertx.deployVerticle(new KafkaVerticle(redisService, mongoClient));


        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.getServerPort(), http -> {
                    if (http.succeeded()) {
                        startPromise.complete();
                        LOGGER.info("HTTP server started on port " + config.getServerPort());
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
    }
}
