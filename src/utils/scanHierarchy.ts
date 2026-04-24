import type { ScanSummary } from "@/api/scan"

type MutableWindow = {
  createTimeMs: number
  updateTimeMs: number
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

function toMillis(value: string): number {
  const t = new Date(value).getTime()
  return Number.isNaN(t) ? 0 : t
}

function initWindow(scan: ScanSummary): MutableWindow {
  return {
    createTimeMs: toMillis(scan.createTime),
    updateTimeMs: toMillis(scan.updateTime),
    createBy: scan.createBy || "-",
    updateBy: scan.updateBy || "-",
    createTime: scan.createTime,
    updateTime: scan.updateTime,
  }
}

function mergeWindow(target: MutableWindow, scan: ScanSummary) {
  const c = toMillis(scan.createTime)
  const u = toMillis(scan.updateTime)
  if (c !== 0 && (target.createTimeMs === 0 || c < target.createTimeMs)) {
    target.createTimeMs = c
    target.createTime = scan.createTime
    target.createBy = scan.createBy || target.createBy
  }
  if (u !== 0 && u > target.updateTimeMs) {
    target.updateTimeMs = u
    target.updateTime = scan.updateTime
    target.updateBy = scan.updateBy || target.updateBy
  }
}

export type JobRow = {
  key: string
  jobId: string
  jobName: string
  jobType: "manual" | "cron"
  systemCount: number
  projectCount: number
  imageCount: number
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

export function aggregateJobs(scans: ScanSummary[]): JobRow[] {
  const map = new Map<
    string,
    {
      jobId: string
      jobName: string
      jobType: "manual" | "cron"
      systems: Set<string>
      projects: Set<string>
      imageCount: number
      w: MutableWindow
    }
  >()

  for (const s of scans) {
    const found = map.get(s.jobId)
    if (!found) {
      map.set(s.jobId, {
        jobId: s.jobId,
        jobName: s.jobName,
        jobType: s.jobType,
        systems: new Set([s.systemName]),
        projects: new Set([`${s.systemName}|${s.projectName}`]),
        imageCount: 1,
        w: initWindow(s),
      })
      continue
    }
    found.systems.add(s.systemName)
    found.projects.add(`${s.systemName}|${s.projectName}`)
    found.imageCount += 1
    mergeWindow(found.w, s)
  }

  return Array.from(map.values())
    .map((v) => ({
      key: v.jobId,
      jobId: v.jobId,
      jobName: v.jobName,
      jobType: v.jobType,
      systemCount: v.systems.size,
      projectCount: v.projects.size,
      imageCount: v.imageCount,
      createBy: v.w.createBy,
      updateBy: v.w.updateBy,
      createTime: v.w.createTime,
      updateTime: v.w.updateTime,
    }))
    .sort((a, b) => toMillis(b.updateTime) - toMillis(a.updateTime))
}

export type SystemRow = {
  key: string
  jobId: string
  jobName: string
  jobType: "manual" | "cron"
  systemName: string
  projectCount: number
  imageCount: number
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

export function aggregateSystems(
  scans: ScanSummary[],
  jobId?: string,
): SystemRow[] {
  const source = jobId ? scans.filter((s) => s.jobId === jobId) : scans
  const map = new Map<
    string,
    {
      jobId: string
      jobName: string
      jobType: "manual" | "cron"
      systemName: string
      projects: Set<string>
      imageCount: number
      w: MutableWindow
    }
  >()
  for (const s of source) {
    const key = `${s.jobId}|${s.systemName}`
    const found = map.get(key)
    if (!found) {
      map.set(key, {
        jobId: s.jobId,
        jobName: s.jobName,
        jobType: s.jobType,
        systemName: s.systemName,
        projects: new Set([s.projectName]),
        imageCount: 1,
        w: initWindow(s),
      })
      continue
    }
    found.projects.add(s.projectName)
    found.imageCount += 1
    mergeWindow(found.w, s)
  }
  return Array.from(map.values())
    .map((v) => ({
      key: `${v.jobId}|${v.systemName}`,
      jobId: v.jobId,
      jobName: v.jobName,
      jobType: v.jobType,
      systemName: v.systemName,
      projectCount: v.projects.size,
      imageCount: v.imageCount,
      createBy: v.w.createBy,
      updateBy: v.w.updateBy,
      createTime: v.w.createTime,
      updateTime: v.w.updateTime,
    }))
    .sort((a, b) => toMillis(b.updateTime) - toMillis(a.updateTime))
}

export type ProjectRow = {
  key: string
  jobId: string
  jobName: string
  jobType: "manual" | "cron"
  systemName: string
  projectName: string
  imageCount: number
  latestImageId: number
  latestImageRef: string
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

export function aggregateProjects(
  scans: ScanSummary[],
  jobId?: string,
  systemName?: string,
): ProjectRow[] {
  const source = scans.filter(
    (s) =>
      (!jobId || s.jobId === jobId) &&
      (!systemName || s.systemName === systemName),
  )
  const map = new Map<
    string,
    {
      jobId: string
      jobName: string
      jobType: "manual" | "cron"
      systemName: string
      projectName: string
      imageCount: number
      latestImageId: number
      latestImageRef: string
      latestUpdateMs: number
      w: MutableWindow
    }
  >()
  for (const s of source) {
    const key = `${s.jobId}|${s.systemName}|${s.projectName}`
    const found = map.get(key)
    if (!found) {
      map.set(key, {
        jobId: s.jobId,
        jobName: s.jobName,
        jobType: s.jobType,
        systemName: s.systemName,
        projectName: s.projectName,
        imageCount: 1,
        latestImageId: s.id,
        latestImageRef: s.imageRef,
        latestUpdateMs: toMillis(s.updateTime),
        w: initWindow(s),
      })
      continue
    }
    found.imageCount += 1
    const upd = toMillis(s.updateTime)
    if (upd >= found.latestUpdateMs) {
      found.latestUpdateMs = upd
      found.latestImageId = s.id
      found.latestImageRef = s.imageRef
    }
    mergeWindow(found.w, s)
  }
  return Array.from(map.values())
    .map((v) => ({
      key: `${v.jobId}|${v.systemName}|${v.projectName}`,
      jobId: v.jobId,
      jobName: v.jobName,
      jobType: v.jobType,
      systemName: v.systemName,
      projectName: v.projectName,
      imageCount: v.imageCount,
      latestImageId: v.latestImageId,
      latestImageRef: v.latestImageRef,
      createBy: v.w.createBy,
      updateBy: v.w.updateBy,
      createTime: v.w.createTime,
      updateTime: v.w.updateTime,
    }))
    .sort((a, b) => toMillis(b.updateTime) - toMillis(a.updateTime))
}
