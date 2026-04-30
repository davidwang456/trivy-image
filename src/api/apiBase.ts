/**
 * Same as `vite.config.ts` `base` (no trailing slash) and Spring `server.servlet.context-path`.
 * Used when the dev UI calls the API on port 9080 directly (not same-origin).
 */
const SERVLET_CONTEXT_PATH = "/trivy-vulnerability-explorer"

/**
 * Base URL for the backend API (no trailing slash).
 * - Production: same origin as the SPA unless VITE_API_BASE is set.
 * - Development: VITE_API_BASE, or direct http(s)://host:9080 + context path when the UI is not on 9080.
 */
export function apiBase(): string {
  const raw = import.meta.env.VITE_API_BASE as string | undefined
  if (raw != null && String(raw).trim() !== "") {
    return String(raw).trim()
  }
  if (import.meta.env.PROD) {
    return ""
  }
  if (typeof window !== "undefined") {
    const protocol = window.location.protocol === "https:" ? "https:" : "http:"
    if (window.location.port !== "9080") {
      return `${protocol}//${window.location.hostname}:9080${SERVLET_CONTEXT_PATH}`
    }
  }
  return ""
}
