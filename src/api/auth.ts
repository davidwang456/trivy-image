import { apiBase } from "@/api/apiBase"

export type LoginRequestBody = {
  username: string
  password: string
}

export type UserInfo = {
  id: number
  username: string
  role: "admin" | "user" | string
  createTime?: string | null
  updateTime?: string | null
  mustChangePassword?: boolean | null
}

export type CreateUserBody = {
  username: string
  password: string
  role: "admin" | "user"
}

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

function withApiBaseHint(error: unknown): Error {
  if (error instanceof TypeError) {
    return new Error(
      "Network error: cannot reach backend API. In local dev ensure the API is on port 9080 or set VITE_API_BASE.",
    )
  }
  return error instanceof Error ? error : new Error(String(error))
}

const withCredentials: RequestInit = { credentials: "include" }

export async function login(body: LoginRequestBody): Promise<UserInfo> {
  try {
    const res = await fetch(`${apiBase()}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<UserInfo>
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${apiBase()}/api/auth/logout`, {
      method: "POST",
      ...withCredentials,
    })
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function me(): Promise<UserInfo> {
  try {
    const res = await fetch(`${apiBase()}/api/auth/me`, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<UserInfo>
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function listUsers(): Promise<UserInfo[]> {
  try {
    const res = await fetch(`${apiBase()}/api/users`, withCredentials)
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<UserInfo[]>
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function createUser(body: CreateUserBody): Promise<UserInfo> {
  try {
    const res = await fetch(`${apiBase()}/api/users`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
    return res.json() as Promise<UserInfo>
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function changeOwnPassword(
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  try {
    const res = await fetch(`${apiBase()}/api/auth/change-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ oldPassword, newPassword }),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
  } catch (error) {
    throw withApiBaseHint(error)
  }
}

export async function adminResetPassword(
  userId: number,
  newPassword: string,
): Promise<void> {
  try {
    const res = await fetch(`${apiBase()}/api/users/${userId}/reset-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ newPassword }),
      ...withCredentials,
    })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res, `HTTP ${res.status}`))
    }
  } catch (error) {
    throw withApiBaseHint(error)
  }
}
