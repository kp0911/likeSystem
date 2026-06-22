package com.like.likesystem.controller;

import com.like.likesystem.service.LoadTestRunnerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoadTestController.class)
class LoadTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoadTestRunnerService loadTestRunnerService;

    @Test
    void startReturnsAcceptedForAllowedMode() throws Exception {
        when(loadTestRunnerService.start("sync")).thenReturn(
                new LoadTestRunnerService.LoadTestStatus(true, "sync", Instant.now(), null, null, false)
        );

        mockMvc.perform(post("/api/v1/load-tests/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"sync\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void startRejectsBlankMode() throws Exception {
        mockMvc.perform(post("/api/v1/load-tests/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopReturnsOk() throws Exception {
        when(loadTestRunnerService.stop()).thenReturn(
                new LoadTestRunnerService.LoadTestStatus(false, "sync", Instant.now(), 143, "k6 테스트를 중지했습니다.", true)
        );

        mockMvc.perform(post("/api/v1/load-tests/stop"))
                .andExpect(status().isOk());
    }
}
