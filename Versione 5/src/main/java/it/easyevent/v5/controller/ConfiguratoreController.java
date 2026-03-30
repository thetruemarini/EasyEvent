package it.easyevent.v5.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import it.easyevent.v5.batch.BatchImporter;
import it.easyevent.v5.batch.BatchRisultato;
import it.easyevent.v5.model.AppData;
import it.easyevent.v5.model.Campo;
import it.easyevent.v5.model.Categoria;
import it.easyevent.v5.model.Configuratore;
import it.easyevent.v5.model.Proposta;
import it.easyevent.v5.model.StatoProposta;
import it.easyevent.v5.persistence.PersistenceManager;

/**
 * Controller per tutte le operazioni del configuratore (Versione 5).
 *
 * Estende la V4 aggiungendo:
 *   - importaBatch(percorso): importa categorie, campi e proposte da un file batch
 *   - importaBatch(lista): importa più file batch in sequenza
 *
 * La modalità interattiva resta invariata.
 *
 * [NUOVO V5 – UC-CONF-09: Importazione batch]
 * Il configuratore può fornire uno o più file di testo contenenti comandi
 * CAMPO_COMUNE, CATEGORIA e PROPOSTA, che vengono eseguiti in sequenza.
 * Il risultato dell'importazione è restituito come BatchRisultato e
 * visualizzato dalla view.
 *
 * Invariante di classe:
 *   - appData != null
 *   - persistenceManager != null
 *   - proposteSessione != null
 */
public class ConfiguratoreController {

    private final AppData            appData;
    private final PersistenceManager persistenceManager;
    private Configuratore            configuratoreCorrente;
    private List<Proposta>           proposteSessione;

    public ConfiguratoreController(AppData appData, PersistenceManager persistenceManager) {
        if (appData == null) throw new IllegalArgumentException("AppData non puo' essere null.");
        if (persistenceManager == null) throw new IllegalArgumentException("PersistenceManager non puo' essere null.");
        this.appData              = appData;
        this.persistenceManager   = persistenceManager;
        this.configuratoreCorrente = null;
        this.proposteSessione     = new ArrayList<>();
    }

    // ================================================================
    // AUTENTICAZIONE
    // ================================================================

    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        if (appData.getConfiguratori().isEmpty()) {
            if (username.equals(AppData.DEFAULT_USERNAME) && password.equals(AppData.DEFAULT_PASSWORD)) {
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                try { salva(); } catch (IOException ignored) {}
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

    public void logout() {
        proposteSessione.clear();
        configuratoreCorrente = null;
    }

    public boolean isLoggato()                 { return configuratoreCorrente != null; }
    public boolean richiedeCambioCredenziali() { return configuratoreCorrente != null && configuratoreCorrente.isPrimoAccesso(); }

    public String impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!isLoggato())               return "Nessun configuratore loggato.";
        if (!richiedeCambioCredenziali()) return "Le credenziali personali sono gia' state impostate.";
        if (nuovoUsername == null || nuovoUsername.isBlank()) return "Lo username non puo' essere vuoto.";
        if (nuovaPassword == null || nuovaPassword.isBlank()) return "La password non puo' essere vuota.";

        String vecchioUsername = configuratoreCorrente.getUsername();
        String vecchiaPassword = configuratoreCorrente.getPassword();
        if (!nuovoUsername.equalsIgnoreCase(vecchioUsername) && appData.esisteUsernameGlobale(nuovoUsername))
            return "Username gia' in uso: " + nuovoUsername;

        configuratoreCorrente.impostaCredenzialiPersonali(nuovoUsername, nuovaPassword);
        try { salva(); } catch (IOException e) {
            configuratoreCorrente.revertCredenziali(vecchioUsername, vecchiaPassword);
            return "Errore nel salvataggio; le credenziali non sono state aggiornate: " + e.getMessage();
        }
        return "";
    }

    // ================================================================
    // CAMPI BASE
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
    // CAMPI COMUNI
    // ================================================================

    public String aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        if (nome == null || nome.isBlank()) return "Il nome del campo non puo' essere vuoto.";
        try {
            appData.aggiungiCampoComune(new Campo(nome.trim(), obbligatorio, Campo.TipoCampo.COMUNE));
            salva(); return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Il nome non puo' essere vuoto.";
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.getCampiSnapshot().containsKey(nomeCampo));
        if (inSessione)
            return "Impossibile rimuovere: esistono proposte in sessione che contengono il campo '" + nomeCampo + "'.";
        if (!appData.rimuoviCampoComune(nomeCampo)) return "Nessun campo comune trovato con nome: " + nomeCampo;
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
    // CATEGORIE
    // ================================================================

    public String aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome non puo' essere vuoto.";
        try {
            appData.aggiungiCategoria(new Categoria(nomeCategoria.trim()));
            salva(); return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Categoria aggiunta ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Il nome non puo' essere vuoto.";
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.getNomeCategoria().equalsIgnoreCase(nomeCategoria));
        if (inSessione)
            return "Impossibile rimuovere: esistono proposte in sessione per la categoria '" + nomeCategoria + "'.";
        if (!appData.rimuoviCategoria(nomeCategoria)) return "Nessuna categoria trovata con nome: " + nomeCategoria;
        try { salva(); return ""; }
        catch (IOException e) { return "Categoria rimossa ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        if (nomeCategoria == null || nomeCategoria.isBlank()) return "Nome categoria vuoto.";
        if (nomeCampo == null || nomeCampo.isBlank()) return "Nome campo vuoto.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (appData.esisteCampoBase(nomeCampo))   return "Esiste gia' un campo base con nome: " + nomeCampo;
        if (appData.esisteCampoComune(nomeCampo)) return "Esiste gia' un campo comune con nome: " + nomeCampo;
        try {
            cat.aggiungiCampoSpecifico(new Campo(nomeCampo.trim(), obbligatorio, Campo.TipoCampo.SPECIFICO));
            salva(); return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) { return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) return "Accesso negato.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (!cat.rimuoviCampoSpecifico(nomeCampo)) return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Campo rimosso ma errore nel salvataggio: " + e.getMessage(); }
    }

    public String modificaObbligatorietaCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) return "Accesso negato.";
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) return "Categoria non trovata: " + nomeCategoria;
        if (!cat.modificaObbligatorietaCampoSpecifico(nomeCampo, obbligatorio))
            return "Nessun campo specifico trovato con nome: " + nomeCampo;
        try { salva(); return ""; }
        catch (IOException e) { return "Modifica effettuata ma errore nel salvataggio: " + e.getMessage(); }
    }

    public List<Categoria> getCategorie()      { return appData.getCategorie(); }
    public Categoria getCategoria(String nome)  { return appData.getCategoria(nome); }

    // ================================================================
    // PROPOSTE – SESSIONE
    // ================================================================

    public Proposta creaProposta(String nomeCategoria) {
        if (!isLoggato()) return null;
        if (nomeCategoria == null || !appData.esisteCategoria(nomeCategoria)) return null;
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Campo c : appData.getCampiBase())   snapshot.put(c.getNome(), c.isObbligatorio());
        for (Campo c : appData.getCampiComuni())  snapshot.put(c.getNome(), c.isObbligatorio());
        Categoria cat = appData.getCategoria(nomeCategoria);
        for (Campo c : cat.getCampiSpecifici())   snapshot.put(c.getNome(), c.isObbligatorio());
        int id = appData.getNuovoIdProposta();
        Proposta p = new Proposta(id, nomeCategoria, configuratoreCorrente.getUsername(), snapshot);
        proposteSessione.add(p);
        return p;
    }

    public String setValoreCampo(Proposta proposta, String nomeCampo, String valore) {
        if (proposta == null) return "Proposta non valida.";
        try {
            proposta.setValore(nomeCampo, valore);
            proposta.aggiornaStato(LocalDate.now());
            return "";
        } catch (IllegalArgumentException | IllegalStateException e) { return e.getMessage(); }
    }

    public String pubblicaProposta(Proposta proposta) {
        if (proposta == null) return "Proposta non valida.";
        if (!proposteSessione.contains(proposta)) return "La proposta non appartiene alla sessione corrente.";
        LocalDate oggi = LocalDate.now();
        proposta.aggiornaStato(oggi);
        if (proposta.getStato() != StatoProposta.VALIDA) {
            List<String> errori = proposta.validazioneErrori(oggi);
            return "Proposta non valida. Problemi:\n" + String.join("\n",
                    errori.stream().map(e -> "    * " + e).toArray(String[]::new));
        }
        proposta.pubblicaInBacheca(oggi);
        appData.aggiungiPropostaAperta(proposta);
        proposteSessione.remove(proposta);
        try { salva(); return ""; }
        catch (IOException e) {
            appData.rimuoviPropostaDaArchivio(proposta.getId());
            proposta.revertToValida();
            proposteSessione.add(proposta);
            return "Errore nel salvataggio; la proposta non e' stata pubblicata: " + e.getMessage();
        }
    }

    public boolean eliminaPropostaSessione(Proposta proposta) { return proposteSessione.remove(proposta); }
    public List<Proposta> getProposteSessione() { return Collections.unmodifiableList(proposteSessione); }

    // ================================================================
    // IMPORTAZIONE BATCH (NUOVO V5 – UC-CONF-09)
    // ================================================================

    /**
     * Importa categorie, campi e proposte da un singolo file batch.
     *
     * [UC-CONF-09 – Importazione batch]
     * Il configuratore può fornire un file di testo con comandi strutturati
     * (CAMPO_COMUNE, CATEGORIA, PROPOSTA) per evitare l'inserimento manuale
     * ripetitivo. La modalità interattiva resta disponibile in parallelo.
     *
     * Precondizioni:
     * - Il configuratore deve essere loggato.
     * - Il file deve esistere e deve essere leggibile.
     *
     * Postcondizioni:
     * - Lo stato dell'applicazione è aggiornato con le operazioni andate a buon fine.
     * - Il file di persistenza è aggiornato.
     * - Il BatchRisultato contiene il resoconto completo (successi, warning, errori).
     *
     * @param percorsoFile path del file batch, non null e non blank
     * @return resoconto dell'importazione
     * @throws IllegalStateException    se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsoFile è null o blank
     * @throws IOException              se il file non esiste o non è leggibile
     */
    public BatchRisultato importaBatch(String percorsoFile)
            throws IOException {
        if (!isLoggato())
            throw new IllegalStateException("Nessun configuratore loggato: impossibile importare in batch.");
        if (percorsoFile == null || percorsoFile.isBlank())
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");

        BatchImporter importer = new BatchImporter(
                appData,
                configuratoreCorrente.getUsername(),
                this::salva          // lambda che delega al metodo salva() di questo controller
        );

        return importer.importa(percorsoFile);
    }

    /**
     * Importa categorie, campi e proposte da più file batch in sequenza.
     *
     * I file vengono elaborati nell'ordine della lista. I risultati sono
     * aggregati in un unico BatchRisultato complessivo.
     *
     * @param percorsiFile lista dei path dei file batch, non null
     * @return resoconto aggregato dell'intera importazione
     * @throws IllegalStateException    se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsiFile è null
     */
    public BatchRisultato importaBatch(List<String> percorsiFile) {
        if (!isLoggato())
            throw new IllegalStateException("Nessun configuratore loggato: impossibile importare in batch.");
        if (percorsiFile == null)
            throw new IllegalArgumentException("La lista dei percorsi non puo' essere null.");

        BatchImporter importer = new BatchImporter(
                appData,
                configuratoreCorrente.getUsername(),
                this::salva
        );

        return importer.importaMultipli(percorsiFile);
    }

    // ================================================================
    // RITIRO PROPOSTA (V4 – invariato)
    // ================================================================

    public String ritirareProposta(int idProposta) {
        if (!isLoggato()) return "Accesso negato.";
        Proposta p = appData.getPropostaDaArchivio(idProposta);
        if (p == null)
            return "Proposta non trovata nell'archivio (ID: " + idProposta + ").";
        LocalDate oggi = LocalDate.now();
        String erroreRitiro = p.verificaRitiroConsentito(oggi);
        if (!erroreRitiro.isEmpty()) return erroreRitiro;

        try {
            appData.ritirareProposta(p, oggi);
        } catch (IllegalStateException e) {
            return "Errore nella transizione di stato: " + e.getMessage();
        }
        try {
            salva(); return "";
        } catch (IOException e) {
            try {
                persistenceManager.carica(appData);
                System.err.println("[Sistema] Rollback ritiro completato.");
            } catch (IOException rollbackEx) {
                System.err.println("[Sistema] Rollback fallito: " + rollbackEx.getMessage());
            }
            return "Errore nel salvataggio; il ritiro non e' stato registrato: " + e.getMessage();
        }
    }

    // ================================================================
    // BACHECA E ARCHIVIO
    // ================================================================

    public List<Proposta> getBacheca()                             { return appData.getBacheca(); }
    public List<Proposta> getBachecaPerCategoria(String nome)      { return appData.getBachecaPerCategoria(nome); }
    public List<Proposta> getArchivio()                            { return appData.getArchivio(); }

    // ================================================================
    // TRANSIZIONI AUTOMATICHE
    // ================================================================

    public int aggiornaTransizioni() {
        int n = appData.aggiornaTransizioni(LocalDate.now());
        if (n > 0) {
            try { salva(); }
            catch (IOException e) {
                System.err.println("[Sistema] Errore salvataggio dopo transizioni: " + e.getMessage());
                try {
                    persistenceManager.carica(appData);
                    System.err.println("[Sistema] Rollback transizioni completato.");
                } catch (IOException rollbackEx) {
                    System.err.println("[Sistema] Rollback fallito: " + rollbackEx.getMessage());
                }
                return 0;
            }
        }
        return n;
    }

    // ================================================================
    // UTILITA'
    // ================================================================

    public void salva() throws IOException        { persistenceManager.salva(appData); }
    public boolean carica() throws IOException    { return persistenceManager.carica(appData); }
    public Configuratore getConfiguratoreCorrente() { return configuratoreCorrente; }
    public AppData getAppData()                     { return appData; }
}