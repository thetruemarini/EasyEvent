package easyevent.view;

import java.time.format.DateTimeFormatter;

/**
 * Costanti e utilità condivise tra le View testuali.
 * Appartiene al layer presentazione: non deve essere importata dal Model.
 */
final class ViewUtils {

    static final String SEP  = "------------------------------------------------------------";
    static final String SEP2 = "  ----------------------------------------------------------";

    /** Formato con cui la CLI mostra le date all'utente. */
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ViewUtils() {
        // classe non istanziabile
    }
}