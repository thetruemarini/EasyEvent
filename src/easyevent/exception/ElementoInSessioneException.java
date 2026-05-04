package easyevent.exception;

/**
 * Lanciata quando si tenta di rimuovere un elemento (campo o categoria) ancora
 * utilizzato da proposte in sessione corrente.
 *
 * Porta dati strutturati: tipo e nome dell'elemento coinvolto. La View è
 * responsabile di costruire il messaggio italiano.
 */
public class ElementoInSessioneException extends RuntimeException {

    public enum TipoElemento {
        CAMPO_COMUNE,
        CATEGORIA
    }

    private final TipoElemento tipoElemento;
    private final String nomeElemento;

    public ElementoInSessioneException(TipoElemento tipoElemento, String nomeElemento) {
        super("Elemento in sessione [" + tipoElemento + "]: '" + nomeElemento + "'");
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
