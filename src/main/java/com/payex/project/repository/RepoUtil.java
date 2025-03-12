package com.payex.project.repository;

import com.payex.project.config.ConfigLoader;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RepoUtil {
    private static final Logger LOGGER = LogManager.getLogger(RepoUtil.class);

    private final MongoClient mongoClient;

    public RepoUtil(Vertx vertx) {
        ConfigLoader config = ConfigLoader.loadConfig();

        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", config.getMongoUri())
                .put("db_name", config.getMongoDatabase());

        this.mongoClient = MongoClient.createShared(vertx, mongoConfig);
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

    public Future<JsonObject> findOneAndUpdate(
            String collectionName, JsonObject query, JsonObject update) {
        Promise<JsonObject> promise = Promise.promise();

        mongoClient
                .findOneAndUpdate(collectionName, query, new JsonObject().put("$set", update))
                .onSuccess(
                        updatedDoc -> {
                            if (updatedDoc != null) {
                                LOGGER.info("Document updated: " + updatedDoc);
                                promise.complete(updatedDoc);
                            } else {
                                LOGGER.info("No document found for update");
                                promise.complete();
                            }
                        })
                .onFailure(
                        err -> {
                            LOGGER.error("Failed to  update document : " + err);
                            promise.fail(err.getCause());
                        });

        return promise.future();
    }

    public Future<JsonObject> findOneAndDelete(String collectionName, JsonObject query) {
        Promise<JsonObject> promise = Promise.promise();

        mongoClient.findOneAndDelete(collectionName, query)
                .onSuccess(result -> {
                    if (result != null) {
                        LOGGER.info("Document deleted from collection '{}' : {}", collectionName, result.encodePrettily());
                        promise.complete(result);
                    } else {
                        LOGGER.warn("No document found in collection '{}' for query: {}", collectionName, query.encode());
                        promise.fail("No document found matching the query.");
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Error deleting document from '{}' for query {}: {}", collectionName, query.encode(), err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }

}
