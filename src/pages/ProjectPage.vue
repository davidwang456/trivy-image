<template>
  <v-container class="py-8">
    <h2 class="text-h5 mb-2">Project Layer</h2>
    <p class="text-medium-emphasis mb-4">
      Grouped by job, system, and project. Use the left navigation to switch
      layers; click image links to open vulnerability details.
    </p>

    <v-alert
      v-if="jobIdFilter || systemFilter"
      type="info"
      variant="tonal"
      class="mb-4"
    >
      Filters:
      <strong v-if="jobIdFilter">JobId={{ jobIdFilter }}</strong>
      <strong v-if="jobIdFilter && systemFilter">, </strong>
      <strong v-if="systemFilter">System={{ systemFilter }}</strong>
    </v-alert>

    <v-card variant="outlined" class="mb-6">
      <v-card-title class="text-subtitle-1"
        >Recent 7-day imageRef trend</v-card-title
      >
      <v-card-text>
        <div ref="chartRef" class="chart-host" />
      </v-card-text>
    </v-card>

    <v-alert v-if="error" color="error" variant="outlined" class="mb-4">{{
      error
    }}</v-alert>

    <v-text-field
      v-model="search"
      class="mb-4"
      label="Search projects (jobId/jobName/system/project/createBy)"
      prepend-inner-icon="mdi-magnify"
      variant="solo-filled"
      flat
      density="comfortable"
      clearable
      hide-details
    />

    <v-data-table
      :headers="headers"
      :items="filteredRows"
      :loading="loading"
      item-key="key"
      density="comfortable"
      :items-per-page="15"
      :items-per-page-options="[10, 15, 30, 50]"
    >
      <template #item.jobId="{ item }">
        <v-btn
          variant="text"
          color="primary"
          class="px-0 text-none"
          :to="{ name: 'systems', query: { jobId: item.jobId } }"
        >
          {{ item.jobId }}
        </v-btn>
      </template>
      <template #item.systemName="{ item }">
        <v-btn
          variant="text"
          color="primary"
          class="px-0 text-none"
          :to="{ name: 'systems', query: { jobId: item.jobId } }"
        >
          {{ item.systemName }}
        </v-btn>
      </template>
      <template #item.imageRef="{ item }">
        <v-btn
          variant="text"
          color="primary"
          class="px-0 text-none"
          @click="loadImage(item.imageId)"
        >
          {{ item.imageRef }}
        </v-btn>
      </template>
      <template #item.createTime="{ item }">{{
        formatDateTime(item.createTime)
      }}</template>
      <template #item.updateTime="{ item }">{{
        formatDateTime(item.updateTime)
      }}</template>
    </v-data-table>

    <v-card variant="outlined" class="mt-6 mb-4">
      <v-card-title>Selected Image</v-card-title>
      <v-card-text>
        <div v-if="selectedImageRef">
          <div><strong>Image:</strong> {{ selectedImageRef }}</div>
        </div>
        <div v-else class="text-medium-emphasis">
          Click imageRef link in table to view vulnerability details.
        </div>
      </v-card-text>
    </v-card>

    <DataTable
      v-if="selectedVulnerabilities.length > 0 && selectedScanId"
      :selectedVulnerabilities="selectedVulnerabilities"
      :scanId="selectedScanId"
    />
  </v-container>
</template>

<script setup lang="ts">
import DataTable from "@/components/DataTable.vue"
import { getScanById, listScans, type ScanSummary } from "@/api/scan"
import type { Vulnerability } from "@/types"
import { extractTargetsFromReport } from "@/utils/trivyReport"
import * as echarts from "echarts"
import type { ECharts, EChartsOption } from "echarts"
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { useRoute } from "vue-router"

const route = useRoute()
const loading = ref(false)
const error = ref<string>()
type ProjectImageRow = {
  key: string
  imageId: number
  imageRef: string
  jobId: string
  jobName: string
  jobType: "manual" | "cron"
  systemName: string
  projectName: string
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}
const rows = ref<ProjectImageRow[]>([])
const search = ref("")
const selectedVulnerabilities = ref<Vulnerability[]>([])
const selectedScanId = ref<number>()
const selectedImageRef = ref("")
const chartRef = ref<HTMLDivElement | null>(null)
const trendDays = ref<string[]>([])
const trendImages = ref<number[]>([])
let chartInstance: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const headers = [
  { title: "Project", key: "projectName" },
  { title: "ImageRef", key: "imageRef" },
  { title: "System", key: "systemName" },
  { title: "JobId", key: "jobId" },
  { title: "JobName", key: "jobName" },
  { title: "JobType", key: "jobType" },
  { title: "CreateBy", key: "createBy" },
  { title: "UpdateBy", key: "updateBy" },
  { title: "CreateTime", key: "createTime" },
  { title: "UpdateTime", key: "updateTime" },
]

const jobIdFilter = computed(() => {
  const raw = route.query.jobId
  return typeof raw === "string" && raw.trim() ? raw.trim() : ""
})
const systemFilter = computed(() => {
  const raw = route.query.system
  return typeof raw === "string" && raw.trim() ? raw.trim() : ""
})

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((r) =>
    [r.jobId, r.jobName, r.systemName, r.projectName, r.imageRef, r.createBy]
      .join(" ")
      .toLowerCase()
      .includes(q),
  )
})

onMounted(() => void load())
watch([jobIdFilter, systemFilter], () => void load())
watch([trendDays, trendImages], () =>
  chartInstance?.setOption(buildChartOption(), true),
)
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chartInstance?.dispose()
  chartInstance = null
})

async function load() {
  loading.value = true
  error.value = undefined
  try {
    const scans = await listScans()
    const scopedScans = scans.filter(
      (s) =>
        (!jobIdFilter.value || s.jobId === jobIdFilter.value) &&
        (!systemFilter.value || s.systemName === systemFilter.value),
    )
    buildDailyImageTrend(scopedScans)
    rows.value = scopedScans
      .map((s) => ({
        key: `${s.id}`,
        imageId: s.id,
        imageRef: s.imageRef,
        jobId: s.jobId,
        jobName: s.jobName,
        jobType: s.jobType,
        systemName: s.systemName,
        projectName: s.projectName,
        createBy: s.createBy || "-",
        updateBy: s.updateBy || "-",
        createTime: s.createTime,
        updateTime: s.updateTime,
      }))
      .sort(
        (a, b) =>
          new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime(),
      )
    await nextTick()
    initOrUpdateChart()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function buildDailyImageTrend(scans: ScanSummary[]) {
  const dates = Array.from({ length: 7 }, (_, idx) => {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() - (6 - idx))
    return d
  })
  const dayKeys = dates.map((d) => formatDayKey(d))
  const buckets = new Map<string, number>(dayKeys.map((k) => [k, 0]))
  for (const s of scans) {
    const key = formatDayKey(new Date(s.createTime))
    if (!buckets.has(key)) continue
    buckets.set(key, (buckets.get(key) ?? 0) + 1)
  }
  trendDays.value = dates.map((d) => `${d.getMonth() + 1}/${d.getDate()}`)
  trendImages.value = dayKeys.map((k) => buckets.get(k) ?? 0)
}

function formatDayKey(date: Date): string {
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return ""
  d.setHours(0, 0, 0, 0)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`
}

function buildChartOption(): EChartsOption {
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { data: ["ImageRefs"], top: 0 },
    grid: { left: 48, right: 24, bottom: 32, top: 40, containLabel: true },
    xAxis: {
      type: "category",
      data: trendDays.value,
      axisTick: { alignWithLabel: true },
    },
    yAxis: { type: "value", minInterval: 1 },
    series: [
      {
        type: "bar",
        name: "ImageRefs",
        data: trendImages.value,
        barMaxWidth: 36,
        itemStyle: { color: "#00897b", borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
}

function initOrUpdateChart() {
  const el = chartRef.value
  if (!el) return
  if (!chartInstance) {
    chartInstance = echarts.init(el, undefined, { renderer: "canvas" })
    resizeObserver = new ResizeObserver(() => chartInstance?.resize())
    resizeObserver.observe(el)
  }
  chartInstance.setOption(buildChartOption(), true)
}

async function loadImage(id: number) {
  loading.value = true
  error.value = undefined
  try {
    const res = await getScanById(id)
    const { targets, error: parseError } = extractTargetsFromReport(res.report)
    if (parseError) {
      error.value = parseError.text
      return
    }
    const flat = targets
      .filter((vr) => vr.Vulnerabilities)
      .flatMap((vr) => vr.Vulnerabilities!) as Vulnerability[]
    flat.forEach((item: Vulnerability, index: number) => (item.id = index))
    selectedVulnerabilities.value = flat
    selectedScanId.value = id
    selectedImageRef.value = res.imageRef
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function formatDateTime(value: string): string {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}
</script>

<style scoped>
.chart-host {
  width: 100%;
  min-width: 0;
  height: 320px;
}
</style>
