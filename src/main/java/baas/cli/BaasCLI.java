package baas.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "baas", mixinStandardHelpOptions = true, version = "1.0",
        description = "BaaS (Backend-as-a-Service) Command Line Interface",
        subcommands = {
                BaasCLI.InsertCommand.class,
                BaasCLI.GetCommand.class,
                BaasCLI.QueryCommand.class
        })
public class BaasCLI implements Runnable {

    @Option(names = {"-s", "--server"},
            description = "Server host (default: ${DEFAULT-VALUE})",
            defaultValue = "localhost")
    private String host;

    @Option(names = {"-p", "--port"},
            description = "Server port (default: ${DEFAULT-VALUE})",
            defaultValue = "9090")
    private int port;


    public static void main(String[] args) {
        int exitCode = new CommandLine(new BaasCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "insert", description = "Insert a document into a collection")
    static class InsertCommand implements Callable<Integer> {

        @ParentCommand
        private BaasCLI parent;

        @Parameters(index = "0", description = "Collection name")
        private String collection;

        @Parameters(index = "1", description = "Document ID")
        private String id;

        @Parameters(index = "2", description = "JSON document content")
        private String document;

        @Override
        public Integer call() {
            System.out.println("Connecting to gRPC server at " + parent.host + ":" + parent.port);
            try (BaasGrpcClient client = new BaasGrpcClient(parent.host, parent.port)) {
                String result = client.insert(collection, id, document);
                System.out.println("..." + result);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }


        @Command(name = "get", description = "Get a document by ID")
    static class GetCommand implements Callable<Integer> {

        @ParentCommand
        private BaasCLI parent;

        @Parameters(index = "0", description = "Collection name")
        private String collection;

        @Parameters(index = "1", description = "Document ID")
        private String id;

        @Override
        public Integer call() {
            try (BaasGrpcClient client = new BaasGrpcClient(parent.host, parent.port)) {
                String result = client.get(collection, id);
                if (result.isEmpty()) {
                    System.out.println("No document found");
                } else {
                    System.out.println("Document:\n" + result);
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "query", description = "Query documents by field value")
    static class QueryCommand implements Callable<Integer> {

        @ParentCommand
        private BaasCLI parent;

        @Parameters(index = "0", description = "Collection name")
        private String collection;

        @Parameters(index = "1", description = "Field name")
        private String field;

        @Parameters(index = "2", description = "Field value")
        private String value;

        @Override
        public Integer call() {
            try (BaasGrpcClient client = new BaasGrpcClient(parent.host, parent.port)) {
                String result = client.query(collection, field, value);
                System.out.println("Query Results:\n" + result);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
