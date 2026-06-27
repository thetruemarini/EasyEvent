package easyevent.proposta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Test unit del Value Object {@link IdProposta}: validazione del valore non
 * negativo e semantica di uguaglianza per valore.
 */
class IdPropostaTest {

    @Test
    void id_ValoreNonNegativo_LoMemorizza() {
        IdProposta id = new IdProposta(7);
        assertEquals(7, id.getValore());
    }

    @Test
    void id_ValoreZero_Consentito() {
        assertEquals(0, new IdProposta(0).getValore());
    }

    @Test
    void id_ValoreNegativo_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new IdProposta(-1));
    }

    @Test
    void id_EqualsSuStessoValore() {
        assertEquals(new IdProposta(3), new IdProposta(3));
    }

    @Test
    void id_ValoriDiversi_NonUguali() {
        assertNotEquals(new IdProposta(3), new IdProposta(4));
    }

    @Test
    void id_HashCodeCoerente() {
        assertEquals(new IdProposta(42).hashCode(), new IdProposta(42).hashCode());
    }
}
