package com.trivyexplorer.web;

import com.trivyexplorer.domain.RegistryDatasource;
import com.trivyexplorer.service.RegistryDatasourceService;
import com.trivyexplorer.service.StoredScanService;
import com.trivyexplorer.service.TrivyScanService;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.BatchScanRequest;
import com.trivyexplorer.web.dto.ImportScanRequest;
import com.trivyexplorer.web.dto.ImageScanSummary;
import com.trivyexplorer.web.dto.ScanDashboardNode;
import com.trivyexplorer.web.dto.ScanRequest;
import com.trivyexplorer.web.dto.ScanResponse;
import com.trivyexplorer.web.dto.ScanDailyStatResponse;
import com.trivyexplorer.web.dto.ScanStatsResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scans")
public class ScanController {
  private static final int SCAN_THREAD_POOL_SIZE = 20;

  private final TrivyScanService trivyScanService;
  private final StoredScanService storedScanService;
  private final RegistryDatasourceService registryDatasourceService;

  public ScanController(
      TrivyScanService trivyScanService,
      StoredScanService storedScanService,
      RegistryDatasourceService registryDatasourceService) {
    this.trivyScanService = trivyScanService;
    this.storedScanService = storedScanService;
    this.registryDatasourceService = registryDatasourceService;
  }

  @GetMapping
  public List<ImageScanSummary> list(@RequestParam(required = false) String q) {
    return storedScanService.listSummaries(q);
  }

  @GetMapping("/stats")
  public ScanStatsResponse scanStats() {
    return storedScanService.scanStats();
  }

  @GetMapping("/stats/daily")
  public List<ScanDailyStatResponse> scanStatsDaily() {
    return storedScanService.dailyTrendLast7Days();
  }

  @GetMapping("/{id}")
  public ScanResponse getById(@PathVariable Long id) {
    return storedScanService.getById(id);
  }

  @GetMapping("/dashboard")
  public List<ScanDashboardNode> dashboard() {
    return storedScanService.dashboard();
  }

  @GetMapping("/{id}/export/pdf")
  public ResponseEntity<byte[]> exportPdfByScanId(@PathVariable Long id) {
    return storedScanService.exportPdfByScanId(id);
  }

  @PostMapping("/import")
  public ResponseEntity<ScanResponse> importReport(
      @Valid @RequestBody ImportScanRequest request, HttpSession session) {
    String operator = requireAuthenticatedUsername(session);
    return ResponseEntity.ok(storedScanService.importAndStore(request, operator));
  }

  @PostMapping
  public ResponseEntity<List<ScanResponse>> scan(
      @Valid @RequestBody ScanRequest request, HttpSession session) {
    String operator = requireAuthenticatedUsername(session);
    RegistryDatasource ds = registryDatasourceService.getById(request.getDatasourceId());
    List<String> imageRefs =
        List.of(request.getImageRef().split(",")).stream()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    if (imageRefs.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageRef must not be blank");
    }
    return ResponseEntity.ok(scanImagesConcurrently(imageRefs, ds, operator));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<ScanResponse>> batchScan(
      @Valid @RequestBody BatchScanRequest request, HttpSession session)
      throws IOException, InterruptedException {
    requireAdmin(session);
    String operator = requireAuthenticatedUsername(session);
    RegistryDatasource ds = registryDatasourceService.getById(request.getDatasourceId());
    List<ScanResponse> result = new ArrayList<>();
    TrivyScanService.ScanMetadata metadata = trivyScanService.buildBatchMetadata(request.getImageRefs());
    for (String imageRef : request.getImageRefs()) {
      result.add(
          trivyScanService.scanAndStore(
              imageRef,
              ds.getUsername(),
              ds.getPassword(),
              metadata,
              operator,
              TrivyScanService.JOB_TYPE_MANUAL));
    }
    return ResponseEntity.ok(result);
  }

  private static void requireAdmin(HttpSession session) {
    Object role = session.getAttribute(AuthController.SESSION_ROLE);
    if (!(role instanceof String) || !UserService.ROLE_ADMIN.equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  private static String requireAuthenticatedUsername(HttpSession session) {
    Object username = session.getAttribute(AuthController.SESSION_USERNAME);
    if (!(username instanceof String s) || s.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    return s.trim();
  }

  private List<ScanResponse> scanImagesConcurrently(
      List<String> imageRefs, RegistryDatasource ds, String operator) {
    String sharedJobId = UUID.randomUUID().toString();
    ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREAD_POOL_SIZE);
    try {
      List<CompletableFuture<ScanResponse>> tasks =
          imageRefs.stream()
              .map(
                  imageRef ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            TrivyScanService.ImageHierarchy hierarchy =
                                TrivyScanService.resolveHierarchy(imageRef);
                            TrivyScanService.ScanMetadata metadata =
                                new TrivyScanService.ScanMetadata(
                                    sharedJobId,
                                    hierarchy.systemName() + "/" + hierarchy.projectName(),
                                    hierarchy.systemName(),
                                    hierarchy.projectName());
                            try {
                              return trivyScanService.scanAndStore(
                                  imageRef,
                                  ds.getUsername(),
                                  ds.getPassword(),
                                  metadata,
                                  operator,
                                  TrivyScanService.JOB_TYPE_MANUAL);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new CompletionException(e);
                            } catch (IOException e) {
                              throw new CompletionException(e);
                            }
                          },
                          executor))
              .toList();
      return tasks.stream().map(CompletableFuture::join).toList();
    } catch (CompletionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      String message = cause.getMessage() == null ? "Scan failed" : cause.getMessage();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    } finally {
      executor.shutdown();
    }
  }
}
