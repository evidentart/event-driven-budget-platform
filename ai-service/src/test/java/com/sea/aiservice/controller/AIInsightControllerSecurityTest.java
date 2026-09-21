package com.sea.aiservice.controller;

import com.sea.aiservice.service.AIInsightQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AIInsightController.class)
@Import(com.sea.aiservice.config.ResourceServerSecurityConfig.class)
class AIInsightControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AIInsightQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/insights/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopesInsightReadsToAuthenticatedSubject() throws Exception {
        when(queryService.getInsightsByOwner("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/insights/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))))
                .andExpect(status().isOk());

        verify(queryService).getInsightsByOwner("alice");
    }

    @Test
    void expenseInsightLookupPassesAuthenticatedSubjectToService() throws Exception {
        var expenseId = java.util.UUID.randomUUID();
        when(queryService.getInsightByExpense("alice", expenseId)).thenReturn(null);

        mockMvc.perform(get("/api/insights/expense/{expenseId}", expenseId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))))
                .andExpect(status().isOk());

        verify(queryService).getInsightByExpense("alice", expenseId);
    }
}
