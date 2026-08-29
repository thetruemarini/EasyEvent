package easyevent.controller;

import easyevent.categoria.Campo;
import easyevent.core.AppData;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.PersistenzaException;
import easyevent.model.Fruitore;
import easyevent.notifica.IdNotifica;
import easyevent.notifica.Notifica;
import easyevent.persistence.PersistenceManager;
import easyevent.proposta.IdProposta;
import easyevent.proposta.Proposta;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controller per tutte le operazioni del fruitore: login, registrazione,
 * adesione/ritiro alle proposte e gestione delle notifiche. Orchestra il Model e
 * la persistenza senza contenere logica di presentazione.
 *
 * Invariante di classe: - appData != null - persistenceManager != null -
 * fruitoreCorrente puo' essere null
 */
public class FruitoreController {

    private final AppData appData;
    private final PersistenceManager persistenceManager;
    private Fruitore fruitoreCorrente;

    /**
     * Le dipendenze sono iniettate tramite costruttore (Dependency Injection).
     * AppData e PersistenceManager sono condivisi con ConfiguratoreController:
     * entrambi operano sullo stesso stato centrale, passato esplicitamente
     * anziché recuperato tramite un registro globale o Singleton.
     */
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

    /**
     * Autentica un fruitore e, se le credenziali sono corrette, lo imposta come
     * fruitore corrente della sessione.
     *
     * @return true se il login ha avuto successo, false altrimenti.
     */
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

    /**
     * Registra un nuovo fruitore, lo persiste e lo imposta come corrente. Se il
     * salvataggio fallisce, annulla l'aggiunta (rollback) e propaga l'errore.
     *
     * @throws PersistenzaException se il salvataggio non riesce.
     */
    public void registra(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Lo username non può essere vuoto.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password non può essere vuota.");
        }
        Fruitore f = new Fruitore(username.trim(), password);
        appData.aggiungiFruitore(f);
        try {
            salva();
            fruitoreCorrente = f;
        } catch (PersistenzaException e) {
            appData.rimuoviFruitore(username.trim()); // rollback
            throw e;
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

    public List<Proposta> getBacheca() {
        return appData.getBacheca();
    }

    public List<Proposta> getBachecaPerCategoria(String nome) {
        return appData.getBachecaPerCategoria(nome);
    }

    public List<String> getCategorieConProposte() {
        return appData.getCategorieConProposteAperte();
    }

    public Proposta getPropostaAperta(IdProposta id) {
        return appData.getBacheca().stream()
                .filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Iscrive il fruitore corrente alla proposta indicata e persiste la
     * modifica. In caso di errore di salvataggio annulla l'iscrizione (rollback).
     *
     * @throws ModificaNonConsentitaException se nessun fruitore è loggato.
     * @throws ElementoNonTrovatoException se la proposta non è in bacheca.
     */
    public void aderisci(IdProposta idProposta) {
        if (!isLoggato()) {
            throw nessunFruitoreLoggato();
        }
        Proposta p = getPropostaAperta(idProposta);
        if (p == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.PROPOSTA,
                    String.valueOf(idProposta)
            );
        }
        String username = fruitoreCorrente.getUsername();
        LocalDate oggi = LocalDate.now();
        p.aggiungiAderente(username, oggi);
        try {
            salva();
        } catch (PersistenzaException e) {
            p.rimuoviAderente(username, oggi); // rollback
            throw e;
        }
    }

    /**
     * Disdice l'iscrizione del fruitore corrente alla proposta indicata e
     * persiste la modifica. In caso di errore di salvataggio ripristina
     * l'iscrizione (rollback).
     *
     * @throws ModificaNonConsentitaException se nessun fruitore è loggato.
     * @throws ElementoNonTrovatoException se la proposta non è in bacheca.
     */
    public void disdiciIscrizione(IdProposta idProposta) {
        if (!isLoggato()) {
            throw nessunFruitoreLoggato();
        }
        Proposta p = getPropostaAperta(idProposta);
        if (p == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.PROPOSTA,
                    String.valueOf(idProposta)
            );
        }
        String username = fruitoreCorrente.getUsername();
        LocalDate oggi = LocalDate.now();
        p.rimuoviAderente(username, oggi);
        try {
            salva();
        } catch (PersistenzaException e) {
            p.aggiungiAderente(username, oggi); // rollback
            throw e;
        }
    }

    public boolean isIscritto(IdProposta idProposta) {
        if (!isLoggato()) {
            return false;
        }
        Proposta p = getPropostaAperta(idProposta);
        return p != null && p.isAderito(fruitoreCorrente.getUsername());
    }

    /**
     * Restituisce il numero di proposte aperte a cui il fruitore è iscritto. La
     * logica "quali iscrizioni conta" sta nel Model, non nella View.
     */
    public int getNumeroIscrizioniAttive() {
        if (!isLoggato()) {
            return 0;
        }
        return appData.getProposteIscrittoFruitore(fruitoreCorrente.getUsername()).size();
    }

    public List<Notifica> getNotifiche() {
        if (!isLoggato()) {
            return new ArrayList<>();
        }
        List<Notifica> lista = new ArrayList<>(fruitoreCorrente.getNotifiche());
        Collections.reverse(lista);
        return lista;
    }

    /**
     * Cancella una singola notifica del fruitore corrente e persiste la
     * modifica. In caso di errore di salvataggio ripristina la notifica
     * (rollback).
     *
     * @throws ModificaNonConsentitaException se nessun fruitore è loggato.
     * @throws ElementoNonTrovatoException se la notifica non esiste.
     */
    public void cancellaNotifica(IdNotifica idNotifica) {
        if (!isLoggato()) {
            throw nessunFruitoreLoggato();
        }
        Notifica daRimuovere = fruitoreCorrente.getNotifiche().stream()
                .filter(n -> n.getId().equals(idNotifica)).findFirst().orElse(null);
        if (daRimuovere == null) {
            throw new ElementoNonTrovatoException(
                    ElementoNonTrovatoException.TipoElemento.NOTIFICA,
                    String.valueOf(idNotifica));
        }
        fruitoreCorrente.rimuoviNotifica(idNotifica);
        try {
            salva();
        } catch (PersistenzaException e) {
            fruitoreCorrente.aggiungiNotifica(daRimuovere); // rollback
            throw e;
        }
    }

    /**
     * Cancella tutte le notifiche del fruitore corrente e persiste la modifica.
     * In caso di errore di salvataggio ripristina l'intero elenco (rollback).
     *
     * @throws ModificaNonConsentitaException se nessun fruitore è loggato.
     */
    public void cancellaAllNotifiche() {
        if (!isLoggato()) {
            throw nessunFruitoreLoggato();
        }
        List<Notifica> copia = new ArrayList<>(fruitoreCorrente.getNotifiche());
        copia.forEach(n -> fruitoreCorrente.rimuoviNotifica(n.getId()));
        try {
            salva();
        } catch (PersistenzaException e) {
            fruitoreCorrente.ripristinaNotifiche(copia); // rollback
            throw e;
        }
    }

    private void salva() {
        persistenceManager.salvaSicuro(appData);
    }

    private ModificaNonConsentitaException nessunFruitoreLoggato() {
        return new ModificaNonConsentitaException(
                ModificaNonConsentitaException.TipoModifica.NESSUN_FRUITORE_LOGGATO,
                null);
    }

    // ================================================================
    // METODI PRESENTAZIONALI PER LA VIEW (Refactoring Problema 6)
    // ================================================================
    public boolean isCampoInEvidenza(String nomeCategoria, String nomeCampo) {
        Campo c = appData.getCampo(nomeCategoria, nomeCampo);
        return c != null && c.isInEvidenza();
    }
}
