package org.team12.teamproject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.UserRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting email migration to uppercase...");
        
        List<User> users = userRepository.findAll();
        int count = 0;
        
        for (User user : users) {
            String currentEmail = user.getEmail();
            if (currentEmail != null) {
                String upperEmail = currentEmail.toUpperCase();
                if (!currentEmail.equals(upperEmail)) {
                    log.info("Migrating email for user {}: {} -> {}", user.getId(), currentEmail, upperEmail);
                    user.setEmail(upperEmail);
                    userRepository.save(user);
                    count++;
                }
            }
        }
        
        if (count > 0) {
            log.info("Email migration completed. {} users updated.", count);
        } else {
            log.info("No emails needed migration.");
        }
    }
}
