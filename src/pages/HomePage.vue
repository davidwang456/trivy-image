<template>
  <div>
    <v-container class="py-8">
      <v-stepper-vertical flat hide-actions editable>
        <template v-slot:default="{ next }">
          <v-stepper-vertical-item
            :complete="reportLoaded"
            title="Import"
            subtitle="Upload or fetch report data"
            value="1"
          >
            <h2 class="headline">Instructions</h2>
            <p class="mb-8">
              Select a JSON Report from
              <a href="https://github.com/aquasecurity/trivy" target="_blank"
                >Trivy</a
              >
              from your local file system. You may limit the displayed
              vulnerabilities to a single target. If you need, select the
              Vulnerabilities that you want to ignore/accept and use the
              .trivyignore output below for further processing in your pipeline.
              Imported reports are also saved to PostgreSQL through the backend
              API so you can later load them from the PostgreSQL data source.
            </p>
            <DataInput
              @inputChanged="reactivelySetNewVulnerabilities($event, next)"
              :presetUrl="presetUrl"
            />
          </v-stepper-vertical-item>

          <v-stepper-vertical-item
            title="Explore"
            subtitle="Checkout the report results"
            value="2"
          >
            <DataTable
              v-if="reportLoaded"
              :selectedVulnerabilities="selectedVulnerabilities"
              :scanId="selectedScanId"
            ></DataTable>
            <v-row v-else class="my-2"
              ><v-col>
                <v-alert border="top" border-color="info" elevation="2">
                  There is no data to display yet. Try to load a trivy report by
                  using the file uploader above.
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
import { ref } from "vue"
import DataInput from "@/components/DataInput.vue"
import DataTable from "@/components/DataTable.vue"
import type { Vulnerability } from "@/types"
import {
  VStepperVertical,
  VStepperVerticalItem,
} from "vuetify/labs/VStepperVertical"

const selectedVulnerabilities = ref<Vulnerability[]>([])
const selectedScanId = ref<number>()
const reportLoaded = ref(false)
const query = useRoute().query
const presetUrl = (Array.isArray(query.url) ? query.url[0] : query.url) as
  | string
  | undefined

function reactivelySetNewVulnerabilities(
  payload: { vulnerabilities: Vulnerability[]; scanId?: number },
  nextStep: () => void,
) {
  selectedVulnerabilities.value.splice(0)
  payload.vulnerabilities.forEach(
    (item: Vulnerability, index: number) => (item.id = index),
  )
  selectedVulnerabilities.value.push(...payload.vulnerabilities)
  selectedScanId.value = payload.scanId
  reportLoaded.value = true
  nextStep()
}
</script>
