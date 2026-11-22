import createClient from "openapi-fetch";
import { authFetch } from "@/lib/auth";
import type { components, paths } from "./schema";

/**
 * Typed API client generated from the backend OpenAPI description.
 * Regenerate the types after changing an API:  npm run api:generate  (backend must be running).
 *
 * The API is always called on the same origin under /api:
 * - Docker Compose: Nginx sends /api to the backend.
 * - npm run dev: next.config.ts forwards /api to BACKEND_URL (default http://localhost:8081).
 * Each call carries the login token and the branch picked in the top bar (see lib/auth.ts).
 */
export const api = createClient<paths>({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "",
  fetch: authFetch,
});

/** The one error format every API returns (see server: common/error/ApiError.java). */
export type ApiError = components["schemas"]["ApiError"];

export type HealthResponse = components["schemas"]["HealthResponse"];

/** The error code of a failed call, e.g. "BAD_LOGIN" (see the backend ApiError). */
export function errorCode(error: unknown): string | null {
  return error && typeof error === "object" && "code" in error && typeof error.code === "string" ? error.code : null;
}

/** Field → message from a failed call, to mark the boxes red. */
export function fieldErrors(error: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (error && typeof error === "object" && "fieldErrors" in error && Array.isArray(error.fieldErrors)) {
    for (const f of error.fieldErrors as { field: string; message: string }[]) out[f.field] = f.message;
  }
  return out;
}

/** Short message for a failed call: the backend message, or a network message. */
export function errorMessage(error: unknown): string {
  if (error && typeof error === "object" && "message" in error && typeof error.message === "string") {
    return error.message;
  }
  return "Cannot reach the server. Check the connection and try again.";
}
