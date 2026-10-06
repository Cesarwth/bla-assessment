package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserRepositoryAdapter.class, UserPersistenceMapper.class})
class JpaUserRepositoryAdapterTest {

    @Autowired JpaUserRepositoryAdapter adapter;

    private static User user(String username, String email) {
        return new User(null, username, email, "hashed", Role.USER);
    }

    @Test
    void savesAndFindsByUsername() {
        User saved = adapter.save(user("ash", "ash@pallet.town"));

        assertThat(saved.id()).isNotNull();
        assertThat(adapter.findByUsername("ash")).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.email()).isEqualTo("ash@pallet.town");
            assertThat(found.passwordHash()).isEqualTo("hashed");
            assertThat(found.role()).isEqualTo(Role.USER);
        });
    }

    @Test
    void findByUsernameIsEmptyWhenMissing() {
        assertThat(adapter.findByUsername("ghost")).isEmpty();
    }

    @Test
    void existsByUsernameAndEmail() {
        adapter.save(user("misty", "misty@cerulean.city"));

        assertThat(adapter.existsByUsername("misty")).isTrue();
        assertThat(adapter.existsByUsername("brock")).isFalse();
        assertThat(adapter.existsByEmail("misty@cerulean.city")).isTrue();
        assertThat(adapter.existsByEmail("brock@pewter.city")).isFalse();
    }

    @Test
    void uniqueUsernameConstraintIsEnforced() {
        adapter.save(user("ash", "ash@pallet.town"));

        assertThatThrownBy(() -> adapter.save(user("ash", "other@pallet.town")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void uniqueEmailConstraintIsEnforced() {
        adapter.save(user("ash", "ash@pallet.town"));

        assertThatThrownBy(() -> adapter.save(user("ash2", "ash@pallet.town")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void findsByIdAndByEmail() {
        User saved = adapter.save(user("brock", "brock@pewter.city"));

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(found -> assertThat(found.username()).isEqualTo("brock"));
        assertThat(adapter.findByEmail("brock@pewter.city")).hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(saved.id()));
        assertThat(adapter.findById(999_999L)).isEmpty();
        assertThat(adapter.findByEmail("nobody@pewter.city")).isEmpty();
    }

    @Test
    void savingAnExistingIdUpdatesTheRowInsteadOfInsertingOne() {
        User saved = adapter.save(user("gary", "gary@pallet.town"));

        adapter.save(new User(saved.id(), "blue", "blue@pallet.town", "new-hash", Role.USER));

        assertThat(adapter.findById(saved.id())).hasValueSatisfying(found -> {
            assertThat(found.username()).isEqualTo("blue");
            assertThat(found.email()).isEqualTo("blue@pallet.town");
            assertThat(found.passwordHash()).isEqualTo("new-hash");
        });
        assertThat(adapter.existsByUsername("gary")).isFalse();
    }
}
