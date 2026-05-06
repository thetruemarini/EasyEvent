package easyevent.main;

import easyevent.core.AppData;
import easyevent.persistence.PersistenceManager;
import easyevent.utente.AppView;
import easyevent.utente.ConfiguratoreController;
import easyevent.utente.FruitoreController;
import java.io.IOException;
import java.util.Scanner;

/**
 * Punto di ingresso principale dell'applicazione EasyEvent - Versione 5.
 *
 * Responsabilità del main (e solo queste): 1. Creare AppData (stato centrale)
 * 2. Creare PersistenceManager 3. Caricare i dati 4. Creare i Controller
 * (iniettando le dipendenze) 5. Creare la View principale e avviarla
 *
 * Nessuna logica di business, nessuna logica di presentazione vive qui. Il main
 * dipende da tutti i layer per definizione: è il suo ruolo. Non appartiene a
 * nessuna delle caselle MVC — ha un package dedicato.
 */
public class MainV5 {

    private static final String DATA_FILE = "data/easyevent_data.json";
    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASS = "admin123";

    public static void main(String[] args) {

        // 1. Stato centrale — creato una sola volta, iniettato tramite costruttore
        AppData appData = new AppData();

        // 2. Persistenza
        PersistenceManager persistenceManager = new PersistenceManager(DATA_FILE);

        // 3. Caricamento dati
        try {
            boolean fileEsisteva = persistenceManager.carica(appData);
            if (fileEsisteva) {
                System.out.println("[Sistema] Dati caricati da: " + DATA_FILE);
            } else {
                System.out.println("[Sistema] Primo avvio: nessun dato precedente trovato.");
            }
        } catch (IOException e) {
            System.err.println("[Sistema] Impossibile caricare i dati: " + e.getMessage());
            System.err.println("[Sistema] L'applicazione partira' con dati vuoti.");
        }

        // 4. Controller (Dependency Injection)
        ConfiguratoreController confController
                = new ConfiguratoreController(appData, persistenceManager,
                        DEFAULT_ADMIN_USER, DEFAULT_ADMIN_PASS);

        FruitoreController fruitController
                = new FruitoreController(appData, persistenceManager);

        // Inizializza campi base se necessario
        try {
            confController.inizializzaCampiBase();
        } catch (IOException e) {
            System.err.println("[Sistema] Campi base creati ma errore nel salvataggio: "
                    + e.getMessage());
        }

        // Transizioni automatiche di stato
        int nTransizioni = confController.aggiornaTransizioni();
        if (nTransizioni > 0) {
            System.out.println("[Sistema] Transizioni automatiche applicate: "
                    + nTransizioni + " proposta/e aggiornata/e.");
        }

        // Riepilogo avvio
        System.out.println("[Sistema] Proposte aperte in bacheca: "
                + appData.getBacheca().size());
        System.out.println("[Sistema] Proposte nell'archivio:     "
                + appData.getArchivio().size());

        // 5. View principale — tutta la presentazione vive qui
        Scanner scanner = new Scanner(System.in);
        AppView appView = new AppView(confController, fruitController, scanner);
        appView.avvia();
        scanner.close();
    }
}
