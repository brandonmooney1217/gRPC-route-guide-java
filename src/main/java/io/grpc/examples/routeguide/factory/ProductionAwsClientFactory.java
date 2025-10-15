package io.grpc.examples.routeguide.factory;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.logging.Logger;

/**
 * Production AWS client factory.
 * Uses default credentials provider and production endpoints.
 */
public class ProductionAwsClientFactory implements AwsClientFactory {

    private static final Logger logger = Logger.getLogger(ProductionAwsClientFactory.class.getName());
    private static final String DEFAULT_REGION = "us-east-1";

    private final Region region;

    public ProductionAwsClientFactory() {
        String regionName = System.getenv("AWS_REGION");
        if (regionName == null || regionName.isEmpty()) {
            regionName = DEFAULT_REGION;
            logger.info("AWS_REGION not set, using default: " + DEFAULT_REGION);
        }
        this.region = Region.of(regionName);
    }

    public ProductionAwsClientFactory(String regionName) {
        this.region = Region.of(regionName);
    }

    @Override
    public DynamoDbClient createDynamoDbClient() {
        logger.info("Creating production DynamoDB client for region: " + region);
        return DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
