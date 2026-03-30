package it.easyevent.v5.model;

/**
 * Rappresenta un utente configuratore del sistema.
 *
 * Invariante di classe:
 * - username != null && !username.isBlank()
 * - password != null && !password.isBlank()
 */
public class Configuratore {

    private String  username;
    private String  password;
    private boolean primoAccesso;

    public Configuratore(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        this.username     = username.trim();
        this.password     = password;
        this.primoAccesso = true;
        assert repOk() : "Invariante violato dopo costruzione Configuratore";
    }

    public Configuratore(String username, String password, boolean primoAccesso) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Lo username non puo' essere null o vuoto.");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("La password non puo' essere null o vuota.");
        this.username     = username.trim();
        this.password     = password;
        this.primoAccesso = primoAccesso;
    }

    public void impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (nuovoUsername == null || nuovoUsername.isBlank())
            throw new IllegalArgumentException("Il nuovo username non puo' essere null o vuoto.");
        if (nuovaPassword == null || nuovaPassword.isBlank())
            throw new IllegalArgumentException("La nuova password non puo' essere null o vuota.");
        this.username     = nuovoUsername.trim();
        this.password     = nuovaPassword;
        this.primoAccesso = false;
        assert repOk() : "Invariante violato dopo impostaCredenzialiPersonali";
    }

    public void revertCredenziali(String vecchioUsername, String vecchiaPassword) {
        if (vecchioUsername == null || vecchioUsername.isBlank())
            throw new IllegalArgumentException("vecchioUsername non puo' essere null o vuoto.");
        if (vecchiaPassword == null || vecchiaPassword.isBlank())
            throw new IllegalArgumentException("vecchiaPassword non puo' essere null o vuota.");
        this.username     = vecchioUsername;
        this.password     = vecchiaPassword;
        this.primoAccesso = true;
        assert repOk() : "Invariante violato dopo revertCredenziali";
    }

    public boolean verificaCredenziali(String username, String password) {
        if (username == null || password == null) return false;
        return this.username.equalsIgnoreCase(username.trim()) && this.password.equals(password);
    }

    public String  getUsername()     { return username; }
    public String  getPassword()     { return password; }
    public boolean isPrimoAccesso()  { return primoAccesso; }

    public boolean repOk() {
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "Configuratore{username='" + username + "', primoAccesso=" + primoAccesso + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Configuratore)) return false;
        return this.username.equalsIgnoreCase(((Configuratore) obj).username);
    }

    @Override
    public int hashCode() { return username.toLowerCase().hashCode(); }
}