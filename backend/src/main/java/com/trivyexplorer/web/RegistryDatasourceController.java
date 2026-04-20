package com.trivyexplorer.web;

import com.trivyexplorer.service.RegistryDatasourceService;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.RegistryDatasourceRequest;
import com.trivyexplorer.web.dto.RegistryDatasourceResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/registry-datasources")
public class RegistryDatasourceController {
  private final RegistryDatasourceService service;

  public RegistryDatasourceController(RegistryDatasourceService service) {
    this.service = service;
  }

  @GetMapping
  public List<RegistryDatasourceResponse> list() {
    return service.listDatasources();
  }

  @PostMapping
  public ResponseEntity<RegistryDatasourceResponse> create(
      @Valid @RequestBody RegistryDatasourceRequest request, HttpSession session) {
    requireAdmin(session);
    return ResponseEntity.ok(service.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<RegistryDatasourceResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody RegistryDatasourceRequest request,
      HttpSession session) {
    requireAdmin(session);
    return ResponseEntity.ok(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id, HttpSession session) {
    requireAdmin(session);
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/repos")
  public List<String> repos(@PathVariable Long id) {
    return service.listRepos(id);
  }

  @GetMapping("/{id}/images")
  public List<String> images(@PathVariable Long id, @RequestParam String repoName, HttpSession session) {
    requireAdmin(session);
    return service.listImageRefs(id, repoName);
  }

  private static void requireAdmin(HttpSession session) {
    Object role = session.getAttribute(AuthController.SESSION_ROLE);
    if (!(role instanceof String) || !UserService.ROLE_ADMIN.equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
