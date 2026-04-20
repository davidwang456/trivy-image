package com.trivyexplorer.config;

import com.trivyexplorer.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminBootstrap implements ApplicationRunner {
  private final UserService userService;

  public DefaultAdminBootstrap(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void run(ApplicationArguments args) {
    userService.ensureDefaultAdmin();
  }
}
