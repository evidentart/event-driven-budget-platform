package com.sea.budgetservice.controller;

import com.sea.budgetservice.dto.BudgetRequest;
import com.sea.budgetservice.dto.BudgetResponse;
import com.sea.budgetservice.dto.UpdateBudgetRequest;
import com.sea.budgetservice.service.BudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Slf4j
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(@Valid @RequestBody BudgetRequest request) {
        log.info("Create budget userId={} monthlyBudget={} period={}",
                request.getUserId(), request.getMonthlyBudget(), request.getPeriod());

        BudgetResponse response = budgetService.createBudget(request);

        // Location: /api/budgets/{budgetId}
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{budgetId}")
                .buildAndExpand(response.getId()) // assumes BudgetResponse has getId()
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/user/{userId}/current")
    public ResponseEntity<BudgetResponse> getCurrentBudget(@PathVariable UUID userId) {
        BudgetResponse response = budgetService.getCurrentBudget(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/period/{period}")
    public ResponseEntity<BudgetResponse> getBudgetByPeriod(
            @PathVariable UUID userId,
            @PathVariable
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must be in format YYYY-MM")
            String period
    ) {
        BudgetResponse response = budgetService.getBudgetByUserAndPeriod(userId, period);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BudgetResponse>> getAllBudgetsByUser(@PathVariable UUID userId) {
        List<BudgetResponse> response = budgetService.getAllBudgetsByUser(userId);
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(@PathVariable UUID budgetId) {
        log.info("Delete budget budgetId={}", budgetId);
        budgetService.deleteBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/user/{userId}/current")
    public ResponseEntity<BudgetResponse> setCurrentBudget(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateBudgetRequest request
    ) {
        return ResponseEntity.ok(budgetService.setCurrentBudget(userId, request.getMonthlyBudget()));
    }

}

