package io.grpc.examples.routeguide.interceptor;

import java.util.Random;
import java.util.logging.Logger;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class LatencyInjectionInterceptor implements ServerInterceptor{

    private static final Logger logger = Logger.getLogger(LatencyInjectionInterceptor.class.getName());

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        int random = new Random().nextInt(100);
        long delay = 5_000;
        if (random < 1) {
            delay = 10_000;
        } else if (random < 5) {
            delay = 5_000;
        } else if (random < 10) {
            delay = 2_000;
        }

        if (delay > 0) {
            logger.info("Injecting " + delay + "ms delay for " + call.getMethodDescriptor().getFullMethodName());
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Processing request for " + call.getMethodDescriptor().getFullMethodName());
        return next.startCall(call, headers);
    }

}
