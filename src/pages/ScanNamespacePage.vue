<template>
  <v-container class="py-8">
    <v-card rounded="xl">
      <v-card-title class="text-h6">Scan by Namespace</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-select
              v-model="selectedDatasourceId"
              :items="datasourceItems"
              item-title="title"
              item-value="value"
              label="SWR datasource"
              variant="solo-filled"
              flat
              density="comfortable"
              hide-details="auto"
              @update:model-value="onDatasourceChanged"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-select
              v-model="selectedNamespace"
              :items="namespaceItems"
              label="Namespace"
              variant="solo-filled"
              flat
              density="comfortable"
              hide-details="auto"
              :disabled="!selectedDatasourceId"
              :loading="loadingNamespaces"
              @update:model-value="onNamespaceChanged"
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-select
              v-model="selectedImageRefs"
              :items="imageItems"
              label="Image"
              variant="solo-filled"
              flat
              density="comfortable"
              hide-details="auto"
              multiple
              chips
              closable-chips
              clearable
              hint="可多选；不选默认扫描当前 Namespace 下全部镜像"
              persistent-hint
              :disabled="!selectedNamespace"
              :loading="loadingImages"
            />
          </v-col>
          <v-col cols="12">
            <v-btn
              color="primary"
              prepend-icon="mdi-radar"
              :loading="scanning"
              :disabled="
                !selectedDatasourceId || effectiveImageRefs.length === 0
              "
              @click="runScan"
            >
              Scan selected/all images
            </v-btn>
          </v-col>
        </v-row>

        <v-alert
          v-if="error"
          class="mt-4"
          color="error"
          variant="outlined"
          closable
          @click:close="error = undefined"
        >
          {{ error }}
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card rounded="xl" class="mt-6">
      <v-card-title class="text-h6">Scan Results</v-card-title>
      <v-card-text>
        <v-data-table
          v-if="jobRows.length > 0"
          :headers="jobHeaders"
          :items="jobRows"
          item-key="id"
          density="comfortable"
          :items-per-page="15"
          :items-per-page-options="[10, 15, 30, 50]"
        />
        <v-alert v-else border="top" border-color="info" elevation="2">
          暂无扫描结果，请先从下拉框选择镜像后执行扫描。
        </v-alert>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import {
  listRegistryDatasources,
  listSwrImages,
  listSwrNamespaces,
  listSwrRepos,
} from "@/api/registry"
import { batchScanImages } from "@/api/scan"

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

const swrDatasources = ref<{ id: number; name: string }[]>([])
const selectedDatasourceId = ref<number>()
const selectedNamespace = ref<string>()
const selectedImageRefs = ref<string[]>([])
const namespaces = ref<string[]>([])
const images = ref<string[]>([])
const loadingNamespaces = ref(false)
const loadingImages = ref(false)
const scanning = ref(false)
const error = ref<string>()
const jobRows = ref<JobResultRow[]>([])

const datasourceItems = computed(() =>
  swrDatasources.value.map((item) => ({ title: item.name, value: item.id })),
)
const namespaceItems = computed(() => namespaces.value)
const imageItems = computed(() => images.value)
const effectiveImageRefs = computed(() =>
  selectedImageRefs.value.length > 0 ? selectedImageRefs.value : images.value,
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

function normalizeRepoName(namespace: string, repoName: string): string {
  const prefix = `${namespace}/`
  return repoName.startsWith(prefix) ? repoName.slice(prefix.length) : repoName
}

onMounted(async () => {
  try {
    const all = await listRegistryDatasources()
    swrDatasources.value = all
      .filter((d) => (d.type ?? "harbor") === "swr")
      .map((d) => ({ id: d.id, name: d.name }))
    if (swrDatasources.value.length > 0) {
      selectedDatasourceId.value = swrDatasources.value[0].id
      await loadNamespaces()
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

async function onDatasourceChanged() {
  selectedNamespace.value = undefined
  selectedImageRefs.value = []
  namespaces.value = []
  images.value = []
  await loadNamespaces()
}

async function onNamespaceChanged() {
  selectedImageRefs.value = []
  images.value = []
  await loadImages()
}

async function loadNamespaces() {
  if (!selectedDatasourceId.value) return
  loadingNamespaces.value = true
  error.value = undefined
  try {
    namespaces.value = await listSwrNamespaces(selectedDatasourceId.value)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingNamespaces.value = false
  }
}

async function loadImages() {
  if (!selectedDatasourceId.value || !selectedNamespace.value) return
  loadingImages.value = true
  error.value = undefined
  try {
    const rawRepos = await listSwrRepos(
      selectedDatasourceId.value,
      selectedNamespace.value,
    )
    const repos = rawRepos.map((repoName) =>
      normalizeRepoName(selectedNamespace.value!, repoName),
    )
    const imageGroups = await Promise.all(
      repos.map((repoName) =>
        listSwrImages(
          selectedDatasourceId.value!,
          selectedNamespace.value!,
          repoName,
        ),
      ),
    )
    images.value = imageGroups.flat().sort((a, b) => a.localeCompare(b))
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingImages.value = false
  }
}

async function runScan() {
  if (!selectedDatasourceId.value || effectiveImageRefs.value.length === 0)
    return
  scanning.value = true
  error.value = undefined
  try {
    const resList = await batchScanImages({
      datasourceId: selectedDatasourceId.value,
      imageRefs: effectiveImageRefs.value,
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
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Unknown error during scan"
  } finally {
    scanning.value = false
  }
}

function formatDateTime(value: string): string {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}
</script>
