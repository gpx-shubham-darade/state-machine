package com.payex.project.repository;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RepoUtil {
    private static final Logger LOGGER = LogManager.getLogger(RepoUtil.class);

    private MongoClient mongoClient;

    public RepoUtil(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public Future<String> save(String collectionName, JsonObject document) {
        Promise<String> promise = Promise.promise();

        mongoClient
                .save(collectionName, document)
                .onSuccess(
                        id -> {
                            LOGGER.info("Document saved");
                            promise.complete("Document saved");
                        })
                .onFailure(
                        err -> {
                            LOGGER.error("Failed to save document: " + err.getMessage());
                            promise.fail(err);
                        });

        return promise.future();
    }

    public Future<JsonObject> findOne(
            String collectionName, JsonObject query, JsonObject projection) {
        Promise<JsonObject> promise = Promise.promise();

        mongoClient
                .findOne(collectionName, query, projection)
                .onSuccess(
                        result -> {
                            if (result != null) {
                                LOGGER.info("Document found: " + result.encodePrettily());
                                promise.complete(result);
                            } else {
                                LOGGER.info("No document found");
                                promise.complete();
                            }
                        })
                .onFailure(
                        err -> {
                            LOGGER.error("Error fetching document: " + err.getMessage());
                            promise.fail(err);
                        });

        return promise.future();
    }
}
