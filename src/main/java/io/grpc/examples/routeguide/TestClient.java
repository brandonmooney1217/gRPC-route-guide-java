package io.grpc.examples.routeguide;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Simple test client for DynamoDB backend testing (uses plaintext connection).
 */
public class TestClient {
    public static void main(String[] args) throws Exception {
        String target = "localhost:8980";

        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        try {
            RouteGuideGrpc.RouteGuideBlockingStub blockingStub =
                RouteGuideGrpc.newBlockingStub(channel);

            // Test 1: Feature that exists
            System.out.println("Test 1: Query existing feature");
            Point point1 = Point.newBuilder()
                    .setLatitude(409146138)
                    .setLongitude(-746188906)
                    .build();
            Feature feature1 = blockingStub.getFeature(point1);
            System.out.println("Response: " + feature1.getName());
            System.out.println("Expected: Berkshire Valley Management Area Trail, Jefferson, NJ, USA");
            System.out.println("Success: " + !feature1.getName().isEmpty());
            System.out.println();

            // Test 2: Another existing feature
            System.out.println("Test 2: Query another existing feature");
            Point point2 = Point.newBuilder()
                    .setLatitude(411633897)
                    .setLongitude(-746118064)
                    .build();
            Feature feature2 = blockingStub.getFeature(point2);
            System.out.println("Response: " + feature2.getName());
            System.out.println("Expected: U.S. 6, Shohola, PA, USA");
            System.out.println("Success: " + !feature2.getName().isEmpty());
            System.out.println();

            // Test 3: Feature that doesn't exist
            System.out.println("Test 3: Query non-existent feature");
            Point point3 = Point.newBuilder()
                    .setLatitude(0)
                    .setLongitude(0)
                    .build();
            Feature feature3 = blockingStub.getFeature(point3);
            System.out.println("Response: '" + feature3.getName() + "'");
            System.out.println("Expected: Empty string");
            System.out.println("Success: " + feature3.getName().isEmpty());
            System.out.println();

            System.out.println("All tests completed!");

        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
