package com.sea.expenseservice.mapper;

import com.sea.expenseservice.model.Expense;
import com.sea.expenseservice.model.ExpenseType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpenseMapperTest {

    @Test
    void preservesDecimalStringsAndMapsBudgetAdvisoryCents() {
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setTitle("Groceries");
        expense.setAmount(new BigDecimal("12.34"));
        expense.setCategory(ExpenseType.FOOD);
        expense.setExpenseDate(Instant.parse("2026-02-10T12:00:00Z"));

        var response = new ExpenseMapper().toResponse(
                expense, "NEAR_LIMIT", "Budget warning", 1234L);

        assertEquals("12.34", response.getAmount());
        assertEquals("12.34", response.getRemainingBudgetAfter());
        assertEquals("NEAR_LIMIT", response.getBudgetStatus());
        assertEquals(expense.getExpenseDate(), response.getExpenseDate());
    }
}
