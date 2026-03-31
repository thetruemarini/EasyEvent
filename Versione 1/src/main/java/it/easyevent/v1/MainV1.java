package it.easyevent.v1;

import it.easyevent.v1.controller.ConfiguratoreController;
import it.easyevent.v1.model.AppData;
import it.easyevent.v1.persistence.PersistenceManager;
import it.easyevent.v1.view.ConfiguratoreView;
import java.io.IOException;

/**
 * Punto di ingresso principale dell'applicazione EasyEvent – Versione 1.
 *
 * Avvia il back-end per i configuratori. Carica i dati persistenti (se
 * presenti) e lancia la view testuale.
 */
public class MainV1 {

    // Percorso del file di persistenza
    private static final String DATA_FILE = "Versione 1/data/easyevent_data.json";

    public static void main(String[] args) {

        // 1. Ottieni l'istanza singleton di AppData
        AppData appData = AppData.getInstance();

        // 2. Crea il gestore di persistenza
        PersistenceManager persistenceManager = new PersistenceManager(DATA_FILE);

        // 3. Carica i dati persistenti (se il file esiste)
        try {
            boolean fileEsisteva = persistenceManager.carica(appData);
            if (fileEsisteva) {
                System.out.println("[Sistema] Dati caricati da: " + DATA_FILE);
            } else {
                System.out.println("[Sistema] Primo avvio: nessun dato precedente trovato.");
            }
        } catch (IOException e) {
            System.err.println("[Sistema] Attenzione: impossibile caricare i dati salvati. " + e.getMessage());
            System.err.println("[Sistema] L'applicazione partirà con dati vuoti.");
        }

        // 4. Crea il controller
        ConfiguratoreController controller = new ConfiguratoreController(appData, persistenceManager);

        // 5. Crea e avvia la view
        ConfiguratoreView view = new ConfiguratoreView(controller);
        view.avvia();
    }
}
