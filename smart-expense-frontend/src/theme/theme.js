import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  shape: { borderRadius: 14 },
  typography: {
    fontFamily: ["Inter", "system-ui", "sans-serif"].join(","),
  },
  palette: {
    mode: "dark",
  },
});
