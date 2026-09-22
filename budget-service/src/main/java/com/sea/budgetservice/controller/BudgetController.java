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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@Validated
@RequiredArgsConstructor
@Slf4j
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            JwtAuthenticationToken auth,
            @Valid @RequestBody BudgetRequest request) {

        String ownerSubject = auth.getToken().getSubject();
        log.info("Create budget ownerSubject={} monthlyBudget={} period={}",
                ownerSubject, request.getMonthlyBudget(), request.getPeriod());

        BudgetResponse response = budgetService.createBudget(ownerSubject, request);

        // Location: /api/budgets/{budgetId}
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{budgetId}")
                .buildAndExpand(response.getId()) // assumes BudgetResponse has getId()
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me/current")
    public ResponseEntity<BudgetResponse> getCurrentBudget(JwtAuthenticationToken auth) {
        BudgetResponse response = budgetService.getCurrentBudget(auth.getToken().getSubject());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/period/{period}")
    public ResponseEntity<BudgetResponse> getBudgetByPeriod(
            JwtAuthenticationToken auth,
            @PathVariable
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must be in format YYYY-MM")
            String period
    ) {
        BudgetResponse response = budgetService.getBudgetByOwnerAndPeriod(auth.getToken().getSubject(), period);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<BudgetResponse>> getAllBudgets(JwtAuthenticationToken auth) {
        List<BudgetResponse> response = budgetService.getAllBudgetsByOwner(auth.getToken().getSubject());
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(
            JwtAuthenticationToken auth,
            @PathVariable UUID budgetId) {
        log.info("Delete budget budgetId={}", budgetId);
        budgetService.deleteBudget(auth.getToken().getSubject(), budgetId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/current")
    public ResponseEntity<BudgetResponse> setCurrentBudget(
            JwtAuthenticationToken auth,
            @Valid @RequestBody UpdateBudgetRequest request
    ) {
        return ResponseEntity.ok(budgetService.setCurrentBudget(
                auth.getToken().getSubject(), request.getMonthlyBudget()));
    }

}
