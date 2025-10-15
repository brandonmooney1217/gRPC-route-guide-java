package io.grpc.examples.routeguide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


import java.util.Random;

import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.routeguide.RouteGuideGrpc.RouteGuideBlockingStub;
import io.grpc.examples.routeguide.RouteGuideGrpc.RouteGuideStub;
import io.grpc.examples.routeguide.header.HeaderClientInterceptor;
import io.grpc.stub.StreamObserver;

public class RouteGuideClient {

    private static final Logger logger = Logger.getLogger(RouteGuideClient.class.getName());
    private final RouteGuideBlockingStub blockingStub;
    private final RouteGuideStub stub;
    private Random random = new Random();
    private final HeaderClientInterceptor headerClientInterceptor;


    public RouteGuideClient(Channel channel) {
        this.headerClientInterceptor = new HeaderClientInterceptor();

        blockingStub = RouteGuideGrpc.newBlockingStub(channel)
            .withInterceptors(headerClientInterceptor);
            
        stub = RouteGuideGrpc.newStub(channel)
            .withInterceptors(headerClientInterceptor);
    }

    public void getFeature(int latitude, int longitude) {
        info("*** GetFeature: lat={0} lon={1}", latitude, longitude);

        final Point point = Point.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .build();
        
        FieldMask fieldMask = FieldMaskUtil.fromFieldNumbers(Feature.class,
            Feature.NAME_FIELD_NUMBER);

        info("Sending GetFeature request with field mask: {0}", fieldMask.getPathsList());

        GetFeatureRequest featureRequest = GetFeatureRequest
            .newBuilder()
            .setPoint(point)
            .setFieldMask(fieldMask)
            .build();

        final Feature feature = blockingStub.getFeature(featureRequest);

        info("Received feature response: {0}", feature);

        if (feature.getName().isEmpty()) {
            info("Feature not found at lat={0} lon={1}", latitude, longitude);
        } else {
            info("Found feature: {0}", feature.getName());
            if (feature.hasLocation()) {
                info("Response includes location (field mask NOT applied correctly)");
            } else {
                info("Response excludes location (field mask applied correctly)");
            }
        }
    }

    public void listFeatures(int lowLat, int lowLon, int hiLat, int hiLon) {
        info("*** ListFeatures: lowLat={0} lowLon={1} hiLat={2} hiLon={3}", lowLat, lowLon, hiLat, hiLon);

        Rectangle rectangle = Rectangle.newBuilder()
                .setLo(Point.newBuilder().setLatitude(lowLat).setLongitude(lowLon).build())
                .setHi(Point.newBuilder().setLatitude(hiLat).setLongitude(hiLon).build())
                .build();
        // Get all features
        Iterator<Feature> features = blockingStub.listFeatures(rectangle);
        Feature feature;
        while (features.hasNext()) {
            feature = features.next();
            info("Found feature: {0} at {1}, {2}",
                feature.getName(),
                feature.getLocation().getLatitude(),
                feature.getLocation().getLongitude());
        }
        info("ListFeatures completed");
    }

    public void recordRoute(List<Feature> features, int points) throws InterruptedException {
        info("*** RecordRoute");
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<RouteSummary> responseObserver = new StreamObserver<RouteSummary>() {

            @Override
            public void onNext(RouteSummary summary) {
                info("Finished trip with {0} points. Passed {1} features.",
                    summary.getPointCount(), summary.getFeatureCount());
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "RecordRoute failed: {0}", t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished RecordRoute");
                finishLatch.countDown();
            }

        };

        StreamObserver<Point> requestObserver = stub.recordRoute(responseObserver);
        try {
        // Send numPoints points randomly selected from the features list.
        for (int i = 0; i < points; ++i) {
            int index = random.nextInt(features.size());
            Point point = features.get(index).getLocation();
            info("Visiting point {0}, {1}", RouteGuideUtil.getLatitude(point),
                RouteGuideUtil.getLongitude(point));
            requestObserver.onNext(point);
            // Sleep for a bit before sending the next one.
            Thread.sleep(random.nextInt(1000) + 500);
        }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // Wait for server to finish and send summary
        finishLatch.await(1, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        String target = "localhost:8980";
        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [target]");
                System.err.println("");
                System.err.println("  target  The server to connect to. Defaults to " + target);
                System.exit(1);
            }
            target = args[0];
        }

        // Load the hedging service config
        String hedgingConfigJson = new String(
            Files.readAllBytes(Paths.get("src/main/java/io/grpc/examples/routeguide/util/hedging_service_config.json"))
        );

        Map<String, ?> hedgingServiceConfig = new com.google.gson.Gson()
            .fromJson(hedgingConfigJson, Map.class);

        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
            .defaultServiceConfig(hedgingServiceConfig)
            .enableRetry()
            .usePlaintext() // For testing without TLS
            // .overrideAuthority("brandon-mooney.com") // Override to match SSL certificate
            .build();

        try {
            RouteGuideClient client = new RouteGuideClient(channel);
            client.getFeature(409146138, -746188906);

            // client.listFeatures(400000000, -750000000, 420000000, -730000000);

            // List<Feature> features = RouteGuideUtil.parseFeatures(RouteGuideUtil.getDefaultFeaturesFile());

            // client.recordRoute(features, 10);

        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }
}
