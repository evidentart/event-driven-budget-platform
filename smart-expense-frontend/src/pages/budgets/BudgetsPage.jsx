import React, { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Box, Button, Paper, Stack, Typography } from "@mui/material";

import { useAuthUser } from "../../auth/useAuthUser";
import { toErrorMessage } from "../../api/errorMessage";
import { createBudget, deleteBudget, getCurrentBudget, listBudgets, setCurrentBudget } from "../../api/budgets.api";
import BudgetDialog from "./BudgetDialog";

export default function BudgetsPage() {
  const qc = useQueryClient();
  const { me, isLoading: meLoading, isError: meIsError, error: meError } = useAuthUser();
  const [createOpen, setCreateOpen] = useState(false);
  const [updateOpen, setUpdateOpen] = useState(false);
  const [actionError, setActionError] = useState("");

  const enabled = useMemo(() => Boolean(me), [me]);

  const currentQ = useQuery({
    queryKey: ["budget.current", "me"],
    queryFn: getCurrentBudget,
    enabled,
    retry: false,
  });

  const allQ = useQuery({
    queryKey: ["budgets", "me"],
    queryFn: listBudgets,
    enabled,
  });

  const createM = useMutation({
    mutationFn: createBudget,
    onMutate: () => {
      setActionError("");
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["budgets", "me"] });
      await qc.invalidateQueries({ queryKey: ["budget.current", "me"] });
      setCreateOpen(false);
    },
    onError: (error) => {
      setActionError(toErrorMessage(error, "Could not create budget."));
    },
  });

  const setCurrentM = useMutation({
    mutationFn: ({ monthlyBudget }) => setCurrentBudget(monthlyBudget),
    onMutate: () => {
      setActionError("");
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["budget.current", "me"] });
      await qc.invalidateQueries({ queryKey: ["budgets", "me"] });
      setUpdateOpen(false);
    },
    onError: (error) => {
      setActionError(toErrorMessage(error, "Could not update current budget."));
    },
  });

  const deleteM = useMutation({
    mutationFn: (budgetId) => deleteBudget(budgetId),
    onMutate: () => {
      setActionError("");
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["budgets", "me"] });
      await qc.invalidateQueries({ queryKey: ["budget.current", "me"] });
    },
    onError: (error) => {
      setActionError(toErrorMessage(error, "Could not delete budget."));
    },
  });

  if (meLoading) return <Typography>Loading profile...</Typography>;
  if (meIsError) {
    return <Alert severity="error">{toErrorMessage(meError, "Could not load user profile.")}</Alert>;
  }
  if (!me) return <Typography>Unable to load authenticated profile.</Typography>;

  const currentBudget = currentQ.data;

  return (
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Budgets
        </Typography>
        <Button variant="contained" onClick={() => setCreateOpen(true)}>
          Create Budget
        </Button>
      </Stack>

      {actionError ? <Alert severity="error">{actionError}</Alert> : null}

      <Paper sx={{ p: 2, borderRadius: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
          Current Period Budget
        </Typography>

        {currentQ.isError ? (
          <Alert severity="info">No current budget found. Create one for this month.</Alert>
        ) : null}
        {currentQ.isLoading ? <Typography>Loading...</Typography> : null}

        {currentBudget ? (
          <Stack spacing={1}>
            <Typography>Period: {currentBudget.period}</Typography>
            <Typography>Monthly Budget: ${currentBudget.monthlyBudget}</Typography>
            <Typography>Spent: ${currentBudget.spent}</Typography>
            <Typography>Remaining: ${currentBudget.remaining}</Typography>
            <Typography>
              Status: {currentBudget.status} ({currentBudget.percentageUsed?.toFixed(1)}%)
            </Typography>

            <Button variant="outlined" onClick={() => setUpdateOpen(true)}>
              Update Current Budget
            </Button>
          </Stack>
        ) : null}
      </Paper>

      <Paper sx={{ p: 2, borderRadius: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
          All Budgets
        </Typography>

        {allQ.isError ? (
          <Alert severity="error">{toErrorMessage(allQ.error, "Could not load budgets.")}</Alert>
        ) : null}
        {allQ.isLoading ? <Typography>Loading...</Typography> : null}
        {allQ.data?.length === 0 ? <Typography>No budgets yet.</Typography> : null}

        <Stack spacing={1.25}>
          {allQ.data?.map((budget) => (
            <Box
              key={budget.id}
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
                <Typography sx={{ fontWeight: 800 }}>{budget.period}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Budget ${budget.monthlyBudget} | Spent ${budget.spent} | Remaining ${budget.remaining} | {budget.status}
                </Typography>
              </Box>
              <Button color="error" onClick={() => deleteM.mutate(budget.id)} disabled={deleteM.isPending}>
                Delete
              </Button>
            </Box>
          ))}
        </Stack>
      </Paper>

      <BudgetDialog
        key={createOpen ? "create-open" : "create-closed"}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        isSubmitting={createM.isPending}
        title="Create Budget"
        submitLabel="Create"
        onSubmit={(values) => {
          const payload = {
            monthlyBudget: values.monthlyBudget,
          };

          if (typeof values.period === "string" && values.period.trim()) {
            payload.period = values.period.trim();
          }

          createM.mutate(payload);
        }}
      />

      <BudgetDialog
        key={updateOpen
          ? `update-open-${currentBudget?.id ?? "none"}-${currentBudget?.monthlyBudget ?? ""}`
          : "update-closed"}
        open={updateOpen}
        onClose={() => setUpdateOpen(false)}
        isSubmitting={setCurrentM.isPending}
        title="Update Current Budget"
        submitLabel="Update"
        showPeriod={false}
        initialValues={{
          monthlyBudget: currentBudget?.monthlyBudget ?? "",
        }}
        onSubmit={(values) => {
          setCurrentM.mutate({ monthlyBudget: values.monthlyBudget });
        }}
      />
    </Stack>
  );
}
