package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.CareerStatsResponse;
import ch.jp.shooting.model.CareerStats;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NullMarked;

/**
 * Mapper für CareerStats-Entity zu DTO.
 */
@Component
@NullMarked
public class CareerStatsMapper {

    public CareerStatsResponse toCareerStatsResponse(CareerStats stats) {
        return new CareerStatsResponse(
            stats.getUserId(),
            stats.getTotalWins(),
            stats.getParticipations(),
            stats.getTotalScore(),
            stats.getAvgScore(),
            stats.getLastCompeted()
        );
    }

    public CareerStats toCareerStats(CareerStatsResponse response) {
        CareerStats stats = new CareerStats();
        stats.setUserId(response.userId());
        stats.setTotalWins(response.totalWins());
        stats.setParticipations(response.participations());
        stats.setTotalScore(response.totalScore());
        stats.setAvgScore(response.avgScore());
        stats.setLastCompeted(response.lastCompeted());
        return stats;
    }
}
