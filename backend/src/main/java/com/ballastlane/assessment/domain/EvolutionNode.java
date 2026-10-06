package com.ballastlane.assessment.domain;

import java.util.List;

/** One species in an evolution tree; branching (e.g. Eevee) is just several children. */
public record EvolutionNode(
        int id, String name, String spriteUrl, List<String> requirements, List<EvolutionNode> evolvesTo) {

    public EvolutionNode {
        requirements = List.copyOf(requirements);
        evolvesTo = List.copyOf(evolvesTo);
    }
}
