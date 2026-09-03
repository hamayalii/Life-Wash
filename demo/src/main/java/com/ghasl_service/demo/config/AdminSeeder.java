package com.ghasl_service.demo.config;

import com.ghasl_service.demo.model.User;
import com.ghasl_service.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password}")
    private String defaultAdminPassword;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdminUser() {
        if (userRepository.count() == 0) {
            log.info("No users found. Seeding default admin user.");
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);
            log.info("Default admin user 'admin' created successfully.");
        }
    }
}
