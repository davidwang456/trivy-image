package com.trivyexplorer.web;

import com.trivyexplorer.domain.RegistryDatasource;
import com.trivyexplorer.service.RegistryDatasourceService;
import com.trivyexplorer.service.StoredScanService;
import com.trivyexplorer.service.TrivyScanService;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.BatchScanRequest;
import com.trivyexplorer.web.dto.ImportScanRequest;
import com.trivyexplorer.web.dto.ImageScanSummary;
import com.trivyexplorer.web.dto.ScanRequest;
import com.trivyexplorer.web.dto.ScanResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @GetMapping("/{id}")
  public ScanResponse getById(@PathVariable Long id) {
    return storedScanService.getById(id);
  }

  @PostMapping("/import")
  public ResponseEntity<ScanResponse> importReport(@Valid @RequestBody ImportScanRequest request) {
    return ResponseEntity.ok(storedScanService.importAndStore(request));
  }

  @PostMapping
  public ResponseEntity<ScanResponse> scan(@Valid @RequestBody ScanRequest request)
      throws IOException, InterruptedException {
    RegistryDatasource ds = registryDatasourceService.getById(request.getDatasourceId());
    return ResponseEntity.ok(
        trivyScanService.scanAndStore(request.getImageRef(), ds.getUsername(), ds.getPassword()));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<ScanResponse>> batchScan(
      @Valid @RequestBody BatchScanRequest request, HttpSession session)
      throws IOException, InterruptedException {
    requireAdmin(session);
    RegistryDatasource ds = registryDatasourceService.getById(request.getDatasourceId());
    List<ScanResponse> result = new ArrayList<>();
    for (String imageRef : request.getImageRefs()) {
      result.add(trivyScanService.scanAndStore(imageRef, ds.getUsername(), ds.getPassword()));
    }
    return ResponseEntity.ok(result);
  }

  private static void requireAdmin(HttpSession session) {
    Object role = session.getAttribute(AuthController.SESSION_ROLE);
    if (!(role instanceof String) || !UserService.ROLE_ADMIN.equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
