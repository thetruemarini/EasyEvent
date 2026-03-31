package it.easyevent.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contenitore centrale dello stato dell'applicazione (Versione 2). Estende la
 * Versione 1 aggiungendo: 
 * - bacheca: lista delle proposte in stato APERTA (persistita)
 * - prossimoIdProposta: contatore per generare ID univoci alle proposte
 * 
 * Pattern Singleton: una sola istanza per tutta l'applicazione.
 *
 * Invariante di classe (aggiornato rispetto a V1): 
 * - configuratori != null 
 * - campiBase != null (impostati una volta sola al primo avvio, poi immutabili)
 * - campiComuni != null 
 * - categorie != null 
 * - bacheca != null; ogni elemento è in stato APERTA 
 * - prossimoIdProposta >= 1 
 * - ogni configuratore ha username univoco
 * - ogni categoria ha nome univoco 
 * - nessun campo comune ha lo stesso nome di un campo base
 */
public class AppData {

    // ---- Costanti predefinite ----
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    // ---- Nomi dei campi base obbligatori (immutabili per specifica) ----
    public static final String[] NOMI_CAMPI_BASE = {
        "Titolo",
        "Numero di partecipanti",
        "Termine ultimo di iscrizione",
        "Luogo",
        "Data inizio",
        "Ora",
        "Quota individuale",
        "Data conclusiva"
    };

    // ---- V1: dati di configurazione ----
    private List<Configuratore> configuratori;
    private List<Campo> campiBase;
    private boolean campiBaseInizialized;
    private List<Campo> campiComuni;
    private List<Categoria> categorie;

    // ---- V2: proposte in bacheca e contatore id ----
    private List<Proposta> bacheca;
    private int prossimoIdProposta;

    // ---- Singleton ----
    private static AppData instance;

    private AppData() {
        configuratori = new ArrayList<>();
        campiBase = new ArrayList<>();
        campiBaseInizialized = false;
        campiComuni = new ArrayList<>();
        categorie = new ArrayList<>();
        bacheca = new ArrayList<>();
        prossimoIdProposta = 1;
    }

    public static AppData getInstance() {
        if (instance == null) {
            instance = new AppData();
        }
        return instance;
    }

    /**
     * Resetta l'istanza singleton (usato per test e deserializzazione).
     */
    public static void resetInstance() {
        instance = null;
    }

    // ================================================================
    // GESTIONE CONFIGURATORI (invariata da V1) 
    // ================================================================
    public void aggiungiConfiguratore(Configuratore conf) {
        if (conf == null) {
            throw new IllegalArgumentException("Configuratore non può essere null.");
        }
        if (esisteUsername(conf.getUsername())) {
            throw new IllegalArgumentException("Username già in uso: " + conf.getUsername());
        }
        configuratori.add(conf);
        assert repOk() : "Invariante violato dopo aggiungiConfiguratore";
    }

    public boolean esisteUsername(String username) {
        if (username == null) {
            return false;
        }
        return configuratori.stream()
                .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));
    }

    public Configuratore getConfiguratore(String username) {
        if (username == null) {
            return null;
        }
        return configuratori.stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public List<Configuratore> getConfiguratori() {
        return Collections.unmodifiableList(configuratori);
    }

    // ================================================================
    // GESTIONE CAMPI BASE  (invariata da V1) 
    // ================================================================
    public void inizializzaCampiBase() {
        if (campiBaseInizialized) {
            throw new IllegalStateException(
                    "I campi base sono già stati inizializzati e sono immutabili.");
        }
        for (String nome : NOMI_CAMPI_BASE) {
            campiBase.add(new Campo(nome, true, Campo.TipoCampo.BASE));
        }
        campiBaseInizialized = true;
        assert repOk() : "Invariante violato dopo inizializzaCampiBase";
    }

    public boolean isCampiBaseInitialized() {
        return campiBaseInizialized;
    }

    public List<Campo> getCampiBase() {
        return Collections.unmodifiableList(campiBase);
    }

    public boolean esisteCampoBase(String nome) {
        if (nome == null) {
            return false;
        }
        return campiBase.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    // ================================================================
    // GESTIONE CAMPI COMUNI  (invariata da V1)
    // ================================================================
    public void aggiungiCampoComune(Campo campo) {
        if (campo == null) {
            throw new IllegalArgumentException("Il campo non può essere null.");
        }
        if (campo.getTipo() != Campo.TipoCampo.COMUNE) {
            throw new IllegalArgumentException("Il campo deve essere di tipo COMUNE.");
        }
        if (esisteCampoBase(campo.getNome())) {
            throw new IllegalArgumentException("Esiste già un campo base con nome: " + campo.getNome());
        }
        if (esisteCampoComune(campo.getNome())) {
            throw new IllegalArgumentException("Esiste già un campo comune con nome: " + campo.getNome());
        }
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
        if (nome == null) {
            return false;
        }
        return campiComuni.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    public Campo getCampoComune(String nome) {
        if (nome == null) {
            return null;
        }
        return campiComuni.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nome))
                .findFirst().orElse(null);
    }

    public List<Campo> getCampiComuni() {
        return Collections.unmodifiableList(campiComuni);
    }

    // ================================================================
    // GESTIONE CATEGORIE  (invariata da V1)
    // ================================================================
    public void aggiungiCategoria(Categoria categoria) {
        if (categoria == null) {
            throw new IllegalArgumentException("La categoria non può essere null.");
        }
        if (esisteCategoria(categoria.getNome())) {
            throw new IllegalArgumentException("Esiste già una categoria con nome: " + categoria.getNome());
        }
        categorie.add(categoria);
        assert repOk() : "Invariante violato dopo aggiungiCategoria";
    }

    public boolean rimuoviCategoria(String nomeCategoria) {
        boolean rimossa = categorie.removeIf(cat -> cat.getNome().equalsIgnoreCase(nomeCategoria));
        assert repOk() : "Invariante violato dopo rimuoviCategoria";
        return rimossa;
    }

    public boolean esisteCategoria(String nome) {
        if (nome == null) {
            return false;
        }
        return categorie.stream().anyMatch(cat -> cat.getNome().equalsIgnoreCase(nome));
    }

    public Categoria getCategoria(String nome) {
        if (nome == null) {
            return null;
        }
        return categorie.stream()
                .filter(cat -> cat.getNome().equalsIgnoreCase(nome))
                .findFirst().orElse(null);
    }

    public List<Categoria> getCategorie() {
        return Collections.unmodifiableList(categorie);
    }

    // ================================================================
    // GESTIONE BACHECA  (nuovo in V2)
    // ================================================================
    /**
     * Aggiunge una proposta APERTA alla bacheca (persistita).
     *
     * @param proposta proposta da aggiungere, non null, stato APERTA
     * @throws IllegalArgumentException se proposta è null o non APERTA
     */
    public void aggiungiPropostaAperta(Proposta proposta) {
        // Precondizioni
        if (proposta == null) {
            throw new IllegalArgumentException("La proposta non può essere null.");
        }
        if (proposta.getStato() != StatoProposta.APERTA) {
            throw new IllegalArgumentException(
                    "Solo proposte in stato APERTA possono essere aggiunte alla bacheca.");
        }

        bacheca.add(proposta);
        assert repOk() : "Invariante violato dopo aggiungiPropostaAperta";
    }

    /**
     * @return lista non modificabile di tutte le proposte in bacheca (stato
     * APERTA)
     */
    public List<Proposta> getBacheca() {
        return Collections.unmodifiableList(bacheca);
    }

    /**
     * Filtra la bacheca per categoria.
     *
     * @param nomeCategoria nome della categoria (case-insensitive)
     * @return lista delle proposte aperte di quella categoria
     */
    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        if (nomeCategoria == null) {
            return new ArrayList<>();
        }
        return bacheca.stream()
                .filter(p -> p.getNomeCategoria().equalsIgnoreCase(nomeCategoria))
                .collect(Collectors.toList());
    }

    /**
     * Genera e restituisce il prossimo ID univoco per una proposta.
     *
     * @return id progressivo (>= 1)
     */
    public int getNuovoIdProposta() {
        return prossimoIdProposta++;
    }

    public int getProssimoIdProposta() {
        return prossimoIdProposta;
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

    public void setBacheca(List<Proposta> bacheca) {
        this.bacheca = new ArrayList<>(bacheca);
    }

    public void setProssimoIdProposta(int id) {
        this.prossimoIdProposta = id;
    }

    // ================================================================
    // INVARIANTE 
    // ================================================================
    public boolean repOk() {
        if (configuratori == null || campiBase == null
                || campiComuni == null || categorie == null
                || bacheca == null) {
            return false;
        }
        if (prossimoIdProposta < 1) {
            return false;
        }

        // Username configuratori univoci
        long usernameDistinti = configuratori.stream()
                .map(c -> c.getUsername().toLowerCase()).distinct().count();
        if (usernameDistinti != configuratori.size()) {
            return false;
        }

        // Nomi categorie univoci
        long nomiDistinti = categorie.stream()
                .map(cat -> cat.getNome().toLowerCase()).distinct().count();
        if (nomiDistinti != categorie.size()) {
            return false;
        }

        // Nessun campo comune col nome di un campo base
        for (Campo cc : campiComuni) {
            if (esisteCampoBase(cc.getNome())) {
                return false;
            }
        }

        // Tutte le proposte in bacheca sono APERTE
        for (Proposta p : bacheca) {
            if (p.getStato() != StatoProposta.APERTA) {
                return false;
            }
        }

        return true;
    }
}
