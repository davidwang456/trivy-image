package com.trivyexplorer.web;

import com.trivyexplorer.service.SwrService;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.ScanResponse;
import com.trivyexplorer.web.dto.SwrNamespaceScanRequest;
import com.trivyexplorer.web.dto.SwrRepoScanRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
@RequestMapping("/api/swr")
public class SwrController {
  private final SwrService swrService;

  public SwrController(SwrService swrService) {
    this.swrService = swrService;
  }

  @GetMapping("/datasources/{id}/namespaces")
  public List<String> namespaces(@PathVariable Long id, HttpSession session) {
    requireAdmin(session);
    return swrService.listNamespaces(id);
  }

  @GetMapping("/datasources/{id}/repos")
  public List<String> repos(
      @PathVariable Long id, @RequestParam(required = false) String namespace, HttpSession session) {
    requireAdmin(session);
    return swrService.listRepos(id, namespace);
  }

  @GetMapping("/datasources/{id}/images")
  public List<String> images(
      @PathVariable Long id,
      @RequestParam String namespace,
      @RequestParam String repoName,
      HttpSession session) {
    requireAdmin(session);
    return swrService.listImageRefs(id, namespace, repoName);
  }

  @PostMapping("/scans/namespace")
  public ResponseEntity<List<ScanResponse>> scanNamespace(
      @Valid @RequestBody SwrNamespaceScanRequest request, HttpSession session) {
    requireAdmin(session);
    String operator = requireAuthenticatedUsername(session);
    return ResponseEntity.ok(swrService.scanNamespace(request.getDatasourceId(), request.getNamespace(), operator));
  }

  @PostMapping("/scans/repo")
  public ResponseEntity<List<ScanResponse>> scanRepo(
      @Valid @RequestBody SwrRepoScanRequest request, HttpSession session) {
    requireAdmin(session);
    String operator = requireAuthenticatedUsername(session);
    return ResponseEntity.ok(
        swrService.scanRepo(
            request.getDatasourceId(), request.getNamespace(), request.getRepoName(), operator));
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
}
