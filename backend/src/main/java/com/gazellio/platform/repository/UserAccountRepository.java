package com.gazellio.platform.repository;
import com.gazellio.platform.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
 Optional<UserAccount> findByUsername(String username);
 Optional<UserAccount> findByDisplayName(String displayName);
 List<UserAccount> findAllByOrderByUsernameAsc();
 long countByAccessRoleId(Long accessRoleId);
}
