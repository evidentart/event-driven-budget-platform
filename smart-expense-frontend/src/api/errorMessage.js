import axios from "axios";

export function toErrorMessage(error, fallback = "Request failed.") {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;

    if (typeof data === "string" && data.trim()) {
      return data;
    }

    if (data && typeof data.message === "string" && data.message.trim()) {
      return data.message;
    }

    if (data && typeof data.error === "string" && data.error.trim()) {
      return data.error;
    }

    if (typeof error.message === "string" && error.message.trim()) {
      return error.message;
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  return fallback;
}
