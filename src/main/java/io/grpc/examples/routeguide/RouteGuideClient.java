package io.grpc.examples.routeguide;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.examples.routeguide.RouteGuideGrpc.RouteGuideBlockingStub;
import io.grpc.examples.routeguide.RouteGuideGrpc.RouteGuideStub;

public class RouteGuideClient {

    private static final Logger logger = Logger.getLogger(RouteGuideClient.class.getName());
    private final RouteGuideBlockingStub blockingStub;
    private final RouteGuideStub stub;

    public RouteGuideClient(Channel channel) {
        blockingStub = RouteGuideGrpc.newBlockingStub(channel);
        stub = RouteGuideGrpc.newStub(channel);
    }

    public void getFeature(int latitude, int longitude) {
        info("*** GetFeature: lat={0} lon={1}", latitude, longitude);

        final Point point = Point.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .build();

        final Feature feature = blockingStub.getFeature(point);

        if (feature.getName().isEmpty()) {
            info("Feature not found at lat={0} lon={1}", latitude, longitude);
        } else {
            info("Found feature: {0}", feature.getName());
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

    public static void main(String[] args) throws InterruptedException {
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

        ManagedChannel channel =
            Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

        try {
            RouteGuideClient client = new RouteGuideClient(channel);
            client.getFeature(409146138, -746188906);

            client.listFeatures(400000000, -750000000, 420000000, -730000000);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }
}
