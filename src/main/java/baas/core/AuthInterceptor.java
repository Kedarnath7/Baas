package baas.core;

import io.grpc.*;

public class AuthInterceptor implements ServerInterceptor {
    private final String expectedToken;

    public AuthInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String token = headers.get(Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER));
        if (!expectedToken.equals(token)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid auth token"), new Metadata());
            return new ServerCall.Listener<>() {}; // no-op
        }
        return next.startCall(call, headers);
    }

    public ServerServiceDefinition intercept(BindableService service) {
        return ServerInterceptors.intercept(service, this);
    }

}
