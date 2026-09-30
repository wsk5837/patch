package com.gazellio.platform.repository;
import com.gazellio.platform.model.AccessRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AccessRoleRepository extends JpaRepository<AccessRole,Long>{
 Optional<AccessRole> findByCode(String code);
 List<AccessRole> findAllByOrderByCodeAsc();
}
