package com.payex.project.server

import com.payex.project.consumer.KafkaVerticle
import com.payex.project.controller.ControllerVerticle
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RequestBody
import io.vertx.ext.web.RoutingContext
import io.vertx.core.http.HttpServerResponse
import spock.lang.Specification

class RequestHandlerTest extends Specification {

//    def "should handle valid JSON request successfully"() {
//        given:
//        ControllerVerticle controllerVerticle = Mock(ControllerVerticle)
//        KafkaVerticle kafkaVerticle = Mock(KafkaVerticle)
//        RequestHandler requestHandler = new RequestHandler(kafkaVerticle, controllerVerticle)
//
//        RoutingContext ctx = Mock(RoutingContext)
//        HttpServerResponse response = Mock(HttpServerResponse)
//        RequestBody requestBody = Mock(RequestBody)  // Mock the RequestBody
//
//        def jsonBody = new JsonObject().put("key", "value")
//        def successResponse = new JsonObject().put("statusCode", 200).put("message", "Success")
//
//        when:
//        ctx.body() >> requestBody   // Ensure ctx.body() is not null
//        requestBody.asJsonObject() >> jsonBody // Return a valid JSON object
//        ctx.response() >> response
//        controllerVerticle.createStateMachine(jsonBody) >> Future.succeededFuture(successResponse)
//
//        requestHandler.createStateMachine(ctx)
//
//        then:
////        1 * response.setStatusCode(200)
//        1 * response.end(successResponse.encodePrettily())
//    }

    def "should handle valid JSON request successfully"() {
        given:
        ControllerVerticle controllerVerticle = Mock(ControllerVerticle)
        KafkaVerticle kafkaVerticle = Mock(KafkaVerticle)
        RequestHandler requestHandler = new RequestHandler(kafkaVerticle, controllerVerticle)

        RoutingContext ctx = Mock(RoutingContext)
        HttpServerResponse response = Mock(HttpServerResponse)
        RequestBody requestBody = Mock(RequestBody)  // Mock RequestBody

        def jsonBody = new JsonObject().put("key", "value")
        def successResponse = new JsonObject().put("statusCode", 200).put("message", "Success")


        ctx.body() >> requestBody   // Ensure ctx.body() is not null
        requestBody.asJsonObject() >> jsonBody // Return a valid JSON object
//        ctx.response() >> response.setStatusCode(200)
        response.setStatusCode(200) >> 200  // Ensure method chaining works
        response.end(_) >> response  // Prevent null return for `end()`

        controllerVerticle.createStateMachine(jsonBody) >> Future.succeededFuture(successResponse)

        when:
        requestHandler.createStateMachine(ctx)

        then:
        1 * response.setStatusCode(200) >> null
        1 * response.end(successResponse.encodePrettily())
    }


}
