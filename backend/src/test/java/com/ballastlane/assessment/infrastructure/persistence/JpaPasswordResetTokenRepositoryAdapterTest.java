package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.PasswordResetToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPasswordResetTokenRepositoryAdapter.class, JpaUserRepositoryAdapter.class, UserPersistenceMapper.class})
class JpaPasswordResetTokenRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired JpaPasswordResetTokenRepositoryAdapter adapter;
    @Autowired JpaUserRepositoryAdapter users;
    Long userId;

    @BeforeEach
    void setUp() {
        userId = users.save(new User(null, "ash", "ash@pallet.town", "hashed", Role.USER)).id();
    }

    private PasswordResetToken token(String hash) {
        return new PasswordResetToken(null, userId, hash, NOW.plusSeconds(1800), null, NOW);
    }

    @Test
    void savesAndFindsByHash() {
        PasswordResetToken saved = adapter.save(token("a".repeat(64)));

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findByTokenHash("a".repeat(64))).hasValueSatisfying(found -> {
            assertThat(found.userId()).isEqualTo(userId);
            assertThat(found.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
            assertThat(found.createdAt()).isEqualTo(NOW);
            assertThat(found.usedAt()).isNull();
        });
        assertThat(adapter.findByTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void markUsedSucceedsOnceThenReportsFalse() {
        Long id = adapter.save(token("a".repeat(64))).id();

        assertThat(adapter.markUsed(id, NOW)).isTrue();
        assertThat(adapter.markUsed(id, NOW.plusSeconds(1))).isFalse();
        assertThat(adapter.findByTokenHash("a".repeat(64))).hasValueSatisfying(
                found -> assertThat(found.usedAt()).isEqualTo(NOW));
    }

    @Test
    void invalidateUnusedForUserOnlyTouchesThatUsersUnusedTokens() {
        Long otherUser = users.save(new User(null, "misty", "misty@cerulean.city", "hashed", Role.USER)).id();
        adapter.save(token("a".repeat(64)));
        adapter.save(token("b".repeat(64)));
        adapter.save(new PasswordResetToken(null, otherUser, "c".repeat(64), NOW.plusSeconds(1800), null, NOW));
        Long alreadyUsed = adapter.save(token("d".repeat(64))).id();
        adapter.markUsed(alreadyUsed, NOW.minusSeconds(60));

        adapter.invalidateUnusedForUser(userId, NOW);

        assertThat(adapter.findByTokenHash("a".repeat(64)).orElseThrow().usedAt()).isEqualTo(NOW);
        assertThat(adapter.findByTokenHash("b".repeat(64)).orElseThrow().usedAt()).isEqualTo(NOW);
        assertThat(adapter.findByTokenHash("d".repeat(64)).orElseThrow().usedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(adapter.findByTokenHash("c".repeat(64)).orElseThrow().usedAt()).isNull();
    }

    @Test
    void theHashIsUnique() {
        adapter.save(token("a".repeat(64)));

        assertThatThrownBy(() -> adapter.save(token("a".repeat(64)))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
