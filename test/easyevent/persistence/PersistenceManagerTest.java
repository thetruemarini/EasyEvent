package easyevent.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.categoria.Campo;
import easyevent.categoria.CampoComune;
import easyevent.categoria.Categoria;
import easyevent.core.AppData;
import easyevent.model.Configuratore;
import easyevent.model.Fruitore;
import easyevent.notifica.IdNotifica;
import easyevent.notifica.Notifica;
import easyevent.notifica.TipoNotifica;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import easyevent.proposta.StatoProposta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test di integrazione di {@link PersistenceManager}: tocca il file system, ma
 * in modo controllato usando una directory temporanea creata e distrutta dal
 * test ({@code @TempDir}). Verifica il round-trip salva/carica.
 */
class PersistenceManagerTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 6, 1);

    @TempDir
    Path tempDir;

    private String filePath() {
        return tempDir.resolve("data.json").toString();
    }

    /** Costruisce e pubblica una proposta APERTA completa (tutti i campi base). */
    private Proposta propostaApertaCompleta(AppData app, String categoria) {
        LinkedHashMap<String, Boolean> snap = new LinkedHashMap<>();
        for (Campo c : app.getCampiBase()) {
            snap.put(c.getNome(), c.isObbligatorio());
        }
        IdProposta id = app.getNuovoIdProposta();
        Proposta p = new Proposta(id, categoria, "creatore", snap);
        p.setValore("Titolo", "Concerto Rock");
        p.setValore("Numero di partecipanti", "10");
        p.setValore("Termine ultimo di iscrizione", OGGI.plusDays(10).format(Proposta.DATE_FORMAT));
        p.setValore("Luogo", "Arena");
        p.setValore("Data inizio", OGGI.plusDays(15).format(Proposta.DATE_FORMAT));
        p.setValore("Ora", "21:00");
        p.setValore("Quota individuale", "20");
        p.setValore("Data conclusiva", OGGI.plusDays(20).format(Proposta.DATE_FORMAT));
        app.pubblicaPropostaDiretta(p, OGGI);
        return p;
    }

    @Test
    void carica_FileInesistente_RestituisceFalse() {
        PersistenceManager pm = new PersistenceManager(filePath());
        assertFalse(pm.caricaSicuro(new AppData()));
    }

    @Test
    void caricaSicuro_FileInesistente_NonLanciaEPreservaAppDataVuota() {
        PersistenceManager pm = new PersistenceManager(filePath());
        AppData app = new AppData();
        assertDoesNotThrow(() -> pm.caricaSicuro(app));
        assertTrue(app.getConfiguratori().isEmpty());
        assertTrue(app.getArchivio().isEmpty());
    }

    @Test
    void salvaECarica_RoundTrip_DatiCoerenti() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiConfiguratore(new Configuratore("admin", "pwd"));
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        app.aggiungiCampoComune(new CampoComune("Sito web", false));
        app.aggiungiCategoria(new Categoria("Concerti"));
        Proposta p = propostaApertaCompleta(app, "Concerti");
        p.aggiungiAderente("mario", OGGI);

        PersistenceManager pm = new PersistenceManager(filePath());
        pm.salvaSicuro(app);

        AppData ricaricato = new AppData();
        assertTrue(pm.caricaSicuro(ricaricato));

        assertEquals(1, ricaricato.getConfiguratori().size());
        assertEquals("admin", ricaricato.getConfiguratori().get(0).getUsername());
        assertEquals(1, ricaricato.getFruitori().size());
        assertTrue(ricaricato.esisteCampoComune("Sito web"));
        assertEquals(1, ricaricato.getCategorie().size());
        assertEquals(1, ricaricato.getArchivio().size());

        Proposta caricata = ricaricato.getArchivio().get(0);
        assertEquals(StatoProposta.APERTA, caricata.getStato());
        assertTrue(caricata.isAderito("mario"));
        assertEquals(app.getProssimoIdProposta(), ricaricato.getProssimoIdProposta());
    }

    @Test
    void salvaECarica_PropostaConStoricoStati_PreservaStorico() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));
        Proposta p = propostaApertaCompleta(app, "Concerti");
        p.transitaStato(StatoProposta.CONFERMATA, OGGI); // storico: APERTA, CONFERMATA

        PersistenceManager pm = new PersistenceManager(filePath());
        pm.salvaSicuro(app);

        AppData ricaricato = new AppData();
        pm.caricaSicuro(ricaricato);

        assertEquals(2, ricaricato.getArchivio().get(0).getStoricoStati().size());
    }

    @Test
    void salvaECarica_FruitoreConNotifiche_PreservaTipoEIdNotifica() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        // id notifica (1) diverso da idProposta (5): smaschera l'uso errato del campo
        Notifica n = new Notifica(new IdNotifica(1), TipoNotifica.PROPOSTA_CONFERMATA, 5,
                "Concerto", "10/07/2026", "21:00", "Arena", "20", OGGI);
        app.getFruitore("mario").aggiungiNotifica(n);

        PersistenceManager pm = new PersistenceManager(filePath());
        pm.salvaSicuro(app);

        AppData ricaricato = new AppData();
        pm.caricaSicuro(ricaricato);

        Fruitore mario = ricaricato.getFruitore("mario");
        assertEquals(1, mario.getNotifiche().size());
        Notifica caricata = mario.getNotifiche().get(0);
        assertEquals(TipoNotifica.PROPOSTA_CONFERMATA, caricata.getTipo());
        assertEquals(1, caricata.getId().getValore()); // ID proprio, non idProposta
        assertEquals(5, caricata.getIdProposta());
    }

    @Test
    void carica_FileIntegro_NessunaPropostaScartata() throws Exception {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));
        propostaApertaCompleta(app, "Concerti");
        PersistenceManager pm = new PersistenceManager(filePath());
        pm.salvaSicuro(app);
        AppData ricaricato = new AppData();
        pm.caricaSicuro(ricaricato);
        assertEquals(0, pm.getProposteScartate());
        assertEquals(1, ricaricato.getArchivio().size());
    }

    @Test
    void carica_PropostaConRecordNonValido_LaScartaELaConta() throws Exception {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));
        propostaApertaCompleta(app, "Concerti");
        PersistenceManager pm = new PersistenceManager(filePath());
        pm.salvaSicuro(app);

        // Rende non valido il record su disco: la categoria diventa vuota.
        Path file = tempDir.resolve("data.json");
        Files.writeString(file,
                Files.readString(file).replace("\"nomeCategoria\": \"Concerti\"",
                        "\"nomeCategoria\": \"\""));

        AppData ricaricato = new AppData();
        pm.caricaSicuro(ricaricato);
        assertEquals(1, pm.getProposteScartate());
        assertTrue(ricaricato.getArchivio().isEmpty());
    }

    @Test
    void carica_FileMalformato_GestitoSenzaCrash() throws Exception {
        Files.writeString(tempDir.resolve("data.json"),
                "{ \"configuratori\": [ {\"username\": \"ad");
        PersistenceManager pm = new PersistenceManager(filePath());
        assertDoesNotThrow(() -> pm.caricaSicuro(new AppData()));
    }
}
