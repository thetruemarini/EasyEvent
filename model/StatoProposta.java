package it.easyevent.v5.model;
/**
 * Rappresenta lo stato di una proposta di iniziativa.
 *
 * Versione 2: BOZZA, VALIDA, APERTA
 * Versione 3: aggiunti CONFERMATA, ANNULLATA, CONCLUSA
 * Versione 4: RITIRATA pienamente implementata
 * Versione 5: nessuna modifica agli stati (aggiunta modalità batch)
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
