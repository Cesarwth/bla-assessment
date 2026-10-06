package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import java.util.Locale;
import java.util.regex.Pattern;

public class GetPokemonDetail implements GetPokemonDetailUseCase {

    // Ids and PokeAPI names only use these characters; rejecting the rest keeps odd input out of upstream URLs.
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[a-z0-9-]+");

    private final PokeApiGateway gateway;

    public GetPokemonDetail(PokeApiGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public PokemonDetail get(String idOrName) {
        String identifier = idOrName == null ? "" : idOrName.strip().toLowerCase(Locale.ROOT);
        if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new InvalidPokemonIdentifierException("Pokemon id or name is not valid");
        }
        return gateway.findDetail(identifier).orElseThrow(() -> new PokemonNotFoundException(identifier));
    }
}
