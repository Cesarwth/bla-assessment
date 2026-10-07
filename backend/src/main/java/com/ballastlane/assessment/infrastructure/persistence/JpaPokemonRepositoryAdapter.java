package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transactions live here, at the edge: use cases stay plain Java and lazy collections are read before mapping. */
@Component
public class JpaPokemonRepositoryAdapter implements PokemonRepository {

    private final SpringDataPokemonRepository catalog;
    private final SpringDataTrainerPokemonRepository entries;
    private final PokemonPersistenceMapper mapper;

    JpaPokemonRepositoryAdapter(
            SpringDataPokemonRepository catalog, SpringDataTrainerPokemonRepository entries, PokemonPersistenceMapper mapper) {
        this.catalog = catalog;
        this.entries = entries;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPokemon> findByTrainerAndPokeApiId(long trainerId, int pokeApiId) {
        return entries.findByUserIdAndPokemonPokeApiId(trainerId, pokeApiId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StoredPokemon> findPage(long trainerId, int page, int size) {
        // Spring Data casts the offset to int; a huge page number would overflow, and is beyond the end anyway.
        if ((long) page * size > Integer.MAX_VALUE) {
            return new PageResult<>(List.of(), page, size, entries.countByUserId(trainerId));
        }
        Page<TrainerPokemonEntity> result =
                entries.findByUserId(trainerId, PageRequest.of(page, size, Sort.by("pokemon.pokeApiId")));
        return new PageResult<>(result.getContent().stream().map(mapper::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> findPokeApiIds(long trainerId) {
        return entries.findPokeApiIdsByUserId(trainerId);
    }

    @Override
    @Transactional
    public StoredPokemon save(StoredPokemon entry) {
        try {
            PokemonEntity shared = upsertCatalog(entry);
            return mapper.toDomain(entry.id() == null ? createEntry(entry, shared) : updateEntry(entry));
        } catch (DataIntegrityViolationException exception) {
            if (UniqueViolations.isUnique(exception)) {
                throw new DuplicateStoredPokemonException(entry.pokeApiId());
            }
            throw exception;
        } catch (OptimisticLockingFailureException exception) {
            throw new StaleStoredPokemonException(entry.pokeApiId());
        }
    }

    @Override
    @Transactional
    public StoredPokemon updateNotes(StoredPokemon entry) {
        // Only the trainer's entry row is loaded and written: the shared catalog row (and its version) stays out of
        // it, so private edits of different trainers never contend with each other or with a sync.
        if (entry.id() == null) {
            throw new StaleStoredPokemonException(entry.pokeApiId());
        }
        try {
            return mapper.toDomain(updateEntry(entry));
        } catch (OptimisticLockingFailureException exception) {
            throw new StaleStoredPokemonException(entry.pokeApiId());
        }
    }

    // A copy older than the stored one (e.g. an entry read before another trainer synced) must not roll the shared
    // data back, so saving a trainer's notes never regresses the catalog.
    private PokemonEntity upsertCatalog(StoredPokemon entry) {
        PokemonEntity shared = catalog.findByPokeApiId(entry.pokeApiId()).orElse(null);
        if (shared == null) {
            return catalog.saveAndFlush(mapper.toCatalogEntity(entry));
        }
        if (!entry.syncedAt().isBefore(shared.syncedAt)) {
            mapper.refreshCatalog(shared, entry);
            return catalog.saveAndFlush(shared);
        }
        return shared;
    }

    private TrainerPokemonEntity createEntry(StoredPokemon entry, PokemonEntity shared) {
        return entries.saveAndFlush(mapper.toNewEntry(entry, shared));
    }

    // The aggregate's version is compared with the stored one: a stale copy (or an entry removed meanwhile) is rejected.
    private TrainerPokemonEntity updateEntry(StoredPokemon entry) {
        TrainerPokemonEntity stored = entries.findById(entry.id()).orElseThrow(
                () -> new StaleStoredPokemonException(entry.pokeApiId()));
        if (!Objects.equals(stored.version, entry.version())) {
            throw new StaleStoredPokemonException(entry.pokeApiId());
        }
        mapper.applyNotes(stored, entry.localAttributes());
        return entries.saveAndFlush(stored);
    }

    @Override
    @Transactional
    public boolean deleteByTrainerAndPokeApiId(long trainerId, int pokeApiId) {
        return entries.deleteByUserIdAndPokemonPokeApiId(trainerId, pokeApiId) > 0;
    }
}
