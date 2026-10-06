package com.ballastlane.assessment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class StatEmbeddable {

    @Column(name = "stat_name", nullable = false, length = 50)
    String name;

    @Column(name = "base_value", nullable = false)
    int baseValue;

    protected StatEmbeddable() {
        // required by JPA
    }

    StatEmbeddable(String name, int baseValue) {
        this.name = name;
        this.baseValue = baseValue;
    }
}
