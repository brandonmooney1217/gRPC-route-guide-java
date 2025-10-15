package io.grpc.examples.routeguide.factory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.logging.Logger;

/**
 * Local development AWS client factory.
 * Configured for DynamoDB Local or LocalStack.
 */
public class LocalDevelopmentAwsClientFactory implements AwsClientFactory {

    private static final Logger logger = Logger.getLogger(LocalDevelopmentAwsClientFactory.class.getName());
    private static final String DEFAULT_ENDPOINT = "http://localhost:8000";
    private static final String DEFAULT_REGION = "us-east-1";

    private final String endpoint;
    private final Region region;

    public LocalDevelopmentAwsClientFactory() {
        this.endpoint = System.getenv().getOrDefault("DYNAMODB_ENDPOINT", DEFAULT_ENDPOINT);
        this.region = Region.of(DEFAULT_REGION);
        logger.info("Using local DynamoDB endpoint: " + endpoint);
    }

    public LocalDevelopmentAwsClientFactory(String endpoint) {
        this.endpoint = endpoint;
        this.region = Region.of(DEFAULT_REGION);
    }

    @Override
    public DynamoDbClient createDynamoDbClient() {
        logger.info("Creating local development DynamoDB client");
        return DynamoDbClient.builder()
                .region(region)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy-key", "dummy-secret")))
                .build();
    }
}
