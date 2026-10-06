package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonSummary;

public interface ListPokemonUseCase {
    /** Null page/size mean "use the defaults". */
    PageResult<PokemonSummary> list(Integer page, Integer size);
}
