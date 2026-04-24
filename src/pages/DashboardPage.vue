<template>
  <v-container class="py-8">
    <h2 class="text-h5 mb-4">Scan Dashboard</h2>
    <p class="text-medium-emphasis mb-4">
      Job -> System -> Project table view with pagination.
    </p>

    <v-card variant="outlined" class="mb-6">
      <v-card-title class="text-subtitle-1">
        Overview (<code>image_scans</code> in PostgreSQL)
      </v-card-title>
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-2">
          Last 7 calendar days (including today), grouped by
          <code>create_time</code> date. Grouped bar chart: distinct
          <code>job_id</code> count per day and <code>image_ref</code> row count
          per day.
        </p>
        <div ref="chartRef" class="chart-host" />
      </v-card-text>
    </v-card>

    <v-alert v-if="error" color="error" variant="outlined" class="mb-4">
      {{ error }}
    </v-alert>

    <v-text-field
      v-model="search"
      class="mb-4"
      label="Search (jobId/jobName/system/project/imageRef/createBy)"
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
      <template #item.imageRef="{ item }">
        <v-btn
          variant="text"
          color="primary"
          class="px-0 text-none"
          @click="loadImage(item.id)"
        >
          {{ item.imageRef }}
        </v-btn>
      </template>
      <template #item.createTime="{ item }">
        {{ formatDateTime(item.createTime) }}
      </template>
      <template #item.updateTime="{ item }">
        {{ formatDateTime(item.updateTime) }}
      </template>
    </v-data-table>

    <v-card variant="outlined" class="mt-6 mb-4">
      <v-card-title>Selected Image</v-card-title>
      <v-card-text>
        <div v-if="selectedImageRef">
          <div><strong>Image:</strong> {{ selectedImageRef }}</div>
        </div>
        <div v-else class="text-medium-emphasis">
          Click `imageRef` link to view vulnerabilities.
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
import {
  getScanById,
  getScanDailyTrend,
  listScans,
  type ScanDailyStat,
  type ScanSummary,
} from "@/api/scan"
import type { Vulnerability } from "@/types"
import { extractTargetsFromReport } from "@/utils/trivyReport"
import * as echarts from "echarts"
import type { ECharts, EChartsOption } from "echarts"
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"

type DashboardRow = {
  key: string
  id: number
  jobId: string
  jobName: string
  jobType: "manual" | "cron"
  system: string
  project: string
  imageRef: string
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

const loading = ref(false)
const error = ref<string>()
const rows = ref<DashboardRow[]>([])
const search = ref("")
const selectedVulnerabilities = ref<Vulnerability[]>([])
const selectedScanId = ref<number>()
const selectedImageRef = ref("")
const chartRef = ref<HTMLDivElement | null>(null)
const dailyTrend = ref<ScanDailyStat[]>([])
let chartInstance: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const headers = [
  { title: "ImageRef", key: "imageRef" },
  { title: "JobId", key: "jobId" },
  { title: "JobName", key: "jobName" },
  { title: "JobType", key: "jobType" },
  { title: "System", key: "system" },
  { title: "Project", key: "project" },
  { title: "CreateBy", key: "createBy" },
  { title: "UpdateBy", key: "updateBy" },
  { title: "CreateTime", key: "createTime" },
  { title: "UpdateTime", key: "updateTime" },
]

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((r) =>
    [r.jobId, r.jobName, r.system, r.project, r.imageRef, r.createBy]
      .join(" ")
      .toLowerCase()
      .includes(q),
  )
})

onMounted(() => {
  void loadDashboard()
})

watch(
  dailyTrend,
  () => {
    if (chartInstance) {
      chartInstance.setOption(buildChartOption(), true)
    }
  },
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chartInstance?.dispose()
  chartInstance = null
})

async function loadDashboard() {
  loading.value = true
  error.value = undefined
  try {
    const [scans, trend] = await Promise.all([listScans(), getScanDailyTrend()])
    rows.value = buildRows(scans)
    dailyTrend.value = trend
    await nextTick()
    initOrUpdateChart()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function formatAxisDate(isoDate: string): string {
  const d = new Date(isoDate + "T12:00:00")
  return Number.isNaN(d.getTime())
    ? isoDate
    : `${d.getMonth() + 1}/${d.getDate()}`
}

function buildChartOption(): EChartsOption {
  const days = dailyTrend.value
  const categories = days.map((d) => formatAxisDate(d.date))
  const jobSeries = days.map((d) => d.jobTotal)
  const imageSeries = days.map((d) => d.imageRefTotal)
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: {
      data: ["Jobs (distinct job_id)", "ImageRef rows"],
      top: 0,
    },
    grid: { left: 48, right: 24, bottom: 32, top: 40, containLabel: true },
    xAxis: {
      type: "category",
      data: categories,
      axisTick: { alignWithLabel: true },
    },
    yAxis: { type: "value", minInterval: 1 },
    series: [
      {
        type: "bar",
        name: "Jobs (distinct job_id)",
        data: jobSeries,
        barMaxWidth: 32,
        barGap: "12%",
        itemStyle: { color: "#1976d2", borderRadius: [4, 4, 0, 0] },
        emphasis: { focus: "series" },
        label: { show: true, position: "top", formatter: "{c}" },
      },
      {
        type: "bar",
        name: "ImageRef rows",
        data: imageSeries,
        barMaxWidth: 32,
        itemStyle: { color: "#00897b", borderRadius: [4, 4, 0, 0] },
        emphasis: { focus: "series" },
        label: { show: true, position: "top", formatter: "{c}" },
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

function buildRows(scans: ScanSummary[]): DashboardRow[] {
  return scans
    .map((scan) => ({
      key: `${scan.id}`,
      id: scan.id,
      jobId: scan.jobId,
      jobName: scan.jobName,
      jobType: scan.jobType,
      system: scan.systemName,
      project: scan.projectName,
      imageRef: scan.imageRef,
      createBy: scan.createBy || "-",
      updateBy: scan.updateBy || "-",
      createTime: scan.createTime,
      updateTime: scan.updateTime,
    }))
    .sort(
      (a, b) =>
        new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime(),
    )
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
