package it.easyevent.v4.model;

/**
 * Rappresenta lo stato di una proposta di iniziativa.
 *
 * Versione 2: BOZZA, VALIDA, APERTA
 * Versione 3: aggiunti CONFERMATA, ANNULLATA, CONCLUSA
 * Versione 4: RITIRATA ora pienamente implementata
 *
 * BOZZA      – proposta in creazione nella sessione corrente; non persistita.
 * VALIDA     – tutti i campi obbligatori compilati e vincoli soddisfatti; non persistita.
 * APERTA     – pubblicata in bacheca; iscrizioni attive. (V2+)
 * CONFERMATA – aderenti >= numero partecipanti a scadenza termine. (V3+)
 * ANNULLATA  – aderenti < numero partecipanti a scadenza termine. (V3+)
 * CONCLUSA   – il giorno successivo alla data conclusiva (solo da CONFERMATA). (V3+)
 * RITIRATA   – il configuratore ha ritirato la proposta (da APERTA o CONFERMATA,
 *              fino alle 23.59 del giorno precedente la "Data"). (V4+)
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