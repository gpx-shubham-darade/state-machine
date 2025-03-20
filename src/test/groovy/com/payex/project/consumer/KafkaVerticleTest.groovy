package com.payex.project.consumer


import com.payex.project.service.RedisService
import io.vertx.kafka.client.producer.KafkaProducer
import com.payex.project.repository.RepoUtil
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import io.vertx.core.Future


class KafkaVerticleTest extends Specification {

    def repoUtil = Mock(RepoUtil)
    def producer = Mock(KafkaProducer)
    def redisService = Mock(RedisService)
    def kafkaVerticle = new KafkaVerticle(redisService, repoUtil)

    def setup() {
        kafkaVerticle.producer = producer
        kafkaVerticle.kafkaTopic = "test-topic"
    }

    def "sendEventToKafka should return 404 when state machine not found"() {
        given:
        def reqJO = new JsonObject()
                .put("stateMachineId", "123")
                .put("processId", "456")
                .put("event", "START")

        when:
        repoUtil.findOne(_, _, _) >> Future.succeededFuture()
        def resultFuture = kafkaVerticle.sendEventToKafka(reqJO)
        def result = resultFuture.result()

        then:
        result.getInteger("statusCode") == 404
        result.getBoolean("success") == false
        result.getString("message") == "State machine not found"
    }

    def "should return true for a valid JSON object"() {
        given:
        def validJson = '{"key":"value"}'

        when:
        def result = kafkaVerticle.isValidJson(validJson)

        then:
        result == true
    }

    def "should return true for a valid JSON object with nested structure"() {
        given:
        def validJson = '{"user":{"name":"John","age":30}}'

        when:
        def result = kafkaVerticle.isValidJson(validJson)

        then:
        result == true
    }

    def "should return true for a valid JSON object with arrays"() {
        given:
        def validJson = '{"fruits":["apple","banana","cherry"]}'

        when:
        def result = kafkaVerticle.isValidJson(validJson)

        then:
        result == true
    }

    def "should return true for a valid JSON object with numbers, booleans, and null"() {
        given:
        def validJson = '{"count":10,"isActive":true,"data":null}'

        when:
        def result = kafkaVerticle.isValidJson(validJson)

        then:
        result == true
    }

    def "should return true for an empty JSON object"() {
        given:
        def validJson = '{}'

        when:
        def result = kafkaVerticle.isValidJson(validJson)

        then:
        result == true
    }

    def "should return false for malformed JSON (missing closing bracket)"() {
        given:
        def invalidJson = '{"key":"value"'

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for malformed JSON (unquoted key name)"() {
        given:
        def invalidJson = '{key:"value"}'

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for malformed JSON (trailing comma)"() {
        given:
        def invalidJson = '{"key":"value",}'

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for JSON with single quotes instead of double quotes"() {
        given:
        def invalidJson = "{'key':'value'}"

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for completely invalid JSON"() {
        given:
        def invalidJson = "random text"

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for empty string input"() {
        given:
        def invalidJson = ""

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for JSON array instead of object"() {
        given:
        def invalidJson = '["apple", "banana", "cherry"]'

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for whitespace only string"() {
        given:
        def invalidJson = " "

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for valid JSON but not an object (number)"() {
        given:
        def invalidJson = "12345"

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should return false for valid JSON but not an object (boolean)"() {
        given:
        def invalidJson = "true"

        when:
        def result = kafkaVerticle.isValidJson(invalidJson)

        then:
        result == false
    }

    def "should call callback with valid JsonObject when state machine is found"() {
        given:
        def id = "valid123"
        def expectedResult = new JsonObject().put("_id", "valid123").put("name", "TestState")
        def callback = Mock(java.util.function.Consumer)

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(expectedResult)

        when:
        kafkaVerticle.fetchStateMachine(id, callback)

        then:
        1 * callback.accept(expectedResult)
    }

    def "should call callback with empty JsonObject when state machine exists but has no data"() {
        given:
        def id = "emptyData"
        def expectedResult = new JsonObject() // Empty JsonObject
        def callback = Mock(java.util.function.Consumer)

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(expectedResult)

        when:
        kafkaVerticle.fetchStateMachine(id, callback)

        then:
        1 * callback.accept(expectedResult)
    }

    def "should call callback with null when no matching record is found"() {
        given:
        def id = "invalid123"
        def callback = Mock(java.util.function.Consumer)

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(null) // Simulate no matching record

        when:
        kafkaVerticle.fetchStateMachine(id, callback)

        then:
        1 * callback.accept(null)
    }

    def "should log error and call callback with null when database connection fails"() {
        given:
        def id = "anyID"
        def callback = Mock(java.util.function.Consumer)
        def errorMessage = "Database connection lost"

        repoUtil.findOne(_, _, _) >> Future.failedFuture(new RuntimeException(errorMessage)) // Simulate DB failure

        when:
        kafkaVerticle.fetchStateMachine(id, callback)

        then:
        1 * callback.accept(null)
    }

    def "should log error and call callback with null when empty ID causes a database failure"() {
        given:
        def id = ""
        def callback = Mock(java.util.function.Consumer)
        def errorMessage = "Invalid ID format"

        repoUtil.findOne(_, _, _) >> Future.failedFuture(new RuntimeException(errorMessage))

        when:
        kafkaVerticle.fetchStateMachine(id, callback)

        then:
        1 * callback.accept(null)
    }

    def "should successfully build a valid state machine"() {
        given: "A valid state machine definition"
        def defination = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        when: "The state machine is built"
        def stateMachine = kafkaVerticle.buildStateMachine(defination)

        then: "The state machine is built successfully"
        stateMachine != null
    }

    def "should successfully build a state machine with multiple states and events"() {
        given: "A valid state machine definition with multiple states and events"
        def definition = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["START", "MIDDLE", "END"])
                .put("events", ["MOVE", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("MOVE", "MIDDLE"))
                        .put("MIDDLE", new JsonObject().put("FINISH", "END"))
                )


        when: "The state machine is built"
        def stateMachine = kafkaVerticle.buildStateMachine(definition)

        then: "The state machine is built successfully"
        stateMachine != null

    }

    def "should throw exception when states are missing"() {
        given: "A state machine definition without 'states'"
        def definition = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("events", ["MOVE", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("MOVE", "MIDDLE"))
                )

        when: "The state machine is built"
        kafkaVerticle.buildStateMachine(definition)

        then: "An exception is thrown"
        def e = thrown(RuntimeException)
        e.message.contains("Failed to build state machine")
    }

    def "should throw exception when events are missing"() {
        given: "A state machine definition without 'events'"
        def definition = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["START", "MIDDLE", "END"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("MOVE", "MIDDLE"))
                )

        when: "The state machine is built"
        kafkaVerticle.buildStateMachine(definition)

        then: "An exception is thrown"
        def e = thrown(RuntimeException)
        e.message.contains("Failed to build state machine")
    }

}
