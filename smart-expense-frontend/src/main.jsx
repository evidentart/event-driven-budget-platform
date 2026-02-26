import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { CssBaseline, ThemeProvider } from "@mui/material";

import { router } from "./app/router";
import Providers from "./app/providers";
import { theme } from "./theme/theme";

import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Providers>
        <RouterProvider router={router} />
      </Providers>
    </ThemeProvider>
  </React.StrictMode>
);
