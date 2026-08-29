package easyevent.exception;

/**
 * Lanciata quando le credenziali fornite dall'utente non sono accettabili.
 *
 * Porta il FATTO strutturato (il motivo del rifiuto). Il testo mostrato
 * all'utente lo costruisce la View a partire dal motivo.
 */
public class CredenzialiNonValideException extends RuntimeException {

    public enum Motivo {
        USERNAME_VUOTO,
        PASSWORD_VUOTA
    }

    private final Motivo motivo;

    public CredenzialiNonValideException(Motivo motivo) {
        // Messaggio per il developer/log, non per l'utente
        super("Invalid credentials: " + motivo);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
