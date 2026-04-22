<template>
  <v-container class="py-8">
    <v-card rounded="xl" class="mb-6">
      <v-card-title class="text-h6">Add Registry Datasource</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="name"
              label="Name"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-select
              v-model="datasourceType"
              :items="datasourceTypes"
              label="Type"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col v-if="datasourceType === 'harbor'" cols="12" md="3">
            <v-text-field
              v-model="harborBaseUrl"
              label="Harbor Base URL"
              placeholder="http://harbor.example.com"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col v-if="datasourceType === 'harbor'" cols="12" md="2">
            <v-text-field
              v-model="username"
              label="Username"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col v-if="datasourceType === 'harbor'" cols="12" md="1">
            <v-text-field
              v-model="password"
              label="Password"
              type="password"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col v-if="datasourceType === 'swr'" cols="12" md="3">
            <v-text-field
              v-model="swrBaseUrl"
              label="SWR Base URL"
              placeholder="http://swr.example.com"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col v-if="datasourceType === 'swr'" cols="12" md="2">
            <v-text-field v-model="ak" label="AK" variant="solo-filled" flat />
          </v-col>
          <v-col v-if="datasourceType === 'swr'" cols="12" md="2">
            <v-text-field
              v-model="sk"
              label="SK"
              type="password"
              variant="solo-filled"
              flat
            />
          </v-col>
          <v-col cols="12" md="1" class="d-flex align-center">
            <v-btn color="primary" block :disabled="!canCreate" @click="create"
              >Add</v-btn
            >
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card rounded="xl" class="mb-6">
      <v-card-title class="text-h6">Harbor Datasources</v-card-title>
      <v-card-text>
        <v-alert
          v-if="error"
          color="error"
          variant="outlined"
          density="compact"
          class="mb-3"
        >
          {{ error }}
        </v-alert>
        <v-data-table
          :headers="harborHeaders"
          :items="harborDatasources"
          item-key="id"
        >
          <template #item.type="{ item }">
            {{ normalizeType(item.type) }}
          </template>
          <template #item.actions="{ item }">
            <v-btn
              size="small"
              color="error"
              variant="text"
              @click="remove(item.id)"
              >Delete</v-btn
            >
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <v-card rounded="xl">
      <v-card-title class="text-h6">SWR Datasources</v-card-title>
      <v-card-text>
        <v-data-table
          :headers="swrHeaders"
          :items="swrDatasources"
          item-key="id"
        >
          <template #item.type="{ item }">
            {{ normalizeType(item.type) }}
          </template>
          <template #item.actions="{ item }">
            <v-btn
              size="small"
              color="error"
              variant="text"
              @click="remove(item.id)"
              >Delete</v-btn
            >
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
const datasourceType = ref<"harbor" | "swr">("harbor")
const datasourceTypes: Array<"harbor" | "swr"> = ["harbor", "swr"]
const harborBaseUrl = ref("")
const username = ref("")
const password = ref("")
const swrBaseUrl = ref("")
const ak = ref("")
const sk = ref("")

const canCreate = computed(() => {
  if (!name.value.trim()) return false
  if (datasourceType.value === "harbor") {
    return (
      !!harborBaseUrl.value.trim() &&
      !!username.value.trim() &&
      !!password.value
    )
  }
  return !!swrBaseUrl.value.trim() && !!ak.value.trim() && !!sk.value
})

const harborHeaders = [
  { title: "Name", key: "name" },
  { title: "Type", key: "type" },
  { title: "Harbor URL", key: "harborBaseUrl" },
  { title: "Username", key: "username" },
  { title: "Created", key: "createTime" },
  { title: "Updated", key: "updateTime" },
  { title: "Actions", key: "actions", sortable: false },
]

const swrHeaders = [
  { title: "Name", key: "name" },
  { title: "Type", key: "type" },
  { title: "SWR Base URL", key: "harborBaseUrl" },
  { title: "AK", key: "username" },
  { title: "Created", key: "createTime" },
  { title: "Updated", key: "updateTime" },
  { title: "Actions", key: "actions", sortable: false },
]

const harborDatasources = computed(() =>
  datasources.value.filter((d) => normalizeType(d.type) === "harbor"),
)

const swrDatasources = computed(() =>
  datasources.value.filter((d) => normalizeType(d.type) === "swr"),
)

function normalizeType(type: RegistryDatasource["type"]) {
  return type === "swr" ? "swr" : "harbor"
}

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
    const body =
      datasourceType.value === "harbor"
        ? {
            name: name.value.trim(),
            type: "harbor" as const,
            harborBaseUrl: harborBaseUrl.value.trim(),
            username: username.value.trim(),
            password: password.value,
          }
        : {
            name: name.value.trim(),
            type: "swr" as const,
            harborBaseUrl: swrBaseUrl.value.trim(),
            ak: ak.value.trim(),
            sk: sk.value,
          }
    await createRegistryDatasource(body)
    name.value = ""
    datasourceType.value = "harbor"
    harborBaseUrl.value = ""
    username.value = ""
    password.value = ""
    swrBaseUrl.value = ""
    ak.value = ""
    sk.value = ""
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
