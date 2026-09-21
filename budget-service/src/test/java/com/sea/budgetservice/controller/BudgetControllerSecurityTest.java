package com.sea.budgetservice.controller;

import com.sea.budgetservice.dto.BudgetResponse;
import com.sea.budgetservice.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BudgetController.class)
@Import(com.sea.budgetservice.config.ResourceServerSecurityConfig.class)
class BudgetControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/budgets/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopesBudgetReadsToAuthenticatedSubject() throws Exception {
        when(budgetService.getAllBudgetsByOwner("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/budgets/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))))
                .andExpect(status().isOk());

        verify(budgetService).getAllBudgetsByOwner(eq("alice"));
    }

    @Test
    void derivesBudgetOwnerFromAuthenticatedSubject() throws Exception {
        when(budgetService.createBudget(eq("alice"), any()))
                .thenReturn(BudgetResponse.builder().build());

        mockMvc.perform(post("/api/budgets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "bob",
                                  "monthlyBudget": 500.00,
                                  "period": "2026-02"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(budgetService).createBudget(eq("alice"), any());
    }
}
