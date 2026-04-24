<template>
  <div>
    <v-container class="py-8">
      <v-stepper-vertical flat hide-actions editable>
        <template v-slot:default="{ next }">
          <v-stepper-vertical-item
            :complete="jobRows.length > 0"
            title="Registry scan"
            subtitle="Select datasource and scan image(s)"
            value="1"
          >
            <h2 class="headline">Scan an image</h2>
            <p class="mb-6 text-medium-emphasis">
              Select a registry datasource first, then enter image references
              separated by commas.
            </p>

            <v-row dense>
              <v-col cols="12" md="8">
                <v-select
                  v-model="selectedDatasourceId"
                  :items="datasourceItems"
                  item-title="title"
                  item-value="value"
                  label="Registry datasource"
                  prepend-inner-icon="mdi-database-cog"
                  variant="solo-filled"
                  flat
                  density="comfortable"
                  hide-details="auto"
                  class="mb-2"
                />
              </v-col>
              <v-col cols="12">
                <v-text-field
                  v-model="imageRef"
                  label="Image reference"
                  prepend-inner-icon="mdi-docker"
                  placeholder="docker.io/library/nginx:latest, docker.io/library/redis:latest"
                  variant="solo-filled"
                  flat
                  density="comfortable"
                  hide-details="auto"
                  class="mb-4"
                />
              </v-col>
              <v-col cols="12">
                <v-btn
                  color="primary"
                  size="large"
                  rounded="xl"
                  :loading="scanning"
                  :disabled="!imageRef.trim() || !selectedDatasourceId"
                  prepend-icon="mdi-radar"
                  @click="runScan(next)"
                >
                  Scan
                </v-btn>
              </v-col>
            </v-row>

            <v-alert
              v-if="scanError"
              class="mt-5"
              color="error"
              icon="$error"
              variant="outlined"
              closable
              @click:close="scanError = undefined"
              title="Scan failed"
            >
              {{ scanError }}
            </v-alert>
          </v-stepper-vertical-item>

          <v-stepper-vertical-item
            title="Explore"
            subtitle="View the job scan results"
            value="2"
          >
            <v-data-table
              v-if="jobRows.length > 0"
              :headers="jobHeaders"
              :items="jobRows"
              item-key="id"
              density="comfortable"
              :items-per-page="15"
              :items-per-page-options="[10, 15, 30, 50]"
            />
            <v-row v-else class="my-2">
              <v-col>
                <v-alert border="top" border-color="info" elevation="2">
                  No job results yet. Run a scan in the step above.
                </v-alert>
              </v-col>
            </v-row>
          </v-stepper-vertical-item>
        </template>
      </v-stepper-vertical>
    </v-container>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import { scanImage } from "@/api/scan"
import { listRegistryDatasources } from "@/api/registry"
import {
  VStepperVertical,
  VStepperVerticalItem,
} from "vuetify/labs/VStepperVertical"

const datasources = ref<{ id: number; name: string }[]>([])
const selectedDatasourceId = ref<number>()
const imageRef = ref("")
const scanning = ref(false)
const scanError = ref<string>()

type JobResultRow = {
  id: number
  jobId: string
  jobType: string
  systemName: string
  projectName: string
  imageRef: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
}
const jobRows = ref<JobResultRow[]>([])
const datasourceItems = computed(() =>
  datasources.value.map((d) => ({ title: d.name, value: d.id })),
)
const jobHeaders = [
  { title: "JobId", key: "jobId" },
  { title: "JobType", key: "jobType" },
  { title: "System", key: "systemName" },
  { title: "Project", key: "projectName" },
  { title: "ImageRef", key: "imageRef" },
  { title: "CreateTime", key: "createTime" },
  { title: "UpdateTime", key: "updateTime" },
  { title: "CreateBy", key: "createBy" },
  { title: "UpdateBy", key: "updateBy" },
]

onMounted(async () => {
  try {
    const list = await listRegistryDatasources()
    datasources.value = list.map((d) => ({ id: d.id, name: d.name }))
    if (datasources.value.length > 0) {
      selectedDatasourceId.value = datasources.value[0].id
    }
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  }
})

async function runScan(nextStep: () => void) {
  if (!selectedDatasourceId.value) return
  scanError.value = undefined
  scanning.value = true
  try {
    const resList = await scanImage({
      datasourceId: selectedDatasourceId.value,
      imageRef: imageRef.value.trim(),
    })
    jobRows.value = resList.map((item) => ({
      id: item.id,
      jobId: item.jobId,
      jobType: item.jobType,
      systemName: item.systemName,
      projectName: item.projectName,
      imageRef: item.imageRef,
      createTime: formatDateTime(item.createTime),
      updateTime: formatDateTime(item.updateTime),
      createBy: item.createBy || "-",
      updateBy: item.updateBy || "-",
    }))
    nextStep()
  } catch (e: unknown) {
    scanError.value =
      e instanceof Error ? e.message : "Unknown error during scan"
  } finally {
    scanning.value = false
  }
}

function formatDateTime(value: string): string {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}
</script>
