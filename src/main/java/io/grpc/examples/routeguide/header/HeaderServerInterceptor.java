package io.grpc.examples.routeguide.header;

import java.util.logging.Logger;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class HeaderServerInterceptor implements ServerInterceptor {

    private static final Logger logger = Logger.getLogger(HeaderServerInterceptor.class.getName());

    static final Metadata.Key<String> CUSTOM_HEADER_KEY =
      Metadata.Key.of("custom_server_header_key", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {

        logger.info("header received from client:" + headers);
        return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                responseHeaders.put(CUSTOM_HEADER_KEY, "customRespondValue");
                super.sendHeaders(responseHeaders);
            } 
        }, 
        headers);
    }
    
}
