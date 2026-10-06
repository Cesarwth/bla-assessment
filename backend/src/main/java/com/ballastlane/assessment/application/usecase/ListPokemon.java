package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonSummary;

public class ListPokemon implements ListPokemonUseCase {

    public static final int DEFAULT_SIZE = Pagination.DEFAULT_SIZE;
    public static final int MAX_SIZE = Pagination.MAX_SIZE;

    private final PokeApiGateway gateway;

    public ListPokemon(PokeApiGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PageResult<PokemonSummary> list(Integer page, Integer size) {
        Pagination pagination = Pagination.of(page, size);
        return gateway.findPage(pagination.page(), pagination.size());
    }
}
