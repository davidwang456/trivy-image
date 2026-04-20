<template>
  <v-container class="py-8">
    <v-card rounded="xl" class="mb-6">
      <v-card-title class="text-h6">新增 Registry Datasource</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="3">
            <v-text-field v-model="name" label="Name" variant="solo-filled" flat />
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="harborBaseUrl"
              label="Harbor Base URL"
              placeholder="http://harbor.example.com"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-text-field v-model="username" label="Username" variant="solo-filled" flat />
          </v-col>
          <v-col cols="12" md="2">
            <v-text-field
              v-model="password"
              label="Password"
              type="password"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="1" class="d-flex align-center">
            <v-btn color="primary" block :disabled="!canCreate" @click="create">Add</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card rounded="xl">
      <v-card-title class="text-h6">Datasources</v-card-title>
      <v-card-text>
        <v-alert v-if="error" color="error" variant="outlined" density="compact" class="mb-3">
          {{ error }}
        </v-alert>
        <v-data-table :headers="headers" :items="datasources" item-key="id">
          <template #item.actions="{ item }">
            <v-btn size="small" color="error" variant="text" @click="remove(item.id)">Delete</v-btn>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import {
  createRegistryDatasource,
  deleteRegistryDatasource,
  listRegistryDatasources,
  type RegistryDatasource,
} from "@/api/registry"

const datasources = ref<RegistryDatasource[]>([])
const error = ref<string>()
const name = ref("")
const harborBaseUrl = ref("")
const username = ref("")
const password = ref("")

const canCreate = computed(
  () =>
    !!name.value.trim() &&
    !!harborBaseUrl.value.trim() &&
    !!username.value.trim() &&
    !!password.value,
)

const headers = [
  { title: "Name", key: "name" },
  { title: "Harbor URL", key: "harborBaseUrl" },
  { title: "Username", key: "username" },
  { title: "Created", key: "createTime" },
  { title: "Updated", key: "updateTime" },
  { title: "Actions", key: "actions", sortable: false },
]

async function refresh() {
  try {
    datasources.value = await listRegistryDatasources()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function create() {
  if (!canCreate.value) return
  error.value = undefined
  try {
    await createRegistryDatasource({
      name: name.value.trim(),
      harborBaseUrl: harborBaseUrl.value.trim(),
      username: username.value.trim(),
      password: password.value,
    })
    name.value = ""
    harborBaseUrl.value = ""
    username.value = ""
    password.value = ""
    await refresh()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function remove(id: number) {
  try {
    await deleteRegistryDatasource(id)
    datasources.value = datasources.value.filter((d) => d.id !== id)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(() => {
  void refresh()
})
</script>
