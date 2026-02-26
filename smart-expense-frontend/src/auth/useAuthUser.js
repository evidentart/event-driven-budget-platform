import { useQuery } from "@tanstack/react-query";
import { getMyProfile } from "../api/users.api";

// Calls /api/users/profile which triggers your get-or-create on backend
export function useAuthUser() {
  const meQuery = useQuery({
    queryKey: ["me"],
    queryFn: getMyProfile,
  });

  return {
    me: meQuery.data,
    dbUserId: meQuery.data?.id, // UUID from DB
    isLoading: meQuery.isLoading,
    isError: meQuery.isError,
    error: meQuery.error,
  };
}
