package it.easyevent.controller;

import it.easyevent.model.AppData;
import it.easyevent.model.Fruitore;
import it.easyevent.model.Notifica;
import it.easyevent.model.Proposta;
import it.easyevent.persistence.PersistenceManager;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller per tutte le operazioni del fruitore (Versione 3).
 *
 * Il fruitore puo':
 *   - registrarsi (primo accesso: sceglie username e password)
 *   - effettuare il login con le proprie credenziali
 *   - visualizzare la bacheca (proposte APERTE) per categoria
 *   - aderire a una proposta aperta
 *   - accedere allo spazio personale (notifiche)
 *   - cancellare selettivamente le notifiche
 *
 * Le iscrizioni sono possibili solo fino alle ore 23.59 del "Termine ultimo
 * di iscrizione"; questa applicazione non gestisce l'ora, quindi la verifica
 * e' effettuata sul giorno: ci si puo' iscrivere se oggi <= termine.
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
        if (appData == null)
            throw new IllegalArgumentException("AppData non puo' essere null.");
        if (persistenceManager == null)
            throw new IllegalArgumentException("PersistenceManager non puo' essere null.");
        this.appData            = appData;
        this.persistenceManager = persistenceManager;
        this.fruitoreCorrente   = null;
    }

    // ================================================================
    // AUTENTICAZIONE E REGISTRAZIONE
    // ================================================================

    /**
     * Effettua il login di un fruitore esistente.
     *
     * @param username username del fruitore
     * @param password password del fruitore
     * @return true se le credenziali sono corrette
     */
    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        Fruitore trovato = appData.getFruitore(username);
        if (trovato != null && trovato.verificaCredenziali(username, password)) {
            fruitoreCorrente = trovato;
            return true;
        }
        return false;
    }

    /**
     * Registra un nuovo fruitore.
     * Lo username deve essere univoco globalmente (tra configuratori e fruitori).
     *
     * @param username  username scelto
     * @param password  password scelta
     * @return stringa vuota se successo, messaggio di errore altrimenti
     */
    public String registra(String username, String password) {
        if (username == null || username.isBlank()) return "Lo username non puo' essere vuoto.";
        if (password == null || password.isBlank()) return "La password non puo' essere vuota.";
        if (appData.esisteUsernameGlobale(username))
            return "Username gia' in uso: " + username
                 + ". Scegliere uno username diverso.";
        try {
            Fruitore f = new Fruitore(username.trim(), password);
            appData.aggiungiFruitore(f);
            salva();
            // fruitoreCorrente viene impostato solo DOPO il salvataggio riuscito.
            // Se salva() lancia IOException, viene eseguito il rollback e
            // fruitoreCorrente resta null, evitando uno stato incoerente.
            fruitoreCorrente = f;
            return "";
        } catch (IllegalArgumentException e) { return e.getMessage(); }
          catch (IOException e) {
            // Rollback: rimuove il fruitore appena aggiunto per ripristinare
            // la coerenza tra stato in memoria e stato su disco.
            appData.rimuoviFruitore(username.trim());
            return "Errore nel salvataggio; la registrazione non e' stata completata: " + e.getMessage();
        }
    }

    /** @return true se esiste un fruitore con tale username */
    public boolean esisteFruitore(String username) {
        return appData.getFruitore(username) != null;
    }

    public void logout() { fruitoreCorrente = null; }

    public boolean isLoggato() { return fruitoreCorrente != null; }
    public Fruitore getFruitoreCorrente() { return fruitoreCorrente; }

    // ================================================================
    // BACHECA (proposte APERTE, visibile ai fruitori)
    // ================================================================

    /**
     * @return lista di tutte le proposte in stato APERTA
     */
    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    /**
     * @param nomeCategoria nome della categoria
     * @return proposte APERTE di quella categoria
     */
    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        return appData.getBachecaPerCategoria(nomeCategoria);
    }

    /**
     * @return lista delle categorie che hanno almeno una proposta APERTA
     */
    public List<String> getCategorieConProposte() {
        return appData.getBacheca().stream()
                .map(Proposta::getNomeCategoria)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Cerca una proposta APERTA per ID.
     *
     * @param id ID della proposta
     * @return la proposta o null se non trovata o non APERTA
     */
    public Proposta getPropostaAperta(int id) {
        return appData.getBacheca().stream()
                .filter(p -> p.getId() == id)
                .findFirst().orElse(null);
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
        if (!isLoggato()) return "Nessun fruitore loggato.";
        Proposta p = getPropostaAperta(idProposta);
        if (p == null)
            return "Proposta non trovata o non piu' aperta (ID: " + idProposta + ").";

        String username = fruitoreCorrente.getUsername();
        // Cattura la data UNA SOLA VOLTA: se la chiamata attraversa la mezzanotte,
        // addizione e rollback userebbero date diverse e rimuoviAderente potrebbe
        // fallire silenziosamente (isIscrizioneAperta restituirebbe false il giorno dopo).
        LocalDate oggi = LocalDate.now();
        String err = p.aggiungiAderente(username, oggi);
        if (!err.isEmpty()) return err;

        try {
            salva();
            return "";
        } catch (IOException e) {
            // Rollback: rimuove l'iscrizione appena aggiunta per mantenere
            // la coerenza tra stato in memoria e stato su disco.
            p.rimuoviAderente(username, oggi);
            return "Errore nel salvataggio; l'iscrizione non e' stata registrata: " + e.getMessage();
        }
    }

    /**
     * @return true se il fruitore corrente e' gia' iscritto alla proposta con tale ID
     */
    public boolean isIscritto(int idProposta) {
        if (!isLoggato()) return false;
        Proposta p = getPropostaAperta(idProposta);
        return p != null && p.isAderito(fruitoreCorrente.getUsername());
    }

    // ================================================================
    // SPAZIO PERSONALE (notifiche)
    // ================================================================

    /**
     * @return lista delle notifiche del fruitore corrente (dal piu' recente)
     */
    public List<Notifica> getNotifiche() {
        if (!isLoggato()) return new ArrayList<>();
        List<Notifica> lista = new ArrayList<>(fruitoreCorrente.getNotifiche());
        // Ordine inverso: piu' recenti prima
        java.util.Collections.reverse(lista);
        return lista;
    }

    /**
     * Cancella la notifica con l'ID indicato dallo spazio personale.
     *
     * @param idNotifica ID della notifica
     * @return stringa vuota se successo, messaggio di errore altrimenti
     */
    public String cancellaNotifica(int idNotifica) {
        if (!isLoggato()) return "Nessun fruitore loggato.";
        // Recupera la notifica prima di rimuoverla, per poter fare rollback.
        Notifica daRimuovere = fruitoreCorrente.getNotifiche().stream()
                .filter(n -> n.getId() == idNotifica)
                .findFirst().orElse(null);
        if (daRimuovere == null)
            return "Notifica non trovata con ID: " + idNotifica;
        fruitoreCorrente.rimuoviNotifica(idNotifica);
        try { salva(); return ""; }
        catch (IOException e) {
            // Rollback: ripristina la notifica rimossa per mantenere
            // la coerenza tra stato in memoria e stato su disco.
            fruitoreCorrente.aggiungiNotifica(daRimuovere);
            return "Errore nel salvataggio; la notifica non e' stata cancellata: " + e.getMessage();
        }
    }

    /**
     * Cancella tutte le notifiche del fruitore corrente.
     *
     * @return stringa vuota se successo, messaggio di errore altrimenti
     */
    public String cancellaAllNotifiche() {
        if (!isLoggato()) return "Nessun fruitore loggato.";
        // Salva una copia ORDINATA per rollback prima di rimuovere dalla memoria.
        List<Notifica> copia = new ArrayList<>(fruitoreCorrente.getNotifiche());
        copia.forEach(n -> fruitoreCorrente.rimuoviNotifica(n.getId()));
        try { salva(); return ""; }
        catch (IOException e) {
            // Rollback: usa ripristinaNotifiche invece di aggiungiNotifica() in loop,
            // perche' append in coda altererebbe l'ordine originale delle notifiche
            // (visibile nella view che mostra le notifiche in ordine inverso).
            fruitoreCorrente.ripristinaNotifiche(copia);
            return "Errore nel salvataggio; le notifiche non sono state cancellate: " + e.getMessage();
        }
    }

    // ================================================================
    // UTILITA'
    // ================================================================

    private void salva() throws IOException { persistenceManager.salva(appData); }
}
