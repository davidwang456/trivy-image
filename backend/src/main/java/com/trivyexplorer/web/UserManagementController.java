package com.trivyexplorer.web;

import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.AdminResetPasswordRequest;
import com.trivyexplorer.web.dto.CreateUserRequest;
import com.trivyexplorer.web.dto.UserInfoResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {
  private final UserService userService;

  public UserManagementController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public List<UserInfoResponse> listUsers(HttpSession session) {
    requireAdmin(session);
    return userService.listUsers();
  }

  @PostMapping
  public ResponseEntity<UserInfoResponse> createUser(
      @Valid @RequestBody CreateUserRequest request, HttpSession session) {
    requireAdmin(session);
    return ResponseEntity.ok(userService.createUser(request));
  }

  @PostMapping("/{id}/reset-password")
  public ResponseEntity<Void> resetPassword(
      @PathVariable Long id,
      @Valid @RequestBody AdminResetPasswordRequest request,
      HttpSession session) {
    requireAdmin(session);
    userService.adminResetPassword(id, request.getNewPassword());
    return ResponseEntity.noContent().build();
  }

  private static void requireAdmin(HttpSession session) {
    Object role = session.getAttribute(AuthController.SESSION_ROLE);
    if (!(role instanceof String) || !UserService.ROLE_ADMIN.equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
