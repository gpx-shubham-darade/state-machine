package com.payex.project.repository

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import spock.lang.Specification
import io.vertx.core.Future

class RepoUtilTest extends Specification {

    Vertx vertx = Vertx.vertx()
    MongoClient mongoClient = Mock()
    RepoUtil repoUtil = new RepoUtil(vertx,mongoClient);


    def "Valid document gets saved successfully"() {
        given:
        def collectionName = "testCollection"
        def document = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a successful Future"
        mongoClient.save(collectionName, document) >> Future.succeededFuture("12345")

        when:
        def futureResult = repoUtil.save(collectionName, document)
        def result = futureResult.result()

        then:
        futureResult.succeeded()
        result == "Document saved"
    }

    def "MongoDB connection failure"() {
        given:
        def collectionName = "testCollection"
        def document = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a failed Future"
        mongoClient.save(collectionName, document) >> Future.failedFuture("MongoDB connection failure")

        when:
        def futureResult = repoUtil.save(collectionName, document)
        def cause = futureResult.cause()

        then:
        futureResult.failed()
        cause.message == "MongoDB connection failure"
    }

    def "Invalid collection name should fail"() {
        given:
        def collectionName = ""  // Empty string (invalid)
        def document = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a failed Future"
        mongoClient.save(collectionName, document) >> Future.failedFuture("Invalid collection name")

        when:
        def futureResult = repoUtil.save(collectionName, document)  // Call method
        def cause = futureResult.cause()  // Get the exception/error

        then:
        futureResult.failed()  // Ensure failure
        cause.message == "Invalid collection name"  // Check error message

    }

    def "Null document should fail"() {
        given:
        def collectionName = "testCollection"
        def document = null  // Null document

        and: "Mock MongoClient to return a failed Future"
        mongoClient.save(collectionName, document) >> Future.failedFuture("Document cannot be null")

        when:
        def futureResult = repoUtil.save(collectionName, document)
        def cause = futureResult.cause()

        then:
        futureResult.failed()  // Ensure failure
        cause.message == "Document cannot be null"  // Check error message
    }

    def "MongoDB operation throws an unexpected exception"() {
        given:
        def collectionName = "testCollection"
        def document = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a failed Future simulating an exception"
        mongoClient.save(collectionName, document) >> Future.failedFuture(new RuntimeException("Unexpected database error"))

        when:
        def futureResult = repoUtil.save(collectionName, document)

        then:
        futureResult.failed()  // Ensure failure
        futureResult.cause().message == "Unexpected database error"  // Check error message
    }

    def "Saving an empty document should fail"() {
        given:
        def collectionName = "testCollection"
        def emptyDocument = new JsonObject()  // Empty JSON {}

        and: "Mock MongoClient to return a failed Future"
        mongoClient.save(collectionName, emptyDocument) >> Future.failedFuture("Document cannot be empty")

        when:
        def futureResult = repoUtil.save(collectionName, emptyDocument)

        then:
        futureResult.failed()
        futureResult.cause().message == "Document cannot be empty"
    }

    def "Saving a duplicate document should fail due to unique constraint"() {
        given:
        def collectionName = "users"
        def duplicateDocument = new JsonObject().put("email", "john@example.com")

        and: "Mock MongoClient to simulate duplicate key error"
        mongoClient.save(collectionName, duplicateDocument) >>
                Future.failedFuture("E11000 duplicate key error collection: users index: email_1 dup key: { email: 'john@example.com' }")

        when:
        def futureResult = repoUtil.save(collectionName, duplicateDocument)

        then:
        futureResult.failed()
        futureResult.cause().message.contains("E11000 duplicate key error")
    }

    def "Saving a very large document should fail due to BSON size limit"() {
        given:
        def collectionName = "largeDocs"
        def largeDocument = new JsonObject().put("data", "x".repeat(17_000_000))  // Exceeding 16MB

        and: "Mock MongoClient to simulate BSON size limit error"
        mongoClient.save(collectionName, largeDocument) >>
                Future.failedFuture("BSON document too large (17100000 bytes) - exceeds maximum 16777216")

        when:
        def futureResult = repoUtil.save(collectionName, largeDocument)

        then:
        futureResult.failed()
        futureResult.cause().message.contains("BSON document too large")
    }

    def "Saving to a collection with invalid characters should fail"() {
        given:
        def invalidCollectionName = "test/invalid"  // Invalid collection name
        def document = new JsonObject().put("name", "John Doe")

        and: "Mock MongoClient to return an error"
        mongoClient.save(invalidCollectionName, document) >>
                Future.failedFuture("Invalid collection name: test/invalid")

        when:
        def futureResult = repoUtil.save(invalidCollectionName, document)

        then:
        futureResult.failed()
        futureResult.cause().message.contains("Invalid collection name")
    }

    def "Find one - Document found successfully"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def projection = new JsonObject().put("age", 1)
        def foundDocument = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a document"
        mongoClient.findOne(collectionName, query, projection) >> Future.succeededFuture(foundDocument)

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.succeeded()
        futureResult.result() == foundDocument
    }

    def "Find one - No document found"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "NonExistent")
        def projection = new JsonObject().put("age", 1)

        and: "Mock MongoClient to return null (no document found)"
        mongoClient.findOne(collectionName, query, projection) >> Future.succeededFuture(null)

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.succeeded()
        futureResult.result() == null
    }

    def "Find one - MongoDB connection failure"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def projection = new JsonObject().put("age", 1)

        and: "Mock MongoClient to return a failure due to connection issue"
        mongoClient.findOne(collectionName, query, projection) >> Future.failedFuture("MongoDB connection error")

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.failed()
        futureResult.cause().message == "MongoDB connection error"
    }

    def "Find one - Invalid collection name"() {
        given:
        def collectionName = ""
        def query = new JsonObject().put("name", "John Doe")
        def projection = new JsonObject().put("age", 1)

        and: "Mock MongoClient to return a failure for invalid collection name"
        mongoClient.findOne(collectionName, query, projection) >> Future.failedFuture("Invalid collection name")

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.failed()
        futureResult.cause().message == "Invalid collection name"
    }

    def "Find one - Query is null"() {
        given:
        def collectionName = "testCollection"
        def query = null
        def projection = new JsonObject().put("age", 1)

        and: "Mock MongoClient to return a failure due to null query"
        mongoClient.findOne(collectionName, query, projection) >> Future.failedFuture("Query cannot be null")

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.failed()
        futureResult.cause().message == "Query cannot be null"
    }

    def "Find one - Null projection should work"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def projection = null
        def foundDocument = new JsonObject().put("name", "John Doe").put("age", 30)

        and: "Mock MongoClient to return a document"
        mongoClient.findOne(collectionName, query, projection) >> Future.succeededFuture(foundDocument)

        when:
        def futureResult = repoUtil.findOne(collectionName, query, projection)

        then:
        futureResult.succeeded()
        futureResult.result() == foundDocument
    }

    def "Successfully updates an existing document"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def update = new JsonObject().put("age", 35)
        def updatedDoc = new JsonObject().put("name", "John Doe").put("age", 35)

        and: "Mock MongoClient to return an updated document"
        mongoClient.findOneAndUpdate(collectionName, query, new JsonObject().put("\$set", update)) >>
                Future.succeededFuture(updatedDoc)

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.succeeded()
        futureResult.result() == updatedDoc
    }

    def "No matching document found for update"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "Nonexistent User")
        def update = new JsonObject().put("age", 40)

        and: "Mock MongoClient to return null"
        mongoClient.findOneAndUpdate(collectionName, query, new JsonObject().put("\$set", update)) >>
                Future.succeededFuture(null)

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.succeeded()
        futureResult.result() == null
    }

    def "MongoDB client throws an unexpected exception"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def update = new JsonObject().put("age", 40)

        and: "Mock MongoClient to return a failed Future"
        mongoClient.findOneAndUpdate(collectionName, query, new JsonObject().put("\$set", update)) >>
                Future.failedFuture(new RuntimeException("Unexpected database error"))

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.failed()
        futureResult.cause().message == "Unexpected database error"
    }

    def "Invalid collection name null or empty"() {
        given:
        def collectionName = ""
        def query = new JsonObject().put("name", "John Doe")
        def update = new JsonObject().put("age", 35)

        and: "Mock MongoClient to fail if collection name is invalid"
        mongoClient.findOneAndUpdate(_, _, _) >> Future.failedFuture(new IllegalArgumentException("Collection name cannot be null or empty"))

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.failed()
        futureResult.cause() instanceof IllegalArgumentException
        futureResult.cause().message == "Collection name cannot be null or empty"
    }


    def "Query is empty"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject() // Empty query
        def update = new JsonObject().put("age", 35)

        and: "Mock MongoClient to fail if query is empty"
        mongoClient.findOneAndUpdate(_, _, _) >> Future.failedFuture(new IllegalArgumentException("Query cannot be empty"))

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.failed()
        futureResult.cause() instanceof IllegalArgumentException
        futureResult.cause().message == "Query cannot be empty"
    }

    def "Special characters in collection name"() {
        given:
        def collectionName = "invalid/collection\0name"
        def query = new JsonObject().put("name", "John Doe")
        def update = new JsonObject().put("age", 35)

        and: "Mock MongoClient to fail if collection name is invalid"
        mongoClient.findOneAndUpdate(collectionName, _, _) >> Future.failedFuture(new IllegalArgumentException("Invalid namespace: " + collectionName))

        when:
        def futureResult = repoUtil.findOneAndUpdate(collectionName, query, update)

        then:
        futureResult.failed()
        futureResult.cause() instanceof IllegalArgumentException
        futureResult.cause().message.contains("Invalid namespace")
    }

    def "Document exists and is deleted successfully"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")
        def deletedDoc = new JsonObject().put("_id", "123").put("name", "John Doe")

        and: "Mock successful deletion"
        mongoClient.findOneAndDelete(_, _) >> Future.succeededFuture(deletedDoc)

        when:
        def futureResult = repoUtil.findOneAndDelete(collectionName, query)

        then:
        futureResult.succeeded()
        futureResult.result() == deletedDoc
    }

    def "No document found matching the query"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "Unknown")

        and: "Mock MongoDB returns null"
        mongoClient.findOneAndDelete(_, _) >> Future.succeededFuture(null)

        when:
        def futureResult = repoUtil.findOneAndDelete(collectionName, query)

        then:
        futureResult.failed()
        futureResult.cause().message == "No document found matching the query."
    }

    def "MongoDB operation fails"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject().put("name", "John Doe")

        and: "Mock MongoDB failure"
        mongoClient.findOneAndDelete(_, _) >> Future.failedFuture(new RuntimeException("Database error"))

        when:
        def futureResult = repoUtil.findOneAndDelete(collectionName, query)

        then:
        futureResult.failed()
        futureResult.cause().message == "Database error"
    }

    def "Collection name is empty"() {
        given:
        def collectionName = ""
        def query = new JsonObject().put("name", "John Doe")

        and: "Mock MongoClient to return a failed future"
        mongoClient.findOneAndDelete(_, _) >> Future.failedFuture(new IllegalArgumentException("Collection name cannot be null or empty"))

        when:
        def futureResult = repoUtil.findOneAndDelete(collectionName, query)

        then:
        futureResult.failed()
        futureResult.cause() instanceof IllegalArgumentException
        futureResult.cause().message == "Collection name cannot be null or empty"
    }

    def "Query is empty in findOneAndDelete"() {
        given:
        def collectionName = "testCollection"
        def query = new JsonObject()

        and: "Mock MongoClient to return a failed future when query is empty"
        mongoClient.findOneAndDelete(_, _) >> Future.failedFuture(new IllegalArgumentException("Query cannot be null or empty"))

        when:
        def futureResult = repoUtil.findOneAndDelete(collectionName, query)

        then:
        futureResult.failed()
        futureResult.cause() instanceof IllegalArgumentException
        futureResult.cause().message == "Query cannot be null or empty"
    }







}
