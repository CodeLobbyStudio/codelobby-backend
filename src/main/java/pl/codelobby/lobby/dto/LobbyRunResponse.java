package pl.codelobby.lobby.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record LobbyRunResponse(
        String requestId,
        String taskVersionId,
        Result result
) {
    public record Result(Verdict status, int passedTests, int totalTests, List<Test> testResults) {
    }

    public record Test(
            String testId,
            TestVerdict status,
            JsonNode input,
            JsonNode expectedOutput,
            JsonNode actualOutput
    ) {
    }

    public enum Verdict {
        ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED, EXECUTION_ERROR
    }

    public enum TestVerdict {
        ACCEPTED, WRONG_ANSWER, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED,
        OUTPUT_LIMIT_EXCEEDED, EXECUTION_ERROR, NOT_RUN
    }
}
