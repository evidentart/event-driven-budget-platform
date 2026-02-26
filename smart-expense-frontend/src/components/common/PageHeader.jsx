import React from "react";
import { Stack, Typography } from "@mui/material";

export default function PageHeader({ title, subtitle = "", action = null }) {
  return (
    <Stack
      direction={{ xs: "column", sm: "row" }}
      alignItems={{ xs: "flex-start", sm: "center" }}
      justifyContent="space-between"
      gap={1.5}
    >
      <Stack spacing={0.5}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          {title}
        </Typography>
        {subtitle ? <Typography color="text.secondary">{subtitle}</Typography> : null}
      </Stack>
      {action}
    </Stack>
  );
}
