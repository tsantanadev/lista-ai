package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.EmailOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailOutboxJpaRepository extends JpaRepository<EmailOutboxEntity, Long> {

    @Query(value = """
           SELECT * FROM email_outbox
           WHERE status = 'PENDING' AND next_attempt_at <= :now
           ORDER BY next_attempt_at
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<EmailOutboxEntity> claimPending(@Param("now") Instant now, @Param("limit") int limit);
}
