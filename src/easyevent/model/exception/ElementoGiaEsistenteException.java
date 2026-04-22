package easyevent.model.exception;

/**
 * Lanciata quando si tenta di aggiungere un elemento con un nome già presente
 * nel sistema.
 *
 * Porta il FATTO strutturato (tipo + nome dell'elemento che confligge).
 */
public class ElementoGiaEsistenteException extends RuntimeException {

    public enum TipoElemento {
        CAMPO_BASE,
        CAMPO_COMUNE,
        CAMPO_SPECIFICO,
        CATEGORIA,
        USERNAME
    }

    private final TipoElemento tipoElemento;
    private final String nomeElemento;

    public ElementoGiaEsistenteException(TipoElemento tipoElemento, String nomeElemento) {
        // Messaggio per il developer/log, non per l'utente
        super("Duplicate " + tipoElemento + ": '" + nomeElemento + "'");
        this.tipoElemento = tipoElemento;
        this.nomeElemento = nomeElemento;
    }

    public TipoElemento getTipoElemento() {
        return tipoElemento;
    }

    public String getNomeElemento() {
        return nomeElemento;
    }
}
