package it.easyevent;

import it.easyevent.controller.ConfiguratoreController;
import it.easyevent.model.AppData;
import it.easyevent.persistence.PersistenceManager;
import it.easyevent.view.ConfiguratoreView;
import java.io.IOException;

/**
 * Punto di ingresso principale dell'applicazione EasyEvent – Versione 2.
 *
 * Novità rispetto alla V1:
 *   - Gestione delle proposte di iniziative (creazione, compilazione, pubblicazione)
 *   - Bacheca delle proposte aperte (visibile ai configuratori, persistita)
 *   - Salvataggio automatico delle proposte aperte su file JSON
 *
 * Il file di persistenza è compatibile con la V1: se viene letto un file V1
 * (senza la sezione "bacheca"), la bacheca viene inizializzata vuota.
 */
public class MainV2 {

    private static final String DATA_FILE = "Versione2/data/easyevent_data.json";

    public static void main(String[] args) {

        // 1. Stato centrale dell'applicazione (Singleton)
        AppData appData = AppData.getInstance();

        // 2. Gestore di persistenza (V2: gestisce anche la bacheca)
        PersistenceManager persistenceManager = new PersistenceManager(DATA_FILE);

        // 3. Caricamento dei dati persistenti (file V1 o V2)
        try {
            boolean fileEsisteva = persistenceManager.carica(appData);
            if (fileEsisteva) {
                int nBacheca = appData.getBacheca().size();
                System.out.println("[Sistema] Dati caricati da: " + DATA_FILE);
                System.out.println("[Sistema] Proposte in bacheca: " + nBacheca);
            } else {
                System.out.println("[Sistema] Primo avvio: nessun dato precedente trovato.");
            }
        } catch (IOException e) {
            System.err.println("[Sistema] Attenzione: impossibile caricare i dati salvati. "
                    + e.getMessage());
            System.err.println("[Sistema] L'applicazione partirà con dati vuoti.");
        }

        // 4. Controller
        ConfiguratoreController controller =
                new ConfiguratoreController(appData, persistenceManager);

        // 5. View testuale (V2)
        ConfiguratoreView view = new ConfiguratoreView(controller);
        view.avvia();
    }
}
