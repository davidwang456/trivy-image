import { expect, Locator, Page, Response } from "@playwright/test"
import * as path from "node:path"
import { fileURLToPath } from "node:url"

// Get current file path since __dirname is not available in ES modules
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export class HomePage {
  readonly page: Page
  readonly urlBtn: Locator
  readonly fileBtn: Locator

  constructor(page: Page) {
    this.page = page
    this.urlBtn = page.getByRole("button", { name: "Get file from URL" })
    this.fileBtn = page.getByRole("button", { name: "Upload a File" })
  }

  async goto(urlQueryParam?: string) {
    let url = "http://localhost:8080/#/"
    if (urlQueryParam) {
      url += `?url=${urlQueryParam}`
    }
    await this.page.goto(url)
  }

  async hasTitle(expectedTitle: string) {
    expect(await this.page.title()).toMatch(expectedTitle)
    expect(await this.page.getByRole("banner").textContent()).toBe(
      expectedTitle,
    )
  }

  async fetchTestFileFromUrl(url: string, waitForResponse = true) {
    await this.urlBtn.click()
    const fetchButton = this.page.getByRole("button", {
      name: /^Fetch$/,
    })
    expect(await fetchButton.isDisabled()).toBe(true)
    await this.page.getByRole("textbox", { name: "Url" }).fill(url)
    let responsePromise: Promise<Response> | undefined
    if (waitForResponse) {
      responsePromise = this.page.waitForResponse(url)
    }
    await fetchButton.click()
    if (responsePromise) {
      await responsePromise
    }
  }

  async uploadLocalTestFile(relativePath: string) {
    await this.fileBtn.click()
    const fileInput = this.page.locator("input[type=file]")
    const invalidFilePath = path.resolve(__dirname, relativePath)
    await fileInput.setInputFiles(invalidFilePath)
  }

  async verifyTableResult(amount: number) {
    expect(await this.page.locator("table").isVisible()).toBe(true)
    const rows = this.page.locator("table tr")
    // + 1 for "check all" row
    expect(await rows.count()).toBeGreaterThanOrEqual(amount + 1)
  }

  async verifyTrivyignore(cveEntries: string[]) {
    await Promise.all(
      cveEntries.map((cve) => {
        return this.toggleCheckedForRowWithTextContent(cve)
      }),
    )

    // check text field
    const generatedTrivyIgnore = await this.page
      .getByRole("textbox", { name: ".trivyignore" })
      .inputValue()
    cveEntries.forEach((cve) => {
      expect(generatedTrivyIgnore).toContain(cve)
    })

    // check clipboard
    await this.page
      .getByRole("button", { name: "Copy .trivyignore to Clipboard" })
      .click()
    await this.checkClipboardContents(cveEntries)
  }

  async setTargetFilter(target: string) {
    await this.page
      .getByRole("combobox", { name: "Select Target" })
      .fill(target)
  }

  async setInputFilter(input: string) {
    await this.page.locator("input[type=search]").fill(input)
  }

  async setSeverityFilter(severity: string) {
    await this.page.getByRole("button", { name: severity }).click()
  }

  async checkAllResults() {
    await this.page
      .locator('table tr th input[type="checkbox"]')
      .first()
      .check({ force: true })

    // Verify that all checkboxes in the table are checked
    const checkboxes = this.page.locator('table tr td input[type="checkbox"]')
    const count = await checkboxes.count()
    for (let i = 0; i < count; i++) {
      const isChecked = await checkboxes.nth(i).isChecked()
      if (!isChecked) {
        throw new Error(`Checkbox at index ${i} is not checked`)
      }
    }
  }

  async firstTablePage() {
    await this.page.getByRole("button", { name: "First page" }).click()
  }

  async previousTablePage() {
    await this.page.getByRole("button", { name: "Previous page" }).click()
  }

  async nextTablePage() {
    await this.page.getByRole("button", { name: "Next page" }).click()
  }

  async lastTablePage() {
    await this.page.getByRole("button", { name: "Last page" }).click()
  }

  async findTableRow(textContent: string) {
    const row = this.page.locator(`table tr:has-text("${textContent}")`).first()
    expect(await row.innerText()).toContain(textContent)
    return row
  }

  async expandOrCollapseTableRow(textContent: string) {
    const row = await this.findTableRow(textContent)
    await row.locator(".v-data-table__td--expanded-row button").click()
  }

  async verifyVulnerabilityDetails(
    title: string,
    description: string,
    url: string,
  ) {
    const detailsRow = this.page
      .locator(`table td:has-text("${title}")`)
      .first()
    expect(detailsRow.getByTitle(title)).toBeTruthy()
    expect(detailsRow.getByText(description)).toBeTruthy()
    expect(await detailsRow.getByRole("link").getAttribute("href")).toEqual(url)
  }

  async getErrorMessage() {
    return await this.page.locator("[role=alert].text-error").textContent()
  }

  private async checkClipboardContents(expectedContents: string[]) {
    const clipboardContent = await this.page.evaluate(() =>
      navigator.clipboard.readText(),
    )
    expectedContents.forEach((expectedContent) => {
      expect(clipboardContent).toContain(expectedContent)
    })
  }

  private async toggleCheckedForRowWithTextContent(textContent: string) {
    // Find the row containing the text content
    const row = await this.findTableRow(textContent)

    // Check if the row exists
    if ((await row.count()) > 0) {
      // Check the checkbox in the first cell of the row
      await this.page.waitForTimeout(300) // animation
      await row.locator('input[type="checkbox"]').check({ force: true })
    } else {
      throw new Error(`Row with text content "${textContent}" not found`)
    }
  }
}
