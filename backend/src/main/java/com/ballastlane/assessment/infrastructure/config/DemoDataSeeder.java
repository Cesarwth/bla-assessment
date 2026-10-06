package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.User;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Fills an empty system with a small collection per demo trainer so there is something to try right away. It only ever
 * adds: an entry a trainer already has is left alone, so restarting never duplicates rows or undoes what users edited.
 * Runs after the user seeder, because trainers are resolved by username.
 */
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final List<DemoEntry> entries;
    private final PokemonRepository repository;
    private final UserRepository users;
    private final Clock clock;

    public DemoDataSeeder(List<DemoEntry> entries, PokemonRepository repository, UserRepository users, Clock clock) {
        this.entries = entries;
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = 0;
        for (DemoEntry entry : entries) {
            Optional<User> account = users.findByUsername(entry.trainer());
            if (account.isEmpty()) {
                log.warn("Skipping demo Pokemon {}: its trainer '{}' does not exist (are the demo passwords set?)",
                        entry.detail().name(), entry.trainer());
                continue;
            }
            User user = account.get();
            if (repository.findByTrainerAndPokeApiId(user.id(), entry.detail().id()).isPresent()) {
                continue;
            }
            LocalAttributes local = new LocalAttributes(entry.nickname(), entry.region(), entry.tags());
            // The shared Pokemon row is created by the first trainer that has it; later ones only add their entry.
            repository.save(StoredPokemon.fromDetail(entry.detail(), clock.instant(), new Actor(user.id(), user.username(), user.role()))
                    .withLocalAttributes(local));
            added++;
        }
        if (added > 0) {
            log.info("Seeded {} demo collection entries", added);
        }
    }
}
