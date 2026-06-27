package easyevent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link BatchRisultato}: accumulatore di esiti di un'importazione.
 * Logica pura in memoria, nessuna I/O.
 */
class BatchRisultatoTest {

    @Test
    void aggiungiSuccesso_IncrementaSuccessi() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiSuccesso("ok");
        assertEquals(1, r.getNumSuccessi());
    }

    @Test
    void aggiungiWarning_IncrementaWarnings_ConRigaEMessaggio() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiWarning(3, "attenzione");
        assertEquals(1, r.getNumWarnings());
        BatchRisultato.Voce voce = r.getVoci().get(0);
        assertEquals(3, voce.getNumeroRiga());
        assertEquals("attenzione", voce.getMessaggio());
    }

    @Test
    void aggiungiErrore_IncrementaErrori() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiErrore(5, "errore");
        assertEquals(1, r.getNumErrori());
    }

    @Test
    void incrementaRighe_AumentaRigheTotali() {
        BatchRisultato r = new BatchRisultato();
        r.incrementaRighe();
        r.incrementaRighe();
        assertEquals(2, r.getRigheTotali());
    }

    @Test
    void isSenzaErrori_ConSoliWarning_Vero() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiWarning(1, "w");
        assertTrue(r.isSenzaErrori());
    }

    @Test
    void isSenzaErrori_ConErrore_Falso() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiErrore(1, "e");
        assertFalse(r.isSenzaErrori());
    }

    @Test
    void isCompletamenteOk_ConWarning_Falso() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiWarning(1, "w");
        assertFalse(r.isCompletamenteOk());
    }

    @Test
    void isCompletamenteOk_SoloSuccessi_Vero() {
        BatchRisultato r = new BatchRisultato();
        r.aggiungiSuccesso("ok");
        assertTrue(r.isCompletamenteOk());
    }

    @Test
    void aggiungi_SommaDueRisultati() {
        BatchRisultato a = new BatchRisultato();
        a.incrementaRighe();
        a.aggiungiSuccesso("ok");
        BatchRisultato b = new BatchRisultato();
        b.incrementaRighe();
        b.aggiungiErrore(1, "e");
        a.aggiungi(b);
        assertEquals(2, a.getRigheTotali());
        assertEquals(1, a.getNumSuccessi());
        assertEquals(1, a.getNumErrori());
    }
}
