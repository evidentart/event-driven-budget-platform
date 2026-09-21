package com.sea.expenseservice.controller;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            JwtAuthenticationToken auth,
            @Valid @RequestBody ExpenseRequest request) {

        String ownerSubject = auth.getToken().getSubject();
        log.info("Creating expense for ownerSubject={}", ownerSubject);
        ExpenseResponse response = expenseService.createExpense(ownerSubject, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<ExpenseResponse>> listMyExpenses(JwtAuthenticationToken auth) {
        String ownerSubject = auth.getToken().getSubject();
        log.info("Listing expenses for ownerSubject={}", ownerSubject);
        return ResponseEntity.ok(expenseService.listExpensesByOwner(ownerSubject));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpense(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        log.info("Fetching expense with id={}", id);
        ExpenseResponse response = expenseService.getExpenseById(auth.getToken().getSubject(), id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(
            JwtAuthenticationToken auth,
            @PathVariable UUID id) {
        log.info("Deleting expense with id={}", id);
        expenseService.deleteExpense(auth.getToken().getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
