package it.easyevent.v5.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Rappresenta una notifica destinata a un fruitore.
 *
 * Invariante di classe:
 *   - id >= 0
 *   - testo != null && !testo.isBlank()
 *   - dataCreazione != null
 */
public class Notifica {

    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final int       id;
    private final String    testo;
    private final LocalDate dataCreazione;

    public Notifica(int id, String testo, LocalDate dataCreazione) {
        if (id < 0)
            throw new IllegalArgumentException("L'id della notifica non puo' essere negativo.");
        if (testo == null || testo.isBlank())
            throw new IllegalArgumentException("Il testo della notifica non puo' essere null o vuoto.");
        if (dataCreazione == null)
            throw new IllegalArgumentException("La data di creazione non puo' essere null.");
        this.id            = id;
        this.testo         = testo;
        this.dataCreazione = dataCreazione;
        assert repOk() : "Invariante violato dopo costruzione Notifica";
    }

    public int       getId()            { return id; }
    public String    getTesto()         { return testo; }
    public LocalDate getDataCreazione() { return dataCreazione; }

    public boolean repOk() {
        return id >= 0 && testo != null && !testo.isBlank() && dataCreazione != null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", dataCreazione.format(DATE_FORMAT), testo);
    }
}