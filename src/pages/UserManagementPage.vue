<template>
  <v-container class="py-8">
    <v-card rounded="xl" class="mb-6">
      <v-card-title class="text-h6">Add User (Admin Only)</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="newUsername"
              label="Username"
              prepend-inner-icon="mdi-account"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="newPassword"
              label="Password (Stored as MD5 on backend)"
              prepend-inner-icon="mdi-lock"
              type="password"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-select
              v-model="newRole"
              :items="roles"
              label="Role"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="2" class="d-flex align-center">
            <v-btn
              color="primary"
              block
              :loading="creating"
              :disabled="!canCreate"
              @click="create"
            >
              Add
            </v-btn>
          </v-col>
        </v-row>
        <v-alert
          v-if="createError"
          class="mt-3"
          color="error"
          variant="outlined"
          density="compact"
        >
          {{ createError }}
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card rounded="xl" class="mb-6">
      <v-card-title class="text-h6">Admin Reset User Password</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="5">
            <v-select
              v-model="resetUserId"
              :items="users"
              item-title="username"
              item-value="id"
              label="Select User"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="5">
            <v-text-field
              v-model="resetNewPassword"
              label="New Password"
              type="password"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="2" class="d-flex align-center">
            <v-btn
              color="warning"
              block
              :loading="resetting"
              :disabled="!canReset"
              @click="resetPasswordByAdmin"
            >
              Reset
            </v-btn>
          </v-col>
        </v-row>
        <v-alert
          v-if="resetError"
          class="mt-3"
          color="error"
          variant="outlined"
          density="compact"
        >
          {{ resetError }}
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card rounded="xl">
      <v-card-title class="text-h6">User List</v-card-title>
      <v-card-text>
        <v-alert
          v-if="loadError"
          color="error"
          variant="outlined"
          density="compact"
          class="mb-3"
        >
          {{ loadError }}
        </v-alert>
        <v-data-table
          :headers="headers"
          :items="users"
          :loading="loading"
          item-key="id"
          :items-per-page="20"
        />
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import {
  adminResetPassword,
  createUser,
  listUsers,
  type UserInfo,
} from "@/api/auth"

const users = ref<UserInfo[]>([])
const loading = ref(false)
const loadError = ref<string>()

const creating = ref(false)
const createError = ref<string>()
const newUsername = ref("")
const newPassword = ref("")
const newRole = ref<"admin" | "user">("user")
const roles: Array<"admin" | "user"> = ["admin", "user"]
const resetUserId = ref<number>()
const resetNewPassword = ref("")
const resetting = ref(false)
const resetError = ref<string>()

const headers = [
  { title: "ID", key: "id" },
  { title: "Username", key: "username" },
  { title: "Role", key: "role" },
  { title: "Created Time", key: "createTime" },
  { title: "Updated Time", key: "updateTime" },
]

const canCreate = computed(
  () => !!newUsername.value.trim() && !!newPassword.value,
)
const canReset = computed(() => !!resetUserId.value && !!resetNewPassword.value)

async function refreshUsers() {
  loading.value = true
  loadError.value = undefined
  try {
    users.value = await listUsers()
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : String(e)
    users.value = []
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!canCreate.value) return
  creating.value = true
  createError.value = undefined
  try {
    await createUser({
      username: newUsername.value.trim(),
      password: newPassword.value,
      role: newRole.value,
    })
    newUsername.value = ""
    newPassword.value = ""
    newRole.value = "user"
    await refreshUsers()
  } catch (e: unknown) {
    createError.value = e instanceof Error ? e.message : String(e)
  } finally {
    creating.value = false
  }
}

async function resetPasswordByAdmin() {
  if (!canReset.value || !resetUserId.value) return
  resetting.value = true
  resetError.value = undefined
  try {
    await adminResetPassword(resetUserId.value, resetNewPassword.value)
    resetNewPassword.value = ""
    await refreshUsers()
  } catch (e: unknown) {
    resetError.value = e instanceof Error ? e.message : String(e)
  } finally {
    resetting.value = false
  }
}

onMounted(() => {
  void refreshUsers()
})
</script>
