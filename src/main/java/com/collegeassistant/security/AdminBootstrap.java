package com.collegeassistant.security;

import com.collegeassistant.config.AppProperties;
import com.collegeassistant.user.Role;
import com.collegeassistant.user.UserRepository;
import com.collegeassistant.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from environment-supplied credentials, only if no ADMIN exists yet.
 * Nothing is hardcoded; with no ADMIN_EMAIL/ADMIN_PASSWORD set it just logs a warning.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final UserService userService;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, UserService userService, AppProperties props) {
        this.users = users;
        this.userService = userService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        AppProperties.Bootstrap b = props.getBootstrap();
        if (b.getAdminEmail().isBlank() || b.getAdminPassword().isBlank()) {
            log.warn("No ADMIN user exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set. "
                    + "Set them and restart to create the first admin.");
            return;
        }
        userService.createBootstrapAdmin(b.getAdminName(), b.getAdminEmail(), b.getAdminPassword());
        log.info("Created initial admin account for {}", b.getAdminEmail());
    }
}
