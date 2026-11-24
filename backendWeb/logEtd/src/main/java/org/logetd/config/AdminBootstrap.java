package org.logetd.config;

import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée un compte ADMIN de démarrage pour le back-office.
 * L'inscription publique refuse toujours le rôle ADMIN : ce compte ne peut venir que de la configuration.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String phone;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.bootstrap.enabled:false}") boolean enabled,
                          @Value("${app.admin.bootstrap.phone:}") String phone,
                          @Value("${app.admin.bootstrap.email:}") String email,
                          @Value("${app.admin.bootstrap.password:}") String password,
                          @Value("${app.admin.bootstrap.fullName:Administrateur}") String fullName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || email.isBlank() || phone.isBlank() || password.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(email) || userRepository.existsByPhone(phone)) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPhone(phone);
        admin.setFullName(fullName);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
    }
}
