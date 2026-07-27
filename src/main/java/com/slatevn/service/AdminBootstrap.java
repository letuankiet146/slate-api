package com.slatevn.service;

import com.slatevn.config.BootstrapProperties;
import com.slatevn.domain.Membership;
import com.slatevn.domain.Role;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RoleRepository;
import com.slatevn.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(
            BootstrapProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(properties.adminEmail())) {
            return;
        }
        Role adminRole = roleRepository.findByCode("SYSTEM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role missing"));

        User admin = new User();
        admin.setEmail(properties.adminEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
        admin.setDisplayName(properties.adminDisplayName());
        admin.setLocale("vi");
        admin.setEnabled(true);
        userRepository.save(admin);

        Membership membership = new Membership();
        membership.setUser(admin);
        membership.setRole(adminRole);
        membership.setScopeType(ScopeType.SYSTEM);
        membershipRepository.save(membership);

        log.info("Bootstrapped SYSTEM_ADMIN user: {}", properties.adminEmail());
    }
}
