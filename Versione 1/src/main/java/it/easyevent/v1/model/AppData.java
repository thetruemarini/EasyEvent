package it.easyevent.v1.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contenitore centrale dello stato dell'applicazione (Versione 1). Gestisce:
 * configuratori, campi base, campi comuni, categorie.
 *
 * Pattern Singleton: una sola istanza per tutta l'applicazione.
 *
 * Invariante di classe: - configuratori != null - campiBase != null (impostati
 * una volta sola al primo avvio, poi immutabili) - campiComuni != null -
 * categorie != null - ogni configuratore ha username univoco - ogni categoria
 * ha nome univoco - nessun campo comune ha lo stesso nome di un campo base
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
        "Data",
        "Ora",
        "Quota individuale",
        "Data conclusiva"
    };

    private List<Configuratore> configuratori;
    private List<Campo> campiBase;
    private boolean campiBaseInizialized;
    private List<Campo> campiComuni;
    private List<Categoria> categorie;

    // ---- Singleton ----
    private static AppData instance;

    private AppData() {
        configuratori = new ArrayList<>();
        campiBase = new ArrayList<>();
        campiBaseInizialized = false;
        campiComuni = new ArrayList<>();
        categorie = new ArrayList<>();
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
    // GESTIONE CONFIGURATORI
    // ================================================================
    /**
     * Aggiunge un configuratore al sistema.
     *
     * @param conf configuratore da aggiungere, non null
     * @throws IllegalArgumentException se conf è null o esiste già uno con lo
     * stesso username
     */
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

    /**
     * Verifica se uno username è già in uso (sia configuratori sia, nelle
     * versioni future, fruitori).
     *
     * @param username username da verificare
     * @return true se in uso
     */
    public boolean esisteUsername(String username) {
        if (username == null) {
            return false;
        }
        return configuratori.stream()
                .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));
    }

    /**
     * Cerca un configuratore per username (case-insensitive).
     *
     * @param username username da cercare
     * @return il Configuratore trovato, o null se assente
     */
    public Configuratore getConfiguratore(String username) {
        if (username == null) {
            return null;
        }
        return configuratori.stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    public List<Configuratore> getConfiguratori() {
        return Collections.unmodifiableList(configuratori);
    }

    // ================================================================
    // GESTIONE CAMPI BASE
    // ================================================================
    /**
     * Inizializza i campi base al primo avvio. Operazione eseguibile una sola
     * volta.
     *
     * @throws IllegalStateException se i campi base sono già stati
     * inizializzati
     */
    public void inizializzaCampiBase() {
        if (campiBaseInizialized) {
            throw new IllegalStateException("I campi base sono già stati inizializzati e sono immutabili.");
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

    /**
     * Verifica se esiste un campo base con il nome dato.
     */
    public boolean esisteCampoBase(String nome) {
        if (nome == null) {
            return false;
        }
        return campiBase.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    // ================================================================
    // GESTIONE CAMPI COMUNI
    // ================================================================
    /**
     * Aggiunge un campo comune.
     *
     * @param campo campo da aggiungere, non null, di tipo COMUNE
     * @throws IllegalArgumentException se campo è null, non COMUNE, ha lo
     * stesso nome di un campo base, o è già presente tra i comuni
     */
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

    /**
     * Rimuove un campo comune per nome.
     *
     * @param nomeCampo nome del campo da rimuovere
     * @return true se rimosso, false se non trovato
     */
    public boolean rimuoviCampoComune(String nomeCampo) {
        boolean rimosso = campiComuni.removeIf(c -> c.getNome().equalsIgnoreCase(nomeCampo));
        assert repOk() : "Invariante violato dopo rimuoviCampoComune";
        return rimosso;
    }

    /**
     * Modifica l'obbligatorietà di un campo comune.
     *
     * @param nomeCampo nome del campo
     * @param obbligatorio nuovo valore
     * @return true se modifica avvenuta
     */
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
                .findFirst()
                .orElse(null);
    }

    public List<Campo> getCampiComuni() {
        return Collections.unmodifiableList(campiComuni);
    }

    // ================================================================
    // GESTIONE CATEGORIE
    // ================================================================
    /**
     * Aggiunge una nuova categoria.
     *
     * @param categoria categoria da aggiungere, non null
     * @throws IllegalArgumentException se categoria è null o esiste già con lo
     * stesso nome
     */
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

    /**
     * Rimuove una categoria per nome.
     *
     * @param nomeCategoria nome della categoria da rimuovere
     * @return true se rimossa, false se non trovata
     */
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
                .findFirst()
                .orElse(null);
    }

    public List<Categoria> getCategorie() {
        return Collections.unmodifiableList(categorie);
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

    // ================================================================
    // INVARIANTE
    // ================================================================
    /**
     * Verifica l'invariante di classe.
     */
    public boolean repOk() {
        if (configuratori == null || campiBase == null || campiComuni == null || categorie == null) {
            return false;
        }
        // Username configuratori univoci
        long usernameDistinti = configuratori.stream()
                .map(c -> c.getUsername().toLowerCase())
                .distinct().count();
        if (usernameDistinti != configuratori.size()) {
            return false;
        }
        // Nomi categorie univoci
        long nomiDistinti = categorie.stream()
                .map(cat -> cat.getNome().toLowerCase())
                .distinct().count();
        if (nomiDistinti != categorie.size()) {
            return false;
        }
        // Nessun campo comune col nome di un campo base
        for (Campo cc : campiComuni) {
            if (esisteCampoBase(cc.getNome())) {
                return false;
            }
        }
        return true;
    }
}
