package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;

public class GetPokemonDetail implements GetPokemonDetailUseCase {

    private final PokeApiGateway gateway;

    public GetPokemonDetail(PokeApiGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PokemonDetail get(String idOrName) {
        PokemonIdentifier identifier = PokemonIdentifier.of(idOrName);
        return gateway.findDetail(identifier.value())
                .orElseThrow(() -> new PokemonNotFoundException(identifier.value()));
    }
}
