/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mongodb;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.After;
import org.junit.Before;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneOptions;

import io.debezium.config.Configuration;
import io.debezium.connector.mongodb.ConnectionContext.MongoPrimary;
import io.debezium.embedded.AbstractConnectorTest;
import io.debezium.util.Testing;

/**
 * A common abstract base class for the Mongodb connector integration testing.
 *
 * @author Chris Cranford
 */
public abstract class AbstractMongoConnectorIT extends AbstractConnectorTest {

    protected static final JsonWriterSettings WRITER_SETTINGS = new JsonWriterSettings(JsonMode.STRICT, "", ""); // most compact JSON

    protected Configuration config;
    protected MongoDbTaskContext context;

    @Before
    public void beforEach() {
        Testing.Debug.disable();
        Testing.Print.disable();
        stopConnector();
        initializeConnectorTestFramework();
    }

    @After
    public void afterEach() {
        try {
            stopConnector();
        }
        finally {
            if (context != null) {
                context.getConnectionContext().shutdown();
            }
        }
    }

    /**
     * Drops the specified collection if it exists and inserts all documents into the empty collection.
     *
     * NOTE: This method will only drop the collection if the documents list is provided.
     *
     * @param dbName the database name
     * @param collectionName the collection name
     * @param documents the documents to be inserted, can be empty
     */
    protected void dropAndInsertDocuments(String dbName, String collectionName, Document... documents) {
        // Do nothing if no documents are provided
        if (documents.length == 0) {
            return;
        }

        primary().execute("store documents", mongo -> {
            Testing.debug("Storing in '" + dbName + "." + collectionName + "' document");

            MongoDatabase db = mongo.getDatabase(dbName);

            MongoCollection<Document> collection = db.getCollection(collectionName);
            collection.drop();

            for (Document document : documents) {
                InsertOneOptions options = new InsertOneOptions().bypassDocumentValidation(true);
                assertThat(document).isNotNull();
                assertThat(document.size()).isGreaterThan(0);
                collection.insertOne(document, options);
            }
        });
    }

    /**
     * Inserts all documents in the specified collection.
     *
     * @param dbName the database name
     * @param collectionName the collection name
     * @param documents the documents to be inserted, can be empty
     */
    protected void insertDocuments(String dbName, String collectionName, Document... documents) {
        // Do nothing if no documents are provided
        if (documents.length == 0) {
            return;
        }

        primary().execute("store documents", mongo -> {
            Testing.debug("Storing in '" + dbName + "." + collectionName + "' document");

            MongoDatabase db = mongo.getDatabase(dbName);

            MongoCollection<Document> collection = db.getCollection(collectionName);

            for (Document document : documents) {
                InsertOneOptions options = new InsertOneOptions().bypassDocumentValidation(true);
                assertThat(document).isNotNull();
                assertThat(document.size()).isGreaterThan(0);
                collection.insertOne(document, options);
            }
        });
    }

    /**
     * Updates a document in a collection based on a specified filter.
     *
     * @param dbName the database name
     * @param collectionName the collection name
     * @param filter the document filter
     * @param document the document fields to be updated
     */
    protected void updateDocument(String dbName, String collectionName, Document filter, Document document) {
        primary().execute("update", mongo -> {
            Testing.debug("Updating document with filter '" + filter + "' in '" + dbName + "." + collectionName + "'");

            MongoDatabase db = mongo.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            collection.updateOne(filter, document);
        });
    }

    protected MongoPrimary primary() {
        ReplicaSet replicaSet = ReplicaSet.parse(context.getConnectionContext().hosts());
        return context.getConnectionContext().primaryFor(replicaSet, context.filters(), connectionErrorHandler(3));
    }

    protected BiConsumer<String, Throwable> connectionErrorHandler(int numErrorsBeforeFailing) {
        AtomicInteger attempts = new AtomicInteger();
        return (desc, error) -> {
            if (attempts.incrementAndGet() > numErrorsBeforeFailing) {
                fail("Unable to connect to primary after " + numErrorsBeforeFailing + " errors trying to " + desc + ": " + error);
            }
            logger.error("Error while attempting to {}: {}", desc, error.getMessage(), error);
        };
    }
}
