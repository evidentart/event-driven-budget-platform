package com.sea.expenseservice.mapper;

import com.sea.expenseservice.dto.ExpenseRequest;
import com.sea.expenseservice.dto.ExpenseResponse;
import com.sea.expenseservice.model.Expense;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ExpenseMapper {

    public Expense toEntity(String ownerSubject, ExpenseRequest request) {
        Expense expense = new Expense();
        expense.setOwnerSubject(ownerSubject);
        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        return expense;
    }

    public ExpenseResponse toResponse(Expense expense) {
        return toResponse(expense, null, null, null);
    }

    public ExpenseResponse toResponse(
            Expense expense, String budgetStatus, String budgetWarning, Long remainingCentsAfter) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount() == null ? null : expense.getAmount().toPlainString())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .budgetStatus(budgetStatus)
                .budgetWarning(budgetWarning)
                .remainingBudgetAfter(remainingCentsAfter == null
                        ? null : BigDecimal.valueOf(remainingCentsAfter, 2).toPlainString())
                .build();
    }
}
