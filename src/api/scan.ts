import type { Version1OrVersion2 } from "@/types"

export type ScanRequestBody = {
  datasourceId: number
  imageRef: string
}

export type ScanResponseBody = {
  id: number
  imageRef: string
  jobId: string
  jobName: string
  systemName: string
  projectName: string
  jobType: "manual" | "cron"
  createBy?: string
  updateBy?: string
  report: Version1OrVersion2
  createTime: string
  updateTime: string
}

export type ScanSummary = {
  id: number
  imageRef: string
  jobId: string
  jobName: string
  systemName: string
  projectName: string
  jobType: "manual" | "cron"
  createBy?: string
  updateBy?: string
  createTime: string
  updateTime: string
}

export type ScanDashboardImage = {
  id: number
  imageRef: string
  createTime: string
  updateTime: string
  jobId: string
  jobName: string
}

export type ScanDashboardProject = {
  projectName: string
  images: ScanDashboardImage[]
}

export type ScanDashboardSystem = {
  systemName: string
  projects: ScanDashboardProject[]
}

export type ScanDashboardJob = {
  jobId: string
  jobName: string
  systems: ScanDashboardSystem[]
  createTime: string
  updateTime: string
}

export type ScanStats = {
  jobTotal: number
  imageRefTotal: number
}

/** One day in the last-7-days trend (by create_time date). */
export type ScanDailyStat = {
  date: string
  jobTotal: number
  imageRefTotal: number
}

export type ImportScanRequestBody = {
  imageRef?: string
  report: Version1OrVersion2
}

function apiBase(): string {
  const base = import.meta.env.VITE_API_BASE as string | undefined
  if (base && base.trim()) {
    return base.trim()
  }

  // In local development, calling backend directly avoids "Failed to fetch"
  // when the frontend is not served with a proxy.
  if (typeof window !== "undefined") {
    const protocol = window.location.protocol === "https:" ? "https:" : "http:"
    if (window.location.port !== "8081") {
      return `${protocol}//${window.location.hostname}:8081`
    }
  }

  return ""
}

function withApiBaseHint(error: unknown): Error {
  if (error instanceof TypeError) {
    return new Error(
      "Network error: cannot reach backend API. Please ensure backend is running on http://localhost:8081 or set VITE_API_BASE.",
    )
  }
  return error instanceof Error ? error : new Error(String(error))
}

const withCredentials: RequestInit = { credentials: "include" }

async function readErrorMessage(
  res: Response,
  fallback: string,
): Promise<string> {
  const text = await res.text()
  let message = text || res.statusText
  try {
    const j = JSON.parse(text) as { message?: string; error?: string }
    message = j.message ?? j.error ?? message
  } catch {
    /* keep text */
  }
  return message || fallback
}

export async function scanImage(
  body: ScanRequestBody,
): Promise<ScanResponseBody> {
  try {
    const url = `${apiBase()}/api/scans`
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanResponseBody>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function batchScanImages(body: {
  datasourceId: number
  imageRefs: string[]
}): Promise<ScanResponseBody[]> {
  try {
    const url = `${apiBase()}/api/scans/batch`
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanResponseBody[]>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function listScans(q?: string): Promise<ScanSummary[]> {
  try {
    const params = new URLSearchParams()
    if (q?.trim()) {
      params.set("q", q.trim())
    }
    const qs = params.toString()
    const url = `${apiBase()}/api/scans${qs ? `?${qs}` : ""}`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanSummary[]>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function getScanById(id: number): Promise<ScanResponseBody> {
  try {
    const url = `${apiBase()}/api/scans/${id}`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanResponseBody>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function getScanStats(): Promise<ScanStats> {
  try {
    const url = `${apiBase()}/api/scans/stats`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanStats>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function getScanDailyTrend(): Promise<ScanDailyStat[]> {
  try {
    const url = `${apiBase()}/api/scans/stats/daily`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanDailyStat[]>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function getScanDashboard(): Promise<ScanDashboardJob[]> {
  try {
    const url = `${apiBase()}/api/scans/dashboard`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanDashboardJob[]>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function importScanReport(
  body: ImportScanRequestBody,
): Promise<ScanResponseBody> {
  try {
    const url = `${apiBase()}/api/scans/import`
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<ScanResponseBody>
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}

export async function downloadTargetPdf(scanId: number): Promise<Blob> {
  try {
    const url = `${apiBase()}/api/scans/${scanId}/export/pdf`
    const res = await fetch(url, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return await res.blob()
  } catch (error: unknown) {
    throw withApiBaseHint(error)
  }
}
