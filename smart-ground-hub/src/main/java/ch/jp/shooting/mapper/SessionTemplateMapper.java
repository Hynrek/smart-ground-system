package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.CompetitionTemplateResponse;
import ch.jp.shooting.model.SessionTemplate;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NullMarked;

/**
 * Mapper für SessionTemplate-Entity zu CompetitionTemplateResponse DTO.
 */
@Component
@NullMarked
public class SessionTemplateMapper {

    public CompetitionTemplateResponse toCompetitionTemplateResponse(SessionTemplate template) {
        return new CompetitionTemplateResponse(
            template.getId(),
            template.getName(),
            template.getType().toString(),
            template.getProgramIds(),
            template.getRangeSegmentMap(),
            template.getDefaultPlayers(),
            template.getMaxGroups(),
            template.getBracketType(),
            template.getDefaultTiebreaker(),
            template.isPublishResults(),
            template.getCreatedBy() != null ? template.getCreatedBy().getId() : null,
            template.getCreatedAt()
        );
    }
}
