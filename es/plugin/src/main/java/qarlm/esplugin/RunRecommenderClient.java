package qarlm.esplugin;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name="RecommenderClient")
public class RunRecommenderClient implements Callable<Integer> {
    @CommandLine.Option(names={"--host"}, required=true, description="domain to use for grpc")
    private String host;

    @CommandLine.Option(names={"--port"})
    private String port = "50051";

    public static void main(String ...args) {
        int exitCode = new CommandLine(new RunRecommenderClient()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        RecommenderClient rec = RecommenderClient.buildRecommenderClient(host + ":" + port);
        rec.score(new long[]{1L, 2L, 3L}, "some_context");
        return 0;
    }
}
