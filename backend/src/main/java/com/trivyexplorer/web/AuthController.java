package com.trivyexplorer.web;

import com.trivyexplorer.domain.AppUser;
import com.trivyexplorer.service.UserService;
import com.trivyexplorer.web.dto.ChangePasswordRequest;
import com.trivyexplorer.web.dto.LoginRequest;
import com.trivyexplorer.web.dto.UserInfoResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  public static final String SESSION_USERNAME = "auth.username";
  public static final String SESSION_ROLE = "auth.role";
  public static final String SESSION_USER_ID = "auth.userId";
  public static final String SESSION_MUST_CHANGE_PASSWORD = "auth.mustChangePassword";

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/login")
  public ResponseEntity<UserInfoResponse> login(
      @Valid @RequestBody LoginRequest request, HttpSession session) {
    AppUser user = userService.authenticate(request.getUsername(), request.getPassword());
    boolean mustChangePassword = userService.mustChangePassword(user.getId());
    session.setAttribute(SESSION_USER_ID, user.getId());
    session.setAttribute(SESSION_USERNAME, user.getUsername());
    session.setAttribute(SESSION_ROLE, user.getRole());
    session.setAttribute(SESSION_MUST_CHANGE_PASSWORD, mustChangePassword);
    return ResponseEntity.ok(
        new UserInfoResponse(
            user.getId(),
            user.getUsername(),
            user.getRole(),
            user.getCreateTime(),
            user.getUpdateTime(),
            mustChangePassword));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpSession session) {
    session.invalidate();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public UserInfoResponse me(HttpSession session) {
    Object id = session.getAttribute(SESSION_USER_ID);
    Object username = session.getAttribute(SESSION_USERNAME);
    Object role = session.getAttribute(SESSION_ROLE);
    Object mustChangePassword = session.getAttribute(SESSION_MUST_CHANGE_PASSWORD);
    if (!(id instanceof Long) || !(username instanceof String) || !(role instanceof String)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    boolean mustChange = mustChangePassword instanceof Boolean b ? b : false;
    return new UserInfoResponse((Long) id, (String) username, (String) role, null, null, mustChange);
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, HttpSession session) {
    Object id = session.getAttribute(SESSION_USER_ID);
    if (!(id instanceof Long userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    userService.changeOwnPassword(userId, request.getOldPassword(), request.getNewPassword());
    session.setAttribute(SESSION_MUST_CHANGE_PASSWORD, false);
    return ResponseEntity.noContent().build();
  }
}
