package easyevent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.core.AppData;
import easyevent.exception.CredenzialiNonValideException;
import easyevent.exception.ElementoInSessioneException;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.PersistenzaException;
import easyevent.persistence.PersistenceManager;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test unit di {@link ConfiguratoreController}: orchestrazione pura (nessuna
 * UI). Verifica autenticazione (incluso primo accesso admin), guardie di
 * accesso, regole di rimozione di sessione e rollback su errore di persistenza.
 */
class ConfiguratoreControllerTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PWD = "admin";

    @TempDir
    Path tempDir;

    private PersistenceManager okPM() {
        return new PersistenceManager(tempDir.resolve("data.json").toString());
    }

    private PersistenceManager failingPM() {
        return new PersistenceManager(tempDir.toString());
    }

    /** Controller con admin loggato (bootstrap primo accesso) e PM funzionante. */
    private ConfiguratoreController loggato(AppData app) {
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        c.login(ADMIN, ADMIN_PWD);
        return c;
    }

    private void riempiCampiBase(ConfiguratoreController c, Proposta p) {
        LocalDate now = LocalDate.now();
        c.setValoreCampo(p, "Titolo", "Concerto");
        c.setValoreCampo(p, "Numero di partecipanti", "10");
        c.setValoreCampo(p, "Termine ultimo di iscrizione", now.plusDays(30).format(Proposta.DATE_FORMAT));
        c.setValoreCampo(p, "Luogo", "Arena");
        c.setValoreCampo(p, "Data inizio", now.plusDays(40).format(Proposta.DATE_FORMAT));
        c.setValoreCampo(p, "Ora", "21:00");
        c.setValoreCampo(p, "Quota individuale", "20");
        c.setValoreCampo(p, "Data conclusiva", now.plusDays(41).format(Proposta.DATE_FORMAT));
    }

    // ================================================================
    // Costruttore
    // ================================================================
    @Test
    void costruttore_AppDataNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguratoreController(null, okPM(), ADMIN, ADMIN_PWD));
    }

    @Test
    void costruttore_PersistenceManagerNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfiguratoreController(new AppData(), null, ADMIN, ADMIN_PWD));
    }

    // ================================================================
    // Autenticazione
    // ================================================================
    @Test
    void login_PrimoAccessoAdminDefault_CreaEAccede() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        assertTrue(c.login(ADMIN, ADMIN_PWD));
        assertTrue(c.isLoggato());
        assertEquals(1, app.getConfiguratori().size());
    }

    @Test
    void login_AdminDefaultSbagliato_QuandoVuoto_RestituisceFalse() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        assertFalse(c.login(ADMIN, "sbagliata"));
    }

    @Test
    void login_ConfiguratoreEsistente_PasswordSbagliata_RestituisceFalse() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app); // crea admin
        c.logout();
        assertFalse(c.login(ADMIN, "sbagliata"));
    }

    @Test
    void impostaCredenzialiPersonali_NonLoggato_Lancia() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> c.impostaCredenzialiPersonali("nuovo", "pwd"));
        assertEquals(ModificaNonConsentitaException.TipoModifica.NESSUN_CONFIGURATORE_LOGGATO,
                ex.getTipoModifica());
    }

    @Test
    void impostaCredenzialiPersonali_PrimoAccesso_AggiornaCredenziali() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.impostaCredenzialiPersonali("nuovoAdmin", "nuovaPwd");
        assertFalse(c.richiedeCambioCredenziali());
        assertEquals("nuovoAdmin", c.getConfiguratoreCorrente().getUsername());
    }

    @Test
    void impostaCredenzialiPersonali_UsernameBlank_LanciaCredenzialiNonValide() {
        ConfiguratoreController c = loggato(new AppData());
        CredenzialiNonValideException ex = assertThrows(CredenzialiNonValideException.class,
                () -> c.impostaCredenzialiPersonali("  ", "pwd"));
        assertEquals(CredenzialiNonValideException.Motivo.USERNAME_VUOTO, ex.getMotivo());
    }

    @Test
    void impostaCredenzialiPersonali_PasswordBlank_LanciaCredenzialiNonValide() {
        ConfiguratoreController c = loggato(new AppData());
        CredenzialiNonValideException ex = assertThrows(CredenzialiNonValideException.class,
                () -> c.impostaCredenzialiPersonali("nuovo", "  "));
        assertEquals(CredenzialiNonValideException.Motivo.PASSWORD_VUOTA, ex.getMotivo());
    }

    @Test
    void impostaCredenzialiPersonali_GiaImpostate_Lancia() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.impostaCredenzialiPersonali("nuovoAdmin", "nuovaPwd");
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> c.impostaCredenzialiPersonali("altro", "altra"));
        assertEquals(ModificaNonConsentitaException.TipoModifica.CREDENZIALI_GIA_IMPOSTATE,
                ex.getTipoModifica());
    }

    @Test
    void impostaCredenzialiPersonali_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        // login bootstrap (ignora errori di persistenza), poi PM fallisce sul cambio
        ConfiguratoreController c =
                new ConfiguratoreController(app, failingPM(), ADMIN, ADMIN_PWD);
        c.login(ADMIN, ADMIN_PWD);
        assertThrows(PersistenzaException.class,
                () -> c.impostaCredenzialiPersonali("nuovoAdmin", "nuovaPwd"));
        // rollback: credenziali tornate a quelle originali
        assertEquals(ADMIN, c.getConfiguratoreCorrente().getUsername());
        assertTrue(c.richiedeCambioCredenziali());
    }

    // ================================================================
    // Campi comuni — guardie e rollback
    // ================================================================
    @Test
    void aggiungiCampoComune_NonLoggato_LanciaAccessoNegato() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> c.aggiungiCampoComune("Sito web", false));
        assertEquals(ModificaNonConsentitaException.TipoModifica.ACCESSO_NEGATO,
                ex.getTipoModifica());
    }

    @Test
    void aggiungiCampoComune_Loggato_LoAggiunge() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.aggiungiCampoComune("Sito web", false);
        assertTrue(app.esisteCampoComune("Sito web"));
    }

    @Test
    void aggiungiCampoComune_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, failingPM(), ADMIN, ADMIN_PWD);
        c.login(ADMIN, ADMIN_PWD);
        assertThrows(PersistenzaException.class, () -> c.aggiungiCampoComune("Sito web", false));
        assertFalse(app.esisteCampoComune("Sito web")); // rollback
    }

    @Test
    void rimuoviCampoComune_UsatoInSessione_LanciaElementoInSessione() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCampoComune("Sito web", false);
        c.aggiungiCategoria("Concerti");
        c.creaProposta("Concerti"); // la proposta di sessione usa "Sito web"
        ElementoInSessioneException ex = assertThrows(ElementoInSessioneException.class,
                () -> c.rimuoviCampoComune("Sito web"));
        assertEquals(ElementoInSessioneException.TipoElemento.CAMPO_COMUNE, ex.getTipoElemento());
    }

    @Test
    void rimuoviCampoComune_Inesistente_LanciaElementoNonTrovato() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        assertThrows(ElementoNonTrovatoException.class,
                () -> c.rimuoviCampoComune("Inesistente"));
    }

    // ================================================================
    // Categorie
    // ================================================================
    @Test
    void aggiungiCategoria_Loggato_LaAggiunge() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.aggiungiCategoria("Concerti");
        assertTrue(app.esisteCategoria("Concerti"));
    }

    @Test
    void aggiungiCategoria_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, failingPM(), ADMIN, ADMIN_PWD);
        c.login(ADMIN, ADMIN_PWD);
        assertThrows(PersistenzaException.class, () -> c.aggiungiCategoria("Concerti"));
        assertFalse(app.esisteCategoria("Concerti")); // rollback
    }

    @Test
    void rimuoviCategoria_UsataInSessione_LanciaElementoInSessione() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        c.creaProposta("Concerti"); // proposta di sessione nella categoria
        assertThrows(ElementoInSessioneException.class,
                () -> c.rimuoviCategoria("Concerti"));
    }

    // ================================================================
    // Proposte di sessione
    // ================================================================
    @Test
    void creaProposta_CategoriaEsistente_AggiungeASessione() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        Proposta p = c.creaProposta("Concerti");
        assertTrue(c.getProposteSessione().contains(p));
    }

    @Test
    void creaProposta_CategoriaInesistente_RestituisceNull() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        assertNull(c.creaProposta("Inesistente"));
    }

    @Test
    void creaProposta_NonLoggato_RestituisceNull() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        assertNull(c.creaProposta("Concerti"));
    }

    @Test
    void pubblicaProposta_NonInSessione_Lancia() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        // proposta creata fuori dalla sessione del controller
        Proposta esterna = new Proposta(new IdProposta(99), "Concerti", ADMIN,
                new LinkedHashMap<>());
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> c.pubblicaProposta(esterna));
        assertEquals(ModificaNonConsentitaException.TipoModifica.PROPOSTA_NON_IN_SESSIONE,
                ex.getTipoModifica());
    }

    @Test
    void pubblicaProposta_Valida_PubblicaERimuoveDaSessione() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        Proposta p = c.creaProposta("Concerti");
        riempiCampiBase(c, p);
        List<?> errori = c.pubblicaProposta(p);
        assertTrue(errori.isEmpty());
        assertEquals(1, app.getBacheca().size());
        assertFalse(c.getProposteSessione().contains(p));
    }

    @Test
    void getProposteSessionePubblicabili_RestituisceSoloLeValide() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        Proposta completa = c.creaProposta("Concerti");
        riempiCampiBase(c, completa);
        c.creaProposta("Concerti"); // resta in BOZZA: nessun campo compilato
        List<Proposta> pubblicabili = c.getProposteSessionePubblicabili();
        assertEquals(1, pubblicabili.size());
        assertEquals(completa.getId(), pubblicabili.get(0).getId());
    }

    @Test
    void getErroriValidazione_PropostaNull_LanciaIllegalArgument() {
        ConfiguratoreController c = loggato(new AppData());
        assertThrows(IllegalArgumentException.class, () -> c.getErroriValidazione(null));
    }

    @Test
    void getErroriValidazione_PropostaSenzaCampiCompilati_RestituisceErrori() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        Proposta p = c.creaProposta("Concerti");
        assertFalse(c.getErroriValidazione(p).isEmpty());
    }

    @Test
    void getErroriValidazione_PropostaCompleta_RestituisceListaVuota() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        c.inizializzaCampiBase();
        c.aggiungiCategoria("Concerti");
        Proposta p = c.creaProposta("Concerti");
        riempiCampiBase(c, p);
        assertTrue(c.getErroriValidazione(p).isEmpty());
    }

    // ================================================================
    // Ritiro
    // ================================================================
    @Test
    void ritirareProposta_NonLoggato_LanciaAccessoNegato() {
        AppData app = new AppData();
        ConfiguratoreController c =
                new ConfiguratoreController(app, okPM(), ADMIN, ADMIN_PWD);
        assertThrows(ModificaNonConsentitaException.class,
                () -> c.ritirareProposta(new IdProposta(1)));
    }

    @Test
    void ritirareProposta_Inesistente_LanciaElementoNonTrovato() {
        AppData app = new AppData();
        ConfiguratoreController c = loggato(app);
        assertThrows(ElementoNonTrovatoException.class,
                () -> c.ritirareProposta(new IdProposta(999)));
    }
}
