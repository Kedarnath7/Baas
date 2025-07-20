package baas.core;

import com.minibaas.proto.DatabaseServiceGrpc;
import com.minibaas.proto.DatabaseServiceProto.*;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.json.JSONObject;

import javax.net.ssl.SSLException;
import java.io.File;

public class QueryService {
    private final ManagedChannel channel;
    private final QueryExecutor executor;

    public QueryService(String host, int port, ConfigLoader config) throws SSLException {
        String token = config.get("auth.token");
        String certPath = config.get("grpc.cert");

        // TLS Channel + cert verification
        this.channel = NettyChannelBuilder.forAddress(host, port)
                .sslContext(GrpcSslContexts.forClient().trustManager(new File(certPath)).build())
                .build();

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER), token);

        ClientInterceptor authInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);

        DatabaseServiceGrpc.DatabaseServiceBlockingStub stub =
                DatabaseServiceGrpc.newBlockingStub(channel).withInterceptors(authInterceptor);

        this.executor = new QueryExecutor(stub);
    }

    public JSONObject executeQuery(String queryJson) {
        return executor.execute(baas.core.QueryParser.parse(queryJson));
    }

    public void shutdown() {
        channel.shutdown();
    }
}
