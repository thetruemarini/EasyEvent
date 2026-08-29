package easyevent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.categoria.CampoBase;
import easyevent.categoria.CampoComune;
import easyevent.exception.ElementoGiaEsistenteException;
import easyevent.model.Configuratore;
import easyevent.model.Fruitore;
import easyevent.notifica.Notifica;
import easyevent.notifica.TipoNotifica;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import easyevent.proposta.StatoProposta;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link AppData}: orchestratore del dominio. Verifica
 * registrazione utenti, regole di rimozione di sessione, filtri di bacheca e
 * transizioni automatiche con relative notifiche. Tutto in memoria, tempo
 * iniettato.
 */
class AppDataTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 6, 1);

    private String fmt(LocalDate d) {
        return d.format(Proposta.DATE_FORMAT);
    }

    private LinkedHashMap<String, Boolean> campi() {
        LinkedHashMap<String, Boolean> c = new LinkedHashMap<>();
        c.put("Titolo", true);
        c.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, true);
        c.put(Proposta.CAMPO_DATA, true);
        c.put(Proposta.CAMPO_DATA_CONCLUSIVA, false);
        c.put(Proposta.CAMPO_NUM_PARTECIPANTI, true);
        return c;
    }

    /** Costruisce una proposta valida (BOZZA) nella categoria indicata. */
    private Proposta propostaValida(int idVal, String categoria, String num) {
        Proposta p = new Proposta(new IdProposta(idVal), categoria, "creatore", campi());
        p.setValore("Titolo", "Concerto Rock");
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(OGGI.plusDays(10)));
        p.setValore(Proposta.CAMPO_DATA, fmt(OGGI.plusDays(15)));
        p.setValore(Proposta.CAMPO_DATA_CONCLUSIVA, fmt(OGGI.plusDays(20)));
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, num);
        return p;
    }

    /** Pubblica una proposta valida in bacheca e la restituisce (APERTA). */
    private Proposta pubblica(AppData app, int idVal, String categoria, String num) {
        Proposta p = propostaValida(idVal, categoria, num);
        app.pubblicaPropostaDiretta(p, OGGI);
        return p;
    }

    // ================================================================
    // Registrazione utenti e unicita' username
    // ================================================================
    @Test
    void aggiungiConfiguratore_NuovoUsername_LoAggiunge() {
        AppData app = new AppData();
        app.aggiungiConfiguratore(new Configuratore("admin", "pwd"));
        assertTrue(app.esisteUsernameGlobale("admin"));
    }

    @Test
    void aggiungiConfiguratore_Null_LanciaIllegalArgument() {
        AppData app = new AppData();
        assertThrows(IllegalArgumentException.class, () -> app.aggiungiConfiguratore(null));
    }

    @Test
    void aggiungiConfiguratore_UsernameDuplicato_LanciaElementoGiaEsistente() {
        AppData app = new AppData();
        app.aggiungiConfiguratore(new Configuratore("admin", "pwd"));
        ElementoGiaEsistenteException ex = assertThrows(ElementoGiaEsistenteException.class,
                () -> app.aggiungiConfiguratore(new Configuratore("Admin", "altra")));
        assertEquals(ElementoGiaEsistenteException.TipoElemento.USERNAME, ex.getTipoElemento());
    }

    @Test
    void aggiungiFruitore_UsernameGiaUsatoDaConfiguratore_LanciaElementoGiaEsistente() {
        AppData app = new AppData();
        app.aggiungiConfiguratore(new Configuratore("mario", "pwd"));
        assertThrows(ElementoGiaEsistenteException.class,
                () -> app.aggiungiFruitore(new Fruitore("Mario", "pwd2")));
    }

    @Test
    void rimuoviFruitore_Esistente_RestituisceTrue() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        assertTrue(app.rimuoviFruitore("mario"));
    }

    @Test
    void rimuoviFruitore_Inesistente_RestituisceFalse() {
        AppData app = new AppData();
        assertFalse(app.rimuoviFruitore("nessuno"));
    }

    // ================================================================
    // Campi base e comuni
    // ================================================================
    @Test
    void inizializzaCampiBase_PrimaChiamata_CreaTuttiICampiBase() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        assertEquals(CampoBase.NOMI.size(), app.getCampiBase().size());
        assertTrue(app.isCampiBaseInitialized());
    }

    @Test
    void inizializzaCampiBase_SecondaChiamata_LanciaIllegalState() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        assertThrows(IllegalStateException.class, app::inizializzaCampiBase);
    }

    @Test
    void aggiungiCampoComune_Nuovo_LoAggiunge() {
        AppData app = new AppData();
        app.aggiungiCampoComune(new CampoComune("Sito web", false));
        assertTrue(app.esisteCampoComune("Sito web"));
    }

    @Test
    void aggiungiCampoComune_Duplicato_LanciaElementoGiaEsistente() {
        AppData app = new AppData();
        app.aggiungiCampoComune(new CampoComune("Sito web", false));
        ElementoGiaEsistenteException ex = assertThrows(ElementoGiaEsistenteException.class,
                () -> app.aggiungiCampoComune(new CampoComune("sito web", true)));
        assertEquals(ElementoGiaEsistenteException.TipoElemento.CAMPO_COMUNE, ex.getTipoElemento());
    }

    @Test
    void aggiungiCampoComune_NomeUgualeACampoBase_LanciaElementoGiaEsistente() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        ElementoGiaEsistenteException ex = assertThrows(ElementoGiaEsistenteException.class,
                () -> app.aggiungiCampoComune(new CampoComune("Luogo", false)));
        assertEquals(ElementoGiaEsistenteException.TipoElemento.CAMPO_BASE, ex.getTipoElemento());
    }

    @Test
    void rimuoviCampoComune_Esistente_RestituisceTrue() {
        AppData app = new AppData();
        app.aggiungiCampoComune(new CampoComune("Sito web", false));
        assertTrue(app.rimuoviCampoComune("Sito web"));
    }

    @Test
    void modificaObbligatorietaCampoComune_Esistente_AggiornaFlag() {
        AppData app = new AppData();
        app.aggiungiCampoComune(new CampoComune("Sito web", false));
        assertTrue(app.modificaObbligatorietaCampoComune("Sito web", true));
        assertTrue(app.getCampoComune("Sito web").isObbligatorio());
    }

    // ================================================================
    // Regole di rimozione di sessione
    // ================================================================
    @Test
    void categoriaUsataInSessione_PropostaUsaCategoria_RestituisceTrue() {
        AppData app = new AppData();
        List<Proposta> sessione = List.of(propostaValida(1, "Concerti", "10"));
        assertTrue(app.categoriaUsataInSessione(sessione, "Concerti"));
    }

    @Test
    void categoriaUsataInSessione_NessunaProposta_RestituisceFalse() {
        AppData app = new AppData();
        assertFalse(app.categoriaUsataInSessione(List.of(), "Concerti"));
    }

    @Test
    void categoriaUsataInSessione_ListaNull_RestituisceFalse() {
        AppData app = new AppData();
        assertFalse(app.categoriaUsataInSessione(null, "Concerti"));
    }

    @Test
    void categoriaUsataInSessione_NomeCaseInsensitive_RestituisceTrue() {
        AppData app = new AppData();
        List<Proposta> sessione = List.of(propostaValida(1, "Concerti", "10"));
        assertTrue(app.categoriaUsataInSessione(sessione, "CONCERTI"));
    }

    @Test
    void campoUsatoInSessione_PropostaUsaCampo_RestituisceTrue() {
        AppData app = new AppData();
        List<Proposta> sessione = List.of(propostaValida(1, "Concerti", "10"));
        assertTrue(app.campoUsatoInSessione(sessione, "Titolo"));
    }

    @Test
    void campoUsatoInSessione_CampoNonUsato_RestituisceFalse() {
        AppData app = new AppData();
        List<Proposta> sessione = List.of(propostaValida(1, "Concerti", "10"));
        assertFalse(app.campoUsatoInSessione(sessione, "Campo Inesistente"));
    }

    @Test
    void campoUsatoInSessione_NullSafe_RestituisceFalse() {
        AppData app = new AppData();
        assertFalse(app.campoUsatoInSessione(null, "Titolo"));
        assertFalse(app.campoUsatoInSessione(List.of(propostaValida(1, "Concerti", "10")), null));
    }

    // ================================================================
    // Archivio, bacheca e filtri
    // ================================================================
    @Test
    void aggiungiPropostaAperta_StatoNonAperto_LanciaIllegalArgument() {
        AppData app = new AppData();
        Proposta bozza = propostaValida(1, "Concerti", "10");
        assertThrows(IllegalArgumentException.class, () -> app.aggiungiPropostaAperta(bozza));
    }

    @Test
    void pubblicaPropostaDiretta_PropostaValida_RestituisceListaVuotaEEntraInBacheca() {
        AppData app = new AppData();
        Proposta p = propostaValida(1, "Concerti", "10");
        List<?> errori = app.pubblicaPropostaDiretta(p, OGGI);
        assertTrue(errori.isEmpty());
        assertEquals(StatoProposta.APERTA, p.getStato());
        assertEquals(1, app.getBacheca().size());
    }

    @Test
    void pubblicaPropostaDiretta_PropostaNonValida_RestituisceErrori() {
        AppData app = new AppData();
        // Manca il "Titolo" obbligatorio
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campi());
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(OGGI.plusDays(10)));
        p.setValore(Proposta.CAMPO_DATA, fmt(OGGI.plusDays(15)));
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "10");
        List<?> errori = app.pubblicaPropostaDiretta(p, OGGI);
        assertFalse(errori.isEmpty());
        assertTrue(app.getBacheca().isEmpty());
    }

    @Test
    void getBacheca_SoloProposteAperte() {
        AppData app = new AppData();
        pubblica(app, 1, "Concerti", "10");
        Proposta ritirata = pubblica(app, 2, "Concerti", "10");
        app.ritirareProposta(ritirata, OGGI);
        assertEquals(1, app.getBacheca().size());
    }

    @Test
    void getBachecaPerCategoria_FiltraPerCategoria() {
        AppData app = new AppData();
        pubblica(app, 1, "Concerti", "10");
        pubblica(app, 2, "Sport", "10");
        assertEquals(1, app.getBachecaPerCategoria("Concerti").size());
    }

    @Test
    void getCategorieConProposteAperte_OrdinateAlfabeticamente_SenzaDuplicati() {
        AppData app = new AppData();
        pubblica(app, 1, "Sport", "10");
        pubblica(app, 2, "Concerti", "10");
        pubblica(app, 3, "Concerti", "10");
        assertEquals(List.of("Concerti", "Sport"), app.getCategorieConProposteAperte());
    }

    @Test
    void getProposteIscrittoFruitore_SoloProposteApertaConIscritto() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        Proposta p = pubblica(app, 1, "Concerti", "10");
        p.aggiungiAderente("mario", OGGI);
        pubblica(app, 2, "Concerti", "10"); // senza mario
        assertEquals(1, app.getProposteIscrittoFruitore("mario").size());
    }

    // ================================================================
    // Transizioni automatiche + notifiche
    // ================================================================
    @Test
    void aggiornaTransizioni_ApertaScadutaConPostiPieni_DiventaConfermata() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        Proposta p = pubblica(app, 1, "Concerti", "1");
        p.aggiungiAderente("mario", OGGI);
        app.aggiornaTransizioni(OGGI.plusDays(11));
        assertEquals(StatoProposta.CONFERMATA, p.getStato());
        List<Notifica> notifiche = app.getFruitore("mario").getNotifiche();
        assertEquals(1, notifiche.size());
        assertEquals(TipoNotifica.PROPOSTA_CONFERMATA, notifiche.get(0).getTipo());
    }

    @Test
    void aggiornaTransizioni_ApertaScadutaSenzaPostiPieni_DiventaAnnullata() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        Proposta p = pubblica(app, 1, "Concerti", "10");
        p.aggiungiAderente("mario", OGGI); // 1 < 10: posti non pieni
        app.aggiornaTransizioni(OGGI.plusDays(11));
        assertEquals(StatoProposta.ANNULLATA, p.getStato());
        assertEquals(TipoNotifica.PROPOSTA_ANNULLATA,
                app.getFruitore("mario").getNotifiche().get(0).getTipo());
    }

    @Test
    void aggiornaTransizioni_ApertaNonAncoraScaduta_RestaAperta() {
        AppData app = new AppData();
        Proposta p = pubblica(app, 1, "Concerti", "10");
        int modificate = app.aggiornaTransizioni(OGGI);
        assertEquals(StatoProposta.APERTA, p.getStato());
        assertEquals(0, modificate);
    }

    @Test
    void aggiornaTransizioni_ConfermataDopoDataConclusiva_DiventaConclusa() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        Proposta p = pubblica(app, 1, "Concerti", "1");
        p.aggiungiAderente("mario", OGGI);
        app.aggiornaTransizioni(OGGI.plusDays(11)); // -> CONFERMATA
        app.aggiornaTransizioni(OGGI.plusDays(21)); // dopo data conclusiva (oggi+20)
        assertEquals(StatoProposta.CONCLUSA, p.getStato());
    }

    @Test
    void aggiornaTransizioni_RestituisceNumeroProposteModificate() {
        AppData app = new AppData();
        pubblica(app, 1, "Concerti", "10");
        pubblica(app, 2, "Sport", "10");
        assertEquals(2, app.aggiornaTransizioni(OGGI.plusDays(11)));
    }

    @Test
    void ritirareProposta_PropostaAperta_DiventaRitirataENotifica() {
        AppData app = new AppData();
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        Proposta p = pubblica(app, 1, "Concerti", "10");
        p.aggiungiAderente("mario", OGGI);
        app.ritirareProposta(p, OGGI);
        assertEquals(StatoProposta.RITIRATA, p.getStato());
        assertEquals(TipoNotifica.PROPOSTA_RITIRATA,
                app.getFruitore("mario").getNotifiche().get(0).getTipo());
    }

    // ================================================================
    // Generatori di ID e invariante
    // ================================================================
    @Test
    void getNuovoIdProposta_IncrementaContatore() {
        AppData app = new AppData();
        IdProposta primo = app.getNuovoIdProposta();
        IdProposta secondo = app.getNuovoIdProposta();
        assertEquals(primo.getValore() + 1, secondo.getValore());
    }

    @Test
    void getNuovoIdNotifica_IncrementaContatore() {
        AppData app = new AppData();
        int primo = app.getNuovoIdNotifica().getValore();
        int secondo = app.getNuovoIdNotifica().getValore();
        assertEquals(primo + 1, secondo);
    }

    @Test
    void repOk_DopoOperazioniValide_RestaVero() {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiConfiguratore(new Configuratore("admin", "pwd"));
        app.aggiungiFruitore(new Fruitore("mario", "pwd"));
        pubblica(app, 1, "Concerti", "10");
        assertTrue(app.repOk());
    }
}
