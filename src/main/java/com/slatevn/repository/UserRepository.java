package com.slatevn.repository;

import com.slatevn.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM User u
            WHERE u.id NOT IN (
                SELECT m.user.id FROM Membership m
                WHERE m.scopeType = com.slatevn.domain.ScopeType.SYSTEM
                  AND m.role.code = 'SYSTEM_ADMIN'
            )
            ORDER BY u.displayName
            """)
    List<User> findAllExcludingSystemAdmins();
}
