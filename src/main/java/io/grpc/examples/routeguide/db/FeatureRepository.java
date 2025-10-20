package io.grpc.examples.routeguide.db;

import ch.hsr.geohash.GeoHash;
import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.Point;
import io.grpc.examples.routeguide.factory.AwsClientFactory;
import io.grpc.examples.routeguide.factory.AwsClientFactoryProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Repository for accessing geographical features stored in DynamoDB.
 * Uses geohash-based partitioning for efficient spatial queries.
 */
public class FeatureRepository {

    private static final Logger logger = Logger.getLogger(FeatureRepository.class.getName());
    private static final int GEOHASH_PRECISION = 6; // 6 chars for partition key (~600m x 1.2km)

    private final DynamoDbTable<FeatureEntity> table;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Create repository using the default factory from AwsClientFactoryProvider.
     */
    public FeatureRepository() {
        this(AwsClientFactoryProvider.getFactory());
    }

    /**
     * Create repository with a specific AWS client factory.
     * Useful for testing or custom configurations.
     */
    public FeatureRepository(AwsClientFactory factory) {
        this.dynamoDbClient = factory.createDynamoDbClient();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.table = enhancedClient.table("RouteGuideFeatures",
                                         TableSchema.fromBean(FeatureEntity.class));
    }

    /**
     * Close the DynamoDB client.
     * Should be called when the repository is no longer needed.
     */
    public void close() {
        if (dynamoDbClient != null) {
            dynamoDbClient.close();
        }
    }

    /**
     * Get a feature at a specific point (exact coordinate lookup).
     *
     * @param point The geographical point to look up
     * @return The feature at that point, or a Feature with empty name if not found
     */
    public Feature getFeature(Point point) {
        if (point == null) {
            return Feature.newBuilder().setName("").setLocation(point).build();
        }

        // Convert E7 coordinates to decimal degrees
        double lat = point.getLatitude() / 1e7;
        double lon = point.getLongitude() / 1e7;

        // Calculate geohash for partition key
        String geoHash = GeoHash.geoHashStringWithCharacterPrecision(lat, lon, GEOHASH_PRECISION);

        logger.info("Looking up feature at (" + lat + ", " + lon + ") with geohash: " + geoHash);

        // Query all items in this partition
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                    Key.builder()
                        .partitionValue(geoHash)
                        .build()))
                .build();

        // Scan results and find exact coordinate match
        for (FeatureEntity entity : table.query(queryRequest).items()) {
            if (entity.getLatitude().equals(point.getLatitude()) &&
                entity.getLongitude().equals(point.getLongitude())) {

                logger.info("Found feature: " + entity.getName());

                return Feature.newBuilder()
                        .setName(entity.getName())
                        .setLocation(Point.newBuilder()
                                .setLatitude(entity.getLatitude())
                                .setLongitude(entity.getLongitude())
                                .build())
                        .build();
            }
        }

        // No feature found at this location
        logger.info("No feature found at this location");
        return Feature.newBuilder()
                .setName("")
                .setLocation(point)
                .build();
    }

    /**
     * Check if a feature exists at the given point.
     *
     * @param point The geographical point to check
     * @return true if a named feature exists at this point
     */
    public boolean hasFeature(Point point) {
        Feature feature = getFeature(point);
        return feature != null && !feature.getName().isEmpty();
    }

    /**
     * Convert FeatureEntity to protobuf Feature.
     */
    private Feature toFeature(FeatureEntity entity) {
        return Feature.newBuilder()
                .setName(entity.getName())
                .setLocation(Point.newBuilder()
                        .setLatitude(entity.getLatitude())
                        .setLongitude(entity.getLongitude())
                        .build())
                .build();
    }

    /**
     * Update a feature with the provided data.
     * Only updates fields specified in the FieldMask.
     *
     * Netflix pattern:
     * - If a field is in the mask with a value, it's updated
     * - If a field is in the mask but value is empty/null, it's deleted (set to empty string)
     * - If a field is NOT in the mask, it's unchanged (even if provided in the feature object)
     *
     * @param feature The feature data to update (must have a valid location)
     * @param fieldMask The FieldMask specifying which fields to update
     * @return The updated feature, or null if the feature doesn't exist at that location
     */
    public Feature updateFeature(Feature feature, com.google.protobuf.FieldMask fieldMask) {
        if (feature == null || !feature.hasLocation()) {
            logger.warning("Cannot update feature without location");
            return null;
        }

        Point location = feature.getLocation();

        // Calculate geohash for lookup
        double lat = location.getLatitude() / 1e7;
        double lon = location.getLongitude() / 1e7;
        String geoHash = GeoHash.geoHashStringWithCharacterPrecision(lat, lon, GEOHASH_PRECISION);

        logger.info("Updating feature at (" + lat + ", " + lon + ") with geohash: " + geoHash);
        logger.info("FieldMask paths: " + fieldMask.getPathsList());

        // Find existing entity
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                    Key.builder()
                        .partitionValue(geoHash)
                        .build()))
                .build();

        FeatureEntity existingEntity = null;
        for (FeatureEntity entity : table.query(queryRequest).items()) {
            if (entity.getLatitude().equals(location.getLatitude()) &&
                entity.getLongitude().equals(location.getLongitude())) {
                existingEntity = entity;
                break;
            }
        }

        if (existingEntity == null) {
            logger.warning("Feature not found at this location - cannot update");
            return null;
        }

        logger.info("Found existing feature: " + existingEntity.getName());

        // Apply field mask updates to the entity
        // For each path in the mask, update the corresponding field
        for (String path : fieldMask.getPathsList()) {
            logger.info("Processing field mask path: " + path);

            switch (path) {
                case "name":
                    // If name is in mask, update it (even if empty - that's a delete)
                    String newName = feature.getName();
                    logger.info("Updating name from '" + existingEntity.getName() + "' to '" + newName + "'");
                    existingEntity.setName(newName);
                    break;

                case "location":
                case "location.latitude":
                case "location.longitude":
                    // Location updates are tricky because they change the partition key
                    // For now, we don't support location updates (would require delete + insert)
                    logger.warning("Location updates not supported (would change partition key)");
                    break;

                default:
                    logger.warning("Unknown field in mask: " + path);
            }
        }

        // Save updated entity back to DynamoDB
        logger.info("Saving updated entity: " + existingEntity);
        table.putItem(existingEntity);

        // Return the updated feature
        return toFeature(existingEntity);
    }
}
