import React, { useEffect, useState } from "react";
import { Alert, Button, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { useAuthUser } from "../../auth/useAuthUser";
import { toErrorMessage } from "../../api/errorMessage";
import { updateMyProfile } from "../../api/users.api";

export default function ProfilePage() {
  const qc = useQueryClient();
  const { me, isLoading: meLoading, isError: meIsError, error: meError } = useAuthUser();
  const [form, setForm] = useState({ firstName: "", lastName: "", phoneNumber: "" });

  useEffect(() => {
    if (!me) return;
    setForm({
      firstName: me.firstName || "",
      lastName: me.lastName || "",
      phoneNumber: me.phoneNumber || "",
    });
  }, [me]);

  const saveM = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["me"] });
    },
  });

  if (meLoading) return <Typography>Loading profile...</Typography>;
  if (meIsError) return <Alert severity="error">{toErrorMessage(meError, "Could not load profile.")}</Alert>;

  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 900 }}>
        Profile
      </Typography>

      {saveM.isError ? (
        <Alert severity="error">{toErrorMessage(saveM.error, "Could not save profile.")}</Alert>
      ) : null}
      {saveM.isSuccess ? <Alert severity="success">Profile saved.</Alert> : null}

      <Paper sx={{ p: 2, borderRadius: 3, maxWidth: 720 }}>
        <Stack spacing={2}>
          <TextField label="Email" value={me?.email || ""} disabled />
          <TextField
            label="First Name"
            value={form.firstName}
            onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))}
          />
          <TextField
            label="Last Name"
            value={form.lastName}
            onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))}
          />
          <TextField
            label="Phone Number"
            value={form.phoneNumber}
            onChange={(e) => setForm((f) => ({ ...f, phoneNumber: e.target.value }))}
          />

          <Button variant="contained" onClick={() => saveM.mutate(form)} disabled={saveM.isPending}>
            Save
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
