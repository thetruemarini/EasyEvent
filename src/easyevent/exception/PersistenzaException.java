package easyevent.exception;

/**
 * Eccezione di dominio che rappresenta un errore di persistenza.
 *
 * Wrappa le eccezioni tecnologiche (IOException, JsonIOException, ecc.) in
 * un'eccezione che parla il linguaggio del dominio, non quello della tecnologia
 * di storage scelta.
 *
 * La View e il Controller conoscono solo PersistenzaException. Solo
 * PersistenceManager conosce IOException.
 */
public class PersistenzaException extends RuntimeException {

    public enum TipoErrore {
        ERRORE_LETTURA,
        ERRORE_SCRITTURA,
        FILE_NON_TROVATO
    }

    private final TipoErrore tipoErrore;

    public PersistenzaException(TipoErrore tipoErrore, String messaggio, Throwable causa) {
        super(messaggio, causa);
        this.tipoErrore = tipoErrore;
    }

    public PersistenzaException(TipoErrore tipoErrore, String messaggio) {
        super(messaggio);
        this.tipoErrore = tipoErrore;
    }

    public TipoErrore getTipoErrore() {
        return tipoErrore;
    }
}
