import type {
  ReportError,
  Version1OrVersion2,
  VulnerabilityReportFile,
  VulnerabilityReportTarget,
} from "@/types"

function setInvalidFormatError(): ReportError {
  return {
    title: "Invalid report format",
    text: `The report cannot be parsed. Please make sure the report is in the correct format.`,
    link: {
      text: "For more information check the 'Version1OrVersion2' interface",
      href: "https://github.com/dbsystel/trivy-vulnerability-explorer/blob/main/src/types.ts",
    },
  }
}

function isSchemaVersion2(
  obj: Version1OrVersion2,
): obj is VulnerabilityReportFile {
  return !!(!Array.isArray(obj) && obj.SchemaVersion && obj.SchemaVersion >= 2)
}

export function extractTargetsFromReport(parsedReport: Version1OrVersion2): {
  targets: VulnerabilityReportTarget[]
  error?: ReportError
} {
  let vulnerabilityTargets: VulnerabilityReportTarget[]

  try {
    if (isSchemaVersion2(parsedReport)) {
      vulnerabilityTargets = parsedReport.Results
    } else {
      vulnerabilityTargets = parsedReport
    }

    if (
      !vulnerabilityTargets.every((i) => {
        i.Vulnerabilities = i.Vulnerabilities || []

        const targetValid =
          i.Target && i.Vulnerabilities && Array.isArray(i.Vulnerabilities)
        const vulnerabilitiesValid = i.Vulnerabilities?.every(
          // Some Trivy results legitimately omit Title/Description; those fields
          // are display-only and should not make the whole report invalid.
          (v) => v.PkgName && v.VulnerabilityID,
        )
        return targetValid && vulnerabilitiesValid
      })
    ) {
      return { targets: [], error: setInvalidFormatError() }
    }

    vulnerabilityTargets.forEach((vr) =>
      vr.Vulnerabilities?.forEach((v) => (v.Target = vr.Target)),
    )
    return { targets: vulnerabilityTargets }
  } catch (e: unknown) {
    console.error(e)
    return { targets: [], error: setInvalidFormatError() }
  }
}
