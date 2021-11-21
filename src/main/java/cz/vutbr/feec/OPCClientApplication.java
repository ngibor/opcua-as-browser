package cz.vutbr.feec;

import org.apache.commons.cli.*;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class OPCClientApplication {
    private final OpcUaClient client;
    private int currentDepth;
    private final boolean verboseOutput;

    public static void main(String[] args) throws Exception {
        OPCClientApplication app = null;
        String serverUrl = "opc.tcp://milo.digitalpetri.com:62541/milo";
        int maxDepth = Integer.MAX_VALUE;
        NodeId rootNode = Identifiers.RootFolder;
        boolean verbose = false;
        FileOutputStream fileOutputStream = null;

        final Options options = new Options();
        options.addOption(new Option("h", "help", false, "Print this message."));
        options.addOption(new Option("t", "time", false, "Print total time spent."));
        options.addOption(new Option("v", "verbose", false, "Print both id and name."));
        options.addOption(new Option("d", "depth", true, "Depth of browsing."));
        options.addOption(new Option("r", "root", true, "Root node in format \"" +
                "namespaceIndex,identifier\""));
        options.addOption(new Option("f", "file", true, "Redirect output to file."));
        options.addOption(new Option("s", "server", true, "Server url, default is opc.tcp://milo.digitalpetri.com:62541/milo."));
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        
        if (cmd.hasOption("help")) {
            String header = "Inspect OPC UA server's Address Space\n\n";
            String footer = "\nSource code is available at http://example.com/issues";
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar opcua-as-browser.jar", header, options, footer, true);
            System.exit(0);
        }

        if (cmd.hasOption("verbose")) {
            verbose = true;
        }

        if (cmd.hasOption("depth")) {
            maxDepth = Integer.parseInt(cmd.getOptionValue("depth"));
        }

        if (cmd.hasOption("root")) {
            String[] nodeId = cmd.getOptionValue("root").split(",");
            rootNode = new NodeId(Integer.parseInt(nodeId[0]), nodeId[1]);
        }

        if (cmd.hasOption("file")) {
            fileOutputStream = new FileOutputStream(cmd.getOptionValue("file"));
            System.setOut(new PrintStream(new BufferedOutputStream(fileOutputStream)));
        }

        if (cmd.hasOption("server")) {
            serverUrl = cmd.getOptionValue("server");
        }

        try {
            app = new OPCClientApplication(serverUrl, verbose);
        } catch (UaException uaException) {
            System.out.println("Unable to create client instance, exiting");
            System.exit(0);
        } catch (Exception ex) {
            System.out.println("Unable to connect to server, exiting");
            System.exit(0);
        }


        LocalDateTime begin = LocalDateTime.now();
        app.browseRoot(rootNode, maxDepth);
        if (cmd.hasOption("time")) {
            LocalDateTime end = LocalDateTime.now();

            long totalSecs = Duration.between(begin, end).getSeconds();
            long hours = totalSecs / 3600;
            long minutes = (totalSecs % 3600) / 60;
            long seconds = totalSecs % 60;
            String timeSpent = "";
            if (hours != 0) {
                timeSpent +=  hours + " hours, ";
                timeSpent += minutes + " minutes, ";
            } else if (minutes != 0) {
                timeSpent += minutes + " minutes, ";
            }
            timeSpent += "" + seconds + " seconds";
            System.out.println("Time spent: " + timeSpent);
        }
        if (fileOutputStream != null) {
            System.out.close();
        }
    }

    private static Predicate<EndpointDescription> endpointFilter() {
        return e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri());
    }


    public OPCClientApplication(String serverUrl, boolean verboseOutput) throws UaException, ExecutionException, InterruptedException {
        // Create client with given address amd endpoint filter set to none
        this.client = OpcUaClient.create(
                serverUrl,
                endpoints ->
                        endpoints.stream()
                                .filter(this.endpointFilter())
                                .findFirst(),
                configBuilder ->
                        configBuilder
                                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                                .setApplicationUri("urn:eclipse:milo:examples:client")
                                .setRequestTimeout(uint(5000))
                                .build()
        );
        // Connect to server
        this.client.connect().get();
        this.verboseOutput = verboseOutput;
    }


    public void browseRoot(NodeId browseRoot, final int maxDepth) {
        currentDepth = 1;
        try {
            List<? extends UaNode> nodes = client.getAddressSpace().browseNodes(browseRoot);
            for (int i = 0; i < nodes.size(); i++) {
                UaNode node = nodes.get(i);
                System.out.print(node.getBrowseName().getName() + ": {");

                if (maxDepth > 1) {
                    browseNode("  ", node.getNodeId(), maxDepth, i == nodes.size() - 1);
                } else {
                    System.out.print(client.getAddressSpace().browseNodes(node.getNodeId()).isEmpty() ? "" : "...");
                    closeObject(i == nodes.size() - 1);
                }
            }
        } catch (UaException e) {
            e.printStackTrace();
        }
    }

    public void browseNode(String indent, NodeId browseRoot, final int maxDepth, boolean isLast) {
        currentDepth++;
        try {
            List<? extends UaNode> nodes = client.getAddressSpace().browseNodes(browseRoot);
            if (nodes.isEmpty()) {
                closeObject(isLast);
                currentDepth--;
                return;
            } else {
                System.out.println();
            }

            for (int i = 0; i < nodes.size(); i++) {
                UaNode node = nodes.get(i);
                System.out.print(indent + node.getBrowseName().getName() + ": {");

//                 recursively browse to children
                if (currentDepth < maxDepth) {
                    browseNode(indent + "  ", node.getNodeId(), maxDepth, i == nodes.size() - 1);
                } else {
                    System.out.print(client.getAddressSpace().browseNodes(node.getNodeId()).isEmpty() ? "" : "...");
                    closeObject(i == nodes.size() - 1);
                }
            }
            currentDepth--;
            System.out.println(indent.replaceFirst("  ", "") + "},");
        } catch (UaException e) {
            System.out.println(String.format("Browsing nodeId={%s} failed: {%s}", browseRoot, e.getMessage()));
        }
    }

    private void closeObject(boolean isLast) {
        System.out.println(isLast ? "}" : "},");
    }

    private String getNodeDescription(UaNode node) {
        String description = node.getBrowseName().getName();
        if (verboseOutput) {
            description += String.format("[%s,%s]", node.getNodeId().getNamespaceIndex(), node.getNodeId().getIdentifier());
        }
        return description;
    }
}
