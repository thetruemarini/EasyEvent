package easyevent.controller;

import easyevent.batch.BatchImporter;
import easyevent.batch.BatchRisultato;
import easyevent.categoria.Campo;
import easyevent.categoria.CampoComune;
import easyevent.categoria.CampoSpecifico;
import easyevent.categoria.Categoria;
import easyevent.core.AppData;
import easyevent.exception.ElementoGiaEsistenteException;
import easyevent.exception.ElementoInSessioneException;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.exception.ErroreValidazione;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.PersistenzaException;
import easyevent.model.Configuratore;
import easyevent.persistence.PersistenceManager;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Controller per tutte le operazioni del configuratore: gestione di campi,
 * categorie e proposte, sia in modalità interattiva sia tramite import batch.
 *
 * Nell'import batch il configuratore fornisce uno o più file di testo contenenti
 * comandi CAMPO_COMUNE, CATEGORIA e PROPOSTA, eseguiti in sequenza; l'esito è
 * restituito come BatchRisultato e visualizzato dalla View.
 *
 * Invariante di classe: - appData != null - persistenceManager != null -
 * proposteSessione != null
 */
public class ConfiguratoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Configuratore configuratoreCorrente;
    private List<Proposta> proposteSessione;
    private final String defaultAdminUsername;
    private final String defaultAdminPassword;

    /**
     * Le dipendenze (AppData, PersistenceManager) sono ricevute tramite
     * costruttore (Dependency Injection). Non si usa getInstance() né variabili
     * globali: chi istanzia questo controller dichiara esplicitamente da cosa
     * dipende, rendendo la dipendenza visibile a compile-time e sostituibile
     * nei test.
     */
    public ConfiguratoreController(AppData appData, PersistenceManager persistenceManager,
            String defaultAdminUsername, String defaultAdminPassword) {
        if (appData == null) {
            throw new IllegalArgumentException("AppData non puo' essere null.");
        }
        if (persistenceManager == null) {
            throw new IllegalArgumentException("PersistenceManager non puo' essere null.");
        }
        this.appData = appData;
        this.persistenceManager = persistenceManager;
        this.configuratoreCorrente = null;
        this.proposteSessione = new ArrayList<>();
        this.defaultAdminUsername = defaultAdminUsername;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    // ================================================================
    // AUTENTICAZIONE
    // ================================================================
    /**
     * Autentica un configuratore. Al primo avvio del sistema (nessun
     * configuratore registrato) accetta le credenziali di amministratore di
     * default e crea il primo configuratore, marcato come "primo accesso".
     *
     * @return true se il login ha avuto successo, false altrimenti.
     */
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (appData.getConfiguratori().isEmpty()) {
            if (username.equals(this.defaultAdminUsername) && password.equals(this.defaultAdminPassword)) {
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                try {
                    salvaInterno();
                } catch (PersistenzaException ignored) {
                }
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

    public boolean isLoggato() {
        return configuratoreCorrente != null;
    }

    public boolean richiedeCambioCredenziali() {
        return configuratoreCorrente != null && configuratoreCorrente.isPrimoAccesso();
    }

    /**
     * Sostituisce le credenziali di default con quelle personali del
     * configuratore, consentito solo al primo accesso. Verifica l'unicità del
     * nuovo username e, se la persistenza fallisce, ripristina le vecchie
     * credenziali (rollback).
     *
     * @throws ModificaNonConsentitaException se nessuno è loggato o le
     * credenziali sono già state impostate.
     * @throws ElementoGiaEsistenteException se il nuovo username è già in uso.
     */
    public void impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!isLoggato()) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.NESSUN_CONFIGURATORE_LOGGATO,
                    null);
        }
        if (!richiedeCambioCredenziali()) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.CREDENZIALI_GIA_IMPOSTATE,
                    null);
        }
        if (nuovoUsername == null || nuovoUsername.isBlank()) {
            throw new IllegalArgumentException("nuovoUsername non può essere vuoto");
        }
        if (nuovaPassword == null || nuovaPassword.isBlank()) {
            throw new IllegalArgumentException("nuovaPassword non può essere vuota");
        }

        String vecchioUsername = configuratoreCorrente.getUsername();
        String vecchiaPassword = configuratoreCorrente.getPassword();
        if (!nuovoUsername.equalsIgnoreCase(vecchioUsername) && appData.esisteUsernameGlobale(nuovoUsername)) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.USERNAME, nuovoUsername);
        }

        configuratoreCorrente.impostaCredenzialiPersonali(nuovoUsername, nuovaPassword);
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            configuratoreCorrente.revertCredenziali(vecchioUsername, vecchiaPassword);
            throw e;
        }
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================
    public void inizializzaCampiBase() {
        if (appData.isCampiBaseInitialized()) {
            return;
        }
        appData.inizializzaCampiBase();
        persistenceManager.salvaSicuro(appData);
    }

    public List<Campo> getCampiBase() {
        return appData.getCampiBase();
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================
    public void aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non può essere vuoto.");
        }
        appData.aggiungiCampoComune(new CampoComune(nome.trim(), obbligatorio));
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            appData.rimuoviCampoComune(nome.trim());
            throw e;
        }
    }

    /**
     * Rimuove un campo comune e persiste la modifica. Il campo non può essere
     * rimosso se è ancora usato da una proposta della sessione corrente.
     *
     * @throws ModificaNonConsentitaException se nessun configuratore è loggato.
     * @throws ElementoInSessioneException se il campo è usato in sessione.
     * @throws ElementoNonTrovatoException se il campo non esiste.
     */
    public void rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (nomeCampo == null || nomeCampo.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non può essere vuoto.");
        }
        if (appData.campoUsatoInSessione(proposteSessione, nomeCampo)) {
            throw new ElementoInSessioneException(
                    ElementoInSessioneException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        if (!appData.rimuoviCampoComune(nomeCampo)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        salvaInterno();
    }

    public void modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (!appData.modificaObbligatorietaCampoComune(nomeCampo, obbligatorio)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        salvaInterno();
    }

    public List<Campo> getCampiComuni() {
        return appData.getCampiComuni();
    }

    // ================================================================
    // CATEGORIE
    // ================================================================
    public void aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome non può essere vuoto.");
        }
        appData.aggiungiCategoria(new Categoria(nomeCategoria.trim()));
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            appData.rimuoviCategoria(nomeCategoria.trim()); // rollback
            throw e;
        }
    }

    /**
     * Rimuove una categoria e persiste la modifica. La categoria non può essere
     * rimossa se è ancora usata da una proposta della sessione corrente.
     *
     * @throws ModificaNonConsentitaException se nessun configuratore è loggato.
     * @throws ElementoInSessioneException se la categoria è usata in sessione.
     * @throws ElementoNonTrovatoException se la categoria non esiste.
     */
    public void rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome non può essere vuoto.");
        }
        if (appData.categoriaUsataInSessione(proposteSessione, nomeCategoria)) {
            throw new ElementoInSessioneException(
                    ElementoInSessioneException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        if (!appData.rimuoviCategoria(nomeCategoria)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        salvaInterno();
    }

    public void aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Nome categoria non può essere vuoto.");
        }
        if (nomeCampo == null || nomeCampo.isBlank()) {
            throw new IllegalArgumentException("Nome campo non può essere vuoto.");
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        if (appData.esisteCampoBase(nomeCampo)) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.CAMPO_BASE, nomeCampo);
        }
        if (appData.esisteCampoComune(nomeCampo)) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        cat.aggiungiCampoSpecifico(new CampoSpecifico(nomeCampo.trim(), obbligatorio));
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            cat.rimuoviCampoSpecifico(nomeCampo.trim()); // rollback
            throw e;
        }
    }

    public void rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        if (!cat.rimuoviCampoSpecifico(nomeCampo)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_SPECIFICO, nomeCampo);
        }
        salvaInterno();
    }

    public void modificaObbligatorietaCampoSpecifico(
            String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        if (!cat.modificaObbligatorietaCampoSpecifico(nomeCampo, obbligatorio)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_SPECIFICO, nomeCampo);
        }
        salvaInterno();
    }

    public List<Categoria> getCategorie() {
        return appData.getCategorie();
    }

    public Categoria getCategoria(String nome) {
        return appData.getCategoria(nome);
    }

    /**
     * Restituisce i campi specifici di una categoria, in sola lettura. La View
     * usa questo metodo invece di ottenere il riferimento alla Categoria.
     */
    public List<Campo> getCampiSpecificiCategoria(String nomeCategoria) {
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            return Collections.emptyList();
        }
        return cat.getCampiSpecifici(); // già unmodifiable in Categoria
    }

    // ================================================================
    // PROPOSTE – SESSIONE
    // ================================================================
    /**
     * Crea una nuova proposta in bozza per la categoria indicata, costruendone
     * lo snapshot dei campi (base + comuni + specifici della categoria con la
     * rispettiva obbligatorietà) e aggiungendola alla sessione corrente.
     *
     * @return la proposta creata, o null se nessuno è loggato o la categoria non
     * esiste.
     */
    public Proposta creaProposta(String nomeCategoria) {
        if (!isLoggato()) {
            return null;
        }
        if (nomeCategoria == null || !appData.esisteCategoria(nomeCategoria)) {
            return null;
        }
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Campo c : appData.getCampiBase()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }
        for (Campo c : appData.getCampiComuni()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        for (Campo c : cat.getCampiSpecifici()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }
        IdProposta id = appData.getNuovoIdProposta();
        Proposta p = new Proposta(id, nomeCategoria, configuratoreCorrente.getUsername(), snapshot);
        proposteSessione.add(p);
        return p;
    }

    public void setValoreCampo(Proposta proposta, String nomeCampo, String valore) {
        if (proposta == null) {
            throw new IllegalArgumentException("proposta non può essere null");
        }
        proposta.setValore(nomeCampo, valore);
        proposta.aggiornaStato(LocalDate.now());
    }

    /**
     * Restituisce gli errori di validazione della proposta rispetto a oggi. La
     * regola sta nel Model: il Controller fornisce soltanto la data corrente,
     * cosi' la View non deve procurarsi l'orologio per sapere cosa mostrare.
     *
     * @return lista vuota se la proposta e' valida.
     */
    public List<ErroreValidazione> getErroriValidazione(Proposta proposta) {
        if (proposta == null) {
            throw new IllegalArgumentException("proposta non può essere null");
        }
        return proposta.validazioneErrori(LocalDate.now());
    }

    /**
     * Pubblica una proposta della sessione corrente in bacheca e persiste la
     * modifica. Se la persistenza fallisce, annulla la pubblicazione e riporta
     * la proposta in sessione (rollback). Restituisce dati strutturati anziché
     * lanciare un'eccezione perché gli errori di validazione sono multipli.
     *
     * @return lista vuota se pubblicata; lista di errori se non ancora valida.
     * @throws ModificaNonConsentitaException se la proposta non è in sessione.
     */
    public List<ErroreValidazione> pubblicaProposta(Proposta proposta) {
        if (proposta == null) {
            throw new IllegalArgumentException("proposta non può essere null");
        }
        if (!proposteSessione.contains(proposta)) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.PROPOSTA_NON_IN_SESSIONE,
                    proposta.getId().toString());
        }
        List<ErroreValidazione> errori = appData.pubblicaPropostaDiretta(proposta, LocalDate.now());
        if (!errori.isEmpty()) {
            return errori;
        }
        proposteSessione.remove(proposta);
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            appData.rimuoviPropostaDaArchivio(proposta.getId());
            proposta.revertToValida();
            proposteSessione.add(proposta);
            throw e;
        }
        return Collections.emptyList();
    }

    public boolean eliminaPropostaSessione(Proposta proposta) {
        return proposteSessione.remove(proposta);
    }

    public List<Proposta> getProposteSessione() {
        return Collections.unmodifiableList(proposteSessione);
    }

    // ================================================================
    // IMPORTAZIONE BATCH
    // ================================================================
    /**
     * Importa categorie, campi e proposte da un singolo file batch.
     *
     * [UC-CONF-09 – Importazione batch] Il configuratore può fornire un file di
     * testo con comandi strutturati (CAMPO_COMUNE, CATEGORIA, PROPOSTA) per
     * evitare l'inserimento manuale ripetitivo. La modalità interattiva resta
     * disponibile in parallelo.
     *
     * Precondizioni: - Il configuratore deve essere loggato. - Il file deve
     * esistere e deve essere leggibile.
     *
     * Postcondizioni: - Lo stato dell'applicazione è aggiornato con le
     * operazioni andate a buon fine. - Il file di persistenza è aggiornato. -
     * Il BatchRisultato contiene il resoconto completo (successi, warning,
     * errori).
     *
     * @param percorsoFile path del file batch, non null e non blank
     * @return resoconto dell'importazione
     * @throws ModificaNonConsentitaException se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsoFile è null o blank
     */
    public BatchRisultato importaBatch(String percorsoFile) {
        if (!isLoggato()) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.NESSUN_CONFIGURATORE_LOGGATO,
                    null);
        }
        if (percorsoFile == null || percorsoFile.isBlank()) {
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");
        }

        BatchImporter importer = new BatchImporter(
                appData,
                configuratoreCorrente.getUsername(),
                this::salvaInterno // lambda che delega al metodo salva() di questo controller
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
     * @throws ModificaNonConsentitaException se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsiFile è null
     */
    public BatchRisultato importaBatch(List<String> percorsiFile) {
        if (!isLoggato()) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.NESSUN_CONFIGURATORE_LOGGATO,
                    null);
        }
        if (percorsiFile == null) {
            throw new IllegalArgumentException("La lista dei percorsi non puo' essere null.");
        }

        BatchImporter importer = new BatchImporter(
                appData,
                configuratoreCorrente.getUsername(),
                this::salvaInterno
        );

        return importer.importaMultipli(percorsiFile);
    }

    // ================================================================
    // RITIRO PROPOSTA
    // ================================================================
    /**
     * Ritira la proposta indicata (notificando gli aderenti) e persiste la
     * modifica. Se la persistenza fallisce, ricarica lo stato da disco per
     * annullare il ritiro (rollback).
     *
     * @throws ModificaNonConsentitaException se nessun configuratore è loggato.
     * @throws ElementoNonTrovatoException se la proposta non esiste in archivio.
     * @throws RitiroNonConsentitoException se il ritiro non è consentito.
     */
    public void ritirareProposta(IdProposta idProposta) {
        if (!isLoggato()) {
            throw accessoNegato();
        }
        Proposta p = appData.getPropostaDaArchivio(idProposta);
        if (p == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.PROPOSTA,
                    idProposta.toString()
            );
        }
        LocalDate oggi = LocalDate.now();
        p.verificaRitiroConsentito(oggi);
        appData.ritirareProposta(p, oggi);
        try {
            salvaInterno();
        } catch (PersistenzaException e) {
            try {
                persistenceManager.caricaSicuro(appData);
            } catch (PersistenzaException re) {
                System.err.println("[Sistema] Rollback fallito: " + re.getMessage());
            }
            throw e;
        }
    }

    /**
     * Restituisce le proposte che il configuratore può ritirare oggi. La regola
     * "quali proposte sono ritirabili" è nel Model.
     */
    public List<Proposta> getProposteRitirabili() {
        return appData.getProposteRitirabili(LocalDate.now());
    }

    // ================================================================
    // BACHECA E ARCHIVIO
    // ================================================================
    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    public List<Proposta> getBachecaPerCategoria(String nome) {
        return appData.getBachecaPerCategoria(nome);
    }

    public List<Proposta> getArchivio() {
        return appData.getArchivio();
    }

    // ================================================================
    // TRANSIZIONI AUTOMATICHE
    // ================================================================
    /**
     * Applica le transizioni di stato automatiche dovute al tempo trascorso e,
     * se qualcosa è cambiato, persiste lo stato aggiornato.
     *
     * @return il numero di proposte che hanno cambiato stato.
     */
    public int aggiornaTransizioni() {
        int n = appData.aggiornaTransizioni(LocalDate.now());
        if (n > 0) {
            try {
                salvaInterno();
            } catch (PersistenzaException e) {
                System.err.println("[Sistema] Errore salvataggio dopo transizioni: " + e.getMessage());
                try {
                    persistenceManager.caricaSicuro(appData);
                    System.err.println("[Sistema] Rollback transizioni completato.");
                } catch (PersistenzaException rollbackEx) {
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
    /**
     * Uso interno al controller: wrappa in PersistenzaException
     */
    private void salvaInterno() {
        persistenceManager.salvaSicuro(appData);
    }

    private ModificaNonConsentitaException accessoNegato() {
        return new ModificaNonConsentitaException(
                ModificaNonConsentitaException.TipoModifica.ACCESSO_NEGATO,
                null);
    }

    public Configuratore getConfiguratoreCorrente() {
        return configuratoreCorrente;
    }

    public AppData getAppData() {
        return appData;
    }

    // ================================================================
    // INTERROGAZIONI SUI CAMPI
    // ================================================================
    public boolean isCampoData(String nomeCategoria, String nomeCampo) {
        Campo c = appData.getCampo(nomeCategoria, nomeCampo);
        return c != null && c.isData();
    }

    public boolean isCampoOra(String nomeCategoria, String nomeCampo) {
        Campo c = appData.getCampo(nomeCategoria, nomeCampo);
        return c != null && c.isOra();
    }
}
