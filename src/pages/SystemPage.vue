<template>
  <v-container class="py-8">
    <h2 class="text-h5 mb-2">System Layer</h2>
    <p class="text-medium-emphasis mb-4">
      Grouped by job and system. Use the left navigation to switch layers; use
      table links to drill down.
    </p>

    <v-alert v-if="jobIdFilter" type="info" variant="tonal" class="mb-4">
      Filtered by JobId: <strong>{{ jobIdFilter }}</strong>
    </v-alert>

    <v-card variant="outlined" class="mb-6">
      <v-card-title class="text-subtitle-1">Recent 7-day trend</v-card-title>
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
      label="Search systems (jobId/jobName/system/createBy)"
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
          :to="{ name: 'dashboard' }"
        >
          {{ item.jobId }}
        </v-btn>
      </template>
      <template #item.projectCount="{ item }">
        <v-btn
          variant="text"
          color="primary"
          class="px-0 text-none"
          :to="{
            name: 'projects',
            query: { jobId: item.jobId, system: item.systemName },
          }"
        >
          {{ item.projectCount }}
        </v-btn>
      </template>
      <template #item.createTime="{ item }">{{
        formatDateTime(item.createTime)
      }}</template>
      <template #item.updateTime="{ item }">{{
        formatDateTime(item.updateTime)
      }}</template>
    </v-data-table>
  </v-container>
</template>

<script setup lang="ts">
import { listScans, type ScanSummary } from "@/api/scan"
import { aggregateSystems, type SystemRow } from "@/utils/scanHierarchy"
import * as echarts from "echarts"
import type { ECharts, EChartsOption } from "echarts"
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { useRoute } from "vue-router"

const route = useRoute()
const loading = ref(false)
const error = ref<string>()
const rows = ref<SystemRow[]>([])
const search = ref("")
const chartRef = ref<HTMLDivElement | null>(null)
const trendProjects = ref<number[]>([])
const trendImages = ref<number[]>([])
const trendDays = ref<string[]>([])
let chartInstance: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const headers = [
  { title: "System", key: "systemName" },
  { title: "Projects", key: "projectCount" },
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

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((r) =>
    [r.jobId, r.jobName, r.systemName, r.createBy]
      .join(" ")
      .toLowerCase()
      .includes(q),
  )
})

onMounted(() => void load())
watch(jobIdFilter, () => void load())
watch([trendDays, trendProjects, trendImages], () =>
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
    const scopedScans = jobIdFilter.value
      ? scans.filter((s) => s.jobId === jobIdFilter.value)
      : scans
    buildDailyTrend(scopedScans)
    rows.value = aggregateSystems(scans, jobIdFilter.value || undefined)
    await nextTick()
    initOrUpdateChart()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function buildDailyTrend(scans: ScanSummary[]) {
  const dates = Array.from({ length: 7 }, (_, idx) => {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() - (6 - idx))
    return d
  })
  const dayKeys = dates.map((d) => formatDayKey(d))
  const buckets = new Map<string, { projects: Set<string>; images: number }>(
    dayKeys.map((k) => [k, { projects: new Set<string>(), images: 0 }]),
  )
  for (const s of scans) {
    const key = formatDayKey(new Date(s.createTime))
    const bucket = buckets.get(key)
    if (!bucket) continue
    bucket.images += 1
    bucket.projects.add(`${s.jobId}|${s.systemName}|${s.projectName}`)
  }
  trendDays.value = dates.map((d) => `${d.getMonth() + 1}/${d.getDate()}`)
  trendProjects.value = dayKeys.map((k) => buckets.get(k)?.projects.size ?? 0)
  trendImages.value = dayKeys.map((k) => buckets.get(k)?.images ?? 0)
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
    legend: { data: ["Projects", "ImageRefs"], top: 0 },
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
        name: "Projects",
        data: trendProjects.value,
        barMaxWidth: 32,
        itemStyle: { color: "#1e88e5", borderRadius: [4, 4, 0, 0] },
      },
      {
        type: "bar",
        name: "ImageRefs",
        data: trendImages.value,
        barMaxWidth: 32,
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
