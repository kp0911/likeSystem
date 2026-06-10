package com.like.likesystem.controller;

import com.like.likesystem.service.LoadTestRunnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/load-tests")
@RequiredArgsConstructor
public class LoadTestController {

    private final LoadTestRunnerService loadTestRunnerService;

    @PostMapping("/start")
    public ResponseEntity<LoadTestRunnerService.LoadTestStatus> start(@Valid @RequestBody LoadTestStartRequest request) {
        return ResponseEntity.accepted().body(loadTestRunnerService.start(request.mode()));
    }

    @GetMapping("/current")
    public LoadTestRunnerService.LoadTestStatus current() {
        return loadTestRunnerService.current();
    }

    @GetMapping(value = "/results/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public String latestResult() {
        return loadTestRunnerService.latestResultJson();
    }

    public record LoadTestStartRequest(@NotBlank String mode) {
    }
}
