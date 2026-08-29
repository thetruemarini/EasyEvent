package easyevent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.categoria.Campo;
import easyevent.core.AppData;
import easyevent.exception.CredenzialiNonValideException;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.PersistenzaException;
import easyevent.model.Fruitore;
import easyevent.notifica.IdNotifica;
import easyevent.notifica.Notifica;
import easyevent.notifica.TipoNotifica;
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
 * Test unit di {@link FruitoreController}: orchestrazione pura (nessuna UI).
 * Verifica autenticazione, guardie di accesso e il rollback dello stato in
 * memoria quando il salvataggio su disco fallisce.
 */
class FruitoreControllerTest {

    @TempDir
    Path tempDir;

    /** PersistenceManager funzionante (scrive su file regolare). */
    private PersistenceManager okPM() {
        return new PersistenceManager(tempDir.resolve("data.json").toString());
    }

    /** PersistenceManager che fallisce sempre il salvataggio (path = directory). */
    private PersistenceManager failingPM() {
        return new PersistenceManager(tempDir.toString());
    }

    /** Pubblica una proposta APERTA con iscrizioni aperte rispetto a oggi reale. */
    private Proposta pubblicaApertaOggi(AppData app, int idVal) {
        LocalDate now = LocalDate.now();
        LinkedHashMap<String, Boolean> snap = new LinkedHashMap<>();
        if (!app.isCampiBaseInitialized()) {
            app.inizializzaCampiBase();
        }
        for (Campo c : app.getCampiBase()) {
            snap.put(c.getNome(), c.isObbligatorio());
        }
        Proposta p = new Proposta(new IdProposta(idVal), "Concerti", "creatore", snap);
        p.setValore("Titolo", "Concerto");
        p.setValore("Numero di partecipanti", "10");
        p.setValore("Termine ultimo di iscrizione", now.plusDays(30).format(Proposta.DATE_FORMAT));
        p.setValore("Luogo", "Arena");
        p.setValore("Data inizio", now.plusDays(40).format(Proposta.DATE_FORMAT));
        p.setValore("Ora", "21:00");
        p.setValore("Quota individuale", "20");
        p.setValore("Data conclusiva", now.plusDays(41).format(Proposta.DATE_FORMAT));
        app.pubblicaPropostaDiretta(p, now);
        return p;
    }

    private Notifica notifica(int id) {
        return new Notifica(new IdNotifica(id), TipoNotifica.PROPOSTA_CONFERMATA, 1,
                "Concerto", "10/07/2026", "21:00", "Arena", "20", LocalDate.of(2026, 6, 1));
    }

    // ================================================================
    // Costruttore
    // ================================================================
    @Test
    void costruttore_AppDataNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new FruitoreController(null, okPM()));
    }

    @Test
    void costruttore_PersistenceManagerNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new FruitoreController(new AppData(), null));
    }

    // ================================================================
    // Autenticazione
    // ================================================================
    @Test
    void login_CredenzialiCorrette_RestituisceTrueEImpostaLoggato() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        FruitoreController c = new FruitoreController(app, okPM());
        assertTrue(c.login("mario", "pwd123"));
        assertTrue(c.isLoggato());
    }

    @Test
    void login_PasswordSbagliata_RestituisceFalse() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        FruitoreController c = new FruitoreController(app, okPM());
        assertFalse(c.login("mario", "sbagliata"));
        assertFalse(c.isLoggato());
    }

    @Test
    void login_UtenteInesistente_RestituisceFalse() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        assertFalse(c.login("nessuno", "pwd"));
    }

    @Test
    void login_ArgomentiNull_RestituisceFalse() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        assertFalse(c.login(null, null));
    }

    @Test
    void registra_NuovoUtente_LoCreaELogga() {
        AppData app = new AppData();
        FruitoreController c = new FruitoreController(app, okPM());
        c.registra("mario", "pwd123");
        assertTrue(c.esisteFruitore("mario"));
        assertTrue(c.isLoggato());
    }

    @Test
    void registra_UsernameBlank_LanciaCredenzialiNonValide() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        CredenzialiNonValideException ex = assertThrows(CredenzialiNonValideException.class,
                () -> c.registra("  ", "pwd"));
        assertEquals(CredenzialiNonValideException.Motivo.USERNAME_VUOTO, ex.getMotivo());
    }

    @Test
    void registra_PasswordBlank_LanciaCredenzialiNonValide() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        CredenzialiNonValideException ex = assertThrows(CredenzialiNonValideException.class,
                () -> c.registra("mario", "  "));
        assertEquals(CredenzialiNonValideException.Motivo.PASSWORD_VUOTA, ex.getMotivo());
    }

    @Test
    void registra_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        FruitoreController c = new FruitoreController(app, failingPM());
        assertThrows(PersistenzaException.class, () -> c.registra("mario", "pwd123"));
        assertFalse(c.esisteFruitore("mario")); // rollback: utente rimosso
        assertFalse(c.isLoggato());
    }

    @Test
    void logout_AzzeraStato() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.logout();
        assertFalse(c.isLoggato());
    }

    // ================================================================
    // Iscrizioni
    // ================================================================
    @Test
    void aderisci_NonLoggato_LanciaModificaNonConsentita() {
        AppData app = new AppData();
        FruitoreController c = new FruitoreController(app, okPM());
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> c.aderisci(new IdProposta(1)));
        assertEquals(ModificaNonConsentitaException.TipoModifica.NESSUN_FRUITORE_LOGGATO,
                ex.getTipoModifica());
    }

    @Test
    void aderisci_PropostaInesistente_LanciaElementoNonTrovato() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        assertThrows(ElementoNonTrovatoException.class, () -> c.aderisci(new IdProposta(999)));
    }

    @Test
    void aderisci_PropostaAperta_AggiungeIscrizione() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p = pubblicaApertaOggi(app, 1);
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.aderisci(p.getId());
        assertTrue(c.isIscritto(p.getId()));
    }

    @Test
    void aderisci_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p = pubblicaApertaOggi(app, 1);
        FruitoreController c = new FruitoreController(app, failingPM());
        c.login("mario", "pwd123");
        assertThrows(RuntimeException.class, () -> c.aderisci(p.getId()));
        assertFalse(p.isAderito("mario")); // rollback
    }

    @Test
    void disdiciIscrizione_RimuoveIscrizione() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p = pubblicaApertaOggi(app, 1);
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.aderisci(p.getId());
        c.disdiciIscrizione(p.getId());
        assertFalse(c.isIscritto(p.getId()));
    }

    @Test
    void getProposteIscritto_RestituisceSoloQuelleSottoscritte() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p1 = pubblicaApertaOggi(app, 1);
        pubblicaApertaOggi(app, 2);
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.aderisci(p1.getId());
        List<Proposta> iscritto = c.getProposteIscritto();
        assertEquals(1, iscritto.size());
        assertEquals(p1.getId(), iscritto.get(0).getId());
    }

    @Test
    void getProposteDisponibili_EscludeQuelleGiaSottoscritte() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p1 = pubblicaApertaOggi(app, 1);
        Proposta p2 = pubblicaApertaOggi(app, 2);
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.aderisci(p1.getId());
        List<Proposta> disponibili = c.getProposteDisponibili();
        assertEquals(1, disponibili.size());
        assertEquals(p2.getId(), disponibili.get(0).getId());
    }

    @Test
    void getProposteDisponibili_NonLoggato_RestituisceListaVuota() {
        AppData app = new AppData();
        pubblicaApertaOggi(app, 1);
        FruitoreController c = new FruitoreController(app, okPM());
        assertTrue(c.getProposteDisponibili().isEmpty());
    }

    @Test
    void getNumeroIscrizioniAttive_ContaIscrizioni() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        Proposta p = pubblicaApertaOggi(app, 1);
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.aderisci(p.getId());
        assertEquals(1, c.getNumeroIscrizioniAttive());
    }

    // ================================================================
    // Notifiche
    // ================================================================
    @Test
    void cancellaNotifica_NonLoggato_Lancia() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        assertThrows(ModificaNonConsentitaException.class,
                () -> c.cancellaNotifica(new IdNotifica(1)));
    }

    @Test
    void cancellaNotifica_NotificaInesistente_LanciaElementoNonTrovato() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        assertThrows(ElementoNonTrovatoException.class,
                () -> c.cancellaNotifica(new IdNotifica(99)));
    }

    @Test
    void cancellaNotifica_Esistente_LaRimuove() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        app.getFruitore("mario").aggiungiNotifica(notifica(1));
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.cancellaNotifica(new IdNotifica(1));
        assertTrue(app.getFruitore("mario").getNotifiche().isEmpty());
    }

    @Test
    void cancellaNotifica_ErrorePersistenza_RollbackERilancia() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        app.getFruitore("mario").aggiungiNotifica(notifica(1));
        FruitoreController c = new FruitoreController(app, failingPM());
        c.login("mario", "pwd123");
        assertThrows(PersistenzaException.class, () -> c.cancellaNotifica(new IdNotifica(1)));
        assertEquals(1, app.getFruitore("mario").getNotifiche().size()); // rollback
    }

    @Test
    void cancellaAllNotifiche_RimuoveTutte() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd123"));
        app.getFruitore("mario").aggiungiNotifica(notifica(1));
        app.getFruitore("mario").aggiungiNotifica(notifica(2));
        FruitoreController c = new FruitoreController(app, okPM());
        c.login("mario", "pwd123");
        c.cancellaAllNotifiche();
        assertTrue(app.getFruitore("mario").getNotifiche().isEmpty());
    }

    @Test
    void getNotifiche_NonLoggato_ListaVuota() {
        FruitoreController c = new FruitoreController(new AppData(), okPM());
        assertTrue(c.getNotifiche().isEmpty());
    }
}
