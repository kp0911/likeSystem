package com.like.likesystem.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadTestRunnerServiceTest {

    private final LoadTestRunnerService service = new LoadTestRunnerService();

    @Test
    void rejectsUnsupportedMode() {
        assertThatThrownBy(() -> service.start("other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported load test mode");
    }

    @Test
    void supportsSustainedAndBurstLoadModes() {
        assertThat(LoadTestRunnerService.supportedModes())
                .containsExactlyInAnyOrder("sync", "buffered-async", "burst-sync", "burst-buffered-async");
    }
}
