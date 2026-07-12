package ch.jp.shooting.model.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    @Test
    void setUsername_trimsAndComputesLowercaseShadow() {
        User user = new User("a@b.ch", "Jonas", "Studer");
        user.setUsername("  JonasS  ");

        assertEquals("JonasS", user.getUsername());
        assertEquals("jonass", user.getUsernameLower());
    }

    @Test
    void toUsernameLower_isTrimmedLowercase() {
        assertEquals("jonass", User.toUsernameLower("  JonasS "));
    }
}
