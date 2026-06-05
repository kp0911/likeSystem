package com.like.likesystem.controller;

import com.like.likesystem.service.LikeAsyncService;
import com.like.likesystem.service.LikeBufferedAsyncService;
import com.like.likesystem.service.LikeMetricsService;
import com.like.likesystem.service.LikeSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikeController.class)
class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LikeSyncService likeSyncService;

    @MockBean
    private LikeAsyncService likeAsyncService;

    @MockBean
    private LikeBufferedAsyncService likeBufferedAsyncService;

    @MockBean
    private LikeMetricsService likeMetricsService;

    @Test
    void likeSyncReturnsOkForValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/like/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":1}"))
                .andExpect(status().isOk());

        verify(likeSyncService).processLike(1L);
    }

    @Test
    void likeSyncRejectsInvalidVideoId() throws Exception {
        mockMvc.perform(post("/api/v1/like/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(likeSyncService);
    }

    @Test
    void likeSyncReturnsNotFoundWhenVideoDoesNotExist() throws Exception {
        doThrow(new IllegalArgumentException("Video not found")).when(likeSyncService).processLike(999L);

        mockMvc.perform(post("/api/v1/like/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void likeAsyncReturnsOkForValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/like/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":1,\"userId\":\"user-1\"}"))
                .andExpect(status().isOk());

        verify(likeAsyncService).processLike(1L, "user-1");
    }

    @Test
    void likeAsyncRejectsBlankUserId() throws Exception {
        mockMvc.perform(post("/api/v1/like/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":1,\"userId\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(likeAsyncService);
    }

    @Test
    void likeBufferedAsyncReturnsOkForValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/like/buffered-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":1,\"userId\":\"user-1\"}"))
                .andExpect(status().isOk());

        verify(likeBufferedAsyncService).processLike(1L, "user-1");
    }

    @Test
    void likeBufferedAsyncRejectsBlankUserId() throws Exception {
        mockMvc.perform(post("/api/v1/like/buffered-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":1,\"userId\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(likeBufferedAsyncService);
    }
}
