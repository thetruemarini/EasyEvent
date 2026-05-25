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
import java.util.List;

/**
 * Controller per tutte le operazioni del fruitore (Versione 5). Identico alla
 * V4; la V5 non aggiunge funzionalità lato fruitore.
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
            throw new RuntimeException("Errore nel salvataggio.", e);
        }
    }

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
            throw new RuntimeException("Errore nel salvataggio.", e);
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
        java.util.Collections.reverse(lista);
        return lista;
    }

    public String cancellaNotifica(IdNotifica idNotifica) {
        if (!isLoggato()) {
            return "Nessun fruitore loggato.";
        }
        Notifica daRimuovere = fruitoreCorrente.getNotifiche().stream()
                .filter(n -> n.getId().equals(idNotifica)).findFirst().orElse(null);
        if (daRimuovere == null) {
            return "Notifica non trovata con ID: " + idNotifica;
        }
        fruitoreCorrente.rimuoviNotifica(idNotifica);
        try {
            salva();
            return "";
        } catch (PersistenzaException e) {
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
        } catch (PersistenzaException e) {
            fruitoreCorrente.ripristinaNotifiche(copia);
            return "Errore nel salvataggio; le notifiche non sono state cancellate: " + e.getMessage();
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
