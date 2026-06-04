package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SerieServiceTest {

    @Mock SerieRepository serieRepository;
    @Mock RangeRepository rangeRepository;
    @Mock SecurityHelper securityHelper;

    @InjectMocks SerieService serieService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        when(securityHelper.currentUser()).thenReturn(user);
    }

    private Serie rangeOwnedSerie(boolean published) {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Test");
        serie.setOwnership("range");
        serie.setPublished(published);
        serie.setStepsJson("[]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        return serie;
    }
}
