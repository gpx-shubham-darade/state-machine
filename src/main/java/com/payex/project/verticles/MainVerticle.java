package com.payex.project.verticles;

import com.payex.project.repository.RepoUtil;
import com.payex.project.server.RequestHandler;
import com.payex.project.server.Routers;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import com.payex.project.config.ConfigLoader;
import com.payex.project.consumer.KafkaVerticle;
import com.payex.project.controller.ControllerVerticle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.service.RedisService;

public class MainVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        ConfigLoader config = ConfigLoader.loadConfig();

        RepoUtil repoUtil = new RepoUtil(vertx);
        RedisService redisService = new RedisService();

        KafkaVerticle kafkaVerticle = new KafkaVerticle(redisService, repoUtil);
        ControllerVerticle controllerVerticle = new ControllerVerticle(repoUtil);

        RequestHandler requestHandler = new RequestHandler(kafkaVerticle, controllerVerticle);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new Routers(requestHandler).registerRoutes(router);

        vertx.deployVerticle(kafkaVerticle)
                .onSuccess(id -> LOGGER.info("KafkaVerticle deployed successfully."))
                .onFailure(err -> LOGGER.error("Failed to deploy KafkaVerticle", err));


        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.getServerPort())
                .onSuccess(server -> {
                    startPromise.complete();
                    LOGGER.info("HTTP server started on port {}", config.getServerPort());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to start HTTP server on port {}", config.getServerPort(), err);
                    startPromise.fail(err);
                });


    }
}
