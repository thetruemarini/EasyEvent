package easyevent.proposta;
/**
 * Rappresenta lo stato di una proposta di iniziativa nel suo ciclo di vita:
 * dalla creazione (BOZZA, VALIDA) alla pubblicazione (APERTA) fino agli esiti
 * finali (CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA).
 */
public enum StatoProposta {
    BOZZA,
    VALIDA,
    APERTA,
    CONFERMATA,
    ANNULLATA,
    CONCLUSA,
    RITIRATA
}
