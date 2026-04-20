<template>
  <v-container class="py-12" style="max-width: 560px">
    <v-card rounded="xl" elevation="2">
      <v-card-title class="text-h6">修改密码</v-card-title>
      <v-card-subtitle>
        <span v-if="mustChangePassword">首次登录请先修改默认管理员密码。</span>
        <span v-else>请输入旧密码和新密码。</span>
      </v-card-subtitle>
      <v-card-text>
        <v-text-field
          v-model="oldPassword"
          label="旧密码"
          type="password"
          prepend-inner-icon="mdi-lock"
          variant="solo-filled"
          flat
          class="mb-3"
        />
        <v-text-field
          v-model="newPassword"
          label="新密码"
          type="password"
          prepend-inner-icon="mdi-lock-reset"
          variant="solo-filled"
          flat
          class="mb-3"
        />
        <v-text-field
          v-model="confirmPassword"
          label="确认新密码"
          type="password"
          prepend-inner-icon="mdi-lock-check"
          variant="solo-filled"
          flat
        />
        <v-alert v-if="error" class="mt-3" color="error" variant="outlined" density="compact">
          {{ error }}
        </v-alert>
        <v-alert v-if="success" class="mt-3" color="success" variant="outlined" density="compact">
          {{ success }}
        </v-alert>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn color="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
          保存
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue"
import { useRouter } from "vue-router"
import { changeOwnPassword } from "@/api/auth"
import { markPasswordChanged, mustChangePassword } from "@/stores/auth"

const router = useRouter()
const oldPassword = ref("")
const newPassword = ref("")
const confirmPassword = ref("")
const loading = ref(false)
const error = ref<string>()
const success = ref<string>()

const canSubmit = computed(
  () => !!oldPassword.value && !!newPassword.value && !!confirmPassword.value,
)

async function submit() {
  if (!canSubmit.value) return
  const wasForced = mustChangePassword.value
  error.value = undefined
  success.value = undefined
  if (newPassword.value !== confirmPassword.value) {
    error.value = "两次输入的新密码不一致"
    return
  }
  loading.value = true
  try {
    await changeOwnPassword(oldPassword.value, newPassword.value)
    markPasswordChanged()
    success.value = "密码修改成功"
    oldPassword.value = ""
    newPassword.value = ""
    confirmPassword.value = ""
    if (wasForced) {
      await router.replace({ name: "home" })
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}
</script>
