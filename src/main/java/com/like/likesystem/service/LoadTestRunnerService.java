package com.like.likesystem.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LoadTestRunnerService {

    private static final Path RESULT_DIR = Path.of("build", "k6-results");
    private static final Map<String, LoadTestDefinition> DEFINITIONS = Map.of(
            "sync", new LoadTestDefinition("sync", "load-test-sync.js", RESULT_DIR.resolve("sync-latest.json")),
            "buffered-async", new LoadTestDefinition("buffered-async", "load-test-buffered-async.js", RESULT_DIR.resolve("buffered-async-latest.json"))
    );

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LoadTestStatus currentStatus = LoadTestStatus.idle();
    private volatile String latestMode;

    public LoadTestStatus start(String mode) {
        LoadTestDefinition definition = DEFINITIONS.get(mode);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported load test mode: " + mode);
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Load test is already running");
        }

        currentStatus = new LoadTestStatus(true, mode, Instant.now(), null, null);
        latestMode = mode;

        Thread thread = new Thread(() -> runK6(definition), "k6-" + mode);
        thread.setDaemon(true);
        thread.start();

        return currentStatus;
    }

    public LoadTestStatus current() {
        return currentStatus;
    }

    public String latestResultJson() {
        if (latestMode == null) {
            return "{}";
        }

        LoadTestDefinition definition = DEFINITIONS.get(latestMode);
        if (definition == null || !Files.exists(definition.resultPath())) {
            return "{}";
        }

        try {
            return Files.readString(definition.resultPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read k6 result", e);
        }
    }

    private void runK6(LoadTestDefinition definition) {
        try {
            Files.createDirectories(RESULT_DIR);
            List<String> command = List.of(
                    "k6",
                    "run",
                    "--summary-export",
                    definition.resultPath().toString(),
                    definition.script()
            );
            Process process = new ProcessBuilder(command)
                    .directory(Path.of("").toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(RESULT_DIR.resolve(definition.mode() + "-latest.log").toFile())
                    .start();

            int exitCode = process.waitFor();
            currentStatus = new LoadTestStatus(false, definition.mode(), currentStatus.startedAt(), exitCode, null);
        } catch (IOException e) {
            currentStatus = new LoadTestStatus(false, definition.mode(), currentStatus.startedAt(), null, "k6 실행 실패: PATH 또는 설치 상태를 확인하세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            currentStatus = new LoadTestStatus(false, definition.mode(), currentStatus.startedAt(), null, "k6 실행이 중단되었습니다.");
        } finally {
            running.set(false);
        }
    }

    private record LoadTestDefinition(String mode, String script, Path resultPath) {
    }

    public record LoadTestStatus(
            boolean running,
            String mode,
            Instant startedAt,
            Integer exitCode,
            String errorMessage
    ) {
        private static LoadTestStatus idle() {
            return new LoadTestStatus(false, null, null, null, null);
        }
    }
}
