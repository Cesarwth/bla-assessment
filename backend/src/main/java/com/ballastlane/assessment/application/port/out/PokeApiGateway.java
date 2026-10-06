package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonSummary;
import java.util.Optional;

public interface PokeApiGateway {
    PageResult<PokemonSummary> findPage(int page, int size);

    Optional<PokemonDetail> findDetail(String idOrName);
}
