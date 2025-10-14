package io.grpc.examples.routeguide.db;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB entity representing a geographical feature.
 * Maps to the RouteGuideFeatures table with geohash-based partitioning.
 */
@DynamoDbBean
public class FeatureEntity {

    private String geoHash;      // 6-char partition key for regional grouping
    private String featureId;    // Sort key (UUID)
    private String name;         // Feature name (e.g., "Liberty Bell")
    private Integer latitude;    // E7 format (multiplied by 10^7)
    private Integer longitude;   // E7 format (multiplied by 10^7)
    private String fullGeoHash;  // 8+ char geohash for precise lookups

    public FeatureEntity() {
        // Default constructor required by DynamoDB Enhanced Client
    }

    public FeatureEntity(String geoHash, String featureId, String name,
                        Integer latitude, Integer longitude, String fullGeoHash) {
        this.geoHash = geoHash;
        this.featureId = featureId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.fullGeoHash = fullGeoHash;
    }

    @DynamoDbPartitionKey
    public String getGeoHash() {
        return geoHash;
    }

    public void setGeoHash(String geoHash) {
        this.geoHash = geoHash;
    }

    @DynamoDbSortKey
    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLatitude() {
        return latitude;
    }

    public void setLatitude(Integer latitude) {
        this.latitude = latitude;
    }

    public Integer getLongitude() {
        return longitude;
    }

    public void setLongitude(Integer longitude) {
        this.longitude = longitude;
    }

    public String getFullGeoHash() {
        return fullGeoHash;
    }

    public void setFullGeoHash(String fullGeoHash) {
        this.fullGeoHash = fullGeoHash;
    }

    @Override
    public String toString() {
        return "FeatureEntity{" +
                "geoHash='" + geoHash + '\'' +
                ", featureId='" + featureId + '\'' +
                ", name='" + name + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", fullGeoHash='" + fullGeoHash + '\'' +
                '}';
    }
}
