package easyevent.notifica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Test unit del Value Object {@link IdNotifica}: validazione del valore non
 * negativo e semantica di uguaglianza per valore.
 */
class IdNotificaTest {

    @Test
    void id_ValoreNonNegativo_LoMemorizza() {
        IdNotifica id = new IdNotifica(5);
        assertEquals(5, id.getValore());
    }

    @Test
    void id_ValoreZero_Consentito() {
        assertEquals(0, new IdNotifica(0).getValore());
    }

    @Test
    void id_ValoreNegativo_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new IdNotifica(-1));
    }

    @Test
    void id_EqualsSuStessoValore() {
        assertEquals(new IdNotifica(3), new IdNotifica(3));
    }

    @Test
    void id_ValoriDiversi_NonUguali() {
        assertNotEquals(new IdNotifica(3), new IdNotifica(4));
    }

    @Test
    void id_HashCodeCoerente() {
        assertEquals(new IdNotifica(42).hashCode(), new IdNotifica(42).hashCode());
    }
}
