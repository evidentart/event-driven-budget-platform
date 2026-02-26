import React from "react";
import { Alert, Grid, Paper, Stack, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";

import { useAuthUser } from "../../auth/useAuthUser";
import { toErrorMessage } from "../../api/errorMessage";
import { getCurrentBudget } from "../../api/budgets.api";
import { listExpensesByUser } from "../../api/expenses.api";
import { getLatestInsight } from "../../api/insights.api";
import StatCard from "../../components/common/StatCard";

export default function DashboardPage() {
  const { me, dbUserId, isLoading: meLoading, isError: meIsError, error: meError } = useAuthUser();

  const budgetQ = useQuery({
    queryKey: ["budget.current", dbUserId],
    queryFn: () => getCurrentBudget(dbUserId),
    enabled: !!dbUserId,
    retry: false,
  });

  const expensesQ = useQuery({
    queryKey: ["expenses", dbUserId],
    queryFn: () => listExpensesByUser(dbUserId),
    enabled: !!dbUserId,
  });

  const latestInsightQ = useQuery({
    queryKey: ["insights.latest", dbUserId],
    queryFn: () => getLatestInsight(dbUserId),
    enabled: !!dbUserId,
    retry: false,
  });

  if (meLoading) return <Typography>Loading profile...</Typography>;
  if (meIsError) return <Alert severity="error">{toErrorMessage(meError, "Could not load user profile.")}</Alert>;
  if (!dbUserId) return <Typography>Unable to load user id from profile.</Typography>;

  const expenseCount = expensesQ.data?.length ?? 0;
  const currentBudget = budgetQ.data;

  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 900 }}>
        Welcome{me?.firstName ? `, ${me.firstName}` : ""}
      </Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <StatCard label="Expenses Logged" value={expenseCount} hint="All-time in this environment" />
        </Grid>
        <Grid item xs={12} md={4}>
          <StatCard
            label="Current Month Budget"
            value={currentBudget ? `$${currentBudget.monthlyBudget}` : "Not set"}
            hint={currentBudget ? `Status: ${currentBudget.status}` : "Create one in Budgets"}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <StatCard
            label="Remaining"
            value={currentBudget ? `$${currentBudget.remaining}` : "-"}
            hint={currentBudget ? `${currentBudget.percentageUsed?.toFixed(1)}% used` : ""}
          />
        </Grid>
      </Grid>

      <Paper sx={{ p: 2, borderRadius: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
          Latest AI Insight
        </Typography>
        {latestInsightQ.isError ? (
          <Typography color="text.secondary">No insights yet. Create an expense to generate one.</Typography>
        ) : latestInsightQ.isLoading ? (
          <Typography>Loading...</Typography>
        ) : (
          <Typography>{latestInsightQ.data?.budgetSummary}</Typography>
        )}
      </Paper>
    </Stack>
  );
}
