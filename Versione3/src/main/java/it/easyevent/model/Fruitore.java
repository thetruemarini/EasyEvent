package it.easyevent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rappresenta un utente fruitore del sistema (Versione 3).
 * Il fruitore accede al front-end dell'applicazione, visualizza
 * la bacheca, si iscrive alle proposte aperte e gestisce il
 * proprio spazio personale (notifiche).
 *
 * A differenza del Configuratore, il fruitore non ha credenziali
 * predefinite: sceglie username e password direttamente in fase
 * di registrazione (primo accesso = registrazione).
 *
 * Invariante di classe:
 *   - username != null && !username.isBlank()
 *   - password != null && !password.isBlank()
 *   - notifiche != null
 */
public class Fruitore {

    private String          username;
    private String          password;
    private List<Notifica>  notifiche;

    /**
     * Costruttore per la registrazione di un nuovo fruitore.
     *
     * @param username username scelto, non null e non blank
     * @param password password scelta, non null e non blank
     * @throws IllegalArgumentException se username o password sono null o blank
     */
    public Fruitore(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        this.username  = username.trim();
        this.password  = password;
        this.notifiche = new ArrayList<>();

        assert repOk() : "Invariante violato dopo costruzione Fruitore";
    }

    /**
     * Costruttore completo per la deserializzazione.
     *
     * @param username  username
     * @param password  password
     * @param notifiche lista notifiche preesistenti
     */
    public Fruitore(String username, String password, List<Notifica> notifiche) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        this.username  = username.trim();
        this.password  = password;
        this.notifiche = notifiche != null ? new ArrayList<>(notifiche) : new ArrayList<>();
    }

    /**
     * Verifica le credenziali fornite.
     *
     * @param username username da verificare
     * @param password password da verificare
     * @return true se le credenziali corrispondono
     */
    public boolean verificaCredenziali(String username, String password) {
        if (username == null || password == null) return false;
        return this.username.equalsIgnoreCase(username.trim()) && this.password.equals(password);
    }

    /**
     * Aggiunge una notifica allo spazio personale del fruitore.
     *
     * @param notifica notifica da aggiungere, non null
     * @throws IllegalArgumentException se notifica e' null
     */
    public void aggiungiNotifica(Notifica notifica) {
        if (notifica == null)
            throw new IllegalArgumentException("La notifica non puo' essere null.");
        notifiche.add(notifica);
        assert repOk() : "Invariante violato dopo aggiungiNotifica";
    }

    /**
     * Rimuove la notifica con l'ID indicato dallo spazio personale.
     *
     * @param id ID della notifica da rimuovere
     * @return true se la notifica e' stata trovata e rimossa
     */
    public boolean rimuoviNotifica(int id) {
        boolean rimossa = notifiche.removeIf(n -> n.getId() == id);
        assert repOk() : "Invariante violato dopo rimuoviNotifica";
        return rimossa;
    }

    /**
     * Sostituisce l'intera lista di notifiche con quella fornita, preservandone
     * l'ordine. Usato esclusivamente come rollback da cancellaAllNotifiche()
     * nel controller quando il salvataggio su disco fallisce: appendere le
     * notifiche una ad una con aggiungiNotifica() le inserirebbe in coda invece
     * che nella posizione originale, alterando l'ordine di visualizzazione.
     *
     * @param notificheDaRipristinare lista ordinata da ripristinare, non null
     * @throws IllegalArgumentException se la lista e' null
     */
    public void ripristinaNotifiche(List<Notifica> notificheDaRipristinare) {
        if (notificheDaRipristinare == null)
            throw new IllegalArgumentException("La lista di notifiche non puo' essere null.");
        this.notifiche.clear();
        this.notifiche.addAll(notificheDaRipristinare);
        assert repOk() : "Invariante violato dopo ripristinaNotifiche";
    }

    // ---- Getters ----

    public String         getUsername()  { return username; }
    public String         getPassword()  { return password; }
    public List<Notifica> getNotifiche() { return Collections.unmodifiableList(notifiche); }

    /**
     * Verifica l'invariante di classe.
     */
    public boolean repOk() {
        return username != null && !username.isBlank()
            && password != null && !password.isBlank()
            && notifiche != null;
    }

    @Override
    public String toString() {
        return "Fruitore{username='" + username + "', notifiche=" + notifiche.size() + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Fruitore)) return false;
        return this.username.equalsIgnoreCase(((Fruitore) obj).username);
    }

    @Override
    public int hashCode() {
        return username.toLowerCase().hashCode();
    }
}
