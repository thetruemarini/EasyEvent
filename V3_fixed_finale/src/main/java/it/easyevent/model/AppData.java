package it.easyevent.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contenitore centrale dello stato dell'applicazione (Versione 3).
 * Estende la V2 aggiungendo:
 *   - fruitori: lista dei fruitori registrati
 *   - archivio: tutte le proposte pubblicate (APERTA, CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA)
 *   - prossimoIdNotifica: contatore per le notifiche
 *   - esisteUsernameGlobale: unicita' username tra configuratori e fruitori
 *   - aggiornaTransizioni: logica di transizione automatica degli stati
 *
 * Pattern Singleton.
 *
 * Invariante di classe (aggiornato rispetto a V2):
 *   - configuratori, fruitori, campiBase, campiComuni, categorie, archivio != null
 *   - prossimoIdProposta >= 1, prossimoIdNotifica >= 1
 *   - username univoci globalmente (configuratori + fruitori)
 *   - archivio: ogni elemento ha stato in {APERTA, CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA}
 */
public class AppData {

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    public static final String[] NOMI_CAMPI_BASE = {
        "Titolo",
        "Numero di partecipanti",
        "Termine ultimo di iscrizione",
        "Luogo",
        "Data",
        "Ora",
        "Quota individuale",
        "Data conclusiva"
    };

    // ---- V1: dati di configurazione ----
    private List<Configuratore> configuratori;
    private List<Campo>         campiBase;
    private boolean             campiBaseInizialized;
    private List<Campo>         campiComuni;
    private List<Categoria>     categorie;

    // ---- V2: proposte e contatore id ----
    private List<Proposta> archivio;          // era "bacheca" in V2; ora contiene tutti gli stati
    private int            prossimoIdProposta;

    // ---- V3: fruitori e contatore notifiche ----
    private List<Fruitore> fruitori;
    private int            prossimoIdNotifica;

    // ---- Singleton ----
    private static AppData instance;

    private AppData() {
        configuratori        = new ArrayList<>();
        campiBase            = new ArrayList<>();
        campiBaseInizialized = false;
        campiComuni          = new ArrayList<>();
        categorie            = new ArrayList<>();
        archivio             = new ArrayList<>();
        prossimoIdProposta   = 1;
        fruitori             = new ArrayList<>();
        prossimoIdNotifica   = 1;
    }

    public static AppData getInstance() {
        if (instance == null) instance = new AppData();
        return instance;
    }

    public static void resetInstance() { instance = null; }

    // ================================================================
    // GESTIONE CONFIGURATORI  (invariata da V1/V2)
    // ================================================================

    public void aggiungiConfiguratore(Configuratore conf) {
        if (conf == null) throw new IllegalArgumentException("Configuratore non puo' essere null.");
        if (esisteUsernameGlobale(conf.getUsername()))
            throw new IllegalArgumentException("Username gia' in uso: " + conf.getUsername());
        configuratori.add(conf);
        assert repOk() : "Invariante violato dopo aggiungiConfiguratore";
    }

    public Configuratore getConfiguratore(String username) {
        if (username == null) return null;
        return configuratori.stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public List<Configuratore> getConfiguratori() { return Collections.unmodifiableList(configuratori); }

    // ================================================================
    // GESTIONE FRUITORI  (nuovo in V3)
    // ================================================================

    /**
     * Registra un nuovo fruitore.
     *
     * @param fruitore fruitore da registrare, non null
     * @throws IllegalArgumentException se fruitore e' null o username gia' in uso
     */
    public void aggiungiFruitore(Fruitore fruitore) {
        if (fruitore == null) throw new IllegalArgumentException("Fruitore non puo' essere null.");
        if (esisteUsernameGlobale(fruitore.getUsername()))
            throw new IllegalArgumentException("Username gia' in uso: " + fruitore.getUsername());
        fruitori.add(fruitore);
        assert repOk() : "Invariante violato dopo aggiungiFruitore";
    }

    public Fruitore getFruitore(String username) {
        if (username == null) return null;
        return fruitori.stream()
                .filter(f -> f.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    /**
     * Rimuove il fruitore con lo username indicato (usato per rollback in caso
     * di errore di salvataggio dopo aggiungiFruitore).
     *
     * @param username username del fruitore da rimuovere
     * @return true se il fruitore e' stato trovato e rimosso
     */
    public boolean rimuoviFruitore(String username) {
        if (username == null) return false;
        return fruitori.removeIf(f -> f.getUsername().equalsIgnoreCase(username));
    }

    public List<Fruitore> getFruitori() { return Collections.unmodifiableList(fruitori); }

    // ================================================================
    // UNICITA' USERNAME GLOBALE  (nuovo in V3)
    // ================================================================

    /**
     * Verifica se un username e' gia' in uso tra configuratori e fruitori.
     * Controllo case-insensitive.
     *
     * @param username username da verificare
     * @return true se gia' in uso
     */
    public boolean esisteUsernameGlobale(String username) {
        if (username == null) return false;
        return configuratori.stream().anyMatch(c -> c.getUsername().equalsIgnoreCase(username))
            || fruitori.stream().anyMatch(f -> f.getUsername().equalsIgnoreCase(username));
    }

    /** @deprecated Usare esisteUsernameGlobale. Mantenuto per compatibilita' V1/V2. */
    @Deprecated
    public boolean esisteUsername(String username) {
        return esisteUsernameGlobale(username);
    }

    // ================================================================
    // GESTIONE CAMPI BASE  (invariata da V1/V2)
    // ================================================================

    public void inizializzaCampiBase() {
        if (campiBaseInizialized)
            throw new IllegalStateException("I campi base sono gia' stati inizializzati.");
        for (String nome : NOMI_CAMPI_BASE)
            campiBase.add(new Campo(nome, true, Campo.TipoCampo.BASE));
        campiBaseInizialized = true;
        assert repOk() : "Invariante violato dopo inizializzaCampiBase";
    }

    public boolean isCampiBaseInitialized()     { return campiBaseInizialized; }
    public List<Campo> getCampiBase()            { return Collections.unmodifiableList(campiBase); }
    public boolean esisteCampoBase(String nome)  {
        if (nome == null) return false;
        return campiBase.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    // ================================================================
    // GESTIONE CAMPI COMUNI  (invariata da V1/V2)
    // ================================================================

    public void aggiungiCampoComune(Campo campo) {
        if (campo == null) throw new IllegalArgumentException("Il campo non puo' essere null.");
        if (campo.getTipo() != Campo.TipoCampo.COMUNE)
            throw new IllegalArgumentException("Il campo deve essere di tipo COMUNE.");
        if (esisteCampoBase(campo.getNome()))
            throw new IllegalArgumentException("Esiste gia' un campo base con nome: " + campo.getNome());
        if (esisteCampoComune(campo.getNome()))
            throw new IllegalArgumentException("Esiste gia' un campo comune con nome: " + campo.getNome());
        campiComuni.add(campo);
        assert repOk() : "Invariante violato dopo aggiungiCampoComune";
    }

    public boolean rimuoviCampoComune(String nomeCampo) {
        boolean rimosso = campiComuni.removeIf(c -> c.getNome().equalsIgnoreCase(nomeCampo));
        assert repOk() : "Invariante violato dopo rimuoviCampoComune";
        return rimosso;
    }

    public boolean modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        for (Campo c : campiComuni) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                c.setObbligatorio(obbligatorio);
                return true;
            }
        }
        return false;
    }

    public boolean esisteCampoComune(String nome) {
        if (nome == null) return false;
        return campiComuni.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    public Campo getCampoComune(String nome) {
        if (nome == null) return null;
        return campiComuni.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nome))
                .findFirst().orElse(null);
    }

    public List<Campo> getCampiComuni() { return Collections.unmodifiableList(campiComuni); }

    // ================================================================
    // GESTIONE CATEGORIE  (invariata da V1/V2)
    // ================================================================

    public void aggiungiCategoria(Categoria categoria) {
        if (categoria == null) throw new IllegalArgumentException("La categoria non puo' essere null.");
        if (esisteCategoria(categoria.getNome()))
            throw new IllegalArgumentException("Esiste gia' una categoria con nome: " + categoria.getNome());
        categorie.add(categoria);
        assert repOk() : "Invariante violato dopo aggiungiCategoria";
    }

    public boolean rimuoviCategoria(String nomeCategoria) {
        boolean rimossa = categorie.removeIf(cat -> cat.getNome().equalsIgnoreCase(nomeCategoria));
        assert repOk() : "Invariante violato dopo rimuoviCategoria";
        return rimossa;
    }

    public boolean esisteCategoria(String nome) {
        if (nome == null) return false;
        return categorie.stream().anyMatch(cat -> cat.getNome().equalsIgnoreCase(nome));
    }

    public Categoria getCategoria(String nome) {
        if (nome == null) return null;
        return categorie.stream()
                .filter(cat -> cat.getNome().equalsIgnoreCase(nome))
                .findFirst().orElse(null);
    }

    public List<Categoria> getCategorie() { return Collections.unmodifiableList(categorie); }

    // ================================================================
    // GESTIONE ARCHIVIO  (evoluzione della bacheca V2)
    // ================================================================

    /**
     * Aggiunge una proposta APERTA all'archivio (chiamato alla pubblicazione).
     *
     * @param proposta proposta in stato APERTA, non null
     * @throws IllegalArgumentException se proposta e' null o non APERTA
     */
    public void aggiungiPropostaAperta(Proposta proposta) {
        if (proposta == null)
            throw new IllegalArgumentException("La proposta non puo' essere null.");
        if (proposta.getStato() != StatoProposta.APERTA)
            throw new IllegalArgumentException("Solo proposte APERTE possono essere aggiunte all'archivio.");
        archivio.add(proposta);
        assert repOk() : "Invariante violato dopo aggiungiPropostaAperta";
    }

    /** @return lista non modificabile di TUTTE le proposte pubblicate (archivio completo) */
    public List<Proposta> getArchivio() { return Collections.unmodifiableList(archivio); }

    /**
     * Rimuove una proposta dall'archivio dato il suo ID.
     * Usato esclusivamente per rollback in caso di errore di salvataggio
     * dopo aggiungiPropostaAperta().
     *
     * @param id ID della proposta da rimuovere
     * @return true se la proposta e' stata trovata e rimossa
     */
    public boolean rimuoviPropostaDaArchivio(int id) {
        return archivio.removeIf(p -> p.getId() == id);
    }

    /** @return proposte in bacheca (solo APERTE), compatibile con V2 */
    public List<Proposta> getBacheca() {
        return archivio.stream()
                .filter(p -> p.getStato() == StatoProposta.APERTA)
                .collect(Collectors.toList());
    }

    /** @return proposte APERTE filtrate per categoria, compatibile con V2 */
    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        if (nomeCategoria == null) return new ArrayList<>();
        return archivio.stream()
                .filter(p -> p.getStato() == StatoProposta.APERTA
                          && p.getNomeCategoria().equalsIgnoreCase(nomeCategoria))
                .collect(Collectors.toList());
    }

    /** Genera e restituisce il prossimo ID univoco per una proposta. */
    public int getNuovoIdProposta()       { return prossimoIdProposta++; }
    public int getProssimoIdProposta()    { return prossimoIdProposta; }

    /** Genera e restituisce il prossimo ID univoco per una notifica. */
    public int getNuovoIdNotifica()       { return prossimoIdNotifica++; }
    public int getProssimoIdNotifica()    { return prossimoIdNotifica; }

    // ================================================================
    // TRANSIZIONI AUTOMATICHE DI STATO  (nuovo in V3)
    // ================================================================

    /**
     * Verifica e aggiorna automaticamente gli stati delle proposte in archivio.
     *
     * Logica:
     *   - APERTA  con oggi > Termine iscrizione:
     *       se aderenti >= Numero partecipanti -> CONFERMATA (notifiche di conferma agli aderenti)
     *       altrimenti                         -> ANNULLATA  (notifiche di annullamento agli aderenti)
     *   - CONFERMATA con oggi > Data conclusiva:
     *       -> CONCLUSA (nessuna notifica)
     *
     * Le notifiche vengono depositate negli spazi personali dei fruitori aderenti.
     *
     * @param oggi data corrente
     * @return numero di proposte il cui stato e' cambiato
     */
    public int aggiornaTransizioni(LocalDate oggi) {
        int modificate = 0;

        for (Proposta p : archivio) {

            // APERTA -> CONFERMATA o ANNULLATA
            // Nota: le proposte RITIRATE (V4), ANNULLATE e CONCLUSE non hanno
            // ulteriori transizioni automatiche e non vengono toccate.
            if (p.getStato() == StatoProposta.APERTA) {
                LocalDate termine = p.getTermineIscrizione();
                if (termine != null && oggi.isAfter(termine)) {
                    int numMax      = p.getNumeroMaxPartecipanti();
                    int numAderenti = p.getAderenti().size();
                    String titolo   = p.getValore("Titolo");

                    // numMax < 0 significa campo corrotto: tratta come ANNULLATA per sicurezza
                    if (numMax >= 0 && numAderenti >= numMax) {
                        // Usiamo >= anziche' == perche' in V4, con le disdette, gli aderenti
                        // possono oscillare; ma poiche' aggiungiAderente blocca gia' l'iscrizione
                        // quando aderenti.size() >= numMax, i due predicati sono di fatto equivalenti.
                        // L'uso di >= e' comunque piu' robusto in caso di dati corrotti.
                        p.transitaStato(StatoProposta.CONFERMATA, oggi);
                        notificaAderenti(p, costruisciNotificaConferma(p), oggi);
                    } else {
                        p.transitaStato(StatoProposta.ANNULLATA, oggi);
                        notificaAderenti(p,
                            "La proposta \"" + titolo + "\" (ID " + p.getId()
                            + ") e' stata ANNULLATA: il numero minimo di iscritti non e' stato raggiunto.",
                            oggi);
                    }
                    modificate++;
                }
            }

            // CONFERMATA -> CONCLUSA
            // Nota: questo ramo e' intenzionalmente assente: il secondo loop
            // separato sotto gestisce anche le proposte appena confermate sopra.
        }

        // Secondo passaggio: CONFERMATA -> CONCLUSA (incluse quelle appena confermate sopra)
        for (Proposta p : archivio) {
            if (p.getStato() == StatoProposta.CONFERMATA) {
                LocalDate dataConc = p.getDataConclusiva();
                if (dataConc != null && oggi.isAfter(dataConc)) {
                    p.transitaStato(StatoProposta.CONCLUSA, oggi);
                    modificate++;
                }
            }
        }

        return modificate;
    }

    /** Costruisce il testo della notifica di conferma con promemoria evento. */
    private String costruisciNotificaConferma(Proposta p) {
        String titolo = p.getValore("Titolo");
        String data   = p.getValore(Proposta.CAMPO_DATA);
        String ora    = p.getValore("Ora");
        String luogo  = p.getValore("Luogo");
        String quota  = p.getValore("Quota individuale");

        StringBuilder sb = new StringBuilder();
        sb.append("La proposta \"").append(titolo).append("\" (ID ").append(p.getId())
          .append(") e' stata CONFERMATA! L'evento si terra':");
        if (!data.isBlank())  sb.append("  Data: ").append(data);
        if (!ora.isBlank())   sb.append("  Ora: ").append(ora);
        if (!luogo.isBlank()) sb.append("  Luogo: ").append(luogo);
        if (!quota.isBlank()) sb.append("  Quota individuale: ").append(quota);
        return sb.toString();
    }

    /** Aggiunge una notifica allo spazio personale di tutti gli aderenti. */
    private void notificaAderenti(Proposta p, String testo, LocalDate oggi) {
        for (String usernameF : p.getAderenti()) {
            Fruitore f = getFruitore(usernameF);
            if (f != null) {
                f.aggiungiNotifica(new Notifica(getNuovoIdNotifica(), testo, oggi));
            }
        }
    }

    // ================================================================
    // SETTERS PER DESERIALIZZAZIONE
    // ================================================================

    public void setCampiBase(List<Campo> campiBase) {
        this.campiBase = new ArrayList<>(campiBase);
        this.campiBaseInizialized = !campiBase.isEmpty();
    }

    public void setCampiComuni(List<Campo> campiComuni) {
        this.campiComuni = new ArrayList<>(campiComuni);
    }

    public void setCategorie(List<Categoria> categorie) {
        this.categorie = new ArrayList<>(categorie);
    }

    public void setConfiguratori(List<Configuratore> configuratori) {
        this.configuratori = new ArrayList<>(configuratori);
    }

    public void setFruitori(List<Fruitore> fruitori) {
        this.fruitori = new ArrayList<>(fruitori);
    }

    /** Carica l'archivio e ricalcola il contatore degli ID. */
    public void setArchivio(List<Proposta> archivio) {
        this.archivio = new ArrayList<>(archivio);
        int maxId = archivio.stream().mapToInt(Proposta::getId).max().orElse(0);
        this.prossimoIdProposta = Math.max(this.prossimoIdProposta, maxId + 1);
    }

    /** Compatibilita' con V2: "bacheca" e' ora "archivio". */
    public void setBacheca(List<Proposta> bacheca) { setArchivio(bacheca); }

    public void setProssimoIdProposta(int id) { this.prossimoIdProposta = id; }
    public void setProssimoIdNotifica(int id) { this.prossimoIdNotifica = id; }

    // ================================================================
    // INVARIANTE
    // ================================================================

    public boolean repOk() {
        if (configuratori == null || fruitori == null || campiBase == null
                || campiComuni == null || categorie == null || archivio == null)
            return false;
        if (prossimoIdProposta < 1 || prossimoIdNotifica < 1) return false;

        // Username globalmente univoci
        List<String> tuttiUsername = new ArrayList<>();
        configuratori.forEach(c -> tuttiUsername.add(c.getUsername().toLowerCase()));
        fruitori.forEach(f -> tuttiUsername.add(f.getUsername().toLowerCase()));
        long distinti = tuttiUsername.stream().distinct().count();
        if (distinti != tuttiUsername.size()) return false;

        // Nomi categorie univoci
        long nomiDistinti = categorie.stream()
                .map(cat -> cat.getNome().toLowerCase()).distinct().count();
        if (nomiDistinti != categorie.size()) return false;

        // Nessun campo comune col nome di un campo base
        for (Campo cc : campiComuni)
            if (esisteCampoBase(cc.getNome())) return false;

        // Archivio: nessuna proposta con stato interno (BOZZA o VALIDA)
        // RITIRATA e' uno stato valido in archivio (aggiunto in V4)
        for (Proposta p : archivio)
            if (p.getStato() == StatoProposta.BOZZA || p.getStato() == StatoProposta.VALIDA)
                return false;

        return true;
    }
}
