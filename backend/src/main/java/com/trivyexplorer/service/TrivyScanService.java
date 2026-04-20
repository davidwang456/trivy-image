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
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
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
  public ScanResponse scanAndStore(String imageRef, String registryUsername, String registryPassword)
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

      ImageScan entity = new ImageScan();
      entity.setImageRef(imageRef.trim());
      entity.setReportJson(jsonBytes);
      ImageScan saved = imageScanRepository.save(entity);

      return new ScanResponse(
          saved.getId(),
          saved.getImageRef(),
          root,
          saved.getCreateTime(),
          saved.getUpdateTime());
    } finally {
      Files.deleteIfExists(output);
    }
  }

  private static String truncate(String s, int maxLen) {
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen) + "...";
  }
}
