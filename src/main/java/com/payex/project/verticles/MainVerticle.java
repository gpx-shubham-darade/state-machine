package com.payex.project.verticles;

import com.payex.project.exception.GlobalExceptionHandler;
import com.payex.project.repository.RepoUtil;
import com.payex.project.server.RequestHandler;
import com.payex.project.server.Routers;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import com.payex.project.config.ConfigLoader;
import com.payex.project.consumer.KafkaVerticle;
import com.payex.project.controller.ControllerVerticle;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.service.RedisService;

import java.util.HashSet;
import java.util.Set;

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

        GlobalExceptionHandler.register(vertx, "MainVerticle");
        GlobalExceptionHandler.register(router, "MainRouter");

        enableCors(router);

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

    public static void enableCors(Router router) {
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("Access-Control-Allow-Credentials");
        allowedHeaders.add("Access-Control-Request-Method");
        allowedHeaders.add("Access-Control-Request-Headers");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("X-Forwarded-For");
        allowedHeaders.add("X-FORWARDED-For");
        allowedHeaders.add("Proxy-Client-IP");
        allowedHeaders.add("WL-Proxy-Client-IP");
        allowedHeaders.add("HTTP_CLIENT_IP");
        allowedHeaders.add("HTTP_X_FORWARDED_FOR");
        allowedHeaders.add("DeviceInfo");
        allowedHeaders.add("accept");
        allowedHeaders.add("Authorization");
        allowedHeaders.add("userId");
        allowedHeaders.add("token");
        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);
        allowedMethods.add(HttpMethod.DELETE);
        allowedMethods.add(HttpMethod.PATCH);
        allowedMethods.add(HttpMethod.OPTIONS);
        allowedMethods.add(HttpMethod.PUT);
        var corsHandler =
                CorsHandler.create(".*.")
                        .allowedHeaders(allowedHeaders)
                        .allowedMethods(allowedMethods)
                        .allowCredentials(true);
        router.route().handler(corsHandler);
    }
}
