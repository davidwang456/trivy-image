<template>
  <v-app>
    <v-navigation-drawer
      v-model="drawer"
      :temporary="isMobile"
      :permanent="!isMobile"
      width="260"
      color="#1f2533"
      theme="dark"
    >
      <div class="drawer-header">
        <img class="icon" src="/logo_dark_bg.svg" alt="" />
        <span class="title">Trivy Explorer</span>
      </div>

      <v-divider />

      <v-list nav density="comfortable">
        <v-list-item
          v-if="isAuthenticated"
          :active="route.name === 'home'"
          prepend-icon="mdi-home"
          title="Local report"
          @click="goTo('home')"
        />
        <v-list-item
          v-if="isAuthenticated"
          :active="route.name === 'scan'"
          prepend-icon="mdi-radar"
          title="Registry scan"
          @click="goTo('scan')"
        />
        <v-list-item
          v-if="isAuthenticated"
          :active="route.name === 'dashboard'"
          prepend-icon="mdi-view-dashboard"
          title="Dashboard"
          @click="goTo('dashboard')"
        />
        <v-list-item
          v-if="isAuthenticated && isAdmin"
          :active="route.name === 'registry-datasources'"
          prepend-icon="mdi-database-cog"
          title="Registry datasource"
          @click="goTo('registry-datasources')"
        />
        <v-list-item
          v-if="isAuthenticated && isAdmin"
          :active="route.name === 'users'"
          prepend-icon="mdi-account-group"
          title="User management"
          @click="goTo('users')"
        />
        <v-list-item
          v-if="isAuthenticated"
          :active="route.name === 'change-password'"
          prepend-icon="mdi-lock-reset"
          title="Change password"
          @click="goTo('change-password')"
        />
        <v-list-item
          v-if="!isAuthenticated"
          :active="route.name === 'login'"
          prepend-icon="mdi-login"
          title="Login"
          @click="goTo('login')"
        />
      </v-list>

      <template #append>
        <div class="pa-3">
          <v-btn
            v-if="isAuthenticated"
            block
            color="primary"
            prepend-icon="mdi-logout"
            @click="doLogout"
          >
            Logout ({{ authUser?.username }})
          </v-btn>
        </div>
      </template>
    </v-navigation-drawer>

    <v-app-bar color="primary" dark>
      <v-app-bar-nav-icon v-if="isMobile" @click="drawer = !drawer" />
      <v-app-bar-title>Trivy Vulnerability Explorer</v-app-bar-title>
    </v-app-bar>

    <v-main>
      <router-view />
    </v-main>
  </v-app>
</template>

<script lang="ts" setup>
import { ref } from "vue"
import { useDisplay } from "vuetify"
import { useRoute, useRouter } from "vue-router"
import { authUser, isAdmin, isAuthenticated, logout } from "@/stores/auth"

const router = useRouter()
const route = useRoute()
const { mdAndDown } = useDisplay()
const isMobile = mdAndDown
const drawer = ref(!isMobile.value)

function goTo(name: string) {
  if (route.name !== name) {
    void router.push({ name })
  }
  if (isMobile.value) {
    drawer.value = false
  }
}

async function doLogout() {
  await logout()
  await router.replace({ name: "login" })
  if (isMobile.value) {
    drawer.value = false
  }
}
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  padding: 16px 14px;
  gap: 10px;
}

.drawer-header .title {
  font-size: 20px;
  font-weight: 600;
}

img.icon {
  width: 28px;
  height: 28px;
}
</style>
