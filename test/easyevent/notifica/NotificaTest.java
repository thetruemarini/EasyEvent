package easyevent.notifica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link Notifica}: e' un dato strutturato (campi grezzi), non una
 * frase. I campi testuali null vengono normalizzati a stringa vuota.
 */
class NotificaTest {

    private static final LocalDate CREAZIONE = LocalDate.of(2026, 6, 1);

    @Test
    void notifica_CostruttoreValido_RepOkVero() {
        Notifica n = new Notifica(new IdNotifica(1), TipoNotifica.PROPOSTA_CONFERMATA, 1,
                "Concerto", "10/07/2026", "21:00", "Arena", "20", CREAZIONE);
        assertTrue(n.repOk());
    }

    @Test
    void notifica_IdNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Notifica(null, TipoNotifica.PROPOSTA_CONFERMATA, 1,
                        "Concerto", "10/07/2026", "21:00", "Arena", "20", CREAZIONE));
    }

    @Test
    void notifica_TipoNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Notifica(new IdNotifica(1), null, 1,
                        "Concerto", "10/07/2026", "21:00", "Arena", "20", CREAZIONE));
    }

    @Test
    void notifica_DataCreazioneNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Notifica(new IdNotifica(1), TipoNotifica.PROPOSTA_CONFERMATA, 1,
                        "Concerto", "10/07/2026", "21:00", "Arena", "20", null));
    }

    @Test
    void notifica_CampiTestualiNull_SostituitiConStringaVuota() {
        Notifica n = new Notifica(new IdNotifica(1), TipoNotifica.PROPOSTA_CONFERMATA, 1,
                null, null, null, null, null, CREAZIONE);
        assertEquals("", n.getTitoloProposta());
        assertEquals("", n.getDataEvento());
        assertEquals("", n.getOraEvento());
        assertEquals("", n.getLuogoEvento());
        assertEquals("", n.getQuotaIndividuale());
    }

    @Test
    void notifica_GetterRestituisconoDatiGrezzi() {
        Notifica n = new Notifica(new IdNotifica(7), TipoNotifica.PROPOSTA_ANNULLATA, 42,
                "Concerto", "10/07/2026", "21:00", "Arena", "20", CREAZIONE);
        assertEquals(7, n.getId().getValore());
        assertEquals(TipoNotifica.PROPOSTA_ANNULLATA, n.getTipo());
        assertEquals(42, n.getIdProposta());
        assertEquals("Concerto", n.getTitoloProposta());
        assertEquals(CREAZIONE, n.getDataCreazione());
    }
}
