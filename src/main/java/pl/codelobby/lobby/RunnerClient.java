package pl.codelobby.lobby;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.lobby.dto.LobbyRunRequest;
import pl.codelobby.lobby.dto.LobbyRunResponse;
import pl.codelobby.task.JavaPublicTests;
import pl.codelobby.task.TaskFileType;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RunnerClient {

    private final RestClient client;
    private final String internalKey;

    public RunnerClient(
            @Value("${app.runner.base-url}") String baseUrl,
            @Value("${app.runner.internal-key}") String internalKey
    ) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(65));

        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalKey = internalKey;
    }

    public LobbyRunResponse run(Run run) {
        if (internalKey.length() < 32) {
            throw unavailable();
        }

        String requestId = UUID.randomUUID().toString();
        List<String> testPaths = run.taskFiles().stream()
                .filter(file -> file.type() == TaskFileType.VISIBLE_TEST)
                .map(SourceFile::path)
                .toList();
        JavaPublicTests tests = run.tests();
        var suite = new Suite("public-suite", "PUBLIC", testPaths,
                new Execution("JAVA_MAIN", tests.mainClass()),
                tests.testIds().stream().map(Case::new).toList());
        var request = new RunEnvelope(requestId, run.taskVersionId(), "JAVA",
                run.taskFiles(), run.files(), List.of(suite), new Limits(2000, 5000, 128_000_000));

        RunReply reply;
        try {
            reply = client.post()
                    .uri("/internal/v1/runs")
                    .header("X-CodeLobby-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RunReply.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 400 || status == 413 || status == 422) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "RUN_INPUT_REJECTED",
                        "The task configuration or source files could not be run");
            }
            if (status == 504) {
                throw timeout();
            }
            throw unavailable();
        } catch (ResourceAccessException exception) {
            if (exception.getCause() instanceof java.net.http.HttpTimeoutException
                    || exception.getCause() instanceof java.net.SocketTimeoutException) {
                throw timeout();
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidReply();
        }

        return toPublicResponse(reply, requestId, run.taskVersionId(), tests.testIds());
    }

    private LobbyRunResponse toPublicResponse(
            RunReply reply,
            String requestId,
            String taskVersionId,
            List<String> expectedTests
    ) {
        if (reply == null || !requestId.equals(reply.requestId())
                || !taskVersionId.equals(reply.taskVersionId()) || reply.result() == null) {
            throw invalidReply();
        }

        var result = reply.result();
        if (result.status() == null || result.testResults() == null
                || result.totalTests() != expectedTests.size()
                || result.testResults().size() != expectedTests.size()) {
            throw invalidReply();
        }

        for (int index = 0; index < expectedTests.size(); index++) {
            var test = result.testResults().get(index);
            if (test == null || !"PUBLIC".equals(test.visibility()) || test.status() == null
                    || !expectedTests.get(index).equals(test.testId())) {
                throw invalidReply();
            }
        }

        long passed = result.testResults().stream()
                .filter(test -> test.status() == LobbyRunResponse.TestVerdict.ACCEPTED).count();
        if (result.passedTests() != passed
                || (result.status() == LobbyRunResponse.Verdict.ACCEPTED && passed != expectedTests.size())) {
            throw invalidReply();
        }

        List<LobbyRunResponse.Test> tests = result.testResults().stream()
                .map(test -> new LobbyRunResponse.Test(test.testId(), test.status(),
                        test.input(), test.expectedOutput(), test.actualOutput()))
                .toList();
        return new LobbyRunResponse(requestId, taskVersionId,
                new LobbyRunResponse.Result(result.status(), result.passedTests(), result.totalTests(), tests));
    }

    private ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "RUNNER_TIMEOUT",
                "Runner did not return a result in time");
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_UNAVAILABLE",
                "Runner is temporarily unavailable");
    }

    private ApiException invalidReply() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "RUNNER_INVALID_RESPONSE",
                "Runner returned an invalid result");
    }

    record Run(String taskVersionId, List<SourceFile> taskFiles, List<LobbyRunRequest.File> files,
               JavaPublicTests tests) {
    }

    record SourceFile(String path, TaskFileType type, String content) {
    }

    private record RunEnvelope(String requestId, String taskVersionId, String language, List<SourceFile> taskFiles,
                               List<LobbyRunRequest.File> submissionFiles, List<Suite> testSuites, Limits limits) {
    }

    private record Suite(String suiteId, String visibility, List<String> testFilePaths,
                         Execution execution, List<Case> cases) {
    }

    private record Execution(String profile, String mainClass) {
    }

    private record Case(String testId) {
    }

    private record Limits(int cpuTimeMs, int wallTimeMs, long memoryLimitBytes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunReply(String requestId, String taskVersionId, RunnerResult result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunnerResult(LobbyRunResponse.Verdict status, int passedTests, int totalTests,
                                List<RunnerTest> testResults) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunnerTest(String testId, String visibility, LobbyRunResponse.TestVerdict status,
                              JsonNode input, JsonNode expectedOutput, JsonNode actualOutput) {
    }
}
