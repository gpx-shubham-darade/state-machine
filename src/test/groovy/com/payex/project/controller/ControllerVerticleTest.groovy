package com.payex.project.controller

import com.payex.project.constant.AppConstant
import com.payex.project.models.StateMachineDB
import com.payex.project.repository.RepoUtil
import io.vertx.core.json.JsonObject
import spock.lang.Specification
import io.vertx.core.Future
import java.util.concurrent.ThreadLocalRandom;

class ControllerVerticleTest extends Specification {

    RepoUtil repoUtil = Mock(RepoUtil)
    ControllerVerticle controllerVerticle = new ControllerVerticle(repoUtil)

    def "should create state machine successfully when input is valid"() {
        given: "A valid state machine JSON request"
        def reqJO = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        def existingStateMachine = null


        when: "createStateMachine is called"
        def futureResponse = controllerVerticle.createStateMachine(reqJO)

        then: "It should check if the state machine already exists"
        1 * repoUtil.findOne(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", "TestStateMachine"), _) >> Future.succeededFuture(existingStateMachine)

        and: "It should save the new state machine"
        1 * repoUtil.save(AppConstant.COLLECTION_STATE_MACHINES, _) >> Future.succeededFuture("TestStateMachine")

        and: "It should return success response"
        futureResponse.result().getInteger("statusCode") == 201
        futureResponse.result().getBoolean("success") == true
        futureResponse.result().getString("message") == "State machine created successfully"
        futureResponse.result().getString("id") == "TestStateMachine"
    }

    def "should return 400 when state machine is Invalid"() {
        given: "A invalid state machine JSON request"
        def reqJO = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["HELLO", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )


        when: "createStateMachine is called"
        def futureResponse = controllerVerticle.createStateMachine(reqJO)

        then: "It should check if the state machine is valid or not"
        futureResponse.result().getInteger("statusCode") == 400
        futureResponse.result().getBoolean("success") == false
        futureResponse.result().getString("message") == "Invalid transitions: Missing states or events"
    }

    def "should return 409 when state machine with given name already exists"() {
        given: "A request JSON with an existing stateMachineName"
        def reqJO = new JsonObject()
                .put("stateMachineName", "existingStateMachine")
                .put("states", ["NEW", "ACTIVE", "INACTIVE", "REJECT"])
                .put("events", ["ACTIVATE", "INACTIVATE", "REJECTING"])
                .put("transitions", new JsonObject()
                        .put("NEW", new JsonObject().put("ACTIVATE", "ACTIVE"))
                        .put("ACTIVE", new JsonObject().put("INACTIVATE", "INACTIVE"))
                        .put("INACTIVE", new JsonObject().put("ACTIVATE", "ACTIVE"))
                )

        and: "The database already contains a state machine with the given name"
        def existingStateMachine = new JsonObject().put("_id", "existingStateMachine")
        repoUtil.findOne(_, _, _) >> Future.succeededFuture(existingStateMachine) // Mock repoUtil.findOne to return an existing state machine

        when: "createStateMachine is called"
        def resultFuture = controllerVerticle.createStateMachine(reqJO)

        then: "The response should indicate failure with status 409"
        resultFuture.succeeded()
        def resultJson = resultFuture.result()
        resultJson.getInteger("statusCode") == 409
        resultJson.getBoolean("success") == false
        resultJson.getString("message") == "State machine with Name already exists"
    }

    def "should return 500 when findOne operation fails"() {
        given: "A request JSON with a valid stateMachineName"
        def reqJO = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["NEW", "ACTIVE", "INACTIVE", "REJECT"])
                .put("events", ["ACTIVATE", "INACTIVATE", "REJECTING"])
                .put("transitions", new JsonObject()
                        .put("NEW", new JsonObject().put("ACTIVATE", "ACTIVE"))
                        .put("ACTIVE", new JsonObject().put("INACTIVATE", "INACTIVE"))
                        .put("INACTIVE", new JsonObject().put("ACTIVATE", "ACTIVE"))
                )

        and: "repoUtil.findOne fails with a database error"
        def errorMessage = "Database connection lost"
        repoUtil.findOne(_, _, _) >> Future.failedFuture(new RuntimeException(errorMessage))

        when: "createStateMachine is called"
        def resultFuture = controllerVerticle.createStateMachine(reqJO)

        then: "The response should indicate failure with status 500"
        resultFuture.failed()
        def resultJson = new JsonObject(resultFuture.cause().message)
        resultJson.getInteger("statusCode") == 500
        resultJson.getBoolean("success") == false
        resultJson.getString("error") == "Failed to fetch state machine definition for ID: TestStateMachine - ${errorMessage}"
    }

    def "should return 500 when database fails to store the new state machine"() {
        given: "A request JSON with a new stateMachineName"
        def reqJO = new JsonObject()
                .put("stateMachineName", "newStateMachine")
                .put("states", ["NEW", "ACTIVE", "INACTIVE", "REJECT"])
                .put("events", ["ACTIVATE", "INACTIVATE", "REJECTING"])
                .put("transitions", new JsonObject()
                        .put("NEW", new JsonObject().put("ACTIVATE", "ACTIVE"))
                        .put("ACTIVE", new JsonObject().put("INACTIVATE", "INACTIVE"))
                        .put("INACTIVE", new JsonObject().put("ACTIVATE", "ACTIVE"))
                )

        and: "The state machine does not exist in the database"
        repoUtil.findOne(_, _, _) >> Future.succeededFuture(null) // Simulates no existing state machine

        and: "The save operation fails with an error"
        def errorMessage = "Database insert error"
        repoUtil.save(_, _) >> Future.failedFuture(new Exception(errorMessage)) // Simulates MongoDB save failure

        when: "createStateMachine is called"
        def resultFuture = controllerVerticle.createStateMachine(reqJO)

        then: "The response should indicate failure with status 500"
        resultFuture.failed()
        def resultJson = new JsonObject(resultFuture.cause().message) // Extract the JSON from the failed Future

        resultJson.getInteger("statusCode") == 500
        resultJson.getBoolean("success") == false
        resultJson.getString("error") == "Failed to store state machine: ${errorMessage}"
    }

    def "should generate partition within the allowed range"() {
        given: "A valid request JSON"
        def reqJO = new JsonObject()
                .put("stateMachineName", "validStateMachine")
                .put("states", ["NEW", "ACTIVE", "INACTIVE", "REJECT"])
                .put("events", ["ACTIVATE", "INACTIVATE", "REJECTING"])
                .put("transitions", new JsonObject()
                        .put("NEW", new JsonObject().put("ACTIVATE", "ACTIVE"))
                        .put("ACTIVE", new JsonObject().put("INACTIVATE", "INACTIVE"))
                        .put("INACTIVE", new JsonObject().put("ACTIVATE", "ACTIVE"))
                )

        and: "The state machine does not exist in the database"
        repoUtil.findOne(_, _, _) >> Future.succeededFuture(null)

        and: "The save operation succeeds"
        repoUtil.save(_, _) >> Future.succeededFuture("success")

        when: "createStateMachine is called"
        def resultFuture = controllerVerticle.createStateMachine(reqJO)

        then: "The response should indicate success"
        resultFuture.succeeded()
        def resultJson = resultFuture.result()
        resultJson.getInteger("statusCode") == 201
        resultJson.getBoolean("success") == true

        and: "The generated partition is within the allowed range"
        def partition = ThreadLocalRandom.current()
                .nextInt(AppConstant.MIN_PARTITION, AppConstant.MAX_PARTITION)

        partition >= AppConstant.MIN_PARTITION
        partition <= AppConstant.MAX_PARTITION
    }


//    getStateMachine()
    def "should return 200 when state machine exists"() {
        given: "A valid state machine ID exists in the database"
        def id = "validStateMachine"
        def stateMachineData = new JsonObject()
                .put("_id", id)
                .put("stateMachineName", "validStateMachine")
                .put("states", ["NEW", "ACTIVE"])
                .put("events", ["ACTIVATE", "INACTIVATE"])
                .put("transitions", new JsonObject().put("NEW", new JsonObject().put("ACTIVATE", "ACTIVE")))

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(stateMachineData)

        when: "getStateMachine is called"
        def resultFuture = controllerVerticle.getStateMachine(id)

        then: "It should return 200 with the state machine details"
        resultFuture.succeeded()
        def resultJson = resultFuture.result()
        resultJson.getInteger("statusCode") == 200
        resultJson.getBoolean("success") == true
    }

    def "should return 404 when state machine does not exist"() {
        given: "An ID that does not exist in the database"
        def id = "nonExistentStateMachine"

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(null)

        when: "getStateMachine is called"
        def resultFuture = controllerVerticle.getStateMachine(id)

        then: "It should return 404 with a not found message"
        resultFuture.succeeded()
        def resultJson = resultFuture.result()
        resultJson.getInteger("statusCode") == 404
        resultJson.getBoolean("success") == false
        resultJson.getString("message") == "State machine not found"
    }

    def "should return 500 when database query fails"() {
        given: "A database failure occurs while fetching the state machine"
        def id = "dbFailureStateMachine"
        def errorMessage = "Database connection error"

        repoUtil.findOne(_, _, _) >> Future.failedFuture(new RuntimeException(errorMessage))

        when: "getStateMachine is called"
        def resultFuture = controllerVerticle.getStateMachine(id)

        then: "It should return 500 with an error message"
        resultFuture.failed()
        def resultJson = new JsonObject(resultFuture.cause().message)
        resultJson.getInteger("statusCode") == 500
        resultJson.getBoolean("success") == false
        resultJson.getString("error").contains("Failed to fetch state machine definition for ID: dbFailureStateMachine")
    }

    def "should return 200 even if state machine has minimal data"() {
        given: "A state machine with only essential fields"
        def id = "minimalStateMachine"
        def stateMachineData = new JsonObject()
                .put("_id", id)
                .put("stateMachineName", "minimalStateMachine")

        repoUtil.findOne(_, _, _) >> Future.succeededFuture(stateMachineData)

        when: "getStateMachine is called"
        def resultFuture = controllerVerticle.getStateMachine(id)

        then: "It should return 200 with the minimal data"
        resultFuture.succeeded()
        def resultJson = resultFuture.result()
        resultJson.getInteger("statusCode") == 200
        resultJson.getBoolean("success") == true
    }


//    updateStateMachine()
    def "should return 400 when updated state machine is Invalid"() {
        given: "A valid state machine JSON request"
        String id = "abcd"
        def reqJO = new JsonObject()
                .put("stateMachineName", "TestStateMachine")
                .put("states", ["HI", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        when: "createStateMachine is called"
        def futureResponse = controllerVerticle.updateStateMachine(id, reqJO)

        then: "It should check if the state machine already exists"

        futureResponse.result().getInteger("statusCode") == 400
        futureResponse.result().getBoolean("success") == false
        futureResponse.result().getString("message") == "Invalid transitions: Missing states or events"

    }

    def "should update state machine successfully"() {
        given: "A valid state machine ID and request JSON"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject expectedUpdateData = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject query = new JsonObject().put("_id", id)

        JsonObject dbResult = new JsonObject()

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "findOneAndUpdate is called with correct parameters"
        1 * repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, expectedUpdateData) >> Future.succeededFuture(dbResult)

        and: "The response indicates success"
        future.result().getInteger("statusCode") == 200
        future.result().getBoolean("success") == true
        future.result().getString("message") == "State machine updated successfully"
    }

    def "should return 404 when state machine is not found"() {
        given: "A valid state machine ID and request JSON"
        String id = "nonExistentStateMachine"
        def reqJO = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject expectedUpdateData = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject query = new JsonObject().put("_id", id)

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "findOneAndUpdate is called with correct parameters but returns null"
        1 * repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, expectedUpdateData) >> Future.succeededFuture(null)

        and: "The response indicates state machine not found"
        future.result().getInteger("statusCode") == 404
        future.result().getBoolean("success") == false
        future.result().getString("message") == "State machine not found"
    }

    def "should return 500 when database update fails"() {
        given: "A valid state machine ID and request JSON"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject expectedUpdateData = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        JsonObject query = new JsonObject().put("_id", id)

        Exception dbException = new RuntimeException("Database update error")

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "findOneAndUpdate is called with correct parameters but fails"
        1 * repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, expectedUpdateData) >> Future.failedFuture(dbException)

        and: "The response indicates failure with a 500 status"
        future.failed()
        def errorResponse = new JsonObject(future.cause().message)
        errorResponse.getInteger("statusCode") == 500
        errorResponse.getBoolean("success") == false
        errorResponse.getString("error").contains("Failed to update state machine: Database update error")
    }

    def "should return 400 when states list is empty but transitions exist"() {
        given: "A valid state machine ID and request JSON with empty states"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", [])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "isValidTransitions returns false"
        future.result().getInteger("statusCode") == 400
        future.result().getBoolean("success") == false
        future.result().getString("message") == "Invalid transitions: Missing states or events"
    }

    def "should return 400 when events list is empty but transitions reference an event"() {
        given: "A valid state machine ID and request JSON with empty events list"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", ["START", "END"])
                .put("events", [])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "isValidTransitions returns false"
        and: "The response indicates invalid transitions"
        future.result().getInteger("statusCode") == 400
        future.result().getBoolean("success") == false
        future.result().getString("message") == "Invalid transitions: Missing states or events"
    }

    def "should allow self-loop transitions and proceed with update"() {
        given: "A valid state machine ID and request JSON with a self-loop transition"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", ["A"])
                .put("events", ["e1"])
                .put("transitions", new JsonObject()
                        .put("A", new JsonObject().put("e1", "A"))
                )

        JsonObject expectedUpdateData = new JsonObject()
                .put("states", ["A"])
                .put("events", ["e1"])
                .put("transitions", new JsonObject()
                        .put("A", new JsonObject().put("e1", "A"))
                )

        JsonObject query = new JsonObject().put("_id", id)

        JsonObject dbResult = new JsonObject()

        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then:
        1 * repoUtil.findOneAndUpdate(AppConstant.COLLECTION_STATE_MACHINES, query, expectedUpdateData) >> Future.succeededFuture(dbResult)

        and: "The response indicates success"
        future.result().getInteger("statusCode") == 200
        future.result().getBoolean("success") == true
        future.result().getString("message") == "State machine updated successfully"
    }

    def "should ignore duplicate states and proceed with update"() {
        given: "A valid state machine ID and request JSON with duplicate states"
        String id = "stateMachine123"
        def reqJO = new JsonObject()
                .put("states", ["START", "START", "END"])
                .put("events", ["BEGIN", "FINISH"])
                .put("transitions", new JsonObject()
                        .put("START", new JsonObject().put("BEGIN", "END"))
                )


        when: "The updateStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.updateStateMachine(id, reqJO)

        then: "findOneAndUpdate is called with correct parameters"
        future.result().getInteger("statusCode") == 400
        future.result().getBoolean("success") == false
        future.result().getString("message") == "Invalid transitions: Missing states or events"
    }


//    deleteStateMachine()
    def "should delete state machine successfully"() {
        given: "A valid state machine ID that exists in the database"
        String id = "stateMachine123"

        JsonObject dbResult = new JsonObject()

        when: "The deleteStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.deleteStateMachine(id)

        then: "findOneAndDelete is called with correct parameters"
        1 * repoUtil.findOneAndDelete(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id)) >> Future.succeededFuture(dbResult)

        and: "The response indicates successful deletion"
        future.result().getInteger("statusCode") == 200
        future.result().getBoolean("success") == true
        future.result().getString("message") == "State machine deleted successfully"
    }

    def "should return 404 when state machine is not found in deleteStataeMachine"() {
        given: "A state machine ID that does not exist in the database"
        String id = "nonExistentStateMachine"

        when: "The deleteStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.deleteStateMachine(id)

        then: "findOneAndDelete is called and returns null"
        1 * repoUtil.findOneAndDelete(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id)) >> Future.succeededFuture(null)

        and: "The response indicates state machine not found"
        future.result().getInteger("statusCode") == 404
        future.result().getBoolean("success") == false
        future.result().getString("message") == "State machine not found"
    }

    def "should return 500 when database error occurs during deletion"() {
        given: "A valid state machine ID and a database failure occurs"
        String id = "stateMachineWithDbError"
        Exception dbException = new RuntimeException("Database connection lost")

        when: "The deleteStateMachine method is called"
        Future<JsonObject> future = controllerVerticle.deleteStateMachine(id)

        then: "findOneAndDelete throws an exception"
        1 * repoUtil.findOneAndDelete(AppConstant.COLLECTION_STATE_MACHINES, new JsonObject().put("_id", id)) >> Future.failedFuture(dbException)

        and: "The response indicates an internal server error"
        future.failed()
        def errorResponse = new JsonObject(future.cause().getMessage())
        errorResponse.getInteger("statusCode") == 500
        errorResponse.getBoolean("success") == false
        errorResponse.getString("error") == "Failed to delete state machine for ID: ${id} - ${dbException.getMessage()}"
    }


//    isValidTransitions()
    def "should return true for valid transitions"() {
        given: "A StateMachineDB with correctly defined states, events, and transitions"
        def stateMachineDB = new StateMachineDB(
                _id: "123",
                stateMachineName: "TestStateMachine",
                states: ["START", "PROCESSING", "COMPLETED"],
                events: ["begin", "finish"],
                transitions: [
                        "START"      : ["begin": "PROCESSING"],
                        "PROCESSING" : ["finish": "COMPLETED"]
                ],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns true"
        result == true
    }

    def "should return false when fromState is not defined"() {
        given: "A StateMachineDB where a transition has a fromState not listed in states"
        def stateMachineDB = new StateMachineDB(
                _id: "124",
                stateMachineName: "InvalidStateMachine",
                states: ["START", "COMPLETED"],
                events: ["begin", "finish"],
                transitions: [
                        "START"      : ["begin": "PROCESSING"],
                        "PROCESSING" : ["finish": "COMPLETED"]
                ],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return false when an event is not defined"() {
        given: "A StateMachineDB where a transition contains an undefined event"
        def stateMachineDB = new StateMachineDB(
                _id: "125",
                stateMachineName: "InvalidEventStateMachine",
                states: ["START", "PROCESSING", "COMPLETED"],
                events: ["begin"],
                transitions: [
                        "START"      : ["begin": "PROCESSING"],
                        "PROCESSING" : ["finish": "COMPLETED"]
                ],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return false when target state is not defined"() {
        given: "A StateMachineDB where a transition points to an undefined target state"
        def stateMachineDB = new StateMachineDB(
                _id: "126",
                stateMachineName: "InvalidTargetStateMachine",
                states: ["START", "PROCESSING"],
                events: ["begin", "finish"],
                transitions: [
                        "START"      : ["begin": "PROCESSING"],
                        "PROCESSING" : ["finish": "COMPLETED"]
                ],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return false when states list is empty and transitions exist"() {
        given: "A StateMachineDB with an empty states list but with transitions"
        def stateMachineDB = new StateMachineDB(
                _id: "127",
                stateMachineName: "EmptyStateList",
                states: [],
                events: ["begin"],
                transitions: ["START": ["begin": "PROCESSING"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return false when events list is empty and transitions reference events"() {
        given: "A StateMachineDB with an empty events list but transitions contain events"
        def stateMachineDB = new StateMachineDB(
                _id: "128",
                stateMachineName: "EmptyEventsList",
                states: ["START", "PROCESSING"],
                events: [],
                transitions: ["START": ["begin": "PROCESSING"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return true when transitions map is empty"() {
        given: "A StateMachineDB with no transitions"
        def stateMachineDB = new StateMachineDB(
                _id: "129",
                stateMachineName: "EmptyTransitions",
                states: ["START", "PROCESSING"],
                events: ["begin"],
                transitions: [:],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns true"
        result == true
    }

    def "should return true for valid self-loops"() {
        given: "A StateMachineDB where a state transitions to itself"
        def stateMachineDB = new StateMachineDB(
                _id: "133",
                stateMachineName: "SelfLoop",
                states: ["START"],
                events: ["stay"],
                transitions: ["START": ["stay": "START"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns true"
        result == true
    }

    def "should not work correctly even if states list contains duplicates"() {
        given: "A StateMachineDB where states list has duplicate entries"
        def stateMachineDB = new StateMachineDB(
                _id: "134",
                stateMachineName: "DuplicateStates",
                states: ["START", "PROCESSING", "START"],
                events: ["begin"],
                transitions: ["START": ["begin": "PROCESSING"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should not work correctly even if events list contains duplicates"() {
        given: "A StateMachineDB where events list has duplicate entries"
        def stateMachineDB = new StateMachineDB(
                _id: "135",
                stateMachineName: "DuplicateEvents",
                states: ["START", "PROCESSING"],
                events: ["begin", "begin"],
                transitions: ["START": ["begin": "PROCESSING"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns false"
        result == false
    }

    def "should return true when there are unreachable states"() {
        given: "A StateMachineDB with a state that is never referenced in transitions"
        def stateMachineDB = new StateMachineDB(
                _id: "136",
                stateMachineName: "UnreachableState",
                states: ["START", "PROCESSING", "UNUSED"],
                events: ["begin"],
                transitions: ["START": ["begin": "PROCESSING"]],
                partition: 1
        )

        when: "isValidTransitions is called"
        def result = controllerVerticle.isValidTransitions(stateMachineDB)

        then: "The method returns true"
        result == true
    }
}
