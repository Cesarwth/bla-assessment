package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.application.port.out.PasswordResetTokenRepository;
import com.ballastlane.assessment.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaPasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final SpringDataPasswordResetTokenRepository repository;

    JpaPasswordResetTokenRepositoryAdapter(SpringDataPasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return toDomain(repository.saveAndFlush(new PasswordResetTokenEntity(
                token.id(), token.userId(), token.tokenHash(), token.expiresAt(), token.usedAt(), token.createdAt())));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(JpaPasswordResetTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean markUsed(Long tokenId, Instant at) {
        return repository.markUsed(tokenId, at) == 1;
    }

    @Override
    @Transactional
    public void invalidateUnusedForUser(Long userId, Instant at) {
        repository.invalidateUnusedForUser(userId, at);
    }

    private static PasswordResetToken toDomain(PasswordResetTokenEntity e) {
        return new PasswordResetToken(e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.getUsedAt(), e.getCreatedAt());
    }
}
