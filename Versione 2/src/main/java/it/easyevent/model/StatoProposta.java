package it.easyevent.model;

/**
 * Rappresenta lo stato di una proposta di iniziativa.
 *
 * Versione 2: BOZZA, VALIDA, APERTA
 * Versione 3 (futuro): CONFERMATA, CONCLUSA, ANNULLATA
 * Versione 4 (futuro): RITIRATA
 *
 * BOZZA  – proposta in creazione nella sessione corrente; uno o più
 *           campi obbligatori non compilati o vincoli di data non soddisfatti.
 *           Non viene mai persistita.
 * VALIDA – tutti i campi obbligatori compilati e vincoli di data soddisfatti;
 *           resta in sessione. Non viene persistita (se non pubblicata,
 *           viene scartata al termine della sessione).
 * APERTA – pubblicata in bacheca dal configuratore; viene persistita.
 */
public enum StatoProposta {
    BOZZA,
    VALIDA,
    APERTA
}
