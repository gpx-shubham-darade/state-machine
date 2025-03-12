package com.payex.project.consumer;

import com.payex.project.constant.AppConstant;
import com.payex.project.repository.RepoUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.RecordMetadata;

public class KafkaHelper {

    private final RepoUtil repoUtil;
    private final KafkaProducer<String, String> kafkaProducer;

    public KafkaHelper(RepoUtil repoUtil, KafkaProducer<String, String> kafkaProducer) {
        this.repoUtil = repoUtil;
        this.kafkaProducer = kafkaProducer;
    }
/*
    public Future<Void> sendStateMachineEvents(String stateMachineId, String orderId, String event) {
        return repoUtil.findOne("stateMachines", new JsonObject().put("_id", stateMachineId), new JsonObject())
                .compose(stateMachine -> {
                    if (stateMachine == null) {
                        return Future.failedFuture("State machine not found");
                    }

                    int partition = stateMachine.getInteger("partition", -1);
                    if (partition == -1) {
                        return Future.failedFuture("Partition number not found");
                    }

                    JsonObject message = new JsonObject()
                            .put("stateMachineId", stateMachineId)
                            .put("orderId", orderId)
                            .put("event", event);

                    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create("order-events", orderId, "message", partition);
                    kafkaProducer.send(record, ar -> {
                        if (ar.succeeded()) {
                            RecordMetadata metadata = ar.result();
                            System.out.println("Message sent to topic: " + metadata.getTopic());
                            System.out.println("Partition: " + metadata.getPartition());
                            System.out.println("Offset: " + metadata.getOffset());
                        } else {
                            System.out.println("Message failed: " + ar.cause().getMessage());
                        }
                    });
//                    return kafkaProducer.send(record).mapEmpty();
                })
                .onSuccess(v -> System.out.println("Message sent successfully"))
                .onFailure(err -> System.err.println("Failed to send message: " + err.getMessage()));

    }

 */

    public Future<JsonObject> sendStateMachineEvent(String stateMachineId, String orderId, String event) {
        Promise<JsonObject> promise = Promise.promise();

        repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", stateMachineId), new JsonObject())
                .compose(stateMachine -> {
                    if (stateMachine == null) {
                        return Future.failedFuture("State machine not found");
                    }

                    int partition = stateMachine.getInteger("partition", -1);
                    if (partition == -1) {
                        return Future.failedFuture("Partition number not found");
                    }

                    System.out.println("partition: " + partition);

                    JsonObject message = new JsonObject()
                            .put("stateMachineId", stateMachineId)
                            .put("orderId", orderId)
                            .put("event", event);

                    System.out.println("message: " + message.encodePrettily());

                    KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(
                            "abd", orderId, message.encode(), partition
                    );
                    System.out.println("Sending message to Kafka: " + message.encodePrettily());



                    KafkaProducerRecord<String, String> testRecord = KafkaProducerRecord.create("abd", "test-key", "test-message", 5);

                    kafkaProducer.send(testRecord, ar -> {
                        if (ar.succeeded()) {
                            System.out.println("Test");
//                            RecordMetadata metadata = ar.result();

//                            System.out.println("Testtttttttttt message sent successfully"+ metadata.getPartition());

                            System.out.println("Testtttttttttt message sent successfully"+ ar.result());
                            System.out.println("Testtttttttttt message sent successfully"+ ar.result().getTopic());
                            System.out.println("Testtttttttttt message sent successfully"+ ar.result().getPartition());
                            promise.complete(new JsonObject().put("hi","hello"));
                        } else {
                            System.out.println("Testttttttttttttt message failed");
                            promise.fail("hihello");
                        }
                    });


                    kafkaProducer.send(record, ar -> {
                        if (ar.succeeded()) {
                            RecordMetadata metadata = ar.result();
                            System.out.println("Message sent to topic: " + metadata.getTopic());
                            System.out.println("Partition: " + metadata.getPartition());
                            System.out.println("Offset: " + metadata.getOffset());
                            promise.complete(new JsonObject().put("hi","hello"));
                        } else {
                            System.err.println("Message failed: " + ar.cause().getMessage());
                            promise.fail(ar.cause());
                        }
                    });

                    return promise.future();
                })
                .onFailure(err -> {
                    System.err.println("Failed to send message: " + err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }


}
