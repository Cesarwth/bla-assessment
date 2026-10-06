package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import java.util.List;
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

    private final SpringDataPokemonRepository repository;
    private final PokemonPersistenceMapper mapper;

    JpaPokemonRepositoryAdapter(SpringDataPokemonRepository repository, PokemonPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPokemon> findByPokeApiId(int pokeApiId) {
        return repository.findByPokeApiId(pokeApiId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StoredPokemon> findPage(int page, int size) {
        // Spring Data casts the offset to int; a huge page number would overflow, and is beyond the end anyway.
        if ((long) page * size > Integer.MAX_VALUE) {
            return new PageResult<>(List.of(), page, size, repository.count());
        }
        Page<PokemonEntity> result = repository.findAll(PageRequest.of(page, size, Sort.by("pokeApiId")));
        return new PageResult<>(result.getContent().stream().map(mapper::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    // The aggregate's version travels in the entity: merging a stale copy makes Hibernate reject the write.
    @Override
    @Transactional
    public StoredPokemon save(StoredPokemon pokemon) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(pokemon)));
        } catch (OptimisticLockingFailureException e) {
            throw new StaleStoredPokemonException(pokemon.pokeApiId());
        } catch (DataIntegrityViolationException e) {
            if (UniqueViolations.isUnique(e)) {
                throw new DuplicateStoredPokemonException(pokemon.pokeApiId());
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean deleteByPokeApiId(int pokeApiId) {
        return repository.deleteByPokeApiId(pokeApiId) > 0;
    }
}
