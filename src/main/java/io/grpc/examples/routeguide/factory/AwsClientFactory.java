package io.grpc.examples.routeguide.factory;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Abstract Factory for creating AWS service clients.
 * Implementations provide different configurations for various environments
 * (production, local development, testing, etc.)
 */
public interface AwsClientFactory {
    /**
     * Creates a configured DynamoDB client.
     * @return DynamoDbClient instance
     */
    DynamoDbClient createDynamoDbClient();

    // Future AWS services can be added here:
    // S3Client createS3Client();
    // SqsClient createSqsClient();
    // SnsClient createSnsClient();
}
