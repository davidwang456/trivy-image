<template>
  <v-autocomplete
    v-model="selectedTarget"
    clearable
    :items="targets"
    label="Select Target"
    hide-details
    class="mb-5"
    multiple
  />
  <v-btn
    color="secondary"
    class="mb-4"
    :disabled="selectedTarget.length !== 1 || downloadingPdf"
    :loading="downloadingPdf"
    @click="handleDownloadPdf"
  >
    Download Target PDF
  </v-btn>

  <v-container>
    <v-row class="align-center">
      <v-btn
        rounded="xl"
        variant="tonal"
        value="ALL"
        size="large"
        v-on:click="severity = []"
        class="mr-5 mb-2"
        :active="severity.length === 0"
        active-color="primary"
      >
        Total: {{ vulnerabilityStats.total }}
      </v-btn>
      <v-spacer />
      <v-btn-toggle
        divided
        v-model="severity"
        rounded="xl"
        color="primary"
        variant="tonal"
        multiple
      >
        <v-btn
          v-for="severityInformation in vulnerabilityStats.severityInformation"
          :key="severityInformation.severity"
          :value="severityInformation.severity"
          :prepend-icon="getSeverityIcon(severityInformation.severity)"
          :aria-label="
            'Filter for severity ' +
            severityInformation.severity +
            ' (' +
            severityInformation.count +
            ')'
          "
        >
          {{ severityInformation.severity }}:
          {{ severityInformation.count }}
        </v-btn>
      </v-btn-toggle>
      <v-spacer />
      <v-checkbox
        v-model="fixable"
        color="success"
        :label="'Only Fixable: ' + vulnerabilityStats.fixable"
        hide-details
      />
    </v-row>
  </v-container>
  <v-text-field
    v-model="search"
    center-affix
    prepend-inner-icon="mdi-magnify"
    label="Search"
    placeholder="e. g. CVE-2021-1234"
    single-line
    class="my-5"
    type="search"
    variant="solo-filled"
    flat
    density="compact"
  ></v-text-field>

  <v-divider />

  <v-data-table
    dense
    v-model="ignoredVulnerabilities"
    item-value="id"
    show-select
    :headers="headers"
    :items-per-page="20"
    :items="filteredVulnerabilities"
    item-key="id"
    class="elevation-1 mb-5"
    show-expand
    single-expand
    return-object
    :v-model:sort-by="sortBy"
    :search="search"
    :v-model:expanded.sync="expanded"
  >
    <template v-slot:expanded-row="{ columns, item }">
      <td
        :colspan="columns.length"
        class="px-5 py-2"
        style="background-color: #f5f5f5"
      >
        <h3 class="my-3">
          {{ getDisplayTitle(item.Title, item.Description) }}
        </h3>
        <p class="mb-3">{{ item.Description }}</p>
        <p class="mb-3">
          <a :href="item.PrimaryURL" target="_blank">{{ item.PrimaryURL }}</a>
        </p>
      </td>
    </template>
  </v-data-table>

  <v-btn color="primary" class="mb-5" v-clipboard="() => ignoredCves">
    Copy .trivyignore to Clipboard
  </v-btn>
  <v-textarea
    outlined
    flat
    label=".trivyignore"
    :model-value="ignoredCves"
    readonly
  />
</template>

<script setup lang="ts">
import { downloadTargetPdf } from "@/api/scan"
import {
  orderedSeverityLevels,
  type Severity,
  type Vulnerability,
} from "@/types"
import { ref, computed } from "vue"

const props = defineProps<{
  selectedVulnerabilities: Vulnerability[]
}>()

const headers = [
  { title: "Target", key: "target", value: "Target", sortable: true },
  { title: "PkgName", key: "pkg", value: "PkgName", sortable: true },
  {
    title: "VulnerabilityID",
    key: "id",
    value: "VulnerabilityID",
    sortable: true,
  },
  {
    value: "Severity",
    key: "severity",
    title: "Severity",
    sort: compareBySeverity,
  },
  {
    title: "InstalledVersion",
    key: "installed",
    value: "InstalledVersion",
    sortable: true,
  },
  {
    title: "FixedVersion",
    key: "fixed",
    value: "FixedVersion",
    sortable: true,
  },
]

const ignoredVulnerabilities = ref<Vulnerability[]>([])
const filteredVulnerabilities = ref<Vulnerability[]>([])
const expanded = ref<unknown[]>([])
const sortBy = ref("Severity")
const search = ref("")
const severity = ref<Severity[]>([])
const fixable = ref<boolean>()
const selectedTarget = ref<string[]>([])
const downloadingPdf = ref(false)

const targets = computed(() => {
  const uniqueTargets = new Set(
    props.selectedVulnerabilities.map((v) => v.Target),
  )
  return Array.from(uniqueTargets)
})

const selectedVulnerabilitiesForTarget = computed(() => {
  return props.selectedVulnerabilities.filter((v) =>
    selectedTarget.value.length
      ? selectedTarget.value.includes(v.Target)
      : true,
  )
})

watch(
  () => [
    severity.value,
    fixable.value,
    search.value,
    selectedVulnerabilitiesForTarget.value,
    selectedTarget.value,
  ],
  () => {
    filteredVulnerabilities.value = selectedVulnerabilitiesForTarget.value
      .map((v) => ({
        ...v,
        id: v.id ? v.id : v.VulnerabilityID + "_" + v.PkgName,
      }))
      .filter((v) => {
        return severity.value.length === 0
          ? true
          : severity.value.includes(v.Severity)
      })
      .filter((v) => {
        return fixable.value ? !!v.FixedVersion : true
      })
  },
  { immediate: true, deep: true },
)

const ignoredCves = computed(() => {
  const uniqueCves = Array.from(
    new Set(ignoredVulnerabilities.value.map((v) => v.VulnerabilityID)),
  ).map((id) => {
    return ignoredVulnerabilities.value.find((v) => v.VulnerabilityID === id)
  })
  const resultingLines: string[] = []
  uniqueCves.sort((a, b) => compareBySeverity(a!.Severity, b!.Severity))

  uniqueCves?.forEach((v) => {
    const titleOrDesc = getDisplayTitle(v!.Title, v!.Description)
    resultingLines.push(`# ${v!.Severity}: ${titleOrDesc}`)
    resultingLines.push(v!.VulnerabilityID)
  })
  return resultingLines.join("\n")
})

const vulnerabilityStats = computed(() => {
  const total = selectedVulnerabilitiesForTarget.value.length
  const fixable = filteredVulnerabilities.value.filter(
    (vulnerabilities) => vulnerabilities.FixedVersion,
  ).length

  const vulnerabilitySeverityTypes: Severity[] = [
    ...new Set(
      selectedVulnerabilitiesForTarget.value.map((item) => item.Severity),
    ),
  ]

  const severityInformation = vulnerabilitySeverityTypes
    .map((severity) => {
      return {
        severity,
        count: selectedVulnerabilitiesForTarget.value.filter(
          (vulnerabilities) => vulnerabilities.Severity === severity,
        ).length,
      }
    })
    .sort((a, b) => compareBySeverity(a.severity, b.severity))
  return {
    total,
    fixable,
    severityInformation,
  }
})

function getSeverityIcon(severity: Severity) {
  switch (severity) {
    case "CRITICAL":
      return "mdi-alert-circle"
    case "HIGH":
      return "mdi-alert"
    case "MEDIUM":
      return "mdi-alert-outline"
    case "LOW":
      return "mdi-information-outline"
    case "UNKNOWN":
      return "mdi-help-circle-outline"
    default:
      return "mdi-help-circle-outline"
  }
}

function compareBySeverity(a: Severity, b: Severity): number {
  return orderedSeverityLevels.indexOf(a) - orderedSeverityLevels.indexOf(b)
}

function getDisplayTitle(
  title?: string,
  description?: string,
): string | undefined {
  if (title) return title
  if (description)
    return description.length > 100
      ? description.slice(0, 100) + "..."
      : description
  return undefined
}

async function handleDownloadPdf() {
  if (selectedTarget.value.length !== 1) return
  downloadingPdf.value = true
  try {
    const target = selectedTarget.value[0]
    const blob = await downloadTargetPdf(target)
    const safeName = target.replace(/[^a-zA-Z0-9._-]/g, "_")
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.download = `${safeName}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } finally {
    downloadingPdf.value = false
  }
}
</script>
