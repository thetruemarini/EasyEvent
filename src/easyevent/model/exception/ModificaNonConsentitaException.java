package easyevent.model.exception;

/**
 * Lanciata quando si tenta un'operazione strutturalmente non consentita sullo
 * stato attuale di un oggetto di dominio.
 */
public class ModificaNonConsentitaException extends RuntimeException {

    public enum TipoModifica {
        CAMPO_BASE_IMMUTABILE, // setObbligatorio su un campo BASE
        PROPOSTA_GIA_PUBBLICATA, // setValore su proposta APERTA/CONFERMATA/...
        CAMPO_NON_PRESENTE           // setValore con nome campo non in snapshot
    }

    private final TipoModifica tipoModifica;
    private final String dettaglio; // es. il nome del campo coinvolto

    public ModificaNonConsentitaException(TipoModifica tipoModifica, String dettaglio) {
        super("Modifica non consentita [" + tipoModifica + "]: " + dettaglio);
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
