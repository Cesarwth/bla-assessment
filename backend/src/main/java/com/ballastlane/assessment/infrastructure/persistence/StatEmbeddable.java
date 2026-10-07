package com.ballastlane.assessment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

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

    // Value semantics let Hibernate see that an unchanged stat list needs no SQL when a sync rewrites it.
    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof StatEmbeddable stat && baseValue == stat.baseValue && Objects.equals(name, stat.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, baseValue);
    }
}
