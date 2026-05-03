package easyevent.utente;

import easyevent.notifica.Notifica;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rappresenta un utente fruitore del sistema.
 *
 * Invariante di classe:
 *   - username != null && !username.isBlank()
 *   - password != null && !password.isBlank()
 *   - notifiche != null
 */
public class Fruitore {

    private String username;
    private String password;
    private List<Notifica> notifiche;

    public Fruitore(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        }
        this.username = username.trim();
        this.password = password;
        this.notifiche = new ArrayList<>();
        assert repOk() : "Invariante violato dopo costruzione Fruitore";
    }

    public Fruitore(String username, String password, List<Notifica> notifiche) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        }
        this.username = username.trim();
        this.password = password;
        this.notifiche = notifiche != null ? new ArrayList<>(notifiche) : new ArrayList<>();
    }

    public boolean verificaCredenziali(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return this.username.equalsIgnoreCase(username.trim()) && this.password.equals(password);
    }

    public void aggiungiNotifica(Notifica notifica) {
        if (notifica == null) {
            throw new IllegalArgumentException("La notifica non puo' essere null.");
        }
        notifiche.add(notifica);
        assert repOk() : "Invariante violato dopo aggiungiNotifica";
    }

    public boolean rimuoviNotifica(int id) {
        boolean rimossa = notifiche.removeIf(n -> n.getId() == id);
        assert repOk() : "Invariante violato dopo rimuoviNotifica";
        return rimossa;
    }

    public void ripristinaNotifiche(List<Notifica> notificheDaRipristinare) {
        if (notificheDaRipristinare == null) {
            throw new IllegalArgumentException("La lista di notifiche non puo' essere null.");
        }
        this.notifiche.clear();
        this.notifiche.addAll(notificheDaRipristinare);
        assert repOk() : "Invariante violato dopo ripristinaNotifiche";
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<Notifica> getNotifiche() {
        return Collections.unmodifiableList(notifiche);
    }

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
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Fruitore)) {
            return false;
        }
        return this.username.equalsIgnoreCase(((Fruitore) obj).username);
    }

    @Override
    public int hashCode() {
        return username.toLowerCase().hashCode();
    }
}
