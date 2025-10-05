package io.grpc.examples.routeguide;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class RouteGuideServer {
      private static final Logger logger = Logger.getLogger(RouteGuideServer.class.getName());

    private final int port;
    private final Server server;

    public RouteGuideServer(int port) throws IOException {
        this(port, RouteGuideUtil.getDefaultFeaturesFile());
    }

    public RouteGuideServer(int port, URL featureFile) throws IOException {
        this(Grpc.newServerBuilderForPort(
            port, InsecureServerCredentials.create()),
            port, RouteGuideUtil.parseFeatures(featureFile));
    }

    public RouteGuideServer(ServerBuilder<?> serverBuilder, int port, Collection<Feature> features) {
        this.port = port;
        server = serverBuilder.addService(new RouteGuideService(features)).build();
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
        private final Collection<Feature> features;

        RouteGuideService(Collection<Feature> features) {
            this.features = features;
        }

        @Override
        public void getFeature(Point request, StreamObserver<Feature> responseObserver) {
            Feature feature = checkFeature(request);
            responseObserver.onNext(feature);
            responseObserver.onCompleted();
        }

        private Feature checkFeature(final Point point) {
            for (Feature feature: features) {
                if (feature.getLocation().getLatitude() == point.getLatitude() && feature.getLocation().getLongitude() == point.getLongitude()) {
                    return feature;
                }
            }
            return Feature.newBuilder()
                .setName("Unknown")
                .setLocation(
                    Point.newBuilder()
                    .setLatitude(0)
                    .setLongitude(0)
                    .build())
                .build();  
        }
    }

}
