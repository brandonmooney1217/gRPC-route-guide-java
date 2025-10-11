package io.grpc.examples.routeguide.header;

import java.util.logging.Logger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;

public class HeaderClientInterceptor implements ClientInterceptor{

    private static final Logger logger = Logger.getLogger(HeaderClientInterceptor.class.getName());

    static final Metadata.Key<String> CUSTOM_HEADER_KEY =
      Metadata.Key.of("custom_client_header_key", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {

        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                /* put custom header */
                headers.put(CUSTOM_HEADER_KEY, "customRequestValue");
                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onHeaders(Metadata headers) {
                    logger.info("header received from server:" + headers);
                    super.onHeaders(headers);
                }
                }, headers);
            }
        };
    }
    
}
