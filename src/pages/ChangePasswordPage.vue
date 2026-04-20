<template>
  <v-container class="py-12" style="max-width: 560px">
    <v-card rounded="xl" elevation="2">
      <v-card-title class="text-h6">Change Password</v-card-title>
      <v-card-subtitle>
        <span v-if="mustChangePassword"
          >Please change the default admin password on first login.</span
        >
        <span v-else>Please enter your old password and a new password.</span>
      </v-card-subtitle>
      <v-card-text>
        <v-text-field
          v-model="oldPassword"
          label="Old Password"
          type="password"
          prepend-inner-icon="mdi-lock"
          variant="solo-filled"
          flat
          class="mb-3"
        />
        <v-text-field
          v-model="newPassword"
          label="New Password"
          type="password"
          prepend-inner-icon="mdi-lock-reset"
          variant="solo-filled"
          flat
          class="mb-3"
        />
        <v-text-field
          v-model="confirmPassword"
          label="Confirm New Password"
          type="password"
          prepend-inner-icon="mdi-lock-check"
          variant="solo-filled"
          flat
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
        <v-alert
          v-if="success"
          class="mt-3"
          color="success"
          variant="outlined"
          density="compact"
        >
          {{ success }}
        </v-alert>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn
          color="primary"
          :loading="loading"
          :disabled="!canSubmit"
          @click="submit"
        >
          Save
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
    error.value = "The two new passwords do not match."
    return
  }
  loading.value = true
  try {
    await changeOwnPassword(oldPassword.value, newPassword.value)
    markPasswordChanged()
    success.value = "Password changed successfully."
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
