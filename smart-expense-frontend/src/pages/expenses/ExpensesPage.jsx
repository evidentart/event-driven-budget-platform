import React, { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Box, Button, Paper, Stack, Typography } from "@mui/material";

import { useAuthUser } from "../../auth/useAuthUser";
import { toErrorMessage } from "../../api/errorMessage";
import { createExpense, deleteExpense, listMyExpenses } from "../../api/expenses.api";
import ExpenseDialog from "./ExpenseDialog";

export default function ExpensesPage() {
  const qc = useQueryClient();
  const { me, isLoading: meLoading, isError: meIsError, error: meError } = useAuthUser();
  const [open, setOpen] = useState(false);
  const [createError, setCreateError] = useState("");
  const [deleteError, setDeleteError] = useState("");

  const enabled = useMemo(() => Boolean(me), [me]);

  const expensesQ = useQuery({
    queryKey: ["expenses", "me"],
    queryFn: listMyExpenses,
    enabled,
  });

  const createM = useMutation({
    mutationFn: createExpense,
    onMutate: () => {
      setCreateError("");
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["expenses", "me"] });
      setOpen(false);
    },
    onError: (error) => {
      setCreateError(toErrorMessage(error, "Could not create expense."));
    },
  });

  const deleteM = useMutation({
    mutationFn: (id) => deleteExpense(id),
    onMutate: () => {
      setDeleteError("");
    },
    onSuccess: async () => qc.invalidateQueries({ queryKey: ["expenses", "me"] }),
    onError: (error) => {
      setDeleteError(toErrorMessage(error, "Could not delete expense."));
    },
  });

  if (meLoading) return <Typography>Loading profile...</Typography>;
  if (meIsError) {
    return <Alert severity="error">{toErrorMessage(meError, "Could not load user profile.")}</Alert>;
  }
  if (!me) return <Typography>Unable to load authenticated profile.</Typography>;

  return (
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Expenses
        </Typography>
        <Button variant="contained" onClick={() => setOpen(true)}>
          Add Expense
        </Button>
      </Stack>

      {createError ? <Alert severity="error">{createError}</Alert> : null}
      {deleteError ? <Alert severity="error">{deleteError}</Alert> : null}

      <Paper sx={{ p: 2, borderRadius: 3 }}>
        {expensesQ.isError ? (
          <Alert severity="error">{toErrorMessage(expensesQ.error, "Could not load expenses.")}</Alert>
        ) : null}
        {expensesQ.isLoading ? <Typography>Loading...</Typography> : null}
        {expensesQ.data?.length === 0 ? <Typography>No expenses yet.</Typography> : null}

        <Stack spacing={1.25}>
          {expensesQ.data?.map((expense) => (
            <Box
              key={expense.id}
              sx={{
                display: "flex",
                justifyContent: "space-between",
                gap: 2,
                p: 2,
                border: "1px solid",
                borderColor: "divider",
                borderRadius: 2,
              }}
            >
              <Box>
                <Typography sx={{ fontWeight: 800 }}>{expense.title}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {expense.category} | ${expense.amount} | {new Date(expense.expenseDate).toLocaleString()}
                </Typography>

                {expense.budgetWarning ? (
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    Warning: {expense.budgetWarning}
                  </Typography>
                ) : null}

                {expense.budgetStatus ? (
                  <Typography variant="caption" color="text.secondary">
                    Budget Status: {expense.budgetStatus}
                    {typeof expense.remainingBudgetCentsAfter === "number"
                      ? ` | Remaining cents after: ${expense.remainingBudgetCentsAfter}`
                      : ""}
                  </Typography>
                ) : null}
              </Box>

              <Button color="error" onClick={() => deleteM.mutate(expense.id)} disabled={deleteM.isPending}>
                Delete
              </Button>
            </Box>
          ))}
        </Stack>
      </Paper>

      <ExpenseDialog
        open={open}
        onClose={() => {
          setOpen(false);
          setCreateError("");
        }}
        isSubmitting={createM.isPending}
        onSubmit={(values) => createM.mutate(values)}
      />
    </Stack>
  );
}
