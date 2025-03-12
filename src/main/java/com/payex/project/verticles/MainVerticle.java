package com.payex.project.verticles;

import com.payex.project.consumer.KafkaHelper;
import com.payex.project.repository.RepoUtil;
import com.payex.project.server.RequestHandler;
import com.payex.project.server.Routers;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import com.payex.project.config.ConfigLoader;
import com.payex.project.consumer.KafkaVerticle;
import com.payex.project.controller.StateMachineVerticle;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.payex.project.service.RedisService;

import java.util.HashMap;
import java.util.Map;

public class MainVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        ConfigLoader config = ConfigLoader.loadConfig();

        RepoUtil repoUtil = new RepoUtil(vertx);
        RedisService redisService = new RedisService();


        /// ////////////////////////

        // Kafka Producer Configuration
        Map<String, String> kafkaConfig = new HashMap<>();
        kafkaConfig.put("bootstrap.servers", "localhost:9092");
        kafkaConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaConfig.put("acks", "1");

        KafkaProducer<String, String> kafkaProducer = KafkaProducer.create(vertx, kafkaConfig);
        KafkaHelper kafkaHelper = new KafkaHelper(repoUtil, kafkaProducer);

        if (kafkaProducer == null) {
            LOGGER.error("Kafka Producer is null");
        } else {
            LOGGER.info("Kafka Producer initialized successfully");
        }

        KafkaProducerRecord<String, String> testRecord = KafkaProducerRecord.create("abd", "test-key", "test-message", 0);

        kafkaProducer.send(testRecord, ar -> {
            if (ar.succeeded()) {
                LOGGER.info("Test message sent successfully");
            } else {
                LOGGER.error("Test message failed", ar.cause());
            }
        });



        /// ////////////////////////

        StateMachineVerticle stateMachineVerticle = new StateMachineVerticle(repoUtil);
        RequestHandler requestHandler = new RequestHandler(stateMachineVerticle, kafkaHelper);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        new Routers(requestHandler).registerRoutes(router);

        vertx.deployVerticle(new KafkaVerticle(redisService, repoUtil), res -> {
            if (res.succeeded()) {
                LOGGER.info("KafkaVerticle deployed successfully.");
            } else {
                LOGGER.error("Failed to deploy KafkaVerticle", res.cause());
            }
        });

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
