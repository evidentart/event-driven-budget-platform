package com.sea.expenseservice.controller;

import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ExpenseController.class)
@Import(com.sea.expenseservice.config.ResourceServerSecurityConfig.class)
class ExpenseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/expenses/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void derivesOwnerFromAuthenticatedSubject() throws Exception {
        UUID expenseId = UUID.randomUUID();
        when(expenseService.createExpense(eq("alice"), any()))
                .thenReturn(ExpenseResponse.builder().id(expenseId).build());

                mockMvc.perform(post("/api/expenses")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "bob",
                                  "title": "Groceries",
                                  "amount": "25.50",
                                  "category": "FOOD",
                                  "expenseDate": "2026-02-18T18:30:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/expenses/" + expenseId));

        verify(expenseService).createExpense(eq("alice"), any());
    }

    @Test
    void returnsBadRequestForValidationFailuresWithConsistentErrorShape() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice")))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/expenses"));
    }

    @Test
    void exposesFinancialResponseValuesAsDecimalStrings() throws Exception {
        UUID expenseId = UUID.randomUUID();
        when(expenseService.createExpense(eq("alice"), any()))
                .thenReturn(ExpenseResponse.builder()
                        .id(expenseId)
                        .amount("12.34")
                        .remainingBudgetAfter("5.66")
                        .build());

        mockMvc.perform(post("/api/expenses")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Groceries",
                                  "amount": "12.34",
                                  "category": "FOOD",
                                  "expenseDate": "2026-02-18T18:30:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").isString())
                .andExpect(jsonPath("$.amount").value("12.34"))
                .andExpect(jsonPath("$.remainingBudgetAfter").isString())
                .andExpect(jsonPath("$.remainingBudgetAfter").value("5.66"));
    }

    @Test
    void rejectsAmountsWithMoreThanTwoDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Groceries",
                                  "amount": "1.001",
                                  "category": "FOOD",
                                  "expenseDate": "2026-02-18T18:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
