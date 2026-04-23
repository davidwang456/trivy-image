import ChangePasswordPage from "@/pages/ChangePasswordPage.vue"
import DashboardPage from "@/pages/DashboardPage.vue"
import HomePage from "@/pages/HomePage.vue"
import LoginPage from "@/pages/LoginPage.vue"
import RegistryDatasourcePage from "@/pages/RegistryDatasourcePage.vue"
import ScanPage from "@/pages/ScanPage.vue"
import UserManagementPage from "@/pages/UserManagementPage.vue"
/**
 * router/index.ts
 *
 * Automatic routes for `./src/pages/*.vue`
 */

// Composables
import { createRouter, createWebHashHistory } from "vue-router"
import {
  hasAuthLoaded,
  isAdmin,
  isAuthenticated,
  loadAuthUser,
  mustChangePassword,
} from "@/stores/auth"

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomePage,
      meta: { i18nKey: "home", requiresAuth: true },
    },
    {
      path: "/scan",
      name: "scan",
      component: ScanPage,
      meta: { i18nKey: "scan", requiresAuth: true },
    },
    {
      path: "/dashboard",
      name: "dashboard",
      component: DashboardPage,
      meta: { i18nKey: "dashboard", requiresAuth: true },
    },
    {
      path: "/login",
      name: "login",
      component: LoginPage,
      meta: { i18nKey: "login" },
    },
    {
      path: "/users",
      name: "users",
      component: UserManagementPage,
      meta: { i18nKey: "users", requiresAuth: true, adminOnly: true },
    },
    {
      path: "/registry-datasources",
      name: "registry-datasources",
      component: RegistryDatasourcePage,
      meta: {
        i18nKey: "registry-datasources",
        requiresAuth: true,
        adminOnly: true,
      },
    },
    {
      path: "/change-password",
      name: "change-password",
      component: ChangePasswordPage,
      meta: { i18nKey: "change-password", requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  if (!hasAuthLoaded()) {
    await loadAuthUser()
  }

  if (to.meta.requiresAuth && !isAuthenticated.value) {
    return { name: "login", query: { redirect: to.fullPath } }
  }

  if (to.name === "login" && isAuthenticated.value) {
    return { name: "home" }
  }

  if (
    isAuthenticated.value &&
    mustChangePassword.value &&
    to.name !== "change-password" &&
    to.name !== "login"
  ) {
    return { name: "change-password" }
  }

  if (to.meta.adminOnly && !isAdmin.value) {
    return { name: "home" }
  }

  return true
})

// Workaround for https://github.com/vitejs/vite/issues/11804
router.onError((err, to) => {
  if (err?.message?.includes?.("Failed to fetch dynamically imported module")) {
    if (!localStorage.getItem("vuetify:dynamic-reload")) {
      console.log("Reloading page to fix dynamic import error")
      localStorage.setItem("vuetify:dynamic-reload", "true")
      location.assign(to.fullPath)
    } else {
      console.error("Dynamic import error, reloading page did not fix it", err)
    }
  } else {
    console.error(err)
  }
})

router.isReady().then(() => {
  localStorage.removeItem("vuetify:dynamic-reload")
})

export default router
