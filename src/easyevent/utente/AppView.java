package easyevent.utente;

import java.util.Scanner;

/**
 * View principale dell'applicazione: gestisce la selezione del ruolo
 * (Configuratore o Fruitore) e delega alle rispettive View.
 *
 * Appartiene al layer di presentazione, non al punto di avvio.
 */
public class AppView {

    private final ConfiguratoreController confController;
    private final FruitoreController fruitController;
    private final Scanner scanner;

    public AppView(ConfiguratoreController confController,
            FruitoreController fruitController,
            Scanner scanner) {
        if (confController == null) {
            throw new IllegalArgumentException("confController non puo' essere null.");
        }
        if (fruitController == null) {
            throw new IllegalArgumentException("fruitController non puo' essere null.");
        }
        if (scanner == null) {
            throw new IllegalArgumentException("scanner non puo' essere null.");
        }
        this.confController = confController;
        this.fruitController = fruitController;
        this.scanner = scanner;
    }

    /**
     * Avvia il loop principale di selezione ruolo.
     */
    public void avvia() {
        while (true) {
            System.out.println("\n" + ViewUtils.SEP);
            System.out.println("  EasyEvent  -  Versione 5");
            System.out.println(ViewUtils.SEP);
            System.out.println("  Con quale ruolo vuoi accedere?");
            System.out.println("  1. Configuratore  (back-end + importazione batch)");
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
