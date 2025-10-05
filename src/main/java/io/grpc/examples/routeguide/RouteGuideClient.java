package io.grpc.examples.routeguide;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.examples.routeguide.RouteGuideGrpc.RouteGuideBlockingStub;

public class RouteGuideClient {

    private static final Logger logger = Logger.getLogger(RouteGuideClient.class.getName());
    private final RouteGuideBlockingStub blockingStub;

    public RouteGuideClient(Channel channel) {
        blockingStub = RouteGuideGrpc.newBlockingStub(channel);
    }

    public void getFeatures(int latitude, int longitude) {
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
            client.getFeatures(409146138, -746188906);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }
}
