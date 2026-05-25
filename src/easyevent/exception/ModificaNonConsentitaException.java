package easyevent.exception;

/**
 * Lanciata quando si tenta un'operazione strutturalmente non consentita sullo
 * stato attuale di un oggetto di dominio.
 */
public class ModificaNonConsentitaException extends RuntimeException {

    public enum TipoModifica {
        CAMPO_BASE_IMMUTABILE, // setObbligatorio su un campo BASE
        PROPOSTA_GIA_PUBBLICATA, // setValore su proposta APERTA/CONFERMATA/...
        CAMPO_NON_PRESENTE, // setValore con nome campo non in snapshot
        ACCESSO_NEGATO,
        NESSUN_CONFIGURATORE_LOGGATO,
        NESSUN_FRUITORE_LOGGATO,
        CREDENZIALI_GIA_IMPOSTATE,
        PROPOSTA_NON_IN_SESSIONE,
        STATO_PROPOSTA_NON_VALIDO,
        TRANSIZIONE_STATO_NON_VALIDA
    }

    private final TipoModifica tipoModifica;
    private final String dettaglio; // es. il nome del campo coinvolto

    public ModificaNonConsentitaException(TipoModifica tipoModifica, String dettaglio) {
        super(tipoModifica.name());
        this.tipoModifica = tipoModifica;
        this.dettaglio = dettaglio;
    }

    public TipoModifica getTipoModifica() {
        return tipoModifica;
    }

    public String getDettaglio() {
        return dettaglio;
    }
}
