package it.easyevent.v3.controller;

import it.easyevent.v3.model.AppData;
import it.easyevent.v3.model.Campo;
import it.easyevent.v3.model.Categoria;
import it.easyevent.v3.model.Configuratore;
import it.easyevent.v3.model.Proposta;
import it.easyevent.v3.model.StatoProposta;
import it.easyevent.v3.persistence.PersistenceManager;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
/**
 * Controller per tutte le operazioni del configuratore (Versione 3).
 *
 * Estende la V2 aggiungendo:
 *   - visualizzazione dell'archivio proposte (tutte le proposte pubblicate)
 *   - aggiornaTransizioni: effettua i passaggi di stato automatici all'avvio
 *
 * Invariante di classe:
 *   - appData != null
 *   - persistenceManager != null
 *   - configuratoreCorrente puo' essere null (nessun utente loggato)
 *   - proposteSessione != null (puo' essere vuota, mai null)
 */
public class ConfiguratoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Configuratore configuratoreCorrente;
    private List<Proposta> proposteSessione;

    public ConfiguratoreController(AppData appData, PersistenceManager persistenceManager) {
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
    }

    // ================================================================
    // AUTENTICAZIONE  (invariata da V1/V2)
    // ================================================================
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        if (appData.getConfiguratori().isEmpty()) {
            if (username.equals(AppData.DEFAULT_USERNAME)
                    && password.equals(AppData.DEFAULT_PASSWORD)) {
                Configuratore nuovo = new Configuratore(username, password, true);
                appData.aggiungiConfiguratore(nuovo);
                // Salva subito: senza questo, se l'utente annulla il cambio credenziali
                // e l'app viene chiusa, al prossimo avvio il configuratore non esiste su
                // disco e si riparte da zero con credenziali predefinite. Il salvataggio
                // fallisce silenziosamente (non blocca il login) per non penalizzare
                // l'utente al primo avvio in ambienti con problemi di I/O.
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

    public String impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!isLoggato()) {
            return "Nessun configuratore loggato.";
        }
        if (!richiedeCambioCredenziali()) {
            return "Le credenziali personali sono gia' state impostate.";
        }
        if (nuovoUsername == null || nuovoUsername.isBlank()) {
            return "Lo username non puo' essere vuoto.";
        }
        if (nuovaPassword == null || nuovaPassword.isBlank()) {
            return "La password non puo' essere vuota.";
        }

        String vecchioUsername = configuratoreCorrente.getUsername();
        String vecchiaPassword = configuratoreCorrente.getPassword();
        if (!nuovoUsername.equalsIgnoreCase(vecchioUsername)
                && appData.esisteUsernameGlobale(nuovoUsername)) {
            return "Username gia' in uso: " + nuovoUsername;
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
        return "";
    }

    // ================================================================
    // CAMPI BASE  (invariata da V1/V2)
    // ================================================================
    public String inizializzaCampiBase() {
        if (appData.isCampiBaseInitialized()) {
            return "";
        }
        appData.inizializzaCampiBase();
        try {
            salva();
        } catch (IOException e) {
            return "Campi base creati ma errore nel salvataggio: " + e.getMessage();
        }
        return "";
    }

    public List<Campo> getCampiBase() {
        return appData.getCampiBase();
    }

    // ================================================================
    // CAMPI COMUNI  (invariata da V1/V2)
    // ================================================================
    public String aggiungiCampoComune(String nome, boolean obbligatorio) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (nome == null || nome.isBlank()) {
            return "Il nome del campo non puo' essere vuoto.";
        }
        try {
            appData.aggiungiCampoComune(new Campo(nome.trim(), obbligatorio, Campo.TipoCampo.COMUNE));
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String rimuoviCampoComune(String nomeCampo) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (nomeCampo == null || nomeCampo.isBlank()) {
            return "Il nome non puo' essere vuoto.";
        }
        // Impedisce la rimozione se ci sono proposte in sessione che includono questo
        // campo nel loro snapshot: la proposta manterrebbe il campo nel suo stato interno
        // creando un'incoerenza con i campi comuni correnti del sistema.
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.getCampiSnapshot().containsKey(nomeCampo));
        if (inSessione) {
            return "Impossibile rimuovere il campo comune '" + nomeCampo
                    + "': esistono proposte in sessione che lo contengono. "
                    + "Eliminare prima le proposte dalla sessione.";
        }
        if (!appData.rimuoviCampoComune(nomeCampo)) {
            return "Nessun campo comune trovato con nome: " + nomeCampo;
        }
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Campo rimosso ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (!appData.modificaObbligatorietaCampoComune(nomeCampo, obbligatorio)) {
            return "Nessun campo comune trovato con nome: " + nomeCampo;
        }
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Modifica effettuata ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public List<Campo> getCampiComuni() {
        return appData.getCampiComuni();
    }

    // ================================================================
    // CATEGORIE  (invariata da V1/V2)
    // ================================================================
    public String aggiungiCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            return "Il nome non puo' essere vuoto.";
        }
        try {
            appData.aggiungiCategoria(new Categoria(nomeCategoria.trim()));
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Categoria aggiunta ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String rimuoviCategoria(String nomeCategoria) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            return "Il nome non puo' essere vuoto.";
        }
        // Impedisce la rimozione se ci sono proposte in sessione per questa categoria:
        // tali proposte rimarrebbero orfane (categoria assente) e non sarebbero piu'
        // pubblicabili ne' correggibili in modo sensato.
        boolean inSessione = proposteSessione.stream()
                .anyMatch(p -> p.getNomeCategoria().equalsIgnoreCase(nomeCategoria));
        if (inSessione) {
            return "Impossibile rimuovere la categoria '" + nomeCategoria
                    + "': esistono proposte in sessione ad essa associate. "
                    + "Eliminare prima le proposte dalla sessione.";
        }
        if (!appData.rimuoviCategoria(nomeCategoria)) {
            return "Nessuna categoria trovata con nome: " + nomeCategoria;
        }
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Categoria rimossa ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String aggiungiCampoSpecifico(String nomeCategoria, String nomeCampo, boolean obbligatorio) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            return "Nome categoria vuoto.";
        }
        if (nomeCampo == null || nomeCampo.isBlank()) {
            return "Nome campo vuoto.";
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            return "Categoria non trovata: " + nomeCategoria;
        }
        if (appData.esisteCampoBase(nomeCampo)) {
            return "Esiste gia' un campo base con nome: " + nomeCampo;
        }
        if (appData.esisteCampoComune(nomeCampo)) {
            return "Esiste gia' un campo comune con nome: " + nomeCampo;
        }
        try {
            cat.aggiungiCampoSpecifico(new Campo(nomeCampo.trim(), obbligatorio, Campo.TipoCampo.SPECIFICO));
            salva();
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "Campo aggiunto ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            return "Categoria non trovata: " + nomeCategoria;
        }
        if (!cat.rimuoviCampoSpecifico(nomeCampo)) {
            return "Nessun campo specifico trovato con nome: " + nomeCampo;
        }
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Campo rimosso ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public String modificaObbligatorietaCampoSpecifico(String nomeCategoria, String nomeCampo,
            boolean obbligatorio) {
        if (!isLoggato()) {
            return "Accesso negato.";
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        if (cat == null) {
            return "Categoria non trovata: " + nomeCategoria;
        }
        if (!cat.modificaObbligatorietaCampoSpecifico(nomeCampo, obbligatorio)) {
            return "Nessun campo specifico trovato con nome: " + nomeCampo;
        }
        try {
            salva();
            return "";
        } catch (IOException e) {
            return "Modifica effettuata ma errore nel salvataggio: " + e.getMessage();
        }
    }

    public List<Categoria> getCategorie() {
        return appData.getCategorie();
    }

    public Categoria getCategoria(String nome) {
        return appData.getCategoria(nome);
    }

    // ================================================================
    // PROPOSTE – GESTIONE DI SESSIONE  (invariata da V2)
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

    public String setValoreCampo(Proposta proposta, String nomeCampo, String valore) {
        if (proposta == null) {
            return "Proposta non valida.";
        }
        try {
            proposta.setValore(nomeCampo, valore);
            proposta.aggiornaStato(LocalDate.now());
            return "";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return e.getMessage();
        }
    }

    public String pubblicaProposta(Proposta proposta) {
        if (proposta == null) {
            return "Proposta non valida.";
        }
        if (!proposteSessione.contains(proposta)) {
            return "La proposta non appartiene alla sessione corrente.";
        }

        // Cattura la data UNA SOLA VOLTA: aggiornaStato(), validazioneErrori() e
        // pubblicaInBacheca() devono ragionare sulla stessa data. Se la chiamata
        // attraversasse mezzanotte e ognuno chiamasse LocalDate.now() per conto suo,
        // aggiornaStato() potrebbe valutare il "Termine ultimo di iscrizione"
        // rispetto a ieri mentre pubblicaInBacheca() userebbe oggi, producendo
        // una dataPubblicazione incoerente con lo stato di validazione.
        LocalDate oggi = LocalDate.now();

        proposta.aggiornaStato(oggi);
        if (proposta.getStato() != StatoProposta.VALIDA) {
            List<String> errori = proposta.validazioneErrori(oggi);
            return "Proposta non valida. Problemi riscontrati:\n"
                    + String.join("\n", errori.stream()
                            .map(e -> "    * " + e)
                            .toArray(String[]::new));
        }

        proposta.pubblicaInBacheca(oggi);
        appData.aggiungiPropostaAperta(proposta);
        proposteSessione.remove(proposta);

        try {
            salva();
            return "";
        } catch (IOException e) {
            // Rollback completo: rimuove dall'archivio, ripristina lo stato VALIDA
            // e reinserisce in sessione, cosi' il configuratore puo' riprovare.
            appData.rimuoviPropostaDaArchivio(proposta.getId());
            proposta.revertToValida();
            proposteSessione.add(proposta);
            return "Errore nel salvataggio; la proposta non e' stata pubblicata: " + e.getMessage();
        }
    }

    public boolean eliminaPropostaSessione(Proposta proposta) {
        return proposteSessione.remove(proposta);
    }

    public List<Proposta> getProposteSessione() {
        return Collections.unmodifiableList(proposteSessione);
    }

    // ================================================================
    // BACHECA E ARCHIVIO  (V3: archivio aggiunge tutti gli stati)
    // ================================================================
    /**
     * @return proposte APERTE (bacheca visibile ai fruitori)
     */
    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    /**
     * @return proposte APERTE filtrate per categoria
     */
    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        return appData.getBachecaPerCategoria(nomeCategoria);
    }

    /**
     * Restituisce l'archivio completo delle proposte pubblicate: tutte quelle
     * che hanno lasciato la bacheca, in qualsiasi stato finale (CONFERMATA,
     * ANNULLATA, CONCLUSA) piu' quelle ancora APERTE. Solo i configuratori
     * possono accedere all'archivio; i fruitori vedono solo la bacheca
     * (proposte APERTE).
     *
     * @return lista non modificabile di tutte le proposte pubblicate
     */
    public List<Proposta> getArchivio() {
        return appData.getArchivio();
    }

    // ================================================================
    // TRANSIZIONI AUTOMATICHE  (V3) 
    // ================================================================
    /**
     * Aggiorna automaticamente gli stati delle proposte in archivio in base
     * alla data odierna. Da chiamare all'avvio di ogni sessione.
     *
     * Se il salvataggio fallisce, viene eseguito un rollback ricaricando i dati
     * dal file su disco (che non e' stato modificato), in modo da ripristinare
     * la coerenza tra memoria e persistenza. Senza rollback, lo stato in
     * memoria sarebbe gia' aggiornato (proposte transitate, notifiche
     * depositate) ma il file sarebbe rimasto alla versione precedente: al
     * prossimo avvio le transizioni verrebbero ri-eseguite producendo notifiche
     * duplicate.
     *
     * @return numero di proposte il cui stato e' cambiato, oppure 0 se il
     * salvataggio e' fallito e il rollback e' stato eseguito
     */
    public int aggiornaTransizioni() {
        int n = appData.aggiornaTransizioni(LocalDate.now());
        if (n > 0) {
            try {
                salva();
            } catch (IOException e) {
                System.err.println("[Sistema] Errore salvataggio dopo transizioni: " + e.getMessage());
                // Rollback: ricarica lo stato dal file su disco (rimasto invariato),
                // ripristinando proposte e notifiche allo stato pre-transizione.
                // Senza questo, l'utente vedrebbe notifiche "fantasma" che sparirebbero
                // al prossimo riavvio, e le transizioni verrebbero ri-eseguite
                // generando notifiche duplicate con ID diversi.
                try {
                    persistenceManager.carica(appData);
                    System.err.println("[Sistema] Rollback transizioni completato: "
                            + "stato ripristinato dal file su disco.");
                } catch (IOException rollbackEx) {
                    System.err.println("[Sistema] Rollback fallito: impossibile ricaricare i dati. "
                            + rollbackEx.getMessage()
                            + " — lo stato in memoria potrebbe essere incoerente con il disco.");
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
}
