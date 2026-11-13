package com.campusclaw.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    List<UserAccount> findByClassIdAndRoleOrderByUsernameAscIdAsc(Long classId, Role role);

    Optional<UserAccount> findByUsername(String username);
}

