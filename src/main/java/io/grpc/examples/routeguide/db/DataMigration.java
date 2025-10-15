package io.grpc.examples.routeguide.db;

import ch.hsr.geohash.GeoHash;
import com.google.protobuf.util.JsonFormat;
import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.FeatureDatabase;
import io.grpc.examples.routeguide.factory.AwsClientFactory;
import io.grpc.examples.routeguide.factory.AwsClientFactoryProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * One-time migration script to load features from route_guide_db.json into DynamoDB.
 *
 * Usage:
 *   java -cp target/route-guide-1.0-SNAPSHOT.jar io.grpc.examples.routeguide.db.DataMigration
 *
 * Prerequisites:
 *   1. DynamoDB table "RouteGuideFeatures" must exist
 *   2. AWS credentials configured (via env vars, credentials file, or IAM role)
 *   3. Set AWS_REGION environment variable (defaults to us-east-1)
 */
public class DataMigration {

    private static final Logger logger = Logger.getLogger(DataMigration.class.getName());
    private static final int GEOHASH_PRECISION = 6; // Partition key precision
    private static final int FULL_GEOHASH_PRECISION = 8; // Full geohash precision
    private static final int BATCH_SIZE = 25; // DynamoDB batch write limit

    public static void main(String[] args) {
        logger.info("Starting data migration from route_guide_db.json to DynamoDB");

        DynamoDbClient dynamoDbClient = null;
        try {
            // Load features from JSON
            List<Feature> features = loadFeaturesFromJson();
            logger.info("Loaded " + features.size() + " features from JSON");

            // Convert to entities and write to DynamoDB
            dynamoDbClient = migrateFeaturesToDynamoDB(features);

            logger.info("Migration completed successfully!");

        } catch (Exception e) {
            logger.severe("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (dynamoDbClient != null) {
                dynamoDbClient.close();
            }
        }
    }

    /**
     * Load features from the JSON file in resources.
     */
    private static List<Feature> loadFeaturesFromJson() throws IOException {
        InputStream input = DataMigration.class.getResourceAsStream(
            "/io/grpc/examples/routeguide/route_guide_db.json");

        if (input == null) {
            throw new IOException("Could not find route_guide_db.json in resources");
        }

        FeatureDatabase.Builder database = FeatureDatabase.newBuilder();
        try (Reader reader = new InputStreamReader(input)) {
            JsonFormat.parser().merge(reader, database);
        }

        return database.getFeatureList();
    }

    /**
     * Convert features to entities and batch write to DynamoDB.
     */
    private static DynamoDbClient migrateFeaturesToDynamoDB(List<Feature> features) {
        AwsClientFactory factory = AwsClientFactoryProvider.getFactory();
        DynamoDbClient dynamoDbClient = factory.createDynamoDbClient();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        DynamoDbTable<FeatureEntity> table = enhancedClient.table(
            "RouteGuideFeatures",
            TableSchema.fromBean(FeatureEntity.class)
        );

        List<FeatureEntity> entities = new ArrayList<>();
        int skippedCount = 0;

        // Convert features to entities
        for (Feature feature : features) {
            // Skip unnamed features (just coordinates without names)
            if (feature.getName() == null || feature.getName().isEmpty()) {
                skippedCount++;
                continue;
            }

            FeatureEntity entity = convertToEntity(feature);
            entities.add(entity);
        }

        logger.info("Converting " + entities.size() + " named features to entities (skipped "
                   + skippedCount + " unnamed locations)");

        // Batch write entities
        writeBatches(enhancedClient, table, entities);

        return dynamoDbClient;
    }

    /**
     * Convert protobuf Feature to DynamoDB entity.
     */
    private static FeatureEntity convertToEntity(Feature feature) {
        int latitude = feature.getLocation().getLatitude();
        int longitude = feature.getLocation().getLongitude();

        // Convert E7 to decimal degrees
        double lat = latitude / 1e7;
        double lon = longitude / 1e7;

        // Calculate geohashes
        String geoHash = GeoHash.geoHashStringWithCharacterPrecision(lat, lon, GEOHASH_PRECISION);
        String fullGeoHash = GeoHash.geoHashStringWithCharacterPrecision(lat, lon, FULL_GEOHASH_PRECISION);

        // Generate unique feature ID
        String featureId = UUID.randomUUID().toString();

        logger.fine("Converting: " + feature.getName() + " at (" + lat + ", " + lon +
                   ") -> geoHash=" + geoHash + ", featureId=" + featureId);

        return new FeatureEntity(geoHash, featureId, feature.getName(),
                                latitude, longitude, fullGeoHash);
    }

    /**
     * Write entities to DynamoDB in batches of 25.
     */
    private static void writeBatches(DynamoDbEnhancedClient enhancedClient,
                                     DynamoDbTable<FeatureEntity> table,
                                     List<FeatureEntity> entities) {
        int totalBatches = (int) Math.ceil((double) entities.size() / BATCH_SIZE);
        int batchNumber = 0;

        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            batchNumber++;
            int end = Math.min(i + BATCH_SIZE, entities.size());
            List<FeatureEntity> batch = entities.subList(i, end);

            logger.info("Writing batch " + batchNumber + "/" + totalBatches +
                       " (" + batch.size() + " items)");

            WriteBatch.Builder<FeatureEntity> batchBuilder = WriteBatch.builder(FeatureEntity.class)
                .mappedTableResource(table);

            for (FeatureEntity entity : batch) {
                batchBuilder.addPutItem(entity);
            }

            BatchWriteItemEnhancedRequest batchRequest = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build();

            enhancedClient.batchWriteItem(batchRequest);
            logger.info("Batch " + batchNumber + " written successfully");
        }

        logger.info("Wrote " + entities.size() + " entities in " + batchNumber + " batches");
    }
}
