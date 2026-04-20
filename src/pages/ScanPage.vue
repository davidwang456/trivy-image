<template>
  <div>
    <v-container class="py-8">
      <v-stepper-vertical flat hide-actions editable>
        <template v-slot:default="{ next }">
          <v-stepper-vertical-item
            :complete="reportLoaded"
            title="Registry scan"
            subtitle="Select datasource and scan image(s)"
            value="1"
          >
            <h2 class="headline">Scan an image</h2>
            <p class="mb-6 text-medium-emphasis">
              Select a registry datasource first, then enter image reference
              for scan. Admin can also browse Harbor repos and batch scan.
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
                  placeholder="docker.io/library/nginx:latest"
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

            <v-divider class="my-6" />
            <div v-if="isAdmin">
              <h3 class="text-h6 mb-3">Admin: Harbor repo batch scan</h3>
              <v-row dense>
                <v-col cols="12" md="6">
                  <v-btn
                    color="secondary"
                    :disabled="!selectedDatasourceId"
                    :loading="loadingRepos"
                    @click="loadRepos"
                  >
                    Load repos
                  </v-btn>
                </v-col>
                <v-col cols="12" md="6">
                  <v-select
                    v-model="selectedRepo"
                    :items="repos"
                    label="Repo"
                    variant="solo-filled"
                    flat
                    density="comfortable"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-btn
                    color="secondary"
                    :disabled="!selectedDatasourceId || !selectedRepo"
                    :loading="loadingImages"
                    @click="loadImages"
                  >
                    Load images
                  </v-btn>
                </v-col>
                <v-col cols="12">
                  <v-select
                    v-model="selectedImages"
                    :items="repoImages"
                    label="Images for batch scan"
                    multiple
                    chips
                    variant="solo-filled"
                    flat
                    density="comfortable"
                  />
                </v-col>
                <v-col cols="12">
                  <v-btn
                    color="warning"
                    :disabled="!selectedDatasourceId || selectedImages.length === 0"
                    :loading="scanningBatch"
                    @click="runBatchScan(next)"
                  >
                    Batch Scan Selected Images
                  </v-btn>
                </v-col>
              </v-row>
            </div>

            <v-divider class="my-6" />
            <div v-if="isAdmin">
              <h3 class="text-h6 mb-3">Admin: Cron schedule</h3>
              <v-row dense>
                <v-col cols="12" md="4">
                  <v-text-field
                    v-model="scheduleCron"
                    label="Cron expression"
                    placeholder="0 */30 * * * *"
                    variant="solo-filled"
                    flat
                  />
                </v-col>
                <v-col cols="12" md="4">
                  <v-text-field
                    v-model="scheduleRepo"
                    label="Repo (optional)"
                    variant="solo-filled"
                    flat
                  />
                </v-col>
                <v-col cols="12" md="4">
                  <v-text-field
                    v-model="scheduleImage"
                    label="Image ref (optional)"
                    variant="solo-filled"
                    flat
                  />
                </v-col>
                <v-col cols="12">
                  <v-btn
                    color="primary"
                    :disabled="!selectedDatasourceId || !scheduleCron.trim()"
                    @click="createSchedule"
                  >
                    Add schedule
                  </v-btn>
                </v-col>
              </v-row>
              <v-data-table
                class="mt-4"
                :headers="scheduleHeaders"
                :items="schedules"
                item-key="id"
              >
                <template #item.actions="{ item }">
                  <v-btn
                    size="small"
                    color="error"
                    variant="text"
                    @click="removeSchedule(item.id)"
                    >Delete</v-btn
                  >
                </template>
              </v-data-table>
            </div>

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
            subtitle="Checkout the report results"
            value="2"
          >
            <DataTable
              v-if="reportLoaded"
              :selectedVulnerabilities="selectedVulnerabilities"
            />
            <v-row v-else class="my-2"
              ><v-col>
                <v-alert border="top" border-color="info" elevation="2">
                  No data yet. Run a scan in the step above to load a Trivy
                  report.
                </v-alert></v-col
              ></v-row
            >
          </v-stepper-vertical-item>
        </template>
      </v-stepper-vertical>
    </v-container>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import DataTable from "@/components/DataTable.vue"
import type { Vulnerability } from "@/types"
import { batchScanImages, scanImage } from "@/api/scan"
import { extractTargetsFromReport } from "@/utils/trivyReport"
import {
  createRegistrySchedule,
  deleteRegistrySchedule,
  listHarborImages,
  listHarborRepos,
  listRegistryDatasources,
  listRegistrySchedules,
  type RegistrySchedule,
} from "@/api/registry"
import { isAdmin } from "@/stores/auth"
import {
  VStepperVertical,
  VStepperVerticalItem,
} from "vuetify/labs/VStepperVertical"

const datasources = ref<{ id: number; name: string }[]>([])
const selectedDatasourceId = ref<number>()
const imageRef = ref("")
const repos = ref<string[]>([])
const selectedRepo = ref("")
const repoImages = ref<string[]>([])
const selectedImages = ref<string[]>([])
const loadingRepos = ref(false)
const loadingImages = ref(false)
const scanning = ref(false)
const scanningBatch = ref(false)
const scanError = ref<string>()
const schedules = ref<RegistrySchedule[]>([])
const scheduleCron = ref("0 0 * * * *")
const scheduleRepo = ref("")
const scheduleImage = ref("")

const selectedVulnerabilities = ref<Vulnerability[]>([])
const reportLoaded = ref(false)
const datasourceItems = computed(() =>
  datasources.value.map((d) => ({ title: d.name, value: d.id })),
)
const scheduleHeaders = [
  { title: "Datasource", key: "datasourceName" },
  { title: "Repo", key: "repoName" },
  { title: "Image", key: "imageRef" },
  { title: "Cron", key: "cronExpression" },
  { title: "Enabled", key: "enabled" },
  { title: "Last Run", key: "lastRunAt" },
  { title: "Actions", key: "actions", sortable: false },
]

onMounted(async () => {
  try {
    const list = await listRegistryDatasources()
    datasources.value = list.map((d) => ({ id: d.id, name: d.name }))
    if (datasources.value.length > 0) {
      selectedDatasourceId.value = datasources.value[0].id
    }
    if (isAdmin.value) {
      schedules.value = await listRegistrySchedules()
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
    const res = await scanImage({
      datasourceId: selectedDatasourceId.value,
      imageRef: imageRef.value.trim(),
    })

    const { targets, error } = extractTargetsFromReport(res.report)
    if (error) {
      scanError.value = error.text
      return
    }

    selectedVulnerabilities.value.splice(0)
    const flat = targets
      .filter((vr) => vr.Vulnerabilities)
      .flatMap((vr) => vr.Vulnerabilities!) as Vulnerability[]

    flat.forEach((item: Vulnerability, index: number) => (item.id = index))
    selectedVulnerabilities.value.push(...flat)
    reportLoaded.value = true
    nextStep()
  } catch (e: unknown) {
    scanError.value =
      e instanceof Error ? e.message : "Unknown error during scan"
  } finally {
    scanning.value = false
  }
}

async function loadRepos() {
  if (!selectedDatasourceId.value) return
  loadingRepos.value = true
  try {
    repos.value = await listHarborRepos(selectedDatasourceId.value)
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingRepos.value = false
  }
}

async function loadImages() {
  if (!selectedDatasourceId.value || !selectedRepo.value) return
  loadingImages.value = true
  try {
    repoImages.value = await listHarborImages(
      selectedDatasourceId.value,
      selectedRepo.value,
    )
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingImages.value = false
  }
}

async function runBatchScan(nextStep: () => void) {
  if (!selectedDatasourceId.value || selectedImages.value.length === 0) return
  scanningBatch.value = true
  scanError.value = undefined
  try {
    const res = await batchScanImages({
      datasourceId: selectedDatasourceId.value,
      imageRefs: selectedImages.value,
    })
    const last = res[res.length - 1]
    const { targets, error } = extractTargetsFromReport(last.report)
    if (error) {
      scanError.value = error.text
      return
    }
    const flat = targets
      .filter((vr) => vr.Vulnerabilities)
      .flatMap((vr) => vr.Vulnerabilities!) as Vulnerability[]
    flat.forEach((item: Vulnerability, index: number) => (item.id = index))
    selectedVulnerabilities.value.splice(0)
    selectedVulnerabilities.value.push(...flat)
    reportLoaded.value = true
    nextStep()
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  } finally {
    scanningBatch.value = false
  }
}

async function createSchedule() {
  if (!selectedDatasourceId.value) return
  try {
    await createRegistrySchedule({
      datasourceId: selectedDatasourceId.value,
      repoName: scheduleRepo.value.trim() || undefined,
      imageRef: scheduleImage.value.trim() || undefined,
      cronExpression: scheduleCron.value.trim(),
      enabled: true,
    })
    schedules.value = await listRegistrySchedules()
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  }
}

async function removeSchedule(id: number) {
  try {
    await deleteRegistrySchedule(id)
    schedules.value = schedules.value.filter((s) => s.id !== id)
  } catch (e: unknown) {
    scanError.value = e instanceof Error ? e.message : String(e)
  }
}
</script>
