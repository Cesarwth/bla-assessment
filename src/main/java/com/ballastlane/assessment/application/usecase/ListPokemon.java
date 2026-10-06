package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;

public class ListPokemon implements ListPokemonUseCase {

    public static final int DEFAULT_SIZE = 12;
    // Each listed item costs one upstream call, so the cap protects PokeAPI (and our latency).
    public static final int MAX_SIZE = 50;

    private final PokeApiGateway gateway;

    public ListPokemon(PokeApiGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PageResult<PokemonSummary> list(Integer page, Integer size) {
        int validPage = page == null ? 0 : page;
        int validSize = size == null ? DEFAULT_SIZE : size;
        if (validPage < 0) {
            throw new InvalidPaginationException("page must be 0 or greater");
        }
        if (validSize < 1 || validSize > MAX_SIZE) {
            throw new InvalidPaginationException("size must be between 1 and " + MAX_SIZE);
        }
        return gateway.findPage(validPage, validSize);
    }
}
