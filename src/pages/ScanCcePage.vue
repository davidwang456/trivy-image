<template>
  <v-container class="py-8">
    <v-card rounded="xl">
      <v-card-title class="text-h6">Scan Huawei CCE</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="region"
              label="Region"
              placeholder="cn-north-4"
              prepend-inner-icon="mdi-map-marker-radius"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="clusterName"
              label="Cluster name"
              placeholder="prod-cce-cluster"
              prepend-inner-icon="mdi-kubernetes"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-select
              v-model="namespace"
              :items="namespaceItems"
              label="Namespace"
              prepend-inner-icon="mdi-file-tree"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="6">
            <v-text-field
              v-model="workload"
              label="Workload"
              placeholder="deployment/payment-service"
              prepend-inner-icon="mdi-cube-outline"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" md="6">
            <v-text-field
              v-model="imageRef"
              label="Image reference"
              placeholder="swr.cn-north-4.myhuaweicloud.com/demo/payment-service:v1"
              prepend-inner-icon="mdi-docker"
              variant="solo-filled"
              flat
              density="comfortable"
            />
          </v-col>
          <v-col cols="12">
            <v-btn
              color="primary"
              prepend-icon="mdi-radar"
              :loading="scanning"
              :disabled="!canRun"
              @click="runMockScan"
            >
              Start CCE scan
            </v-btn>
          </v-col>
        </v-row>

        <v-alert
          class="mt-4"
          color="info"
          variant="tonal"
          icon="mdi-information-outline"
        >
          This page is for demonstration only. It does not call backend APIs.
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card rounded="xl" class="mt-6">
      <v-card-title class="text-h6">Scan Results</v-card-title>
      <v-card-text>
        <v-data-table
          v-if="mockRows.length > 0"
          :headers="headers"
          :items="mockRows"
          item-key="id"
          density="comfortable"
          :items-per-page="10"
        />
        <v-alert v-else border="top" border-color="info" elevation="2">
          No results yet. Fill in the form and start a scan.
        </v-alert>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue"

type MockRow = {
  id: number
  region: string
  cluster: string
  namespace: string
  workload: string
  imageRef: string
  status: string
  severityHigh: number
  severityCritical: number
  scanTime: string
}

const region = ref("cn-north-4")
const clusterName = ref("")
const namespace = ref<string>()
const workload = ref("")
const imageRef = ref("")
const scanning = ref(false)
const mockRows = ref<MockRow[]>([])

const namespaceItems = ["default", "kube-system", "prod", "staging"]

const headers = [
  { title: "Region", key: "region" },
  { title: "Cluster", key: "cluster" },
  { title: "Namespace", key: "namespace" },
  { title: "Workload", key: "workload" },
  { title: "ImageRef", key: "imageRef" },
  { title: "Status", key: "status" },
  { title: "High", key: "severityHigh" },
  { title: "Critical", key: "severityCritical" },
  { title: "ScanTime", key: "scanTime" },
]

const canRun = computed(
  () =>
    region.value.trim() &&
    clusterName.value.trim() &&
    namespace.value &&
    workload.value.trim() &&
    imageRef.value.trim(),
)

async function runMockScan() {
  scanning.value = true
  await new Promise((resolve) => setTimeout(resolve, 600))
  const now = new Date()
  mockRows.value = [
    {
      id: now.getTime(),
      region: region.value.trim(),
      cluster: clusterName.value.trim(),
      namespace: namespace.value || "-",
      workload: workload.value.trim(),
      imageRef: imageRef.value.trim(),
      status: "COMPLETED",
      severityHigh: 3,
      severityCritical: 1,
      scanTime: now.toLocaleString(),
    },
  ]
  scanning.value = false
}
</script>
