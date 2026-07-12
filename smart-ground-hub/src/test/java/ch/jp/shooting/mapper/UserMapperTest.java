package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.model.auth.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toDto_mapsUsername() {
        User user = new User("jonas@example.com", "Jonas", "Studer");
        user.setUsername("JonasS");

        UserDTO dto = mapper.toDto(user);

        assertEquals("JonasS", dto.getUsername());
    }
}
