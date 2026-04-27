package com.trivyexplorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivyexplorer.domain.ImageScan;
import com.trivyexplorer.repo.ImageScanRepository;
import com.trivyexplorer.web.dto.ScanResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Runs the Trivy CLI via {@link ProcessBuilder}. Intended for Linux hosts where {@code trivy} is
 * installed and on {@code PATH}, or set {@code trivy.executable} to an absolute path (e.g. {@code
 * /usr/local/bin/trivy}).
 */
@Service
public class TrivyScanService {
  public static final String JOB_TYPE_MANUAL = "manual";
  public static final String JOB_TYPE_CRON = "cron";

  private final ImageScanRepository imageScanRepository;
  private final ObjectMapper objectMapper;
  private final String trivyExecutable;
  private final long trivyTimeoutSeconds;

  public TrivyScanService(
      ImageScanRepository imageScanRepository,
      ObjectMapper objectMapper,
      @Value("${trivy.executable:trivy}") String trivyExecutable,
      @Value("${trivy.timeout-seconds:600}") long trivyTimeoutSeconds) {
    this.imageScanRepository = imageScanRepository;
    this.objectMapper = objectMapper;
    this.trivyExecutable = trivyExecutable;
    this.trivyTimeoutSeconds = trivyTimeoutSeconds;
  }

  @Transactional
  public ScanResponse scanAndStore(
      String imageRef, String registryUsername, String registryPassword, String operator)
      throws IOException, InterruptedException {
    ImageHierarchy hierarchy = resolveHierarchy(imageRef);
    ScanMetadata metadata =
        new ScanMetadata(
            UUID.randomUUID().toString(),
            buildJobName(hierarchy.systemName(), hierarchy.projectName()),
            hierarchy.systemName(),
            hierarchy.projectName());
    return scanAndStore(
        imageRef, registryUsername, registryPassword, metadata, operator, JOB_TYPE_MANUAL);
  }

  @Transactional
  public ScanResponse scanAndStore(
      String imageRef,
      String registryUsername,
      String registryPassword,
      String operator,
      String jobType)
      throws IOException, InterruptedException {
    ImageHierarchy hierarchy = resolveHierarchy(imageRef);
    ScanMetadata metadata =
        new ScanMetadata(
            UUID.randomUUID().toString(),
            buildJobName(hierarchy.systemName(), hierarchy.projectName()),
            hierarchy.systemName(),
            hierarchy.projectName());
    return scanAndStore(imageRef, registryUsername, registryPassword, metadata, operator, jobType);
  }

  @Transactional
  public ScanResponse scanAndStore(
      String imageRef,
      String registryUsername,
      String registryPassword,
      ScanMetadata metadata,
      String operator,
      String jobType)
      throws IOException, InterruptedException {
    Path output = Files.createTempFile("trivy-report-", ".json");
    try {
      List<String> command = new ArrayList<>();
      command.add(trivyExecutable);
      command.add("image");
      command.add("--format");
      command.add("json");
      command.add("--output");
      command.add(output.toAbsolutePath().toString());
      if (StringUtils.hasText(registryUsername)) {
        command.add("--username");
        command.add(registryUsername);
      }
      if (StringUtils.hasText(registryPassword)) {
        command.add("--password");
        command.add(registryPassword);
      }
      command.add(imageRef.trim());

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String combined =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      boolean finished = process.waitFor(trivyTimeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException(
            "Trivy timed out after " + trivyTimeoutSeconds + " seconds.");
      }
      int exit = process.exitValue();
      if (exit != 0) {
        String msg =
            combined.isBlank()
                ? ("Trivy exited with code " + exit)
                : ("Trivy failed: " + truncate(combined, 8000));
        throw new IllegalStateException(msg);
      }

      byte[] jsonBytes = Files.readAllBytes(output);
      JsonNode root = objectMapper.readTree(jsonBytes);

      String ref = imageRef.trim();
      ImageScan saved = upsertScanByImageRef(ref, metadata, operator, jobType, jsonBytes);

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
          root,
          saved.getCreateTime(),
          saved.getUpdateTime());
    } finally {
      Files.deleteIfExists(output);
    }
  }

  private ImageScan upsertScanByImageRef(
      String imageRef, ScanMetadata metadata, String operator, String jobType, byte[] jsonBytes) {
    try {
      ImageScan entity =
          imageScanRepository
              .findByImageRef(imageRef)
              .map(existing -> applyScanFields(existing, metadata, operator, jobType, jsonBytes))
              .orElseGet(
                  () -> {
                    ImageScan created = new ImageScan();
                    created.setImageRef(imageRef);
                    created.setCreateBy(operator);
                    return applyScanFields(created, metadata, operator, jobType, jsonBytes);
                  });
      return imageScanRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException e) {
      // Another concurrent transaction inserted the same imageRef first.
      ImageScan existing =
          imageScanRepository
              .findByImageRef(imageRef)
              .orElseThrow(() -> new IllegalStateException("Failed to upsert image scan: " + imageRef, e));
      applyScanFields(existing, metadata, operator, jobType, jsonBytes);
      return imageScanRepository.saveAndFlush(existing);
    }
  }

  private static ImageScan applyScanFields(
      ImageScan entity, ScanMetadata metadata, String operator, String jobType, byte[] jsonBytes) {
    entity.setJobId(metadata.jobId());
    entity.setJobName(metadata.jobName());
    entity.setSystemName(metadata.systemName());
    entity.setProjectName(metadata.projectName());
    entity.setJobType(normalizeJobType(jobType));
    entity.setUpdateBy(operator);
    entity.setReportJson(jsonBytes);
    return entity;
  }

  private static String truncate(String s, int maxLen) {
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen) + "...";
  }

  public ScanMetadata buildBatchMetadata(List<String> imageRefs) {
    ImageHierarchy hierarchy =
        imageRefs.stream()
            .findFirst()
            .map(TrivyScanService::resolveHierarchy)
            .orElse(new ImageHierarchy("default-system", "default-project"));
    return new ScanMetadata(
        UUID.randomUUID().toString(),
        buildJobName(hierarchy.systemName(), hierarchy.projectName()),
        hierarchy.systemName(),
        hierarchy.projectName());
  }

  private static String buildJobName(String systemName, String projectName) {
    return systemName + "/" + projectName;
  }

  private static String normalizeJobType(String jobType) {
    if (JOB_TYPE_CRON.equalsIgnoreCase(jobType)) {
      return JOB_TYPE_CRON;
    }
    return JOB_TYPE_MANUAL;
  }

  public static ImageHierarchy resolveHierarchy(String imageRef) {
    if (!StringUtils.hasText(imageRef)) {
      return new ImageHierarchy("default-system", "default-project");
    }
    String normalized = imageRef.trim();
    int atIndex = normalized.indexOf('@');
    if (atIndex > 0) {
      normalized = normalized.substring(0, atIndex);
    }
    int colonIndex = normalized.lastIndexOf(':');
    int slashIndex = normalized.lastIndexOf('/');
    if (colonIndex > slashIndex) {
      normalized = normalized.substring(0, colonIndex);
    }
    String[] parts = normalized.split("/");
    if (parts.length == 0) {
      return new ImageHierarchy("default-system", "default-project");
    }

    boolean hasRegistryHost = parts[0].contains(".") || parts[0].contains(":");
    if (hasRegistryHost) {
      String systemName = parts.length >= 2 ? parts[1] : "default-system";
      String projectName = parts.length >= 3 ? parts[2] : "default-project";
      return new ImageHierarchy(systemName, projectName);
    }
    String systemName = parts.length >= 1 ? parts[0] : "default-system";
    String projectName = parts.length >= 2 ? parts[1] : "default-project";
    return new ImageHierarchy(systemName, projectName);
  }

  public record ScanMetadata(String jobId, String jobName, String systemName, String projectName) {}

  public record ImageHierarchy(String systemName, String projectName) {}
}
