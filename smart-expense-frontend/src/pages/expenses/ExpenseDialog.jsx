import React, { useEffect, useState } from "react";
import {
  Alert,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Button,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { EXPENSE_CATEGORIES } from "../../config/constants";
import { normalizePositiveMoney } from "../../utils/money";

function toInputDateTime(date = new Date()) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function toApiInstant(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

export default function ExpenseDialog({ open, onClose, onSubmit, isSubmitting }) {
  const initialValues = () => ({
    title: "",
    description: "",
    amount: "",
    category: "FOOD",
    expenseDate: toInputDateTime(),
  });

  const [values, setValues] = useState(initialValues);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setValues(initialValues());
    setError("");
  }, [open]);

  const submit = () => {
    const title = values.title.trim();
    const amount = normalizePositiveMoney(values.amount);
    const expenseDate = toApiInstant(values.expenseDate);

    if (!title) {
      setError("Title is required.");
      return;
    }

    if (!amount) {
      setError("Amount must be greater than 0.");
      return;
    }

    if (!values.category) {
      setError("Category is required.");
      return;
    }

    if (!expenseDate) {
      setError("Expense date is required.");
      return;
    }

    setError("");
    onSubmit({
      title,
      description: values.description.trim(),
      amount,
      category: values.category,
      expenseDate,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Add Expense</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}

          <TextField
            required
            label="Title"
            value={values.title}
            onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))}
          />
          <TextField
            label="Description"
            value={values.description}
            onChange={(e) => setValues((v) => ({ ...v, description: e.target.value }))}
          />
          <TextField
            required
            label="Amount"
            type="number"
            inputProps={{ min: 0.01, step: "0.01" }}
            value={values.amount}
            onChange={(e) => setValues((v) => ({ ...v, amount: e.target.value }))}
          />
          <TextField
            select
            required
            label="Category"
            value={values.category}
            onChange={(e) => setValues((v) => ({ ...v, category: e.target.value }))}
          >
            {EXPENSE_CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            required
            type="datetime-local"
            label="Expense Date"
            value={values.expenseDate}
            onChange={(e) => setValues((v) => ({ ...v, expenseDate: e.target.value }))}
            InputLabelProps={{ shrink: true }}
          />
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={isSubmitting}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
