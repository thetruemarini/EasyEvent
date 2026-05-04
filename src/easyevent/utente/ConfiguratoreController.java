package easyevent.utente;

import easyevent.batch.BatchImporter;
import easyevent.batch.BatchRisultato;
import easyevent.categoria.Campo;
import easyevent.categoria.Categoria;
import easyevent.core.AppData;
import easyevent.exception.ElementoGiaEsistenteException;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.persistence.PersistenceManager;
import easyevent.proposta.Proposta;
import easyevent.proposta.StatoProposta;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Controller per tutte le operazioni del configuratore (Versione 5).
 *
 * Estende la V4 aggiungendo: - importaBatch(percorso): importa categorie, campi
 * e proposte da un file batch - importaBatch(lista): importa più file batch in
 * sequenza
 *
 * La modalità interattiva resta invariata.
 *
 * Il configuratore può fornire uno o più file di testo contenenti comandi
 * CAMPO_COMUNE, CATEGORIA e PROPOSTA, che vengono eseguiti in sequenza. Il
 * risultato dell'importazione è restituito come BatchRisultato e visualizzato
 * dalla view.
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
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (appData.getConfiguratori().isEmpty()) {
            if (username.equals(this.defaultAdminUsername) && password.equals(this.defaultAdminPassword)) {
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                try {
                    salva();
                } catch (IOException ignored) {
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

    public void impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!isLoggato()) {
            throw new IllegalStateException("Nessun configuratore loggato.");
        }
        if (!richiedeCambioCredenziali()) {
            throw new IllegalStateException("Le credenziali personali sono gia' state impostate.");
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
            salva();
        } catch (IOException e) {
            configuratoreCorrente.revertCredenziali(vecchioUsername, vecchiaPassword);
            throw new RuntimeException("Errore nel salvataggio delle credenziali.", e);
        }
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================
    public void inizializzaCampiBase() throws IOException {
        if (appData.isCampiBaseInitialized()) {
            return;
        }
        appData.inizializzaCampiBase();
        salva();
    }

    public List<Campo> getCampiBase() {
        return appData.getCampiBase();
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================
    public void aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non può essere vuoto.");
        }
        appData.aggiungiCampoComune(new Campo(nome.trim(), obbligatorio, Campo.TipoCampo.COMUNE));
        try {
            salva();
        } catch (IOException e) {
            appData.rimuoviCampoComune(nome.trim()); // rollback
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        if (nomeCampo == null || nomeCampo.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non può essere vuoto.");
        }
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.usaCampo(nomeCampo));
        if (inSessione) {
            throw new IllegalStateException("campoInSessione:" + nomeCampo);
        }
        if (!appData.rimuoviCampoComune(nomeCampo)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        try {
            salva();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        if (!appData.modificaObbligatorietaCampoComune(nomeCampo, obbligatorio)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CAMPO_COMUNE, nomeCampo);
        }
        try {
            salva();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public List<Campo> getCampiComuni() {
        return appData.getCampiComuni();
    }

    // ================================================================
    // CATEGORIE
    // ================================================================
    public void aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome non può essere vuoto.");
        }
        appData.aggiungiCategoria(new Categoria(nomeCategoria.trim()));
        try {
            salva();
        } catch (IOException e) {
            appData.rimuoviCategoria(nomeCategoria.trim()); // rollback
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome non può essere vuoto.");
        }
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.getNomeCategoria().equalsIgnoreCase(nomeCategoria));
        if (inSessione) {
            throw new IllegalStateException("categoriaInSessione:" + nomeCategoria);
        }
        if (!appData.rimuoviCategoria(nomeCategoria)) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.CATEGORIA, nomeCategoria);
        }
        try {
            salva();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
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
        cat.aggiungiCampoSpecifico(new Campo(nomeCampo.trim(), obbligatorio, Campo.TipoCampo.SPECIFICO));
        try {
            salva();
        } catch (IOException e) {
            cat.rimuoviCampoSpecifico(nomeCampo.trim()); // rollback
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
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
        try {
            salva();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public void modificaObbligatorietaCampoSpecifico(
            String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
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
        try {
            salva();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

    public List<Categoria> getCategorie() {
        return appData.getCategorie();
    }

    public Categoria getCategoria(String nome) {
        return appData.getCategoria(nome);
    }

    // ================================================================
    // PROPOSTE – SESSIONE
    // ================================================================
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
        int id = appData.getNuovoIdProposta();
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

    // Nota: restituisce List<String> errori se la proposta non è valida,
    // perché gli errori di validazione sono multipli e strutturati.
    public List<String> pubblicaProposta(Proposta proposta) {
        if (proposta == null) {
            throw new IllegalArgumentException("proposta non può essere null");
        }
        if (!proposteSessione.contains(proposta)) {
            throw new IllegalStateException("La proposta non appartiene alla sessione corrente.");
        }
        LocalDate oggi = LocalDate.now();
        proposta.aggiornaStato(oggi);
        if (proposta.getStato() != StatoProposta.VALIDA) {
            return proposta.validazioneErrori(oggi);
        }
        proposta.pubblicaInBacheca(oggi);
        appData.aggiungiPropostaAperta(proposta);
        proposteSessione.remove(proposta);
        try {
            salva();
        } catch (IOException e) {
            appData.rimuoviPropostaDaArchivio(proposta.getId());
            proposta.revertToValida();
            proposteSessione.add(proposta);
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
        return Collections.emptyList(); // lista vuota = successo
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
     * @throws IllegalStateException se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsoFile è null o blank
     * @throws IOException se il file non esiste o non è leggibile
     */
    public BatchRisultato importaBatch(String percorsoFile)
            throws IOException {
        if (!isLoggato()) {
            throw new IllegalStateException("Nessun configuratore loggato: impossibile importare in batch.");
        }
        if (percorsoFile == null || percorsoFile.isBlank()) {
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");
        }

        BatchImporter importer = new BatchImporter(
                appData,
                configuratoreCorrente.getUsername(),
                this::salva // lambda che delega al metodo salva() di questo controller
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
     * @throws IllegalStateException se nessun configuratore è loggato
     * @throws IllegalArgumentException se percorsiFile è null
     */
    public BatchRisultato importaBatch(List<String> percorsiFile) {
        if (!isLoggato()) {
            throw new IllegalStateException("Nessun configuratore loggato: impossibile importare in batch.");
        }
        if (percorsiFile == null) {
            throw new IllegalArgumentException("La lista dei percorsi non puo' essere null.");
        }

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
    public void ritirareProposta(int idProposta) {
        if (!isLoggato()) {
            throw new IllegalStateException("Accesso negato.");
        }
        Proposta p = appData.getPropostaDaArchivio(idProposta);
        if (p == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.PROPOSTA,
                    String.valueOf(idProposta)
            );
        }
        LocalDate oggi = LocalDate.now();
        p.verificaRitiroConsentito(oggi);
        appData.ritirareProposta(p, oggi);
        try {
            salva();
        } catch (IOException e) {
            try {
                persistenceManager.carica(appData);
            } catch (IOException re) {
                System.err.println("[Sistema] Rollback fallito: " + re.getMessage());
            }
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
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
    public int aggiornaTransizioni() {
        int n = appData.aggiornaTransizioni(LocalDate.now());
        if (n > 0) {
            try {
                salva();
            } catch (IOException e) {
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
    public void salva() throws IOException {
        persistenceManager.salva(appData);
    }

    public boolean carica() throws IOException {
        return persistenceManager.carica(appData);
    }

    public Configuratore getConfiguratoreCorrente() {
        return configuratoreCorrente;
    }

    public AppData getAppData() {
        return appData;
    }

    // ================================================================
    // METODI PRESENTAZIONALI PER LA VIEW (Refactoring Problema 6)
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
