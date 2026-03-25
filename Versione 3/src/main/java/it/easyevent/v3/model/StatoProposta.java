package it.easyevent.v3.model;

/**
 * Rappresenta lo stato di una proposta di iniziativa.
 *
 * Versione 2: BOZZA, VALIDA, APERTA
 * Versione 3: aggiunti CONFERMATA, ANNULLATA, CONCLUSA
 * Versione 4: aggiunto RITIRATA (transizioni implementate in V4)
 *
 * BOZZA      – proposta in creazione nella sessione corrente; uno o piu'
 *              campi obbligatori non compilati o vincoli di data non soddisfatti.
 *              Non viene mai persistita.
 * VALIDA     – tutti i campi obbligatori compilati e vincoli di data soddisfatti;
 *              resta in sessione. Non viene persistita (se non pubblicata,
 *              viene scartata al termine della sessione).
 * APERTA     – pubblicata in bacheca; iscrizioni attive. Persistita. (V2+)
 * CONFERMATA – allo scadere del termine iscrizione, il numero di aderenti
 *              ha raggiunto il numero di partecipanti previsto. (V3+)
 * ANNULLATA  – allo scadere del termine iscrizione, il numero di aderenti
 *              e' inferiore al numero di partecipanti previsto. (V3+)
 * CONCLUSA   – il giorno successivo alla data conclusiva dell'iniziativa
 *              (solo da CONFERMATA). (V3+)
 * RITIRATA   – il configuratore ha ritirato la proposta per cause di forza
 *              maggiore (da APERTA o CONFERMATA, fino alle 23.59 del giorno
 *              precedente la data "Data"). Notifiche inviate a tutti gli
 *              aderenti. Implementato in V4. (V4+)
 */
public enum StatoProposta {
    BOZZA,
    VALIDA,
    APERTA,
    CONFERMATA,
    ANNULLATA,
    CONCLUSA,
    RITIRATA    // V4: transizioni APERTA->RITIRATA e CONFERMATA->RITIRATA
}
