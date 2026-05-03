package easyevent.exception;

/**
 * Lanciata quando un'operazione di iscrizione o disdetta non può essere
 * completata per una regola di business.
 */
public class IscrizioneException extends RuntimeException {

    public enum TipoErrore {
        PROPOSTA_NON_APERTA,
        ISCRIZIONI_CHIUSE,
        GIA_ISCRITTO,
        NON_ISCRITTO,
        POSTI_ESAURITI,
        NUM_PARTECIPANTI_NON_VALIDO
    }

    private final TipoErrore tipoErrore;

    public IscrizioneException(TipoErrore tipoErrore) {
        super("Errore iscrizione: " + tipoErrore);
        this.tipoErrore = tipoErrore;
    }

    public TipoErrore getTipoErrore() {
        return tipoErrore;
    }
}
