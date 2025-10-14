package io.grpc.examples.routeguide.db;

import ch.hsr.geohash.GeoHash;
import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.Point;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

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

    public FeatureRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("RouteGuideFeatures",
                                         TableSchema.fromBean(FeatureEntity.class));
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
}
