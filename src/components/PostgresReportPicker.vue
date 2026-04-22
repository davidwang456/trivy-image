<template>
  <div>
    <p class="text-body-2 text-medium-emphasis mb-4">
      Reports saved by Repository Scan. Search by image name (max 200). Requires
      API and PostgreSQL.
    </p>
    <v-autocomplete
      v-model="selectedId"
      :items="items"
      item-title="title"
      item-value="value"
      :loading="loading"
      clearable
      hide-no-data
      no-filter
      label="Saved image reports"
      placeholder="Search or pick from the list"
      prepend-inner-icon="mdi-database-search"
      variant="solo-filled"
      flat
      density="comfortable"
      hide-details="auto"
      class="mb-2"
      @update:search="onSearchInput"
      @update:model-value="onSelect"
    />
    <v-alert
      v-if="localError"
      color="error"
      icon="$error"
      variant="outlined"
      density="compact"
      class="mt-2"
    >
      {{ localError }}
    </v-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { useDebounceFn } from "@vueuse/core"
import { getScanById, listScans } from "@/api/scan"
import type { Vulnerability } from "@/types"
import { extractTargetsFromReport } from "@/utils/trivyReport"

const emit = defineEmits<{
  reportLoaded: [payload: { vulnerabilities: Vulnerability[]; scanId: number }]
}>()

const selectedId = ref<number | undefined>()
const items = ref<{ title: string; value: number }[]>([])
const loading = ref(false)
const localError = ref<string>()

function formatItem(s: { id: number; imageRef: string; createTime: string }) {
  const d = new Date(s.createTime)
  const ds = Number.isNaN(d.getTime()) ? s.createTime : d.toLocaleString()
  return {
    title: `${s.imageRef} — ${ds}`,
    value: s.id,
  }
}

async function fetchList(q?: string) {
  loading.value = true
  localError.value = undefined
  try {
    const list = await listScans(q)
    items.value = list.map(formatItem)
  } catch (e) {
    localError.value = e instanceof Error ? e.message : String(e)
    items.value = []
  } finally {
    loading.value = false
  }
}

const debouncedFetch = useDebounceFn((val: string) => {
  void fetchList(val || undefined)
}, 300)

function onSearchInput(val: string) {
  debouncedFetch(val)
}

onMounted(() => {
  void fetchList()
})

async function onSelect(id: number | null | undefined) {
  if (id == null) {
    return
  }
  loading.value = true
  localError.value = undefined
  try {
    const res = await getScanById(id)
    const { targets, error } = extractTargetsFromReport(res.report)
    if (error) {
      localError.value = error.text
      return
    }
    const flat = targets
      .filter((vr) => vr.Vulnerabilities)
      .flatMap((vr) => vr.Vulnerabilities!) as Vulnerability[]

    const unique = Array.from(
      new Map(
        flat.map((v) => [
          [
            v.Target ?? "",
            v.VulnerabilityID ?? "",
            v.PkgName ?? "",
            v.InstalledVersion ?? "",
            v.FixedVersion ?? "",
          ].join("|"),
          v,
        ]),
      ).values(),
    )

    emit("reportLoaded", { vulnerabilities: unique, scanId: id })
  } catch (e) {
    localError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}
</script>
