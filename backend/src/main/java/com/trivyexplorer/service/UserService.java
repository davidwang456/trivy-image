package com.trivyexplorer.service;

import com.trivyexplorer.domain.AppUser;
import com.trivyexplorer.repo.AppUserRepository;
import com.trivyexplorer.web.dto.CreateUserRequest;
import com.trivyexplorer.web.dto.UserInfoResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
  public static final String ROLE_ADMIN = "admin";
  public static final String ROLE_USER = "user";

  private final AppUserRepository appUserRepository;

  public UserService(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Transactional(readOnly = true)
  public AppUser authenticate(String username, String rawPassword) {
    AppUser user =
        appUserRepository
            .findByUsername(username.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    String encoded = md5(rawPassword);
    if (!user.getPasswordMd5().equals(encoded)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    return user;
  }

  @Transactional(readOnly = true)
  public List<UserInfoResponse> listUsers() {
    return appUserRepository.findAll().stream()
        .map(
            u ->
                new UserInfoResponse(
                    u.getId(),
                    u.getUsername(),
                    u.getRole(),
                    u.getCreateTime(),
                    u.getUpdateTime(),
                    isUsingDefaultAdminPassword(u)))
        .toList();
  }

  @Transactional
  public UserInfoResponse createUser(CreateUserRequest request) {
    String username = normalizeUsername(request.getUsername());
    if (appUserRepository.existsByUsername(username)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }

    String normalizedRole = normalizeRole(request.getRole());
    AppUser user = new AppUser();
    user.setUsername(username);
    user.setPasswordMd5(md5(request.getPassword()));
    user.setRole(normalizedRole);
    AppUser saved = appUserRepository.save(user);
    return new UserInfoResponse(
        saved.getId(),
        saved.getUsername(),
        saved.getRole(),
        saved.getCreateTime(),
        saved.getUpdateTime(),
        isUsingDefaultAdminPassword(saved));
  }

  @Transactional
  public void changeOwnPassword(Long userId, String oldPassword, String newPassword) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    if (!user.getPasswordMd5().equals(md5(oldPassword))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
    }
    user.setPasswordMd5(md5(newPassword));
    appUserRepository.save(user);
  }

  @Transactional
  public void adminResetPassword(Long targetUserId, String newPassword) {
    AppUser user =
        appUserRepository
            .findById(targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    user.setPasswordMd5(md5(newPassword));
    appUserRepository.save(user);
  }

  @Transactional(readOnly = true)
  public boolean mustChangePassword(Long userId) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    return isUsingDefaultAdminPassword(user);
  }

  @Transactional
  public void ensureDefaultAdmin() {
    if (appUserRepository.count() > 0) {
      return;
    }
    AppUser admin = new AppUser();
    admin.setUsername("admin");
    admin.setPasswordMd5(md5("admin"));
    admin.setRole(ROLE_ADMIN);
    appUserRepository.save(admin);
  }

  private static String md5(String raw) {
    return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String normalizeUsername(String username) {
    if (!StringUtils.hasText(username)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
    }
    return username.trim();
  }

  private static String normalizeRole(String role) {
    if (!StringUtils.hasText(role)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
    }
    String normalized = role.trim().toLowerCase(Locale.ROOT);
    if (!ROLE_ADMIN.equals(normalized) && !ROLE_USER.equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be admin or user");
    }
    return normalized;
  }

  private static boolean isUsingDefaultAdminPassword(AppUser user) {
    return ROLE_ADMIN.equals(user.getRole())
        && "admin".equals(user.getUsername())
        && md5("admin").equals(user.getPasswordMd5());
  }
}
