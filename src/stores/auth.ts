import { computed, ref } from "vue"
import { login as apiLogin, logout as apiLogout, me, type UserInfo } from "@/api/auth"

const currentUser = ref<UserInfo | null>(null)
const initialized = ref(false)

export const authUser = computed(() => currentUser.value)
export const isAuthenticated = computed(() => !!currentUser.value)
export const isAdmin = computed(() => currentUser.value?.role === "admin")
export const mustChangePassword = computed(
  () => !!currentUser.value?.mustChangePassword,
)

export async function loadAuthUser() {
  try {
    const user = await me()
    currentUser.value = user
  } catch {
    currentUser.value = null
  } finally {
    initialized.value = true
  }
}

export async function login(username: string, password: string) {
  const user = await apiLogin({ username, password })
  currentUser.value = user
  initialized.value = true
  return user
}

export function markPasswordChanged() {
  if (currentUser.value) {
    currentUser.value = { ...currentUser.value, mustChangePassword: false }
  }
}

export async function logout() {
  try {
    await apiLogout()
  } finally {
    currentUser.value = null
    initialized.value = true
  }
}

export function hasAuthLoaded() {
  return initialized.value
}
