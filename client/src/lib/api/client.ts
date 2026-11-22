import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

/**
 * Typed API client generated from the backend OpenAPI description.
 * Regenerate the types after changing an API:  npm run api:generate  (backend must be running).
 *
 * The API is always called on the same origin under /api:
 * - Docker Compose: Nginx sends /api to the backend.
 * - npm run dev: next.config.ts forwards /api to BACKEND_URL (default http://localhost:8081).
 */
export const api = createClient<paths>({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "",
});

/** The one error format every API returns (see server: common/error/ApiError.java). */
export type ApiError = components["schemas"]["ApiError"];

export type HealthResponse = components["schemas"]["HealthResponse"];

/** Short message for a failed call: the backend message, or a network message. */
export function errorMessage(error: unknown): string {
  if (error && typeof error === "object" && "message" in error && typeof error.message === "string") {
    return error.message;
  }
  return "Cannot reach the server. Check the connection and try again.";
}
