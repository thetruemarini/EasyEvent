package easyevent.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.categoria.Campo;
import easyevent.categoria.Categoria;
import easyevent.core.AppData;
import easyevent.persistence.PersistenceManager;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import easyevent.proposta.StatoProposta;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test end-to-end (in memoria, senza UI): esercita la spina dorsale del
 * sistema — creazione, pubblicazione, salvataggio e ricarica — verificando che
 * lo stato resti coerente. Uno solo, in cima alla piramide.
 */
class FlussoBaseEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void flusso_CreaPubblicaSalvaRicarica_StatoCoerente() {
        LocalDate oggi = LocalDate.of(2026, 6, 1);

        // Arrange: sistema con campi base e una categoria
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));

        // Crea una proposta valida valorizzando tutti i campi base
        LinkedHashMap<String, Boolean> snap = new LinkedHashMap<>();
        for (Campo c : app.getCampiBase()) {
            snap.put(c.getNome(), c.isObbligatorio());
        }
        IdProposta id = app.getNuovoIdProposta();
        Proposta p = new Proposta(id, "Concerti", "creatore", snap);
        p.setValore("Titolo", "Concerto Rock");
        p.setValore("Numero di partecipanti", "10");
        p.setValore("Termine ultimo di iscrizione", oggi.plusDays(10).format(Proposta.DATE_FORMAT));
        p.setValore("Luogo", "Arena");
        p.setValore("Data inizio", oggi.plusDays(15).format(Proposta.DATE_FORMAT));
        p.setValore("Ora", "21:00");
        p.setValore("Quota individuale", "20");
        p.setValore("Data conclusiva", oggi.plusDays(20).format(Proposta.DATE_FORMAT));

        // Act: pubblica e salva
        app.pubblicaPropostaDiretta(p, oggi);
        PersistenceManager pm = new PersistenceManager(tempDir.resolve("data.json").toString());
        pm.salvaSicuro(app);

        // Ricarica in un nuovo AppData
        AppData ricaricato = new AppData();
        assertTrue(pm.caricaSicuro(ricaricato));

        // Assert: la bacheca contiene 1 proposta APERTA
        assertEquals(1, ricaricato.getBacheca().size());
        assertEquals(StatoProposta.APERTA, ricaricato.getBacheca().get(0).getStato());
    }
}
