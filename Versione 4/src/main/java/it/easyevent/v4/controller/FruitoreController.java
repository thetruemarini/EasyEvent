package it.easyevent.v4.controller;

import it.easyevent.v4.model.AppData;
import it.easyevent.v4.model.Fruitore;
import it.easyevent.v4.model.Notifica;
import it.easyevent.v4.model.Proposta;
import it.easyevent.v4.persistence.PersistenceManager;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Controller per tutte le operazioni del fruitore (Versione 4).
 *
 * Estende la V3 aggiungendo:
 *   - disdiciIscrizione: il fruitore disdice la propria iscrizione a una proposta
 *     aperta, con possibilita' di re-iscrizione.
 *
 * Invariante di classe:
 *   - appData != null
 *   - persistenceManager != null
 *   - fruitoreCorrente puo' essere null (nessun fruitore loggato)
 */
public class FruitoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Fruitore fruitoreCorrente;

    public FruitoreController(AppData appData, PersistenceManager persistenceManager) {
        if (appData == null) {
            throw new IllegalArgumentException("AppData non puo' essere null.");
        }
        if (persistenceManager == null) {
            throw new IllegalArgumentException("PersistenceManager non puo' essere null.");
        }
        this.appData = appData;
        this.persistenceManager = persistenceManager;
        this.fruitoreCorrente = null;
    }

    // ================================================================
    // AUTENTICAZIONE E REGISTRAZIONE
    // ================================================================
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        Fruitore trovato = appData.getFruitore(username);
        if (trovato != null && trovato.verificaCredenziali(username, password)) {
            fruitoreCorrente = trovato;
            return true;
        }
        return false;
    }

    public String registra(String username, String password) {
        if (username == null || username.isBlank()) {
            return "Lo username non puo' essere vuoto.";
        }
        if (password == null || password.isBlank()) {
            return "La password non puo' essere vuota.";
        }
        if (appData.esisteUsernameGlobale(username)) {
            return "Username gia' in uso: " + username + ". Scegliere uno username diverso.";
        }
        try {
            Fruitore f = new Fruitore(username.trim(), password);
            appData.aggiungiFruitore(f);
            salva();
            fruitoreCorrente = f;
            return "";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (IOException e) {
            appData.rimuoviFruitore(username.trim());
            return "Errore nel salvataggio; la registrazione non e' stata completata: " + e.getMessage();
        }
    }

    public boolean esisteFruitore(String username) {
        return appData.getFruitore(username) != null;
    }

    public void logout() {
        fruitoreCorrente = null;
    }

    public boolean isLoggato() {
        return fruitoreCorrente != null;
    }

    public Fruitore getFruitoreCorrente() {
        return fruitoreCorrente;
    }

    // ================================================================
    // BACHECA
    // ================================================================
    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    public List<Proposta> getBachecaPerCategoria(String nome) {
        return appData.getBachecaPerCategoria(nome);
    }

    public List<String> getCategorieConProposte() {
        return appData.getBacheca().stream()
                .map(Proposta::getNomeCategoria).distinct().sorted().collect(Collectors.toList());
    }

    public Proposta getPropostaAperta(int id) {
        return appData.getBacheca().stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    // ================================================================
    // ISCRIZIONI
    // ================================================================
    /**
     * Iscrive il fruitore corrente a una proposta aperta.
     *
     * @param idProposta ID della proposta
     * @return stringa vuota se successo, messaggio di errore altrimenti
     */
    public String aderisci(int idProposta) {
        if (!isLoggato()) {
            return "Nessun fruitore loggato.";
        }
        Proposta p = getPropostaAperta(idProposta);
        if (p == null) {
            return "Proposta non trovata o non piu' aperta (ID: " + idProposta + ").";
        }

        String username = fruitoreCorrente.getUsername();
        LocalDate oggi = LocalDate.now();
        String err = p.aggiungiAderente(username, oggi);
        if (!err.isEmpty()) {
            return err;
        }

        try {
            salva();
            return "";
        } catch (IOException e) {
            p.rimuoviAderente(username, oggi);
            return "Errore nel salvataggio; l'iscrizione non e' stata registrata: " + e.getMessage();
        }
    }

    /**
     * Il fruitore disdice la propria iscrizione a una proposta aperta.
     *
     * La disdetta e' consentita solo fino alle ore 23:59 del "Termine ultimo di
     * iscrizione". L'applicazione non gestisce l'ora: la verifica e' sul giorno
     * (oggi <= termine).
     *
     * Dopo la disdetta il fruitore PUO' re-iscriversi alla stessa proposta
     * (sempre nel rispetto del termine).
     *
     * Precondizioni: 
     * - Fruitore loggato. 
     * - La proposta deve essere APERTA. 
     * - Il fruitore deve essere iscritto alla proposta. 
     * - Il termine ultimo di iscrizione non deve essere scaduto.
     *
     * Postcondizione: il fruitore non risulta piu' nell'elenco degli aderenti.
     *
     * @param idProposta ID della proposta
     * @return stringa vuota se la disdetta e' avvenuta, messaggio di errore
     * altrimenti
     */
    public String disdiciIscrizione(int idProposta) {
        if (!isLoggato()) {
            return "Nessun fruitore loggato.";
        }

        Proposta p = getPropostaAperta(idProposta);
        if (p == null) {
            return "Proposta non trovata o non piu' aperta (ID: " + idProposta + ").";
        }

        String username = fruitoreCorrente.getUsername();
        LocalDate oggi = LocalDate.now();

        // rimuoviAderente esegue i controlli (stato APERTA, termine non scaduto, fruitore iscritto)
        String err = p.rimuoviAderente(username, oggi);
        if (!err.isEmpty()) {
            return err;
        }

        try {
            salva();
            return "";
        } catch (IOException e) {
            // Rollback: re-iscrive il fruitore per mantenere la coerenza memoria/disco
            p.aggiungiAderente(username, oggi);
            return "Errore nel salvataggio; la disdetta non e' stata registrata: " + e.getMessage();
        }
    }

    public boolean isIscritto(int idProposta) {
        if (!isLoggato()) {
            return false;
        }
        Proposta p = getPropostaAperta(idProposta);
        return p != null && p.isAderito(fruitoreCorrente.getUsername());
    }

    // ================================================================
    // SPAZIO PERSONALE (notifiche)
    // ================================================================
    public List<Notifica> getNotifiche() {
        if (!isLoggato()) {
            return new ArrayList<>();
        }
        List<Notifica> lista = new ArrayList<>(fruitoreCorrente.getNotifiche());
        java.util.Collections.reverse(lista);
        return lista;
    }

    public String cancellaNotifica(int idNotifica) {
        if (!isLoggato()) {
            return "Nessun fruitore loggato.";
        }
        Notifica daRimuovere = fruitoreCorrente.getNotifiche().stream()
                .filter(n -> n.getId() == idNotifica).findFirst().orElse(null);
        if (daRimuovere == null) {
            return "Notifica non trovata con ID: " + idNotifica;
        }
        fruitoreCorrente.rimuoviNotifica(idNotifica);
        try {
            salva();
            return "";
        } catch (IOException e) {
            fruitoreCorrente.aggiungiNotifica(daRimuovere);
            return "Errore nel salvataggio; la notifica non e' stata cancellata: " + e.getMessage();
        }
    }

    public String cancellaAllNotifiche() {
        if (!isLoggato()) {
            return "Nessun fruitore loggato.";
        }
        List<Notifica> copia = new ArrayList<>(fruitoreCorrente.getNotifiche());
        copia.forEach(n -> fruitoreCorrente.rimuoviNotifica(n.getId()));
        try {
            salva();
            return "";
        } catch (IOException e) {
            fruitoreCorrente.ripristinaNotifiche(copia);
            return "Errore nel salvataggio; le notifiche non sono state cancellate: " + e.getMessage();
        }
    }

    // ================================================================
    // UTILITA'
    // ================================================================
    private void salva() throws IOException {
        persistenceManager.salva(appData);
    }
}
