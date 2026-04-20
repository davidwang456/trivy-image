<template>
  <v-toolbar color="transparent">
    <v-text-field
      v-model="url"
      label="Url"
      hide-details
      class="mr-3"
      :loading="state === 'loading'"
    />
    <v-dialog v-model="dialog" persistent max-width="600px">
      <template v-slot:activator="{ props: activatorProps }">
        <v-btn
          color="primary"
          icon
          v-bind="activatorProps"
          variant="tonal"
          aria-label="Add Authorization Header"
        >
          <v-icon>mdi-shield-lock</v-icon>
        </v-btn>
      </template>
      <v-card>
        <v-card-title>
          <span class="text-h5">Authorization</span>
        </v-card-title>
        <v-card-text>
          <v-container>
            <v-row>
              <v-col cols="12" sm="6" md="4">
                <v-text-field
                  v-model="headerName"
                  label="Header Name"
                ></v-text-field>
              </v-col>
              <v-col cols="12" sm="6" md="4">
                <v-text-field
                  v-model="headerValue"
                  label="Header Value"
                ></v-text-field>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                The data will be stored in the localStorage of your Browser and
                is added as a header to the fetch call. If you want to fetch a
                report from a
                <a
                  href="https://docs.gitlab.com/ee/api/job_artifacts.html#download-a-single-artifact-file-by-job-id"
                  target="_blank"
                  >GitLab Job</a
                >
                , you need to add the PRIVATE-TOKEN header and set it to a
                <a
                  href="https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html"
                  >personal access token</a
                >
                with the scope read_api.
              </v-col>
            </v-row>
          </v-container>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            color="blue darken-1"
            variant="text"
            @click="saveAuthorization"
          >
            Save&Close
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-btn
      :loading="state === 'loading'"
      :disabled="state === 'loading' || !url"
      variant="flat"
      @click="fetchReportFromUrl"
      class="mx-2"
      :color="state === 'error' ? 'error' : 'primary'"
    >
      Fetch
    </v-btn>
  </v-toolbar>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue"
import { useLocalStorage } from "@vueuse/core"
import type { ReportError, Version1OrVersion2 } from "@/types"

const props = defineProps<{
  onNewReport: (
    report: Version1OrVersion2 | { error: ReportError },
  ) => void | Promise<void>
  presetUrl?: string
}>()

const url = ref("")
const state = ref("ready")
const dialog = ref(false)
const headerName = useLocalStorage("headerName", "")
const headerValue = useLocalStorage("headerValue", "")

onMounted(() => {
  if (props.presetUrl) {
    url.value = props.presetUrl
    fetchReportFromUrl()
  }
})

const saveAuthorization = () => {
  dialog.value = false
}

const fetchReportFromUrl = async () => {
  if (url.value) {
    state.value = "loading"
    const headers: Record<string, string> = {}
    if (headerName.value && headerValue.value) {
      headers[headerName.value] = headerValue.value
    }
    try {
      const response = await fetch(url.value, { headers })
      state.value = "ready"
      if (response.ok) {
        const report = await response.json()
        await props.onNewReport(report)
        state.value = "success"
      } else {
        props.onNewReport({
          error: {
            title: "Response not successful",
            text: `Did not receive Response status 200 OK. Got ${response.status} ${response.statusText}`,
          },
        })
        state.value = "error"
      }
    } catch (error: unknown) {
      props.onNewReport({
        error: {
          title: "Error when fetching report",
          text: error instanceof Error ? error.message : String(error),
        },
      })
      state.value = "error"
    }
  }
}
</script>
