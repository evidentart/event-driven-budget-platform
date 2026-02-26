import React from "react";
import { Paper, Stack, Typography } from "@mui/material";

export default function EmptyState({ title = "Nothing to show", description = "", action = null }) {
  return (
    <Paper sx={{ p: 3, borderRadius: 3 }}>
      <Stack spacing={1}>
        <Typography variant="h6" sx={{ fontWeight: 800 }}>
          {title}
        </Typography>
        {description ? <Typography color="text.secondary">{description}</Typography> : null}
        {action}
      </Stack>
    </Paper>
  );
}
