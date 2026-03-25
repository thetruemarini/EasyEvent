package it.easyevent.v1.model;

import it.easyevent.v1.model.Configuratore;

/**
 * Rappresenta un utente configuratore del sistema.
 * Il configuratore accede al back-end dell'applicazione.
 *
 * Invariante di classe:
 * - username != null && !username.isBlank()
 * - password != null && !password.isBlank()
 * - primoAccesso indica se il configuratore non ha ancora cambiato le credenziali predefinite
 */
public class Configuratore {

    private String username;
    private String password;
    private boolean primoAccesso;

    /**
     * Costruttore per un configuratore appena registrato (primo accesso).
     *
     * @param username username scelto, non null e non blank
     * @param password password iniziale (predefinita), non null e non blank
     * @throws IllegalArgumentException se username o password sono null o blank
     */
    public Configuratore(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Lo username non può essere null o vuoto.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password non può essere null o vuota.");
        }
        this.username = username.trim();
        this.password = password;
        this.primoAccesso = true;

        assert repOk() : "Invariante violato dopo costruzione Configuratore";
    }

    /**
     * Costruttore completo (usato per la deserializzazione).
     *
     * @param username    username
     * @param password    password
     * @param primoAccesso stato del primo accesso
     */
    public Configuratore(String username, String password, boolean primoAccesso) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Lo username non può essere null o vuoto.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La password non può essere null o vuota.");
        }
        this.username = username.trim();
        this.password = password;
        this.primoAccesso = primoAccesso;
    }

    /**
     * Aggiorna le credenziali personali del configuratore al primo accesso.
     * Dopo questa operazione, primoAccesso diventa false.
     *
     * @param nuovoUsername nuovo username, non null e non blank
     * @param nuovaPassword nuova password, non null e non blank
     * @throws IllegalArgumentException     se i parametri sono null o blank
     * @throws IllegalStateException        se il configuratore ha già completato il primo accesso
     */
    public void impostaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        // Precondizioni
        if (nuovoUsername == null || nuovoUsername.isBlank()) {
            throw new IllegalArgumentException("Il nuovo username non può essere null o vuoto.");
        }
        if (nuovaPassword == null || nuovaPassword.isBlank()) {
            throw new IllegalArgumentException("La nuova password non può essere null o vuota.");
        }

        this.username = nuovoUsername.trim();
        this.password = nuovaPassword;
        this.primoAccesso = false;

        // Postcondizione
        assert !this.primoAccesso : "Postcondizione violata: primoAccesso ancora true dopo impostazione credenziali";
        assert repOk() : "Invariante violato dopo impostaCredenzialiPersonali";
    }

    /**
     * Riporta le credenziali ai valori precedenti annullando un cambio fallito.
     * Da usare ESCLUSIVAMENTE come rollback quando
     * impostaCredenzialiPersonali() ha avuto successo ma il salvataggio su
     * disco ha fallito.
     *
     * @param vecchioUsername username precedente, non null e non blank
     * @param vecchiaPassword password precedente, non null e non blank
     */
    public void revertCredenziali(String vecchioUsername, String vecchiaPassword) {
        if (vecchioUsername == null || vecchioUsername.isBlank()) {
            throw new IllegalArgumentException("vecchioUsername non puo' essere null o vuoto.");
        }
        if (vecchiaPassword == null || vecchiaPassword.isBlank()) {
            throw new IllegalArgumentException("vecchiaPassword non puo' essere null o vuota.");
        }
        this.username = vecchioUsername;
        this.password = vecchiaPassword;
        this.primoAccesso = true;

        assert repOk() : "Invariante violato dopo revertCredenziali";
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

    // ---- Getters ----

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isPrimoAccesso() {
        return primoAccesso;
    }

    /**
     * Verifica l'invariante di classe.
     */
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
    public int hashCode() {
        return username.toLowerCase().hashCode();
    }
}
