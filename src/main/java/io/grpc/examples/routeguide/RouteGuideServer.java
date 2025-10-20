package io.grpc.examples.routeguide;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.examples.routeguide.db.FeatureRepository;
import io.grpc.examples.routeguide.header.HeaderServerInterceptor;
import io.grpc.examples.routeguide.interceptor.LatencyInjectionInterceptor;
import io.grpc.stub.StreamObserver;

public class RouteGuideServer {
      private static final Logger logger = Logger.getLogger(RouteGuideServer.class.getName());

    private final int port;
    private final Server server;
    private final HeaderServerInterceptor headerServerInterceptor;
    private final FeatureRepository repository;

    public RouteGuideServer(int port) throws IOException {
        this(Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create()), port);
    }

    public RouteGuideServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        this.headerServerInterceptor = new HeaderServerInterceptor();

        // Create repository for DynamoDB access using Abstract Factory pattern
        this.repository = new FeatureRepository();

        server = serverBuilder
            .addService(new RouteGuideService(repository))
            .intercept(new LatencyInjectionInterceptor())
            .intercept(headerServerInterceptor)
            .build();
    }

    public void start () throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                RouteGuideServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }
        });
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        // Close DynamoDB clients
        if (repository != null) {
            repository.close();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        RouteGuideServer server = new RouteGuideServer(8980);
        server.start();
        server.blockUntilShutdown();
    }

    private static class RouteGuideService extends RouteGuideGrpc.RouteGuideImplBase {
        private final FeatureRepository repository;

        RouteGuideService(FeatureRepository repository) {
            this.repository = repository;
        }

        @Override
        public void getFeature(GetFeatureRequest request, StreamObserver<Feature> responseObserver) {
            Feature feature = repository.getFeature(request.getPoint());

            // If field mask is provided and not empty, apply it
            if (request.hasFieldMask() && request.getFieldMask().getPathsCount() > 0) {
                logger.info("Field mask provided with paths: " + request.getFieldMask().getPathsList());
                Feature.Builder featureWithMaskedFields = Feature.newBuilder();
                FieldMaskUtil.merge(request.getFieldMask(), feature, featureWithMaskedFields);
                Feature maskedFeature = featureWithMaskedFields.build();
                logger.info("Returning masked feature: " + maskedFeature);
                responseObserver.onNext(maskedFeature);
            } else {
                // No field mask, return full feature
                logger.info("No field mask provided, returning full feature: " + feature);
                responseObserver.onNext(feature);
            }

            responseObserver.onCompleted();
        }

        // @Override
        // public void listFeatures(final Rectangle rectangle, StreamObserver<Feature> responseObserver) {
        //     for (Feature feature: features) {
        //         if (isFeatureInRectangle(feature, rectangle)) {
        //             responseObserver.onNext(feature);
        //         }
        //     }
        //     responseObserver.onCompleted();
        // }


        @Override
        public StreamObserver<Point> recordRoute(StreamObserver<RouteSummary> responseObserver) {
            
            return new StreamObserver<Point>() {

                int pointCount = 0;
                int featureCount = 0;

                @Override
                public void onNext(Point value) {
                    pointCount++;
                    if (repository.hasFeature(value)) {
                        featureCount++;
                    }
                }
                @Override
                public void onError(Throwable t) {
                    logger.warning("recordRoute cancelled");
                }
                @Override
                public void onCompleted() {
                    responseObserver.onNext(RouteSummary.newBuilder()
                        .setFeatureCount(featureCount)
                        .setPointCount(pointCount)
                        .build());

                    responseObserver.onCompleted();
                }
            };
            
        }
        @Override
        public void updateFeature(UpdateFeatureRequest request, StreamObserver<UpdateFeatureResponse> responseObserver) {
            logger.info("UpdateFeature called");

            // Validate request has feature and update_mask
            if (!request.hasFeature()) {
                logger.warning("UpdateFeature request missing feature");
                responseObserver.onError(new IllegalArgumentException("Feature is required"));
                return;
            }

            if (!request.hasUpdateMask() || request.getUpdateMask().getPathsCount() == 0) {
                logger.warning("UpdateFeature request missing or empty update_mask");
                responseObserver.onError(new IllegalArgumentException("update_mask is required and must not be empty"));
                return;
            }

            Feature requestedFeature = request.getFeature();
            FieldMask updateMask = request.getUpdateMask();

            logger.info("Updating feature at location (" +
                requestedFeature.getLocation().getLatitude() + ", " +
                requestedFeature.getLocation().getLongitude() + ")");
            logger.info("Update mask paths: " + updateMask.getPathsList());

            // Call repository to perform the update
            Feature updatedFeature = repository.updateFeature(requestedFeature, updateMask);

            if (updatedFeature == null) {
                logger.warning("Feature not found or update failed");
                responseObserver.onError(new IllegalArgumentException("Feature not found at specified location"));
                return;
            }

            logger.info("Successfully updated feature: " + updatedFeature.getName());

            // Return the updated feature in the response
            UpdateFeatureResponse response = UpdateFeatureResponse.newBuilder()
                .setFeature(updatedFeature)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        private boolean isFeatureInRectangle(Feature feature, final Rectangle rectangle) {
            Point lo = rectangle.getLo();
            Point hi = rectangle.getHi();
            int lat = feature.getLocation().getLatitude();
            int lon = feature.getLocation().getLongitude();

            return lat >= Math.min(lo.getLatitude(), hi.getLatitude())
                && lat <= Math.max(lo.getLatitude(), hi.getLatitude())
                && lon >= Math.min(lo.getLongitude(), hi.getLongitude())
                && lon <= Math.max(lo.getLongitude(), hi.getLongitude());
        }
    }

}
