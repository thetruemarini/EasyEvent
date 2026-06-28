package easyevent.notifica;

/**
 * Tipo di evento che ha generato una notifica per il fruitore: l'esito del ciclo
 * di vita di una proposta a cui era iscritto (confermata, annullata o ritirata).
 * Il testo mostrato all'utente è costruito dalla View a partire da questo dato.
 */
public enum TipoNotifica {
    PROPOSTA_CONFERMATA,
    PROPOSTA_ANNULLATA,
    PROPOSTA_RITIRATA
}