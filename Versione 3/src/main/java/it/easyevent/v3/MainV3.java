package it.easyevent.v3;

import it.easyevent.v3.controller.ConfiguratoreController;
import it.easyevent.v3.controller.FruitoreController;
import it.easyevent.v3.model.AppData;
import it.easyevent.v3.persistence.PersistenceManager;
import it.easyevent.v3.view.ConfiguratoreView;
import it.easyevent.v3.view.FruitoreView;
import java.io.IOException;
import java.util.Scanner;
/**
 * Punto di ingresso principale dell'applicazione EasyEvent - Versione 3.
 *
 * Novita' rispetto alla V2:
 *   - Il sistema consente l'accesso anche al fruitore (front-end).
 *   - All'avvio vengono applicate le transizioni automatiche di stato
 *     (proposte scadute: APERTA -> CONFERMATA/ANNULLATA; CONFERMATA -> CONCLUSA).
 *   - Il menu iniziale chiede se si vuole accedere come configuratore o fruitore.
 *
 * Compatibilita': legge file di dati V2 (campo "bacheca") e li aggiorna
 * automaticamente al formato V3 ("archivio") al primo salvataggio.
 */
public class MainV3 {

    private static final String DATA_FILE = "Versione 3/data/easyevent_data.json";

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
            System.err.println("[Sistema] Attenzione: impossibile caricare i dati salvati: " + e.getMessage());
            System.err.println("[Sistema] L'applicazione partira' con dati vuoti.");
        }

        // 4. Controller
        ConfiguratoreController confController
                = new ConfiguratoreController(appData, persistenceManager);

        // Inizializza i campi base se necessario
        String errInit = confController.inizializzaCampiBase();
        if (!errInit.isEmpty()) {
            System.err.println("[Sistema] " + errInit);
        }

        // 5. Transizioni automatiche di stato (da chiamare ad ogni avvio)
        //    Gestisce il caso in cui l'app non fosse in esecuzione alla mezzanotte
        int nTransizioni = confController.aggiornaTransizioni();
        if (nTransizioni > 0) {
            System.out.println("[Sistema] Transizioni automatiche applicate: "
                    + nTransizioni + " proposta/e aggiornata/e.");
        }

        // Stampa riepilogo avvio
        System.out.println("[Sistema] Proposte in bacheca (aperte): " + appData.getBacheca().size());
        System.out.println("[Sistema] Proposte nell'archivio: " + appData.getArchivio().size());

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
            System.out.println("  EasyEvent  -  Versione 3");
            System.out.println(SEP);
            System.out.println("  Con quale ruolo vuoi accedere?");
            System.out.println("  1. Configuratore  (back-end)");
            System.out.println("  2. Fruitore       (front-end)");
            System.out.println("  0. Esci");
            System.out.println();
            System.out.print("  Scelta: ");
            String scelta = scanner.nextLine().trim();

            switch (scelta) {
                case "1" -> {
                    ConfiguratoreView confView = new ConfiguratoreView(confController, scanner);
                    if (!confView.avvia()) {
                        return;
                    }
                }
                case "2" -> {
                    FruitoreView fruitView = new FruitoreView(fruitController, scanner);
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
