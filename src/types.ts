export const orderedSeverityLevels = [
  "CRITICAL",
  "HIGH",
  "MEDIUM",
  "LOW",
  "UNKNOWN",
] as const
export type Severity = (typeof orderedSeverityLevels)[number]

export type VulnerabilityReportFile = {
  SchemaVersion?: number
  Results: VulnerabilityReportTarget[]
}

export type Version1OrVersion2 =
  | VulnerabilityReportTarget[]
  | VulnerabilityReportFile

export type VulnerabilityReportTarget = {
  Target: string
  Type: string
  Vulnerabilities?: Vulnerability[]
}

export type Vulnerability = {
  id?: number | string
  Title?: string
  Description?: string
  PrimaryURL?: string
  PkgName: string
  VulnerabilityID: string
  Severity: Severity
  InstalledVersion?: string
  FixedVersion?: string
  Target: string
}

export type VulnerabilitySeverityInformation = {
  severity: Severity
  count: number
}

export type ReportError = {
  title: string
  text: string
  link?: { text: string; href: string }
}
