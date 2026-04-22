package com.trivyexplorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivyexplorer.domain.ImageScan;
import com.trivyexplorer.repo.ImageScanRepository;
import com.trivyexplorer.web.dto.ImportScanRequest;
import com.trivyexplorer.web.dto.ImageScanSummary;
import com.trivyexplorer.web.dto.ScanResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoredScanService {

  private static final int LIST_MAX = 200;

  private final ImageScanRepository imageScanRepository;
  private final ObjectMapper objectMapper;

  public StoredScanService(
      ImageScanRepository imageScanRepository, ObjectMapper objectMapper) {
    this.imageScanRepository = imageScanRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<ImageScanSummary> listSummaries(String q) {
    Pageable pageable = PageRequest.of(0, LIST_MAX);
    if (StringUtils.hasText(q)) {
      return imageScanRepository.findSummariesByImageRef(q.trim(), pageable);
    }
    return imageScanRepository.findAllSummaries(pageable);
  }

  @Transactional
  public ScanResponse importAndStore(ImportScanRequest request) {
    JsonNode report = request.getReport();
    try {
      byte[] jsonBytes = objectMapper.writeValueAsBytes(report);
      String imageRef = resolveImageRef(request.getImageRef(), report);

      ImageScan entity = new ImageScan();
      entity.setImageRef(imageRef);
      entity.setReportJson(jsonBytes);
      ImageScan saved = imageScanRepository.save(entity);

      return new ScanResponse(
          saved.getId(),
          saved.getImageRef(),
          report,
          saved.getCreateTime(),
          saved.getUpdateTime());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize report JSON", e);
    }
  }

  @Transactional(readOnly = true)
  public ScanResponse getById(Long id) {
    ImageScan entity =
        imageScanRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scan not found: " + id));
    try {
      JsonNode root = objectMapper.readTree(entity.getReportJson());
      return new ScanResponse(
          entity.getId(),
          entity.getImageRef(),
          root,
          entity.getCreateTime(),
          entity.getUpdateTime());
    } catch (IOException e) {
      throw new IllegalStateException("Stored report JSON is invalid", e);
    }
  }

  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> exportPdfByScanId(Long scanId) {
    ImageScan latestScan =
        imageScanRepository
            .findById(scanId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scan not found: " + scanId));

    JsonNode matchedReport;
    try {
      matchedReport = objectMapper.readTree(latestScan.getReportJson());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored report JSON is invalid");
    }

    Path inputJson = null;
    Path outputPdf = null;
    try {
      inputJson = Files.createTempFile("trivy-target-", ".json");
      outputPdf = Files.createTempFile("trivy-target-", ".pdf");
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(inputJson.toFile(), matchedReport);

      Path scriptPath = Path.of("libs", "change.sh");
      if (!Files.exists(scriptPath)) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "PDF conversion script not found: " + scriptPath);
      }

      Process process =
          new ProcessBuilder("bash", scriptPath.toString(), inputJson.toString(), outputPdf.toString())
              .directory(Path.of(".").toFile())
              .redirectErrorStream(true)
              .start();
      int exitCode = process.waitFor();
      if (exitCode != 0 || !Files.exists(outputPdf) || Files.size(outputPdf) == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "PDF conversion failed. Ensure libs/change.sh writes PDF to output path.");
      }

      byte[] bytes = Files.readAllBytes(outputPdf);
      String fileBase = latestScan.getImageRef();
      String safeName = fileBase.replaceAll("[^a-zA-Z0-9._-]", "_");
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header("Content-Disposition", "attachment; filename=\"" + safeName + ".pdf\"")
          .body(bytes);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to prepare export files: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "PDF conversion interrupted");
    } finally {
      if (inputJson != null) {
        try {
          Files.deleteIfExists(inputJson);
        } catch (IOException ignored) {
        }
      }
      if (outputPdf != null) {
        try {
          Files.deleteIfExists(outputPdf);
        } catch (IOException ignored) {
        }
      }
    }
  }

  private String resolveImageRef(String requestedImageRef, JsonNode report) {
    if (StringUtils.hasText(requestedImageRef)) {
      return requestedImageRef.trim();
    }

    String artifactName = textValue(report.get("ArtifactName"));
    if (StringUtils.hasText(artifactName)) {
      return artifactName;
    }

    String metadataImageId = textValue(report.path("Metadata").path("ImageID"));
    if (StringUtils.hasText(metadataImageId)) {
      return metadataImageId;
    }

    JsonNode results = report.get("Results");
    if (results != null && results.isArray()) {
      for (JsonNode result : results) {
        String target = textValue(result.get("Target"));
        if (StringUtils.hasText(target)) {
          return target;
        }
      }
    }

    if (report.isArray()) {
      for (JsonNode item : report) {
        String target = textValue(item.get("Target"));
        if (StringUtils.hasText(target)) {
          return target;
        }
      }
    }

    return "imported-report-" + System.currentTimeMillis();
  }

  private static String textValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
