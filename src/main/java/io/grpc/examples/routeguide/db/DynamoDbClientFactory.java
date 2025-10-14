package io.grpc.examples.routeguide.db;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.logging.Logger;

/**
 * Factory for creating and configuring DynamoDB clients.
 * Uses AWS default credentials provider chain:
 * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. Java system properties
 * 3. AWS credentials file (~/.aws/credentials)
 * 4. IAM role (if running on EC2/ECS/Lambda)
 */
public class DynamoDbClientFactory {

    private static final Logger logger = Logger.getLogger(DynamoDbClientFactory.class.getName());
    private static final String DEFAULT_REGION = "us-east-1";

    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbEnhancedClient enhancedClient;

    /**
     * Create or return existing DynamoDB client.
     * Uses region from AWS_REGION environment variable or defaults to us-east-1.
     */
    public static DynamoDbClient getDynamoDbClient() {
        if (dynamoDbClient == null) {
            String regionName = System.getenv("AWS_REGION");
            if (regionName == null || regionName.isEmpty()) {
                regionName = DEFAULT_REGION;
                logger.info("AWS_REGION not set, using default: " + DEFAULT_REGION);
            }

            Region region = Region.of(regionName);
            logger.info("Initializing DynamoDB client for region: " + regionName);

            dynamoDbClient = DynamoDbClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            logger.info("DynamoDB client initialized successfully");
        }
        return dynamoDbClient;
    }

    /**
     * Create or return existing DynamoDB Enhanced Client.
     * Enhanced client provides higher-level API for working with POJOs.
     */
    public static DynamoDbEnhancedClient getEnhancedClient() {
        if (enhancedClient == null) {
            logger.info("Initializing DynamoDB Enhanced Client");
            enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(getDynamoDbClient())
                    .build();
            logger.info("DynamoDB Enhanced Client initialized successfully");
        }
        return enhancedClient;
    }

    /**
     * Create a FeatureRepository instance configured with the enhanced client.
     */
    public static FeatureRepository createFeatureRepository() {
        logger.info("Creating FeatureRepository");
        return new FeatureRepository(getEnhancedClient());
    }

    /**
     * Close all clients and release resources.
     * Should be called on application shutdown.
     */
    public static void close() {
        if (dynamoDbClient != null) {
            logger.info("Closing DynamoDB client");
            dynamoDbClient.close();
            dynamoDbClient = null;
            enhancedClient = null;
        }
    }
}
