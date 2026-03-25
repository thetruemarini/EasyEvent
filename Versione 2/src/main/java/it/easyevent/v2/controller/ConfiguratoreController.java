package it.easyevent.v2.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import it.easyevent.v2.model.AppData;
import it.easyevent.v2.model.Campo;
import it.easyevent.v2.model.Categoria;
import it.easyevent.v2.model.Configuratore;
import it.easyevent.v2.model.Proposta;
import it.easyevent.v2.model.StatoProposta;
import it.easyevent.v2.persistence.PersistenceManager;

/**
 * Controller per tutte le operazioni del configuratore (Versione 2).
 *
 * Estende la V1 aggiungendo la gestione delle proposte:
 *   - creazione di proposte (in sessione, non persistite finché non pubblicate)
 *   - compilazione dei campi di una proposta
 *   - pubblicazione di proposte valide in bacheca (persistita)
 *   - visualizzazione della bacheca per categoria
 *
 * Le proposte create in sessione (stato BOZZA o VALIDA) vengono scartate
 * al logout se non pubblicate, come da specifica.
 *
 * Invariante di classe:
 *   - appData != null
 *   - persistenceManager != null
 *   - configuratoreCorrente può essere null (nessun utente loggato)
 *   - proposteSessione != null (può essere vuota, mai null)
 */
public class ConfiguratoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Configuratore configuratoreCorrente;

    /** Proposte create nella sessione corrente, non ancora pubblicate (V2). */
    private List<Proposta> proposteSessione;

    public ConfiguratoreController(AppData appData, PersistenceManager persistenceManager) {
        if (appData == null)
            throw new IllegalArgumentException("AppData non può essere null.");
        if (persistenceManager == null)
            throw new IllegalArgumentException("PersistenceManager non può essere null.");
        this.appData              = appData;
        this.persistenceManager   = persistenceManager;
        this.configuratoreCorrente = null;
        this.proposteSessione     = new ArrayList<>();
    }

    // ================================================================
    // AUTENTICAZIONE  (invariata da V1)
    // ================================================================

    public boolean login(String username, String password) {
        if (username == null || password == null) return false;

        if (appData.getConfiguratori().isEmpty()) {
            if (username.equalsIgnoreCase(AppData.DEFAULT_USERNAME)
                    && password.equals(AppData.DEFAULT_PASSWORD)) {
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                configuratoreCorrente = nuovo;
                return true;
            }
            return false;
        }

        Configuratore trovato = appData.getConfiguratore(username);
        if (trovato != null && trovato.verificaCredenziali(username, password)) {
            configuratoreCorrente = trovato;
            return true;
        }
        return false;
    }

    /**
     * Effettua il logout: scarta le proposte di sessione non pubblicate (V2).
     */
    public void logout() {
        proposteSessione.clear(); // V2: proposte non pubblicate vengono scartate
        configuratoreCorrente = null;
    }

    public boolean isLoggato()                  { return configuratoreCorrente != null; }
    public boolean richiedeCambioCredenziali()  { return configuratoreCorrente != null && configuratoreCorrente.isPrimoAccesso(); }

    public String impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!isLoggato())               return "Nessun configuratore loggato.";
        if (!richiedeCambioCredenziali()) return "Le credenziali personali sono già state impostate.";
        if (nuovoUsername == null || nuovoUsername.isBlank()) return "Lo username non può essere vuoto.";
        if (nuovaPassword == null || nuovaPassword.isBlank()) return "La password non può essere vuota.";

        String vecchioUsername = configuratoreCorrente.getUsername();
        if (!nuovoUsername.equalsIgnoreCase(vecchioUsername) && appData.esisteUsername(nuovoUsername)) {
            return "Username già in uso: " + nuovoUsername;
        }

        String vecchiaPassword = configuratoreCorrente.getPassword();
        configuratoreCorrente.impostaCredenzialiPersonali(nuovoUsername, nuovaPassword);
        try {
            salva();
        } catch (IOException e) {
            // Rollback: ripristina le credenziali precedenti in memoria
            configuratoreCorrente.revertCredenziali(vecchioUsername, vecchiaPassword);
            return "Attenzione: errore nel salvataggio delle credenziali: " + e.getMessage();
        }
        assert !configuratoreCorrente.isPrimoAccesso() : "Postcondizione violata";
        return "";
    }

    // ================================================================
    // CAMPI BASE  (invariata da V1)
    // ================================================================

    public String inizializzaCampiBase() {
        if (appData.isCampiBaseInitialized()) return "";
        appData.inizializzaCampiBase();
        try { salva(); } catch (IOException e) {
            return "Campi base creati ma errore nel salvataggio: " + e.getMessage();
        }
        return "";
    }

    public List<Campo> getCampiBase() { return appData.getCampiBase(); }

    // ================================================================
    // CAMPI COMUNI  (invariata da V1)
    // ================================================================

    public String aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        if (nome == null || nome.isBlank()) return "Il nome del campo non può essere vuoto.";
        try {
            appData.aggiungiCampoComune(new Campo(nome.trim(), obbligatorio, Campo.TipoCampo.COMUNE));
            salva();
            return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Il nome non può essere vuoto.";
        if (!appData.rimuoviCampoComune(nomeCampo))
            return "Nessun campo comune trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Campo rimosso ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        if (!appData.modificaObbligatorietaCampoComune(nomeCampo, obbligatorio))
            return "Nessun campo comune trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Modifica effettuata ma errore nel salvataggio: " + e.getMessage(); }
    }

    public List<Campo> getCampiComuni() { return appData.getCampiComuni(); }

    // ================================================================
    // CATEGORIE  (invariata da V1)
    // ================================================================

    public String aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome non può essere vuoto.";
        try {
            appData.aggiungiCategoria(new Categoria(nomeCategoria.trim()));
            salva();
            return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Categoria aggiunta ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome non può essere vuoto.";
        if (!appData.rimuoviCategoria(nomeCategoria))
            return "Nessuna categoria trovata con nome: " + nomeCategoria;
        try { salva(); return ""; }
        catch (IOException e) { return "Categoria rimossa ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome della categoria non può essere vuoto.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Il nome del campo non può essere vuoto.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (appData.esisteCampoBase(nomeCampo))  return "Esiste già un campo base con nome: " + nomeCampo;
        if (appData.esisteCampoComune(nomeCampo)) return "Esiste già un campo comune con nome: " + nomeCampo;
        try {
            cat.aggiungiCampoSpecifico(new Campo(nomeCampo.trim(), obbligatorio, Campo.TipoCampo.SPECIFICO));
            salva();
            return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) return "Accesso negato.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (!cat.rimuoviCampoSpecifico(nomeCampo))
            return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Campo rimosso ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String modificaObbligatorietaCampoSpecifico(String nomeCategoria, String nomeCampo,
                                                        boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (!cat.modificaObbligatorietaCampoSpecifico(nomeCampo, obbligatorio))
            return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Modifica effettuata ma errore nel salvataggio: " + e.getMessage(); }
    }

    public List<Categoria> getCategorie()           { return appData.getCategorie(); }
    public Categoria getCategoria(String nome)       { return appData.getCategoria(nome); }

    // ================================================================
    // PROPOSTE – GESTIONE DI SESSIONE  (nuovo in V2)
    // ================================================================

    /**
     * Crea una nuova proposta per la categoria indicata e la aggiunge
     * alla lista di sessione. Lo snapshot dei campi rispecchia la
     * configurazione corrente al momento della creazione.
     *
     * @param nomeCategoria categoria della proposta, deve esistere
     * @return la Proposta creata, o null se la categoria non esiste o non loggato
     */
    public Proposta creaProposta(String nomeCategoria) {
        if (!isLoggato()) return null;
        if (nomeCategoria == null || !appData.esisteCategoria(nomeCategoria)) return null;

        // Snapshot ordinato: BASE → COMUNI → SPECIFICI
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Campo c : appData.getCampiBase())
            snapshot.put(c.getNome(), c.isObbligatorio());
        for (Campo c : appData.getCampiComuni())
            snapshot.put(c.getNome(), c.isObbligatorio());
        Categoria cat = appData.getCategoria(nomeCategoria);
        for (Campo c : cat.getCampiSpecifici())
            snapshot.put(c.getNome(), c.isObbligatorio());

        int id = appData.getNuovoIdProposta();
        Proposta p = new Proposta(id, nomeCategoria,
                configuratoreCorrente.getUsername(), snapshot);
        proposteSessione.add(p);

        assert proposteSessione.contains(p) : "Postcondizione: proposta non aggiunta alla sessione";
        return p;
    }

    /**
     * Imposta il valore di un campo di una proposta in sessione e ricalcola
     * automaticamente lo stato (BOZZA / VALIDA).
     *
     * @param proposta  proposta da aggiornare
     * @param nomeCampo nome del campo
     * @param valore    valore da assegnare
     * @return messaggio di errore, stringa vuota se successo
     */
    public String setValoreCampo(Proposta proposta, String nomeCampo, String valore) {
        if (proposta == null) return "Proposta non valida.";
        try {
            proposta.setValore(nomeCampo, valore);
            proposta.aggiornaStato(LocalDate.now());
            return "";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return e.getMessage();
        }
    }

    /**
     * Tenta di pubblicare in bacheca una proposta della sessione corrente.
     * La proposta deve essere in stato VALIDA.
     * Se la pubblicazione ha successo, la proposta viene rimossa dalla sessione
     * e aggiunta alla bacheca (persistita).
     *
     * @param proposta la proposta da pubblicare
     * @return messaggio di errore con dettagli, stringa vuota se successo
     */
    public String pubblicaProposta(Proposta proposta) {
        if (proposta == null) return "Proposta non valida.";
        if (!proposteSessione.contains(proposta))
            return "La proposta non appartiene alla sessione corrente.";

        // Aggiorna e verifica stato
        proposta.aggiornaStato(LocalDate.now());
        if (proposta.getStato() != StatoProposta.VALIDA) {
            List<String> errori = proposta.validazioneErrori(LocalDate.now());
            return "Proposta non valida. Problemi riscontrati:\n"
                    + String.join("\n", errori.stream()
                            .map(e -> "    * " + e)
                            .toArray(String[]::new));
        }

        // Pubblica
        proposta.pubblicaInBacheca(LocalDate.now());
        appData.aggiungiPropostaAperta(proposta);
        proposteSessione.remove(proposta);

        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Proposta pubblicata ma errore nel salvataggio: " + e.getMessage();
        }
    }

    /**
     * Rimuove una proposta non ancora pubblicata dalla sessione corrente.
     *
     * @param proposta proposta da eliminare
     * @return true se rimossa, false se non era in sessione
     */
    public boolean eliminaPropostaSessione(Proposta proposta) {
        return proposteSessione.remove(proposta);
    }

    /**
     * @return lista non modificabile delle proposte create nella sessione corrente
     *         (stato BOZZA o VALIDA, non ancora pubblicate)
     */
    public List<Proposta> getProposteSessione() {
        return Collections.unmodifiableList(proposteSessione);
    }

    // ================================================================
    // BACHECA  (nuovo in V2)
    // ================================================================

    /**
     * @return lista di tutte le proposte APERTE in bacheca
     */
    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    /**
     * @param nomeCategoria nome della categoria
     * @return proposte aperte per quella categoria
     */
    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        return appData.getBachecaPerCategoria(nomeCategoria);
    }

    // ================================================================
    // UTILITÀ
    // ================================================================

    private void salva() throws IOException {
        persistenceManager.salva(appData);
    }

    public boolean carica() throws IOException {
        return persistenceManager.carica(appData);
    }

    public Configuratore getConfiguratoreCorrente() { return configuratoreCorrente; }
    public AppData getAppData()                      { return appData; }
}
