package easyevent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test unit del Value Object {@link Username}: validazione e semantica di
 * uguaglianza case-insensitive.
 */
class UsernameTest {

    @Test
    void username_ValoreValido_LoMemorizzaTrimmato() {
        // Arrange / Act
        Username u = new Username("  mario ");
        // Assert
        assertEquals("mario", u.getValore());
    }

    @Test
    void username_Null_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new Username(null));
    }

    @Test
    void username_Blank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new Username("   "));
    }

    @Test
    void username_EqualsCaseInsensitive() {
        // Arrange
        Username mario = new Username("Mario");
        Username marioMinuscolo = new Username("mario");
        // Assert
        assertEquals(mario, marioMinuscolo);
    }

    @Test
    void username_DiversoValore_NonUguale() {
        assertNotEquals(new Username("mario"), new Username("luigi"));
    }

    @Test
    void username_HashCodeCoerenteConEquals() {
        // Arrange
        Username mario = new Username("Mario");
        Username marioMinuscolo = new Username("mario");
        // Assert: due username uguali (case-insensitive) hanno lo stesso hashCode
        assertTrue(mario.equals(marioMinuscolo));
        assertEquals(mario.hashCode(), marioMinuscolo.hashCode());
    }
}
