<template>
  <v-btn-toggle
    v-model="reportSource"
    mandatory
    rounded="pill"
    color="primary"
    variant="outlined"
    class="mb-6"
  >
    <v-btn :value="ReportSource.File" prepend-icon="mdi-file-upload"
      >Upload a File</v-btn
    >
    <v-btn :value="ReportSource.Url" prepend-icon="mdi-link"
      >Get File from URL</v-btn
    >
    <v-btn :value="ReportSource.Postgres" prepend-icon="mdi-database"
      >PostgreSQL</v-btn
    >
  </v-btn-toggle>
  <v-container>
    <v-file-upload
      v-if="reportSource === ReportSource.File"
      density="compact"
      browse-text="Local Filesystem"
      divider-text="or choose locally"
      title="Choose file"
      file-type="application/json"
      :multiple="false"
      color="#f2f2f2"
      @update:model-value="handleFileUpload($event)"
    />
  </v-container>

  <ReportUrlFetcher
    :onNewReport="onNewReportFromUrl"
    v-if="reportSource === ReportSource.Url"
    :presetUrl="presetUrl"
  />

  <PostgresReportPicker
    v-if="reportSource === ReportSource.Postgres"
    @report-loaded="onReportFromPostgres"
  />

  <v-alert
    v-if="reportError"
    class="mt-5"
    color="error"
    icon="$error"
    variant="outlined"
    closable
    @click:close="reportError = undefined"
    :title="reportError.title"
  >
    <p>{{ reportError.text }}</p>
    <a v-if="reportError.link" :href="reportError.link.href" target="_blank">{{
      reportError.link.text
    }}</a>
  </v-alert>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import type {
  ReportError,
  Version1OrVersion2,
  VulnerabilityReportTarget,
} from "@/types"
import ReportUrlFetcher from "@/components/ReportUrlFetcher.vue"
import PostgresReportPicker from "@/components/PostgresReportPicker.vue"
import { extractTargetsFromReport } from "@/utils/trivyReport"
import type { Vulnerability } from "@/types"
import { importScanReport } from "@/api/scan"

enum ReportSource {
  File,
  Url,
  Postgres,
}

const props = defineProps<{ presetUrl?: string }>()
const emit = defineEmits<{
  inputChanged: [payload: { vulnerabilities: Vulnerability[]; scanId?: number }]
}>()

const reportSource = ref(ReportSource.File)
const vulnerabilityReport = ref<VulnerabilityReportTarget[]>([])
const reportError = ref<ReportError>()

const handleFileUpload = (files: Blob[] | Blob) => {
  const file = Array.isArray(files) ? files[0] : files
  const imageRefFromFileName =
    file instanceof File && file.name.trim() ? file.name.trim() : undefined
  const reader = new FileReader()
  if (file) {
    reader.readAsText(file)
    reader.onload = async (e) => {
      await parseFileAndImport(e, imageRefFromFileName)
    }
  }
}

onMounted(() => {
  if (props.presetUrl) {
    reportSource.value = ReportSource.Url
  }
})

const selectedVulnerabilities = computed(() => {
  return vulnerabilityReport.value
    .filter(
      (
        vr,
      ): vr is VulnerabilityReportTarget & {
        Vulnerabilities: Vulnerability[]
      } => Array.isArray(vr.Vulnerabilities),
    )
    .flatMap((vr) => vr.Vulnerabilities)
})

const parseFileAndImport = async (
  fileEvent: ProgressEvent<FileReader>,
  imageRefFromFileName?: string,
): Promise<void> => {
  reportError.value = undefined
  if (
    fileEvent?.target?.result &&
    typeof fileEvent.target.result === "string"
  ) {
    try {
      const parsedReport = JSON.parse(
        fileEvent.target.result,
      ) as Version1OrVersion2
      const { targets, error } = extractTargetsFromReport(parsedReport)
      reportError.value = error
      if (error) {
        return
      }
      const scanId = await storeReportInPostgres(
        parsedReport,
        imageRefFromFileName,
      )
      handleNewReport(targets, scanId)
    } catch (e: unknown) {
      reportError.value = {
        title: "Invalid JSON file",
        text: e instanceof Error ? e.message : String(e),
      }
    }
  }
}

const onNewReportFromUrl = (
  report: Version1OrVersion2 | { error: ReportError },
) => {
  if (report instanceof Object && "error" in report) {
    reportError.value = report.error
    return
  }

  return onNewReport(report)
}

const onNewReport = async (report: Version1OrVersion2) => {
  const { targets, error } = extractTargetsFromReport(report)
  reportError.value = error
  if (error) {
    return
  }
  const scanId = await storeReportInPostgres(report)
  handleNewReport(targets, scanId)
}

const handleNewReport = (
  vulnerabilityTargets: VulnerabilityReportTarget[],
  scanId?: number,
) => {
  if (vulnerabilityTargets.length > 0) {
    vulnerabilityReport.value.splice(0)
    vulnerabilityReport.value.push(...vulnerabilityTargets)
    emit("inputChanged", {
      vulnerabilities: selectedVulnerabilities.value,
      scanId,
    })
  }
}

const onReportFromPostgres = (payload: {
  vulnerabilities: Vulnerability[]
  scanId: number
}) => {
  reportError.value = undefined
  emit("inputChanged", payload)
}

const storeReportInPostgres = async (
  report: Version1OrVersion2,
  imageRefOverride?: string,
) => {
  try {
    const { targets } = extractTargetsFromReport(report)
    const imageRef =
      imageRefOverride?.trim() || targets.find((t) => !!t.Target)?.Target
    const saved = await importScanReport({ report, imageRef })
    return saved.id
  } catch (e: unknown) {
    reportError.value = {
      title: "Save to PostgreSQL failed",
      text: e instanceof Error ? e.message : String(e),
    }
    throw e
  }
}
</script>
