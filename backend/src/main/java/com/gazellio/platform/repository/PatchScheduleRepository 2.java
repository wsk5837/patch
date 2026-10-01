package com.gazellio.platform.repository;

import com.gazellio.platform.model.PatchSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface PatchScheduleRepository extends JpaRepository<PatchSchedule,Long> {
    List<PatchSchedule> findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(Instant rangeEnd,Instant rangeStart);
}
