package com.trivyexplorer.web;

import com.trivyexplorer.service.RegistryScanScheduleService;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.RegistryScanScheduleRequest;
import com.trivyexplorer.web.dto.RegistryScanScheduleResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/registry-schedules")
public class RegistryScanScheduleController {
  private final RegistryScanScheduleService service;

  public RegistryScanScheduleController(RegistryScanScheduleService service) {
    this.service = service;
  }

  @GetMapping
  public List<RegistryScanScheduleResponse> list(HttpSession session) {
    requireAdmin(session);
    return service.list();
  }

  @PostMapping
  public ResponseEntity<RegistryScanScheduleResponse> create(
      @Valid @RequestBody RegistryScanScheduleRequest request, HttpSession session) {
    requireAdmin(session);
    return ResponseEntity.ok(service.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<RegistryScanScheduleResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody RegistryScanScheduleRequest request,
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

  private static void requireAdmin(HttpSession session) {
    Object role = session.getAttribute(AuthController.SESSION_ROLE);
    if (!(role instanceof String) || !UserService.ROLE_ADMIN.equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
