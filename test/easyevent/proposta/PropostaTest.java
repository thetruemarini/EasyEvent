package easyevent.proposta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.exception.ErroreValidazione;
import easyevent.exception.IscrizioneException;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.RitiroNonConsentitoException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link Proposta}: la classe con piu' logica di dominio del
 * sistema (validazione, macchina a stati a 7 stati, iscrizioni, ritiro). Il
 * tempo e' sempre iniettato, quindi i test sono deterministici senza test
 * double.
 */
class PropostaTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 6, 1);
    private static final String CAMPO_ORA = "Ora";

    // ================================================================
    // Helper di costruzione
    // ================================================================
    private LinkedHashMap<String, Boolean> campiCompleti() {
        LinkedHashMap<String, Boolean> c = new LinkedHashMap<>();
        c.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, true);
        c.put(Proposta.CAMPO_DATA, true);
        c.put(Proposta.CAMPO_DATA_CONCLUSIVA, false);
        c.put(Proposta.CAMPO_NUM_PARTECIPANTI, true);
        c.put(CAMPO_ORA, false);
        return c;
    }

    private String fmt(LocalDate d) {
        return d.format(Proposta.DATE_FORMAT);
    }

    /** Proposta in BOZZA con tutti i campi valorizzati coerentemente con oggi. */
    private Proposta propostaValida(LocalDate oggi) {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(oggi.plusDays(10)));
        p.setValore(Proposta.CAMPO_DATA, fmt(oggi.plusDays(15)));
        p.setValore(Proposta.CAMPO_DATA_CONCLUSIVA, fmt(oggi.plusDays(16)));
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "10");
        return p;
    }

    /** Proposta APERTA (validata e pubblicata) con iscrizioni aperte a oggi. */
    private Proposta propostaAperta(LocalDate oggi) {
        Proposta p = propostaValida(oggi);
        p.aggiornaStato(oggi);
        p.pubblicaInBacheca(oggi);
        return p;
    }

    private boolean haErrore(List<ErroreValidazione> errori, ErroreValidazione.Tipo tipo) {
        return errori.stream().anyMatch(e -> e.getTipo() == tipo);
    }

    // ================================================================
    // Costruzione e invariante
    // ================================================================
    @Test
    void costruttore_DatiValidi_StatoInizialeBozza() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        assertEquals(StatoProposta.BOZZA, p.getStato());
        assertNull(p.getDataPubblicazione());
    }

    @Test
    void costruttore_IdNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Proposta(null, "Concerti", "creatore", campiCompleti()));
    }

    @Test
    void costruttore_CategoriaBlank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Proposta(new IdProposta(1), "  ", "creatore", campiCompleti()));
    }

    @Test
    void costruttore_CreatoreBlank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Proposta(new IdProposta(1), "Concerti", "  ", campiCompleti()));
    }

    @Test
    void costruttore_CampiNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new Proposta(new IdProposta(1), "Concerti", "creatore", null));
    }

    @Test
    void costruttore_InizializzaValoriVuotiPerOgniCampo() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        for (String nome : p.getNomiCampi()) {
            assertTrue(p.getValore(nome).isEmpty(), "Il campo " + nome + " dovrebbe partire vuoto");
        }
    }

    // ================================================================
    // Validazione
    // ================================================================
    @Test
    void validazione_TuttiICampiObbligatoriValorizzati_NessunErrore() {
        Proposta p = propostaValida(OGGI);
        assertTrue(p.validazioneErrori(OGGI).isEmpty());
    }

    @Test
    void validazione_CampoObbligatorioVuoto_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_DATA, "");
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.CAMPO_OBBLIGATORIO_VUOTO));
    }

    @Test
    void validazione_DataFormatoErrato_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_DATA, "31-13-2026");
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.DATA_FORMATO_NON_VALIDO));
    }

    @Test
    void validazione_TermineNonFuturo_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(OGGI));
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.TERMINE_NON_FUTURO));
    }

    @Test
    void validazione_DataInizioTroppoVicinaAlTermine_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        // termine = oggi+10; data inizio = oggi+11 (< termine + 2)
        p.setValore(Proposta.CAMPO_DATA, fmt(OGGI.plusDays(11)));
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.DATA_INIZIO_TROPPO_VICINA));
    }

    @Test
    void validazione_DataConclusivaPrimaDellInizio_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        // data inizio = oggi+15; conclusiva = oggi+14
        p.setValore(Proposta.CAMPO_DATA_CONCLUSIVA, fmt(OGGI.plusDays(14)));
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.DATA_CONCLUSIVA_PRECEDENTE));
    }

    @Test
    void validazione_NumPartecipantiNonPositivo_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "0");
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.NUM_PARTECIPANTI_NON_POSITIVO));
    }

    @Test
    void validazione_NumPartecipantiNonNumerico_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "abc");
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.NUM_PARTECIPANTI_NON_NUMERICO));
    }

    @Test
    void validazione_OraFormatoNonValido_RestituisceErrore() {
        Proposta p = propostaValida(OGGI);
        p.setValore(CAMPO_ORA, "25:00");
        assertTrue(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.ORA_FORMATO_NON_VALIDO));
    }

    @Test
    void validazione_OraFormatoValido_NessunErroreOra() {
        Proposta p = propostaValida(OGGI);
        p.setValore(CAMPO_ORA, "09:30");
        assertFalse(haErrore(p.validazioneErrori(OGGI),
                ErroreValidazione.Tipo.ORA_FORMATO_NON_VALIDO));
    }

    @Test
    void aggiornaStato_PropostaCompleta_DiventaValida() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI);
        assertEquals(StatoProposta.VALIDA, p.getStato());
    }

    @Test
    void isPubblicabile_PropostaValida_RestituisceTrue() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI);
        assertTrue(p.isPubblicabile());
    }

    @Test
    void isPubblicabile_PropostaInBozza_RestituisceFalse() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        p.aggiornaStato(OGGI);
        assertFalse(p.isPubblicabile());
    }

    @Test
    void aggiornaStato_PropostaIncompleta_RestaBozza() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        p.aggiornaStato(OGGI);
        assertEquals(StatoProposta.BOZZA, p.getStato());
    }

    @Test
    void aggiornaStato_PropostaGiaPubblicata_NonRetrocede() {
        Proposta p = propostaAperta(OGGI);
        p.aggiornaStato(OGGI);
        assertEquals(StatoProposta.APERTA, p.getStato());
    }

    // ================================================================
    // Pubblicazione e revert
    // ================================================================
    @Test
    void pubblica_DaValida_DiventaAperta() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI);
        p.pubblicaInBacheca(OGGI);
        assertEquals(StatoProposta.APERTA, p.getStato());
        assertEquals(OGGI, p.getDataPubblicazione());
        assertEquals(1, p.getStoricoStati().size());
        assertEquals(StatoProposta.APERTA, p.getStoricoStati().get(0).getStato());
    }

    @Test
    void pubblica_DaBozza_LanciaModificaNonConsentita() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> p.pubblicaInBacheca(OGGI));
        assertEquals(ModificaNonConsentitaException.TipoModifica.STATO_PROPOSTA_NON_VALIDO,
                ex.getTipoModifica());
    }

    @Test
    void pubblica_DataNull_LanciaIllegalArgument() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI);
        assertThrows(IllegalArgumentException.class, () -> p.pubblicaInBacheca(null));
    }

    @Test
    void revert_DaAperta_TornaValida() {
        Proposta p = propostaAperta(OGGI);
        p.revertToValida();
        assertEquals(StatoProposta.VALIDA, p.getStato());
        assertNull(p.getDataPubblicazione());
        assertEquals(0, p.getStoricoStati().size());
    }

    @Test
    void revert_DaStatoNonAperto_LanciaModificaNonConsentita() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI); // VALIDA, non APERTA
        assertThrows(ModificaNonConsentitaException.class, p::revertToValida);
    }

    // ================================================================
    // Transizioni di stato
    // ================================================================
    @Test
    void transizione_ApertaAConfermata_Consentita() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.CONFERMATA, OGGI);
        assertEquals(StatoProposta.CONFERMATA, p.getStato());
        assertEquals(2, p.getStoricoStati().size());
    }

    @Test
    void transizione_ApertaAdAnnullata_Consentita() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.ANNULLATA, OGGI);
        assertEquals(StatoProposta.ANNULLATA, p.getStato());
    }

    @Test
    void transizione_ApertaARitirata_Consentita() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.RITIRATA, OGGI);
        assertEquals(StatoProposta.RITIRATA, p.getStato());
    }

    @Test
    void transizione_ConfermataAConclusa_Consentita() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.CONFERMATA, OGGI);
        p.transitaStato(StatoProposta.CONCLUSA, OGGI);
        assertEquals(StatoProposta.CONCLUSA, p.getStato());
    }

    @Test
    void transizione_ConfermataARitirata_Consentita() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.CONFERMATA, OGGI);
        p.transitaStato(StatoProposta.RITIRATA, OGGI);
        assertEquals(StatoProposta.RITIRATA, p.getStato());
    }

    @Test
    void transizione_BozzaAConfermata_Vietata() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> p.transitaStato(StatoProposta.CONFERMATA, OGGI));
        assertEquals(ModificaNonConsentitaException.TipoModifica.TRANSIZIONE_STATO_NON_VALIDA,
                ex.getTipoModifica());
    }

    @Test
    void transizione_ValidaAdAperta_Vietata() {
        Proposta p = propostaValida(OGGI);
        p.aggiornaStato(OGGI); // VALIDA
        assertThrows(ModificaNonConsentitaException.class,
                () -> p.transitaStato(StatoProposta.APERTA, OGGI));
    }

    @Test
    void transizione_ConclusaAQualsiasi_Vietata() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.CONFERMATA, OGGI);
        p.transitaStato(StatoProposta.CONCLUSA, OGGI);
        assertThrows(ModificaNonConsentitaException.class,
                () -> p.transitaStato(StatoProposta.RITIRATA, OGGI));
    }

    @Test
    void transizione_AnnullataAConfermata_Vietata() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.ANNULLATA, OGGI);
        assertThrows(ModificaNonConsentitaException.class,
                () -> p.transitaStato(StatoProposta.CONFERMATA, OGGI));
    }

    @Test
    void transizione_RitirataAQualsiasi_Vietata() {
        Proposta p = propostaAperta(OGGI);
        p.transitaStato(StatoProposta.RITIRATA, OGGI);
        assertThrows(ModificaNonConsentitaException.class,
                () -> p.transitaStato(StatoProposta.CONFERMATA, OGGI));
    }

    @Test
    void transizione_StatoNull_LanciaIllegalArgument() {
        Proposta p = propostaAperta(OGGI);
        assertThrows(IllegalArgumentException.class, () -> p.transitaStato(null, OGGI));
    }

    @Test
    void transizione_DataNull_LanciaIllegalArgument() {
        Proposta p = propostaAperta(OGGI);
        assertThrows(IllegalArgumentException.class,
                () -> p.transitaStato(StatoProposta.CONFERMATA, null));
    }

    // ================================================================
    // Gestione aderenti
    // ================================================================
    @Test
    void iscrizione_PropostaApertaIscrizioniAperte_AggiungeAderente() {
        Proposta p = propostaAperta(OGGI);
        p.aggiungiAderente("mario", OGGI);
        assertEquals(1, p.getNumAderenti());
        assertTrue(p.isAderito("mario"));
    }

    @Test
    void iscrizione_PropostaNonAperta_LanciaPropostaNonAperta() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.aggiungiAderente("mario", OGGI));
        assertEquals(IscrizioneException.TipoErrore.PROPOSTA_NON_APERTA, ex.getTipoErrore());
    }

    @Test
    void iscrizione_OltreTermine_LanciaIscrizioniChiuse() {
        Proposta p = propostaAperta(OGGI);
        // termine = oggi+10; mi iscrivo a oggi+20
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.aggiungiAderente("mario", OGGI.plusDays(20)));
        assertEquals(IscrizioneException.TipoErrore.ISCRIZIONI_CHIUSE, ex.getTipoErrore());
    }

    @Test
    void iscrizione_GiaIscritto_LanciaGiaIscritto() {
        Proposta p = propostaAperta(OGGI);
        p.aggiungiAderente("mario", OGGI);
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.aggiungiAderente("mario", OGGI));
        assertEquals(IscrizioneException.TipoErrore.GIA_ISCRITTO, ex.getTipoErrore());
    }

    @Test
    void iscrizione_NumPartecipantiNonValido_LanciaNumNonValido() {
        // Proposta valida ma senza "Numero di partecipanti" valorizzato (campo opzionale)
        LinkedHashMap<String, Boolean> campi = new LinkedHashMap<>();
        campi.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, true);
        campi.put(Proposta.CAMPO_DATA, true);
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campi);
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(OGGI.plusDays(10)));
        p.setValore(Proposta.CAMPO_DATA, fmt(OGGI.plusDays(15)));
        p.aggiornaStato(OGGI);
        p.pubblicaInBacheca(OGGI);
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.aggiungiAderente("mario", OGGI));
        assertEquals(IscrizioneException.TipoErrore.NUM_PARTECIPANTI_NON_VALIDO, ex.getTipoErrore());
    }

    @Test
    void iscrizione_PostiEsauriti_LanciaPostiEsauriti() {
        Proposta p = propostaValida(OGGI);
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "1");
        p.aggiornaStato(OGGI);
        p.pubblicaInBacheca(OGGI);
        p.aggiungiAderente("mario", OGGI);
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.aggiungiAderente("luigi", OGGI));
        assertEquals(IscrizioneException.TipoErrore.POSTI_ESAURITI, ex.getTipoErrore());
    }

    @Test
    void iscrizione_UsernameNullOBlank_LanciaIllegalArgument() {
        Proposta p = propostaAperta(OGGI);
        assertThrows(IllegalArgumentException.class, () -> p.aggiungiAderente("  ", OGGI));
    }

    @Test
    void rimozione_AderentePresente_LoRimuove() {
        Proposta p = propostaAperta(OGGI);
        p.aggiungiAderente("mario", OGGI);
        p.rimuoviAderente("mario", OGGI);
        assertFalse(p.isAderito("mario"));
        assertEquals(0, p.getNumAderenti());
    }

    @Test
    void rimozione_AderenteNonIscritto_LanciaNonIscritto() {
        Proposta p = propostaAperta(OGGI);
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.rimuoviAderente("nessuno", OGGI));
        assertEquals(IscrizioneException.TipoErrore.NON_ISCRITTO, ex.getTipoErrore());
    }

    @Test
    void rimozione_IscrizioniChiuse_LanciaIscrizioniChiuse() {
        Proposta p = propostaAperta(OGGI);
        p.aggiungiAderente("mario", OGGI);
        IscrizioneException ex = assertThrows(IscrizioneException.class,
                () -> p.rimuoviAderente("mario", OGGI.plusDays(20)));
        assertEquals(IscrizioneException.TipoErrore.ISCRIZIONI_CHIUSE, ex.getTipoErrore());
    }

    @Test
    void isAderito_CaseInsensitive() {
        Proposta p = propostaAperta(OGGI);
        p.aggiungiAderente("Mario", OGGI);
        assertTrue(p.isAderito("mario"));
    }

    // ================================================================
    // Ritiro
    // ================================================================
    @Test
    void ritiro_PropostaApertaConDataFutura_Consentito() {
        Proposta p = propostaAperta(OGGI);
        // data inizio = oggi+15, futura rispetto a oggi: nessuna eccezione
        p.verificaRitiroConsentito(OGGI);
    }

    @Test
    void ritiro_StatoNonRitirabile_LanciaStatoNonRitirabile() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        RitiroNonConsentitoException ex = assertThrows(RitiroNonConsentitoException.class,
                () -> p.verificaRitiroConsentito(OGGI));
        assertEquals(RitiroNonConsentitoException.TipoErrore.STATO_NON_RITIRABILE, ex.getTipoErrore());
    }

    @Test
    void ritiro_DataEventoNonValorizzata_LanciaDataNonValorizzata() {
        // Proposta APERTA ma senza "Data inizio" valorizzata (campo opzionale)
        LinkedHashMap<String, Boolean> campi = new LinkedHashMap<>();
        campi.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, true);
        campi.put(Proposta.CAMPO_NUM_PARTECIPANTI, true);
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campi);
        p.setValore(Proposta.CAMPO_TERMINE_ISCRIZIONE, fmt(OGGI.plusDays(10)));
        p.setValore(Proposta.CAMPO_NUM_PARTECIPANTI, "10");
        p.aggiornaStato(OGGI);
        p.pubblicaInBacheca(OGGI);
        RitiroNonConsentitoException ex = assertThrows(RitiroNonConsentitoException.class,
                () -> p.verificaRitiroConsentito(OGGI));
        assertEquals(RitiroNonConsentitoException.TipoErrore.DATA_EVENTO_NON_VALORIZZATA,
                ex.getTipoErrore());
    }

    @Test
    void ritiro_DataEventoPassata_LanciaDataEventoPassata() {
        Proposta p = propostaAperta(OGGI);
        // data inizio = oggi+15; verifico a oggi+20 (evento gia' passato)
        RitiroNonConsentitoException ex = assertThrows(RitiroNonConsentitoException.class,
                () -> p.verificaRitiroConsentito(OGGI.plusDays(20)));
        assertEquals(RitiroNonConsentitoException.TipoErrore.DATA_EVENTO_PASSATA, ex.getTipoErrore());
    }

    // ================================================================
    // Protezione dei valori dopo pubblicazione
    // ================================================================
    @Test
    void setValore_CampoInesistente_LanciaCampoNonPresente() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> p.setValore("Campo Fantasma", "x"));
        assertEquals(ModificaNonConsentitaException.TipoModifica.CAMPO_NON_PRESENTE,
                ex.getTipoModifica());
    }

    @Test
    void setValore_PropostaInBozza_Consentito() {
        Proposta p = new Proposta(new IdProposta(1), "Concerti", "creatore", campiCompleti());
        p.setValore(Proposta.CAMPO_DATA, "10/07/2026");
        assertEquals("10/07/2026", p.getValore(Proposta.CAMPO_DATA));
    }

    @Test
    void setValore_PropostaPubblicata_LanciaPropostaGiaPubblicata() {
        Proposta p = propostaAperta(OGGI);
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> p.setValore(Proposta.CAMPO_DATA, "10/07/2026"));
        assertEquals(ModificaNonConsentitaException.TipoModifica.PROPOSTA_GIA_PUBBLICATA,
                ex.getTipoModifica());
    }
}
