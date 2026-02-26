import React, { useEffect, useMemo, useState } from "react";
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from "@mui/material";

function currentMonthValue() {
  return new Date().toISOString().slice(0, 7);
}

function normalizeInitialValues(initialValues) {
  return {
    monthlyBudget: initialValues?.monthlyBudget ?? "",
    period: initialValues?.period ?? currentMonthValue(),
  };
}

export default function BudgetDialog({
  open,
  onClose,
  onSubmit,
  isSubmitting,
  title = "Create Budget",
  submitLabel = "Save",
  initialValues,
  showPeriod = true,
}) {
  const defaultValues = useMemo(() => normalizeInitialValues(initialValues), [initialValues]);
  const [values, setValues] = useState(defaultValues);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setValues(defaultValues);
    setError("");
  }, [defaultValues, open]);

  const submit = () => {
    const monthlyBudget = Number(values.monthlyBudget);

    if (!Number.isFinite(monthlyBudget) || monthlyBudget <= 0) {
      setError("Monthly budget must be greater than 0.");
      return;
    }

    if (showPeriod && values.period && !/^\d{4}-(0[1-9]|1[0-2])$/.test(values.period)) {
      setError("Period must be in format YYYY-MM.");
      return;
    }

    setError("");
    onSubmit({
      monthlyBudget,
      period: showPeriod ? values.period : undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            required
            label="Monthly Budget"
            type="number"
            inputProps={{ min: 0.01, step: "0.01" }}
            value={values.monthlyBudget}
            onChange={(e) => setValues((v) => ({ ...v, monthlyBudget: e.target.value }))}
          />

          {showPeriod ? (
            <TextField
              label="Period"
              type="month"
              value={values.period}
              onChange={(e) => setValues((v) => ({ ...v, period: e.target.value }))}
              helperText="Optional. Defaults to current month if omitted."
              InputLabelProps={{ shrink: true }}
            />
          ) : null}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={isSubmitting}>
          {submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
