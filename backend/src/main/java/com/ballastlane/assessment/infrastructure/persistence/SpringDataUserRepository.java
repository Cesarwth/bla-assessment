package com.ballastlane.assessment.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Targeted writes: only the named columns, so concurrent changes to the other columns are never reverted.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.username = :username, u.email = :email where u.id = :id")
    int updateProfile(@Param("id") Long id, @Param("username") String username, @Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.passwordHash = :hash where u.id = :id")
    int updatePasswordHash(@Param("id") Long id, @Param("hash") String hash);
}
