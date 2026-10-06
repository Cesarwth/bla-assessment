package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.EvolutionNode;
import java.util.List;

public record EvolutionNodeResponse(
        int id, String name, String spriteUrl, List<String> requirements, List<EvolutionNodeResponse> evolvesTo) {

    public static EvolutionNodeResponse from(EvolutionNode node) {
        return new EvolutionNodeResponse(node.id(), node.name(), node.spriteUrl(), node.requirements(),
                node.evolvesTo().stream().map(EvolutionNodeResponse::from).toList());
    }
}
