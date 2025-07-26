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

        @Parameters(index = "1", description = "JSON document or array of documents")
        private String document;

        @Override
        public Integer call() {
            System.out.println("Connecting to gRPC server at " + parent.host + ":" + parent.port);
            try (BaasGrpcClient client = new BaasGrpcClient(parent.host, parent.port)) {
                String result = client.insert(collection, null, document);
                System.out.println("..." + result);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }



    @Command(name = "get", description = "Get document(s) - by ID, all documents, or with conditions")
    static class GetCommand implements Callable<Integer> {

        @ParentCommand
        private BaasCLI parent;

        @Parameters(index = "0", description = "Collection name")
        private String collection;

        @Parameters(index = "1", description = "Document ID (optional when using --all)", arity = "0..1")
        private String id;

        @Option(names = {"--all"}, description = "Get all documents in the collection")
        private boolean getAll;

        @Option(names = {"--where"}, description = "Filter condition (e.g., 'name=Alice', 'age>=25')")
        private String whereCondition;

        @Option(names = {"--limit"}, description = "Limit number of results (default: no limit)")
        private Integer limit;

        @Option(names = {"--fields"}, description = "Comma-separated list of fields to return (e.g., 'name,age')")
        private String fields;

        @Option(names = {"--sort"}, description = "Sort by field (e.g., 'name ASC', 'age DESC')")
        private String sort;

        @Override
        public Integer call() {
            try (BaasGrpcClient client = new BaasGrpcClient(parent.host, parent.port)) {
                String result;

                // Handle different query modes
                if (getAll || whereCondition != null || limit != null || fields != null || sort != null) {
                    // Use enhanced query functionality
                    result = client.queryDocuments(collection, whereCondition, limit, fields, sort, getAll);

                    if (result.isEmpty()) {
                        System.out.println("No documents found");
                    } else {
                        if (getAll) {
                            System.out.println("All documents in collection '" + collection + "':");
                        } else if (whereCondition != null) {
                            System.out.println("Documents matching '" + whereCondition + "':");
                        }
                        System.out.println(result);
                    }
                } else if (id != null) {
                    // Original single document get by ID
                    result = client.get(collection, id);
                    if (result.isEmpty()) {
                        System.out.println("No document found with ID: " + id);
                    } else {
                        System.out.println("Document:\n" + result);
                    }
                } else {
                    System.err.println("Error: Please provide either a document ID or use --all flag");
                    System.err.println("Examples:");
                    System.err.println("  get myCollection documentId");
                    System.err.println("  get myCollection --all");
                    System.err.println("  get myCollection --where \"name=Alice\"");
                    return 1;
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