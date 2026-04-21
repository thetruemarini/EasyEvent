package easyevent;

import easyevent.controller.ConfiguratoreController;
import easyevent.controller.FruitoreController;
import easyevent.model.AppData;
import easyevent.persistence.PersistenceManager;
import easyevent.view.ConfiguratoreView;
import easyevent.view.FruitoreView;
import java.io.IOException;
import java.util.Scanner;
/**
 * Punto di ingresso principale dell'applicazione EasyEvent - Versione 5.
 *
 * Novita' rispetto alla V4:
 *   - Il configuratore puo' importare categorie, campi e proposte in modalita'
 *     batch (da uno o piu' file di testo), oltre a operare in modo interattivo
 *     come nelle versioni precedenti.
 *
 * Compatibilita': legge file di dati V4/V3/V2/V1 senza modifiche.
 * Il formato del file JSON di persistenza e' invariato rispetto alla V4.
 */
public class MainV5 {

    private static final String DATA_FILE = "data/easyevent_data.json";

    public static void main(String[] args) {

        // 1. Stato centrale (Singleton)
        AppData appData = AppData.getInstance();

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

        // 4. Controller
        ConfiguratoreController confController
                = new ConfiguratoreController(appData, persistenceManager);

        // Inizializza campi base se necessario
        String errInit = confController.inizializzaCampiBase();
        if (!errInit.isEmpty()) {
            System.err.println("[Sistema] " + errInit);
        }

        // 5. Transizioni automatiche di stato (da invocare ad ogni avvio)
        int nTransizioni = confController.aggiornaTransizioni();
        if (nTransizioni > 0) {
            System.out.println("[Sistema] Transizioni automatiche applicate: "
                    + nTransizioni + " proposta/e aggiornata/e.");
        }

        // Riepilogo avvio
        System.out.println("[Sistema] Proposte aperte in bacheca: " + appData.getBacheca().size());
        System.out.println("[Sistema] Proposte nell'archivio:     " + appData.getArchivio().size());

        // 6. Menu di selezione ruolo
        FruitoreController fruitController = new FruitoreController(appData, persistenceManager);
        Scanner scanner = new Scanner(System.in);

        menuSelezioneRuolo(confController, fruitController, scanner);

        scanner.close();
    }

    // ================================================================
    // SELEZIONE RUOLO
    // ================================================================
    private static void menuSelezioneRuolo(ConfiguratoreController confController,
            FruitoreController fruitController,
            Scanner scanner) {
        String SEP = "------------------------------------------------------------";
        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  EasyEvent  -  Versione 5");
            System.out.println(SEP);
            System.out.println("  Con quale ruolo vuoi accedere?");
            System.out.println("  1. Configuratore  (back-end + importazione batch)");
            System.out.println("  2. Fruitore       (front-end)");
            System.out.println("  0. Esci");
            System.out.println();
            System.out.print("  Scelta: ");
            String scelta = scanner.nextLine().trim();

            switch (scelta) {
                case "1" -> {
                    ConfiguratoreView confView
                            = new ConfiguratoreView(confController, scanner);
                    if (!confView.avvia()) {
                        return;
                    }
                }
                case "2" -> {
                    FruitoreView fruitView
                            = new FruitoreView(fruitController, scanner);
                    if (!fruitView.avvia()) {
                        return;
                    }
                }
                case "0" -> {
                    System.out.println("\n  Arrivederci.");
                    return;
                }
                default ->
                    System.out.println("\n  ERRORE: Scelta non valida.");
            }
        }
    }
}
