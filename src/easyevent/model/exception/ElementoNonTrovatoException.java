package easyevent.model.exception;

/**
 * Lanciata quando si cerca un elemento che non esiste nel sistema.
 */
public class ElementoNonTrovatoException extends RuntimeException {

    public enum TipoElemento {
        CAMPO_COMUNE,
        CAMPO_SPECIFICO,
        CATEGORIA,
        PROPOSTA
    }

    private final TipoElemento tipoElemento;
    private final String nomeElemento;

    public ElementoNonTrovatoException(TipoElemento tipoElemento, String nomeElemento) {
        super("Not found " + tipoElemento + ": '" + nomeElemento + "'");
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
