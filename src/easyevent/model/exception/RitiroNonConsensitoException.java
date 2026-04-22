package easyevent.model.exception;

/**
 * Lanciata quando il ritiro di una proposta non è consentito.
 */
public class RitiroNonConsensitoException extends RuntimeException {

    public enum TipoErrore {
        STATO_NON_RITIRABILE, // stato non è APERTA né CONFERMATA
        DATA_EVENTO_PASSATA, // la data dell'evento è già oggi o passata
        DATA_EVENTO_NON_VALORIZZATA // campo Data inizio non compilato
    }

    private final TipoErrore tipoErrore;
    private final String statoAttuale; // usato solo per STATO_NON_RITIRABILE

    public RitiroNonConsensitoException(TipoErrore tipoErrore, String statoAttuale) {
        super("Ritiro non consentito [" + tipoErrore + "]");
        this.tipoErrore = tipoErrore;
        this.statoAttuale = statoAttuale != null ? statoAttuale : "";
    }

    public TipoErrore getTipoErrore() {
        return tipoErrore;
    }

    public String getStatoAttuale() {
        return statoAttuale;
    }
}
