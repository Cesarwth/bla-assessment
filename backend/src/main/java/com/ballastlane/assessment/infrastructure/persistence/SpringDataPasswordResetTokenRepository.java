package com.ballastlane.assessment.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    // The "used_at is null" condition makes the flip atomic: of two concurrent callers only one updates a row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetTokenEntity t set t.usedAt = :at where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetTokenEntity t set t.usedAt = :at where t.userId = :userId and t.usedAt is null")
    int invalidateUnusedForUser(@Param("userId") Long userId, @Param("at") Instant at);
}
