package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;

public interface ListStoredPokemonUseCase {
    /** Null page/size mean "use the defaults". */
    PageResult<StoredPokemon> list(Integer page, Integer size);
}
