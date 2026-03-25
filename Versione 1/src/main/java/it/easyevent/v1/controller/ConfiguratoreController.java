package it.easyevent.v1.controller;

import it.easyevent.v1.model.AppData;
import it.easyevent.v1.model.Campo;
import it.easyevent.v1.model.Categoria;
import it.easyevent.v1.model.Configuratore;
import it.easyevent.v1.persistence.PersistenceManager;

import java.io.IOException;
import java.util.List;

/**
 * Controller per tutte le operazioni del configuratore (Versione 1).
 * Incapsula la logica di business e media tra la view e il model.
 *
 * Invariante di classe:
 * - appData != null
 * - persistenceManager != null
 * - configuratoreCorrente può essere null (nessun utente loggato)
 */
public class ConfiguratoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Configuratore configuratoreCorrente;

    /**
     * @param appData            stato dell'applicazione, non null
     * @param persistenceManager gestore persistenza, non null
     */
    public ConfiguratoreController(AppData appData, PersistenceManager persistenceManager) {
        if (appData == null) throw new IllegalArgumentException("AppData non può essere null.");
        if (persistenceManager == null) throw new IllegalArgumentException("PersistenceManager non può essere null.");
        this.appData = appData;
        this.persistenceManager = persistenceManager;
        this.configuratoreCorrente = null;
    }

    // ================================================================
    // AUTENTICAZIONE
    // ================================================================

    /**
     * Effettua il login di un configuratore.
     * Se è il primo configuratore e non ce ne sono ancora registrati, usa le credenziali di default.
     *
     * @param username username inserito
     * @param password password inserita
     * @return true se il login ha avuto successo
     */
    public boolean login(String username, String password) {
        // Precondizioni
        if (username == null || password == null) return false;

        // Caso: nessun configuratore ancora registrato → usa credenziali default
        if (appData.getConfiguratori().isEmpty()) {
            if (username.equalsIgnoreCase(AppData.DEFAULT_USERNAME) && password.equals(AppData.DEFAULT_PASSWORD)) {
                // Crea il primo configuratore con credenziali di default
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                configuratoreCorrente = nuovo;
                return true;
            }
            return false;
        }

        // Cerca il configuratore con quelle credenziali
        Configuratore trovato = appData.getConfiguratore(username);
        if (trovato != null && trovato.verificaCredenziali(username, password)) {
            configuratoreCorrente = trovato;
            return true;
        }
        return false;
    }

    /**
     * Effettua il logout del configuratore corrente.
     */
    public void logout() {
        configuratoreCorrente = null;
    }

    /**
     * @return true se un configuratore è attualmente loggato
     */
    public boolean isLoggato() {
        return configuratoreCorrente != null;
    }

    /**
     * @return true se il configuratore corrente deve ancora scegliere le credenziali personali
     */
    public boolean richiedeCambioCredenziali() {
        return configuratoreCorrente != null && configuratoreCorrente.isPrimoAccesso();
    }

    /**
     * Imposta le credenziali personali al primo accesso.
     *
     * @param nuovoUsername nuovo username, non null e non blank
     * @param nuovaPassword nuova password, non null e non blank
     * @return messaggio di esito (stringa vuota = successo)
     */
    public String impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        // Precondizioni
        if (!isLoggato()) return "Nessun configuratore loggato.";
        if (!richiedeCambioCredenziali()) return "Le credenziali personali sono già state impostate.";
        if (nuovoUsername == null || nuovoUsername.isBlank()) return "Lo username non può essere vuoto.";
        if (nuovaPassword == null || nuovaPassword.isBlank()) return "La password non può essere vuota.";

        // Verifica unicità username (escludendo se stesso)
        String vecchioUsername = configuratoreCorrente.getUsername();
        String vecchiaPassword = configuratoreCorrente.getPassword();
        if (!nuovoUsername.equalsIgnoreCase(vecchioUsername) && appData.esisteUsername(nuovoUsername)) {
            return "Username già in uso: " + nuovoUsername;
        }

        configuratoreCorrente.impostaCredenzialiPersonali(nuovoUsername, nuovaPassword);

        try {
            salva();
        } catch (IOException e) {
            // Rollback: ripristina username, password e primoAccesso precedenti per
            // mantenere la coerenza tra stato in memoria e stato su disco.
            configuratoreCorrente.revertCredenziali(vecchioUsername, vecchiaPassword);
            return "Errore nel salvataggio; le credenziali non sono state aggiornate: " + e.getMessage();
        }

        // Postcondizione
        assert !configuratoreCorrente.isPrimoAccesso() : "Postcondizione violata: primo accesso ancora true";
        return "";
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================

    /**
     * Inizializza i campi base se non già fatto. Operazione eseguita solo al primo avvio.
     *
     * @return messaggio di esito (stringa vuota = successo o già inizializzati)
     */
    public String inizializzaCampiBase() {
        if (appData.isCampiBaseInitialized()) {
            return ""; // Già inizializzati, nessuna azione necessaria
        }
        appData.inizializzaCampiBase();
        try {
            salva();
        } catch (IOException e) {
            return "Campi base creati ma errore nel salvataggio: " + e.getMessage();
        }
        return "";
    }

    /**
     * @return lista dei campi base
     */
    public List<Campo> getCampiBase() {
        return appData.getCampiBase();
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================

    /**
     * Aggiunge un campo comune.
     *
     * @param nome         nome del campo, non null e non blank
     * @param obbligatorio true se obbligatorio
     * @return messaggio di errore, stringa vuota se successo
     */
    public String aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        if (nome == null || nome.isBlank()) return "Il nome del campo non può essere vuoto.";
        try {
            Campo campo = new Campo(nome.trim(), obbligatorio, Campo.TipoCampo.COMUNE);
            appData.aggiungiCampoComune(campo);
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Rimuove un campo comune per nome.
     *
     * @param nomeCampo nome del campo da rimuovere
     * @return messaggio di errore, stringa vuota se successo
     */
    public String rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Il nome del campo non può essere vuoto.";
        boolean rimosso = appData.rimuoviCampoComune(nomeCampo);
        if (!rimosso) return "Nessun campo comune trovato con nome: " + nomeCampo;
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Campo rimosso ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Modifica l'obbligatorietà di un campo comune.
     *
     * @param nomeCampo    nome del campo
     * @param obbligatorio nuovo valore
     * @return messaggio di errore, stringa vuota se successo
     */
    public String modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        boolean modificato = appData.modificaObbligatorietaCampoComune(nomeCampo, obbligatorio);
        if (!modificato) return "Nessun campo comune trovato con nome: " + nomeCampo;
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Obbligatorietà modificata ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * @return lista dei campi comuni
     */
    public List<Campo> getCampiComuni() {
        return appData.getCampiComuni();
    }

    // ================================================================
    // CATEGORIE
    // ================================================================

    /**
     * Crea e aggiunge una nuova categoria.
     *
     * @param nomeCategoria nome della categoria, non null e non blank
     * @return messaggio di errore, stringa vuota se successo
     */
    public String aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome della categoria non può essere vuoto.";
        try {
            Categoria cat = new Categoria(nomeCategoria.trim());
            appData.aggiungiCategoria(cat);
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Categoria aggiunta ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Rimuove una categoria esistente.
     *
     * @param nomeCategoria nome della categoria da rimuovere
     * @return messaggio di errore, stringa vuota se successo
     */
    public String rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome della categoria non può essere vuoto.";
        boolean rimossa = appData.rimuoviCategoria(nomeCategoria);
        if (!rimossa) return "Nessuna categoria trovata con nome: " + nomeCategoria;
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Categoria rimossa ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Aggiunge un campo specifico a una categoria esistente.
     *
     * @param nomeCategoria nome della categoria
     * @param nomeCampo     nome del campo specifico
     * @param obbligatorio  true se obbligatorio
     * @return messaggio di errore, stringa vuota se successo
     */
    public String aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome della categoria non può essere vuoto.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Il nome del campo non può essere vuoto.";

        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;

        // Verifica che il nome non coincida con un campo base o comune
        if (appData.esisteCampoBase(nomeCampo)) {
            return "Esiste già un campo base con nome: " + nomeCampo;
        }
        if (appData.esisteCampoComune(nomeCampo)) {
            return "Esiste già un campo comune con nome: " + nomeCampo;
        }

        try {
            Campo campo = new Campo(nomeCampo.trim(), obbligatorio, Campo.TipoCampo.SPECIFICO);
            cat.aggiungiCampoSpecifico(campo);
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Campo specifico aggiunto ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Rimuove un campo specifico da una categoria.
     *
     * @param nomeCategoria nome della categoria
     * @param nomeCampo     nome del campo da rimuovere
     * @return messaggio di errore, stringa vuota se successo
     */
    public String rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        boolean rimosso = cat.rimuoviCampoSpecifico(nomeCampo);
        if (!rimosso) return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Campo rimosso ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Modifica l'obbligatorietà di un campo specifico di una categoria.
     *
     * @param nomeCategoria nome della categoria
     * @param nomeCampo     nome del campo
     * @param obbligatorio  nuovo valore
     * @return messaggio di errore, stringa vuota se successo
     */
    public String modificaObbligatorietaCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato: effettuare il login.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        boolean modificato = cat.modificaObbligatorietaCampoSpecifico(nomeCampo, obbligatorio);
        if (!modificato) return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Obbligatorietà modificata ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * @return lista di tutte le categorie
     */
    public List<Categoria> getCategorie() {
        return appData.getCategorie();
    }

    /**
     * Restituisce una categoria per nome.
     *
     * @param nome nome della categoria
     * @return Categoria o null se non trovata
     */
    public Categoria getCategoria(String nome) {
        return appData.getCategoria(nome);
    }

    // ================================================================
    // UTILITÀ
    // ================================================================

    /**
     * Salva lo stato corrente su file.
     *
     * @throws IOException in caso di errore di scrittura
     */
    private void salva() throws IOException {
        persistenceManager.salva(appData);
    }

    /**
     * Carica lo stato dal file.
     *
     * @return true se il file esisteva ed è stato caricato
     * @throws IOException in caso di errore di lettura
     */
    public boolean carica() throws IOException {
        return persistenceManager.carica(appData);
    }

    public Configuratore getConfiguratoreCorrente() {
        return configuratoreCorrente;
    }

    public AppData getAppData() {
        return appData;
    }
}
