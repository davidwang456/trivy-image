<template>
  <v-container class="py-12" style="max-width: 520px">
    <v-card rounded="xl" elevation="2">
      <v-card-title class="text-h5">用户登录</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="username"
          label="用户名"
          prepend-inner-icon="mdi-account"
          variant="solo-filled"
          flat
          class="mb-3"
          autocomplete="username"
        />
        <v-text-field
          v-model="password"
          label="密码"
          prepend-inner-icon="mdi-lock"
          type="password"
          variant="solo-filled"
          flat
          autocomplete="current-password"
          @keyup.enter="submit"
        />
        <v-alert
          v-if="error"
          class="mt-3"
          color="error"
          variant="outlined"
          density="compact"
        >
          {{ error }}
        </v-alert>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn color="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
          登录
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue"
import { useRouter, useRoute } from "vue-router"
import { login } from "@/stores/auth"

const router = useRouter()
const route = useRoute()

const username = ref("")
const password = ref("")
const loading = ref(false)
const error = ref<string>()

const canSubmit = computed(() => username.value.trim() && password.value)

async function submit() {
  if (!canSubmit.value) return
  error.value = undefined
  loading.value = true
  try {
    await login(username.value.trim(), password.value)
    const redirectTo =
      typeof route.query.redirect === "string" && route.query.redirect
        ? route.query.redirect
        : "/"
    await router.replace(redirectTo)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}
</script>
