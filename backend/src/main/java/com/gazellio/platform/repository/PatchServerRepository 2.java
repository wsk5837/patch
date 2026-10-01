package com.gazellio.platform.repository;
import com.gazellio.platform.model.PatchServer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PatchServerRepository extends JpaRepository<PatchServer,Long>{List<PatchServer> findAllByOrderByNameAsc();}
