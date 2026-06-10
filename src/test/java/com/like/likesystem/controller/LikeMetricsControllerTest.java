package com.like.likesystem.controller;

import com.like.likesystem.service.LikeFlowMetricsService;
import com.like.likesystem.service.LikeMetricsService;
import com.like.likesystem.service.LikeSystemStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikeMetricsController.class)
class LikeMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LikeMetricsService likeMetricsService;

    @MockBean
    private LikeFlowMetricsService likeFlowMetricsService;

    @MockBean
    private LikeSystemStateService likeSystemStateService;

    @Test
    void flowMetricsReturnsOk() throws Exception {
        when(likeFlowMetricsService.snapshot()).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/like/flow-metrics"))
                .andExpect(status().isOk());
    }

    @Test
    void systemStateReturnsOk() throws Exception {
        when(likeSystemStateService.snapshot(1L)).thenReturn(
                new LikeSystemStateService.LikeSystemStateSnapshot(1L, 0L, 0L, 0L, 0L, 0L, 0L)
        );

        mockMvc.perform(get("/api/v1/like/system-state?videoId=1"))
                .andExpect(status().isOk());
    }
}
