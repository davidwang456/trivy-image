import { apiBase } from "@/api/apiBase"

export type RegistryDatasource = {
  id: number
  name: string
  type?: "harbor" | "swr"
  harborBaseUrl: string
  username: string
  createTime: string
  updateTime: string
}

export type RegistryDatasourceRequest = {
  name: string
  type?: "harbor" | "swr"
  harborBaseUrl?: string
  username?: string
  password?: string
  ak?: string
  sk?: string
}

export type RegistrySchedule = {
  id: number
  datasourceId: number
  datasourceName: string
  repoName?: string
  imageRef?: string
  cronExpression: string
  enabled: boolean
  lastRunAt?: string
  createTime: string
  updateTime: string
}

const withCredentials: RequestInit = { credentials: "include" }

async function readErrorMessage(
  res: Response,
  fallback: string,
): Promise<string> {
  const text = await res.text()
  try {
    const j = JSON.parse(text) as { message?: string; error?: string }
    return j.message ?? j.error ?? text ?? fallback
  } catch {
    return text || fallback
  }
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, { ...withCredentials, ...init })
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
  }
  return res.json() as Promise<T>
}

async function requestVoid(url: string, init?: RequestInit): Promise<void> {
  const res = await fetch(url, { ...withCredentials, ...init })
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
  }
}

export function listRegistryDatasources() {
  return requestJson<RegistryDatasource[]>(
    `${apiBase()}/api/registry-datasources`,
  )
}

export function createRegistryDatasource(body: RegistryDatasourceRequest) {
  return requestJson<RegistryDatasource>(
    `${apiBase()}/api/registry-datasources`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  )
}

export function updateRegistryDatasource(
  id: number,
  body: RegistryDatasourceRequest,
) {
  return requestJson<RegistryDatasource>(
    `${apiBase()}/api/registry-datasources/${id}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  )
}

export function deleteRegistryDatasource(id: number) {
  return requestVoid(`${apiBase()}/api/registry-datasources/${id}`, {
    method: "DELETE",
  })
}

export function listHarborRepos(datasourceId: number) {
  return requestJson<string[]>(
    `${apiBase()}/api/registry-datasources/${datasourceId}/repos`,
  )
}

export function listHarborImages(datasourceId: number, repoName: string) {
  const q = new URLSearchParams({ repoName }).toString()
  return requestJson<string[]>(
    `${apiBase()}/api/registry-datasources/${datasourceId}/images?${q}`,
  )
}

export function listSwrNamespaces(datasourceId: number) {
  return requestJson<string[]>(
    `${apiBase()}/api/swr/datasources/${datasourceId}/namespaces`,
  )
}

export function listSwrRepos(datasourceId: number, namespace?: string) {
  const q = namespace ? `?${new URLSearchParams({ namespace }).toString()}` : ""
  return requestJson<string[]>(
    `${apiBase()}/api/swr/datasources/${datasourceId}/repos${q}`,
  )
}

export function listSwrImages(
  datasourceId: number,
  namespace: string,
  repoName: string,
) {
  const q = new URLSearchParams({ namespace, repoName }).toString()
  return requestJson<string[]>(
    `${apiBase()}/api/swr/datasources/${datasourceId}/images?${q}`,
  )
}

export function listRegistrySchedules() {
  return requestJson<RegistrySchedule[]>(`${apiBase()}/api/registry-schedules`)
}

export function createRegistrySchedule(body: {
  datasourceId: number
  repoName?: string
  imageRef?: string
  cronExpression: string
  enabled: boolean
}) {
  return requestJson<RegistrySchedule>(`${apiBase()}/api/registry-schedules`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  })
}

export function deleteRegistrySchedule(id: number) {
  return requestVoid(`${apiBase()}/api/registry-schedules/${id}`, {
    method: "DELETE",
  })
}
