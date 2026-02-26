package com.sea.expenseservice.controller;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request) {
        log.info("Creating expense for userId={}", request.getUserId());
        ExpenseResponse response = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpense(@PathVariable UUID id) {
        log.info("Fetching expense with id={}", id);
        ExpenseResponse response = expenseService.getExpenseById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ExpenseResponse>> listExpensesByUser(@PathVariable UUID userId) {
        log.info("Listing expenses for userId={}", userId);
        List<ExpenseResponse> expenses = expenseService.listExpensesByUser(userId);
        return ResponseEntity.ok(expenses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID id) {
        log.info("Deleting expense with id={}", id);
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
