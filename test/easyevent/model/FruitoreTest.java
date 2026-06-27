package easyevent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.notifica.IdNotifica;
import easyevent.notifica.Notifica;
import easyevent.notifica.TipoNotifica;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link Fruitore}: gestione della lista di notifiche con
 * protezione della collezione interna.
 */
class FruitoreTest {

    private Notifica notifica(int id) {
        return new Notifica(new IdNotifica(id), TipoNotifica.PROPOSTA_CONFERMATA, 1,
                "Concerto", "10/07/2026", "21:00", "Arena", "20", LocalDate.of(2026, 6, 1));
    }

    @Test
    void aggiungiNotifica_Valida_EntraInLista() {
        Fruitore f = new Fruitore("mario", "pwd");
        f.aggiungiNotifica(notifica(1));
        assertEquals(1, f.getNotifiche().size());
    }

    @Test
    void aggiungiNotifica_Null_LanciaIllegalArgument() {
        Fruitore f = new Fruitore("mario", "pwd");
        assertThrows(IllegalArgumentException.class, () -> f.aggiungiNotifica(null));
    }

    @Test
    void rimuoviNotifica_IdPresente_RestituisceTrueELaRimuove() {
        Fruitore f = new Fruitore("mario", "pwd");
        f.aggiungiNotifica(notifica(1));
        assertTrue(f.rimuoviNotifica(new IdNotifica(1)));
        assertEquals(0, f.getNotifiche().size());
    }

    @Test
    void rimuoviNotifica_IdAssente_RestituisceFalse() {
        Fruitore f = new Fruitore("mario", "pwd");
        f.aggiungiNotifica(notifica(1));
        assertFalse(f.rimuoviNotifica(new IdNotifica(99)));
    }

    @Test
    void ripristinaNotifiche_SostituisceLaLista() {
        Fruitore f = new Fruitore("mario", "pwd");
        f.aggiungiNotifica(notifica(1));
        List<Notifica> nuove = new ArrayList<>();
        nuove.add(notifica(2));
        nuove.add(notifica(3));
        f.ripristinaNotifiche(nuove);
        assertEquals(2, f.getNotifiche().size());
    }

    @Test
    void getNotifiche_NonModificabile() {
        Fruitore f = new Fruitore("mario", "pwd");
        assertThrows(UnsupportedOperationException.class,
                () -> f.getNotifiche().add(notifica(1)));
    }
}
