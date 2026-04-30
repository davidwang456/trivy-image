import { expect, Page, test } from "@playwright/test"

import { HomePage } from "./home-page"

const filenameSchemaVersion1 = "test-result-v1.json"
const filenameSchemaVersion2 = "test-result-v2.json"
const filenameTestManyResults = "test-many-results.json"
const cveEntries = ["CVE-2021-3450", "CVE-2021-3449", "CVE-2019-14697"]
const testReportUrl = `https://raw.githubusercontent.com/dbsystel/trivy-vulnerability-explorer/refs/heads/main/public/${filenameSchemaVersion2}`

test("should have the correct title", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.hasTitle("GOS Vulnerability Explorer")
})

test("fetches from URL", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.fetchTestFileFromUrl(testReportUrl)
  await homePage.verifyTableResult(5)
  await homePage.verifyTrivyignore(cveEntries)
})

test("fetches from URL passed as query param", async ({ page }) => {
  const homePage = new HomePage(page)
  const responsePromise = homePage.page.waitForResponse(testReportUrl)
  await homePage.goto(testReportUrl)
  await responsePromise

  await homePage.verifyTableResult(5)
  await homePage.verifyTrivyignore(cveEntries)
})

test("fetches from uploaded file (Schema version 1)", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion1}`)
  await homePage.verifyTableResult(5)
  await homePage.verifyTrivyignore(cveEntries)
})

test("fetches from uploaded file (Schema version 2)", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion2}`)
  await homePage.verifyTableResult(5)
  await homePage.verifyTrivyignore(cveEntries)
})

test("user can filter results by target", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion2}`)
  await homePage.setTargetFilter("dummy-image:1.0.2")
  await homePage.verifyTableResult(3)
  await homePage.setTargetFilter("dummy-image:1.0.5")
  await homePage.verifyTableResult(2)
})

test("user can filter results by input string", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion2}`)
  await homePage.setInputFilter("1.1.1d-r0")
  await homePage.verifyTableResult(1)
})

test("user can filter results by severity", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion2}`)
  await homePage.setSeverityFilter("LOW")
  await homePage.verifyTableResult(1)
  await homePage.setSeverityFilter("MEDIUM")
  await homePage.verifyTableResult(1)
  await homePage.setSeverityFilter("HIGH")
  await homePage.verifyTableResult(2)
  await homePage.setSeverityFilter("CRITICAL")
  await homePage.verifyTableResult(1)
})

test("user check all items", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameSchemaVersion2}`)
  await homePage.checkAllResults()
  await homePage.verifyTrivyignore(cveEntries)
})

test("pagination works as expected", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameTestManyResults}`)
  await homePage.verifyTableResult(20)

  homePage.findTableRow("CVE-0000-0000-1")
  homePage.findTableRow("CVE-0000-0000-20")

  await homePage.nextTablePage()
  homePage.findTableRow("CVE-0000-0000-21")
  homePage.findTableRow("CVE-0000-0000-40")

  await homePage.lastTablePage()
  homePage.findTableRow("CVE-0000-0000-41")
  homePage.findTableRow("CVE-0000-0000-53")

  await homePage.previousTablePage()
  homePage.findTableRow("CVE-0000-0000-21")
  homePage.findTableRow("CVE-0000-0000-40")

  await homePage.firstTablePage()
  homePage.findTableRow("CVE-0000-0000-1")
  homePage.findTableRow("CVE-0000-0000-20")
})

test("vulnerability details can be shown", async ({ page }) => {
  const homePage = new HomePage(page)
  await homePage.goto()
  await homePage.uploadLocalTestFile(`../public/${filenameTestManyResults}`)
  await homePage.expandOrCollapseTableRow("CVE-0000-0000-3")
  await homePage.verifyVulnerabilityDetails(
    "my-lib3.0: a serious vulnerability",
    "Some content should be here",
    "https://example.org",
  )
})

test.describe.parallel("Error handling", () => {
  test("Error message for files with wrong format is shown", async ({
    page,
  }) => {
    const homePage = new HomePage(page)
    await homePage.goto()

    await homePage.uploadLocalTestFile(
      "../public/test-invalid-report-format.json",
    )
    let message = await homePage.getErrorMessage()
    expect(message).toContain("Invalid report format")
    expect(message).toContain(
      "The cannot be parsed. Please make sure the report is in the correct format.",
    )

    await homePage.uploadLocalTestFile(
      "../public/test-invalid-vulnerability-format.json",
    )
    message = await homePage.getErrorMessage()
    expect(message).toContain("Invalid report format")
    expect(message).toContain(
      "The cannot be parsed. Please make sure the report is in the correct format.",
    )
  })

  test("Error message for a network error is shown", async ({ page }) => {
    const homePage = new HomePage(page)
    await homePage.goto()

    const url = `http://localhost:8080/my-non-existing-file.json`

    await homePage.page.route(url, (route) => route.abort("failed"))
    await homePage.fetchTestFileFromUrl(url, false)

    const message = await homePage.getErrorMessage()
    expect(message).toContain("Error when fetching report")
    expect(message).toContain("Failed to fetch")
  })

  test("Error message for an invalid JSON file is shown", async ({ page }) => {
    const homePage = new HomePage(page)
    await homePage.goto()

    const url = `index.html`

    await homePage.page.route(url, (route) => route.abort("failed"))
    await homePage.fetchTestFileFromUrl(url, false)

    const message = await homePage.getErrorMessage()
    expect(message).toContain("Error when fetching report")
    expect(message).toContain("is not valid JSON")
  })

  test("Error message for a non 200 OK status is shown", async ({ page }) => {
    const homePage = new HomePage(page)
    await homePage.goto()

    const url = `http://localhost:8080/my-non-existing-file.json`

    await homePage.page.route(url, (route) => route.fulfill({ status: 404 }))
    await homePage.fetchTestFileFromUrl(url, false)

    const message = await homePage.getErrorMessage()
    expect(message).toContain("Response not successful")
    expect(message).toContain(
      "Did not receive Response status 200 OK. Got 404 Not Found",
    )
  })
})
