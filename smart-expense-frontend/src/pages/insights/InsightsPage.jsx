import React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Box, Button, Paper, Stack, Typography } from "@mui/material";

import { useAuthUser } from "../../auth/useAuthUser";
import { toErrorMessage } from "../../api/errorMessage";
import { deleteInsightByExpense, listInsights } from "../../api/insights.api";

export default function InsightsPage() {
  const qc = useQueryClient();
  const { dbUserId, isLoading: meLoading, isError: meIsError, error: meError } = useAuthUser();

  const insightsQ = useQuery({
    queryKey: ["insights", dbUserId],
    queryFn: () => listInsights(dbUserId),
    enabled: !!dbUserId,
  });

  const deleteM = useMutation({
    mutationFn: ({ userId, expenseId }) => deleteInsightByExpense(userId, expenseId),
    onSuccess: async () => qc.invalidateQueries({ queryKey: ["insights", dbUserId] }),
  });

  if (meLoading) return <Typography>Loading profile...</Typography>;
  if (meIsError) return <Alert severity="error">{toErrorMessage(meError, "Could not load user profile.")}</Alert>;
  if (!dbUserId) return <Typography>Unable to load user id from profile.</Typography>;

  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 900 }}>
        AI Insights
      </Typography>

      <Paper sx={{ p: 2, borderRadius: 3 }}>
        {insightsQ.isError ? (
          <Alert severity="error">{toErrorMessage(insightsQ.error, "Could not load insights.")}</Alert>
        ) : null}
        {insightsQ.isLoading ? <Typography>Loading...</Typography> : null}
        {insightsQ.data?.length === 0 ? (
          <Typography color="text.secondary">
            No insights yet. Create an expense and AI insights will be generated for that user.
          </Typography>
        ) : null}

        <Stack spacing={1.5}>
          {insightsQ.data?.map((insight) => (
            <Paper key={insight.id} sx={{ p: 2, borderRadius: 3 }}>
              <Stack spacing={1}>
                <Typography sx={{ fontWeight: 900 }}>
                  {insight.category} | Severity: {insight.severity}
                </Typography>

                <Typography>{insight.budgetSummary}</Typography>

                {insight.budgetWarnings?.length ? (
                  <Box>
                    <Typography sx={{ fontWeight: 800, mt: 1 }}>Warnings</Typography>
                    {insight.budgetWarnings.map((warning, index) => (
                      <Typography key={index} variant="body2">
                        - {warning}
                      </Typography>
                    ))}
                  </Box>
                ) : null}

                <Box>
                  <Typography sx={{ fontWeight: 800, mt: 1 }}>Spending Improvements</Typography>
                  {insight.spendingImprovements?.map((item, index) => (
                    <Typography key={index} variant="body2">
                      - {item}
                    </Typography>
                  ))}
                </Box>

                <Box>
                  <Typography sx={{ fontWeight: 800, mt: 1 }}>Saving Suggestions</Typography>
                  {insight.savingSuggestions?.map((item, index) => (
                    <Typography key={index} variant="body2">
                      - {item}
                    </Typography>
                  ))}
                </Box>

                <Typography variant="caption" color="text.secondary">
                  Created: {new Date(insight.createdAt).toLocaleString()} | Expense: {insight.expenseId}
                </Typography>

                <Box>
                  <Button
                    color="error"
                    onClick={() => deleteM.mutate({ userId: dbUserId, expenseId: insight.expenseId })}
                  >
                    Delete Insight
                  </Button>
                </Box>
              </Stack>
            </Paper>
          ))}
        </Stack>
      </Paper>
    </Stack>
  );
}
