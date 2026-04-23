package com.trivyexplorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivyexplorer.domain.ImageScan;
import com.trivyexplorer.repo.ImageScanRepository;
import com.trivyexplorer.web.dto.ImportScanRequest;
import com.trivyexplorer.web.dto.ImageScanSummary;
import com.trivyexplorer.web.dto.ScanDashboardNode;
import com.trivyexplorer.web.dto.ScanResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  public ScanResponse importAndStore(ImportScanRequest request, String operator) {
    JsonNode report = request.getReport();
    try {
      byte[] jsonBytes = objectMapper.writeValueAsBytes(report);
      String imageRef = resolveImageRef(request.getImageRef(), report);
      TrivyScanService.ImageHierarchy hierarchy = TrivyScanService.resolveHierarchy(imageRef);

      Optional<ImageScan> existing = imageScanRepository.findByImageRef(imageRef);
      ImageScan entity;
      if (existing.isPresent()) {
        entity = existing.get();
        //entity.setSystemName(hierarchy.systemName());
        //entity.setProjectName(hierarchy.projectName());
        entity.setJobName(entity.getSystemName() + "/" + entity.getProjectName());
        entity.setJobType(TrivyScanService.JOB_TYPE_MANUAL);
        entity.setUpdateBy(operator);
        entity.setReportJson(jsonBytes);
      } else {
        entity = new ImageScan();
        entity.setImageRef(imageRef);
        entity.setJobId(UUID.randomUUID().toString());
        entity.setSystemName(hierarchy.systemName());
        entity.setProjectName(hierarchy.projectName());
        entity.setJobName(entity.getSystemName() + "/" + entity.getProjectName());
        entity.setJobType(TrivyScanService.JOB_TYPE_MANUAL);
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        entity.setReportJson(jsonBytes);
      }
      ImageScan saved = imageScanRepository.save(entity);

      return new ScanResponse(
          saved.getId(),
          saved.getImageRef(),
          saved.getJobId(),
          saved.getJobName(),
          saved.getSystemName(),
          saved.getProjectName(),
          saved.getJobType(),
          saved.getCreateBy(),
          saved.getUpdateBy(),
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
          entity.getJobId(),
          entity.getJobName(),
          entity.getSystemName(),
          entity.getProjectName(),
          entity.getJobType(),
          entity.getCreateBy(),
          entity.getUpdateBy(),
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

  @Transactional(readOnly = true)
  public List<ScanDashboardNode> dashboard() {
    List<ImageScan> scans = imageScanRepository.findAllByOrderByCreateTimeDesc();
    Map<String, JobAcc> jobs = new LinkedHashMap<>();
    for (ImageScan scan : scans) {
      JobAcc job =
          jobs.computeIfAbsent(
              scan.getJobId(),
              jobId ->
                  new JobAcc(
                      scan.getJobId(),
                      scan.getJobName(),
                      scan.getCreateTime(),
                      scan.getUpdateTime(),
                      new LinkedHashMap<>()));
      if (scan.getCreateTime() != null
          && (job.createTime == null || scan.getCreateTime().isBefore(job.createTime))) {
        job.createTime = scan.getCreateTime();
      }
      if (scan.getUpdateTime() != null
          && (job.updateTime == null || scan.getUpdateTime().isAfter(job.updateTime))) {
        job.updateTime = scan.getUpdateTime();
      }

      SystemAcc system =
          job.systems.computeIfAbsent(scan.getSystemName(), key -> new SystemAcc(new LinkedHashMap<>()));
      List<ScanDashboardNode.ImageNode> images =
          system.projects.computeIfAbsent(scan.getProjectName(), key -> new ArrayList<>());
      images.add(
          new ScanDashboardNode.ImageNode(
              scan.getId(),
              scan.getImageRef(),
              scan.getCreateTime(),
              scan.getUpdateTime(),
              scan.getJobId(),
              scan.getJobName()));
    }

    return jobs.values().stream()
        .map(
            job ->
                new ScanDashboardNode(
                    job.jobId,
                    job.jobName,
                    job.systems.entrySet().stream()
                        .map(
                            system ->
                                new ScanDashboardNode.SystemNode(
                                    system.getKey(),
                                    system.getValue().projects.entrySet().stream()
                                        .map(
                                            project ->
                                                new ScanDashboardNode.ProjectNode(
                                                    project.getKey(),
                                                    project.getValue().stream()
                                                        .sorted(
                                                            Comparator.comparing(
                                                                    ScanDashboardNode.ImageNode::createTime,
                                                                    Comparator.nullsLast(
                                                                        Comparator.reverseOrder()))
                                                                .thenComparing(
                                                                    ScanDashboardNode.ImageNode::id))
                                                        .toList()))
                                        .toList()))
                        .toList(),
                    job.createTime,
                    job.updateTime))
        .toList();
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

  private static final class JobAcc {
    private final String jobId;
    private final String jobName;
    private Instant createTime;
    private Instant updateTime;
    private final Map<String, SystemAcc> systems;

    private JobAcc(
        String jobId,
        String jobName,
        Instant createTime,
        Instant updateTime,
        Map<String, SystemAcc> systems) {
      this.jobId = jobId;
      this.jobName = jobName;
      this.createTime = createTime;
      this.updateTime = updateTime;
      this.systems = systems;
    }
  }

  private static final class SystemAcc {
    private final Map<String, List<ScanDashboardNode.ImageNode>> projects;

    private SystemAcc(Map<String, List<ScanDashboardNode.ImageNode>> projects) {
      this.projects = projects;
    }
  }
}
