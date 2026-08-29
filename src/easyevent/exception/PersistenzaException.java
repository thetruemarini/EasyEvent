package easyevent.exception;

/**
 * Eccezione di dominio che rappresenta un errore di persistenza.
 *
 * Wrappa le eccezioni tecnologiche (IOException, JsonIOException, ecc.) in
 * un'eccezione che parla il linguaggio del dominio, non quello della tecnologia
 * di storage scelta.
 *
 * Porta il FATTO strutturato (tipo di errore + file coinvolto). Il messaggio di
 * Throwable e' per il log; il testo mostrato all'utente lo costruisce la View.
 *
 * La View e il Controller conoscono solo PersistenzaException. Solo
 * PersistenceManager conosce IOException.
 */
public class PersistenzaException extends RuntimeException {

    public enum TipoErrore {
        ERRORE_LETTURA,
        ERRORE_SCRITTURA,
        FILE_NON_TROVATO,
        NON_E_UN_FILE
    }

    private final TipoErrore tipoErrore;
    private final String percorso;   // file coinvolto, puo' essere null

    public PersistenzaException(TipoErrore tipoErrore, String percorso) {
        this(tipoErrore, percorso, null);
    }

    public PersistenzaException(TipoErrore tipoErrore, String percorso, Throwable causa) {
        // Messaggio per il developer/log, non per l'utente
        super("Persistence failure: " + tipoErrore
                + (percorso != null ? " on '" + percorso + "'" : ""), causa);
        this.tipoErrore = tipoErrore;
        this.percorso = percorso;
    }

    public TipoErrore getTipoErrore() {
        return tipoErrore;
    }

    /**
     * @return il percorso del file coinvolto, o null se l'errore non ne
     * riguarda uno in particolare.
     */
    public String getPercorso() {
        return percorso;
    }
}
