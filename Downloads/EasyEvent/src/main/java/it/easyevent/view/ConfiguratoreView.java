package it.easyevent.view;

import it.easyevent.controller.ConfiguratoreController;
import it.easyevent.model.Campo;
import it.easyevent.model.Categoria;

import java.util.List;
import java.util.Scanner;

/**
 * Interfaccia testuale (CLI) per il configuratore – Versione 1.
 *
 * Questa classe gestisce esclusivamente l'I/O con l'utente.
 * Tutta la logica di business è delegata al ConfiguratoreController.
 * La separazione garantisce facilità di sostituzione con una GUI in futuro.
 *
 * Invariante di classe:
 * - controller != null
 * - scanner != null
 */
public class ConfiguratoreView {

    private static final String SEPARATORE = "--------------------------------------------";
    private static final String TITOLO_APP  = " EasyEvent - Back-end Configuratore (V1) ";

    private final ConfiguratoreController controller;
    private final Scanner scanner;

    /**
     * @param controller controller del configuratore, non null
     */
    public ConfiguratoreView(ConfiguratoreController controller) {
        if (controller == null) throw new IllegalArgumentException("Controller non può essere null.");
        this.controller = controller;
        this.scanner = new Scanner(System.in);
    }

    // ================================================================
    // ENTRY POINT
    // ================================================================

    /**
     * Avvia l'applicazione: effettua il login e poi mostra il menu principale.
     */
    public void avvia() {
        stampaBanner();

        // Inizializza campi base al primo avvio (operazione idempotente)
        String errInit = controller.inizializzaCampiBase();
        if (!errInit.isEmpty()) {
            stampaErrore(errInit);
        }

        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneLogin()) {
                    System.out.println("\nArrivederci.");
                    break;
                }
                // Se primo accesso, forza cambio credenziali
                if (controller.richiedeCambioCredenziali()) {
                    System.out.println("\n*** Primo accesso: è necessario impostare le credenziali personali. ***");
                    if (!gestionePrimoAccesso()) {
                        System.out.println("Operazione annullata. Logout in corso.");
                        controller.logout();
                        continue;
                    }
                }
            }
            boolean continua = menuPrincipale();
            if (!continua) {
                controller.logout();
                System.out.println("\nLogout effettuato.");
                System.out.print("Continuare con un altro account? (s/n): ");
                String risposta = scanner.nextLine().trim();
                if (!risposta.equalsIgnoreCase("s")) {
                    System.out.println("Arrivederci.");
                    break;
                }
            }
        }
        scanner.close();
    }

    // ================================================================
    // LOGIN
    // ================================================================

    /**
     * Gestisce il login.
     *
     * @return true se login effettuato, false se l'utente vuole uscire
     */
    private boolean gestioneLogin() {
        System.out.println("\n" + SEPARATORE);
        System.out.println("  LOGIN CONFIGURATORE");
        System.out.println(SEPARATORE);
        System.out.println("  (digita 'esci' per uscire dall'applicazione)");

        for (int tentativi = 0; tentativi < 3; tentativi++) {
            System.out.print("  Username: ");
            String username = scanner.nextLine().trim();
            if (username.equalsIgnoreCase("esci")) return false;

            System.out.print("  Password: ");
            String password = scanner.nextLine().trim();

            if (controller.login(username, password)) {
                System.out.println("  Login effettuato. Benvenuto, " + username + "!");
                return true;
            } else {
                int rimasti = 2 - tentativi;
                stampaErrore("Credenziali non valide."
                        + (rimasti > 0 ? " Riprovare (" + rimasti + " tentativo/i rimasto/i)." : ""));
            }
        }
        System.out.println("  Numero massimo di tentativi raggiunto.");
        return false;
    }

    /**
     * Gestisce l'impostazione delle credenziali personali al primo accesso.
     *
     * @return true se le credenziali sono state impostate correttamente
     */
    private boolean gestionePrimoAccesso() {
        System.out.println("\n" + SEPARATORE);
        System.out.println("  IMPOSTAZIONE CREDENZIALI PERSONALI");
        System.out.println(SEPARATORE);
        System.out.println("  (digita 'annulla' per tornare indietro e uscire)");

        while (true) {
            System.out.print("  Scegli il tuo username: ");
            String nuovoUsername = scanner.nextLine().trim();
            if (nuovoUsername.equalsIgnoreCase("annulla")) {
                return false;
            }

            System.out.print("  Scegli la tua password: ");
            String nuovaPassword = scanner.nextLine().trim();
            if (nuovaPassword.equalsIgnoreCase("annulla")) {
                return false;
            }

            System.out.print("  Conferma la tua password: ");
            String confermaPassword = scanner.nextLine().trim();
            if (!nuovaPassword.equals(confermaPassword)) {
                stampaErrore("Le password non coincidono. Riprovare.");
                continue;
            }

            String errore = controller.impostaCredenzialiPersonali(nuovoUsername, nuovaPassword);
            if (!errore.isEmpty()) {
                stampaErrore(errore + " Riprovare.");
                continue;
            }

            System.out.println("  Credenziali impostate correttamente.");
            return true;
        }
    }

    // ================================================================
    // MENU PRINCIPALE
    // ================================================================

    /**
     * Mostra e gestisce il menu principale.
     *
     * @return false se l'utente ha scelto logout
     */
    private boolean menuPrincipale() {
        while (true) {
            System.out.println("\n" + SEPARATORE);
            System.out.println("  MENU PRINCIPALE - [" + controller.getConfiguratoreCorrente().getUsername() + "]");
            System.out.println(SEPARATORE);
            System.out.println("  1. Gestione campi base");
            System.out.println("  2. Gestione campi comuni");
            System.out.println("  3. Gestione categorie");
            System.out.println("  4. Visualizza riepilogo categorie e campi");
            System.out.println("  0. Logout");
            System.out.print("  Scelta: ");

            String scelta = scanner.nextLine().trim();
            switch (scelta) {
                case "1" -> menuCampiBase();
                case "2" -> menuCampiComuni();
                case "3" -> menuCategorie();
                case "4" -> visualizzaRiepilogo();
                case "0" -> { return false; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // MENU CAMPI BASE
    // ================================================================

    private void menuCampiBase() {
        System.out.println("\n" + SEPARATORE);
        System.out.println("  CAMPI BASE (immutabili)");
        System.out.println(SEPARATORE);
        List<Campo> campiBase = controller.getCampiBase();
        if (campiBase.isEmpty()) {
            System.out.println("  Nessun campo base definito.");
        } else {
            for (int i = 0; i < campiBase.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, campiBase.get(i));
            }
        }
        System.out.println("\n  (I campi base sono fissati al primo avvio e non modificabili)");
        System.out.println("  Premi INVIO per tornare al menu principale...");
        scanner.nextLine();
    }

    // ================================================================
    // MENU CAMPI COMUNI
    // ================================================================

    private void menuCampiComuni() {
        while (true) {
            System.out.println("\n" + SEPARATORE);
            System.out.println("  GESTIONE CAMPI COMUNI");
            System.out.println(SEPARATORE);
            List<Campo> campiComuni = controller.getCampiComuni();
            if (campiComuni.isEmpty()) {
                System.out.println("  Nessun campo comune definito.");
            } else {
                for (int i = 0; i < campiComuni.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, campiComuni.get(i));
                }
            }
            System.out.println("\n  a. Aggiungi campo comune");
            System.out.println("  r. Rimuovi campo comune");
            System.out.println("  m. Modifica obbligatorietà campo comune");
            System.out.println("  0. Torna al menu principale");
            System.out.print("  Scelta: ");

            String scelta = scanner.nextLine().trim();
            switch (scelta) {
                case "a" -> aggiungiCampoComune();
                case "r" -> rimuoviCampoComune();
                case "m" -> modificaObbligatorietaCampoComune();
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void aggiungiCampoComune() {
        System.out.print("  Nome del nuovo campo comune: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) { stampaErrore("Nome non può essere vuoto."); return; }

        boolean obbligatorio = chiediObbligatorio();
        String errore = controller.aggiungiCampoComune(nome, obbligatorio);
        if (errore.isEmpty()) {
            System.out.println("  Campo comune '" + nome + "' aggiunto.");
        } else {
            stampaErrore(errore);
        }
    }

    private void rimuoviCampoComune() {
        System.out.print("  Nome del campo comune da rimuovere: ");
        String nome = scanner.nextLine().trim();
        String errore = controller.rimuoviCampoComune(nome);
        if (errore.isEmpty()) {
            System.out.println("  Campo comune '" + nome + "' rimosso.");
        } else {
            stampaErrore(errore);
        }
    }

    private void modificaObbligatorietaCampoComune() {
        System.out.print("  Nome del campo comune da modificare: ");
        String nome = scanner.nextLine().trim();
        boolean obbligatorio = chiediObbligatorio();
        String errore = controller.modificaObbligatorietaCampoComune(nome, obbligatorio);
        if (errore.isEmpty()) {
            System.out.println("  ✓ Obbligatorietà di '" + nome + "' aggiornata.");
        } else {
            stampaErrore(errore);
        }
    }

    // ================================================================
    // MENU CATEGORIE
    // ================================================================

    private void menuCategorie() {
        while (true) {
            System.out.println("\n" + SEPARATORE);
            System.out.println("  GESTIONE CATEGORIE");
            System.out.println(SEPARATORE);
            List<Categoria> categorie = controller.getCategorie();
            if (categorie.isEmpty()) {
                System.out.println("  Nessuna categoria definita.");
            } else {
                for (int i = 0; i < categorie.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, categorie.get(i).getNome());
                }
            }
            System.out.println("\n  a. Aggiungi categoria");
            System.out.println("  r. Rimuovi categoria");
            System.out.println("  c. Gestisci campi specifici di una categoria");
            System.out.println("  v. Visualizza dettaglio categoria");
            System.out.println("  0. Torna al menu principale");
            System.out.print("  Scelta: ");

            String scelta = scanner.nextLine().trim();
            switch (scelta) {
                case "a" -> aggiungiCategoria();
                case "r" -> rimuoviCategoria();
                case "c" -> menuCampiSpecifici();
                case "v" -> visualizzaDettaglioCategoria();
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void aggiungiCategoria() {
        System.out.print("  Nome della nuova categoria: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) { stampaErrore("Nome non può essere vuoto."); return; }
        String errore = controller.aggiungiCategoria(nome);
        if (errore.isEmpty()) {
            System.out.println("  Categoria '" + nome + "' aggiunta.");
            // Chiedi se aggiungere subito campi specifici
            System.out.print("  Vuoi aggiungere subito campi specifici per questa categoria? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                aggiungiCampiSpecificiInterattivo(nome);
            }
        } else {
            stampaErrore(errore);
        }
    }

    private void aggiungiCampiSpecificiInterattivo(String nomeCategoria) {
        while (true) {
            System.out.print("  Nome del campo specifico (INVIO per finire): ");
            String nome = scanner.nextLine().trim();
            if (nome.isEmpty()) break;
            boolean obbligatorio = chiediObbligatorio();
            String errore = controller.aggiungiCampoSpecifico(nomeCategoria, nome, obbligatorio);
            if (errore.isEmpty()) {
                System.out.println("  Campo '" + nome + "' aggiunto.");
            } else {
                stampaErrore(errore);
            }
        }
    }

    private void rimuoviCategoria() {
        System.out.print("  Nome della categoria da rimuovere: ");
        String nome = scanner.nextLine().trim();
        System.out.print("  Confermi la rimozione di '" + nome + "'? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }
        String errore = controller.rimuoviCategoria(nome);
        if (errore.isEmpty()) {
            System.out.println("  Categoria '" + nome + "' rimossa (con tutti i suoi campi specifici).");
        } else {
            stampaErrore(errore);
        }
    }

    private void menuCampiSpecifici() {
        if (controller.getCategorie().isEmpty()) {
            stampaErrore("Nessuna categoria disponibile.");
            return;
        }
        System.out.print("  Nome della categoria: ");
        String nomeCategoria = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nomeCategoria);
        if (cat == null) {
            stampaErrore("Categoria non trovata: " + nomeCategoria);
            return;
        }

        while (true) {
            System.out.println("\n  -- Campi specifici di '" + nomeCategoria + "' --");
            if (cat.getCampiSpecifici().isEmpty()) {
                System.out.println("  (nessun campo specifico)");
            } else {
                cat.getCampiSpecifici().forEach(c -> System.out.println("  - " + c));
            }
            System.out.println("\n  a. Aggiungi campo specifico");
            System.out.println("  r. Rimuovi campo specifico");
            System.out.println("  m. Modifica obbligatorietà campo specifico");
            System.out.println("  0. Torna");
            System.out.print("  Scelta: ");

            String scelta = scanner.nextLine().trim();
            switch (scelta) {
                case "a" -> {
                    System.out.print("  Nome del nuovo campo specifico: ");
                    String nome = scanner.nextLine().trim();
                    if (nome.isEmpty()) { stampaErrore("Nome vuoto."); break; }
                    boolean ob = chiediObbligatorio();
                    String err = controller.aggiungiCampoSpecifico(nomeCategoria, nome, ob);
                    if (err.isEmpty()) System.out.println("  Campo '" + nome + "' aggiunto.");
                    else stampaErrore(err);
                }
                case "r" -> {
                    System.out.print("  Nome del campo da rimuovere: ");
                    String nome = scanner.nextLine().trim();
                    String err = controller.rimuoviCampoSpecifico(nomeCategoria, nome);
                    if (err.isEmpty()) System.out.println("  Campo '" + nome + "' rimosso.");
                    else stampaErrore(err);
                }
                case "m" -> {
                    System.out.print("  Nome del campo da modificare: ");
                    String nome = scanner.nextLine().trim();
                    boolean ob = chiediObbligatorio();
                    String err = controller.modificaObbligatorietaCampoSpecifico(nomeCategoria, nome, ob);
                    if (err.isEmpty()) System.out.println("  Obbligatorietà aggiornata.");
                    else stampaErrore(err);
                }
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void visualizzaDettaglioCategoria() {
        System.out.print("  Nome della categoria da visualizzare: ");
        String nome = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nome);
        if (cat == null) {
            stampaErrore("Categoria non trovata: " + nome);
            return;
        }
        System.out.println("\n  " + SEPARATORE);
        System.out.println("  DETTAGLIO CATEGORIA: " + cat.getNome().toUpperCase());
        System.out.println("  " + SEPARATORE);
        System.out.println("  -- Campi BASE (condivisi da tutte le categorie) --");
        controller.getCampiBase().forEach(c -> System.out.println("    - " + c));
        System.out.println("  -- Campi COMUNI (condivisi da tutte le categorie) --");
        if (controller.getCampiComuni().isEmpty()) System.out.println("    (nessuno)");
        else controller.getCampiComuni().forEach(c -> System.out.println("    - " + c));
        System.out.println("  -- Campi SPECIFICI di '" + cat.getNome() + "' --");
        if (cat.getCampiSpecifici().isEmpty()) System.out.println("    (nessuno)");
        else cat.getCampiSpecifici().forEach(c -> System.out.println("    - " + c));

        System.out.println("\n  Premi INVIO per tornare...");
        scanner.nextLine();
    }

    // ================================================================
    // RIEPILOGO GENERALE
    // ================================================================

    private void visualizzaRiepilogo() {
        System.out.println("\n" + SEPARATORE);
        System.out.println("  RIEPILOGO COMPLETO CATEGORIE E CAMPI");
        System.out.println(SEPARATORE);

        System.out.println("\n  [CAMPI BASE - obbligatori - condivisi da tutte le categorie]");
        List<Campo> campiBase = controller.getCampiBase();
        if (campiBase.isEmpty()) System.out.println("    (non ancora inizializzati)");
        else campiBase.forEach(c -> System.out.println("    - " + c.getNome() + " (obbligatorio)"));

        System.out.println("\n  [CAMPI COMUNI - condivisi da tutte le categorie]");
        List<Campo> campiComuni = controller.getCampiComuni();
        if (campiComuni.isEmpty()) System.out.println("    (nessuno definito)");
        else campiComuni.forEach(c -> System.out.println("    - " + c));

        System.out.println("\n  [CATEGORIE]");
        List<Categoria> categorie = controller.getCategorie();
        if (categorie.isEmpty()) {
            System.out.println("    (nessuna categoria definita)");
        } else {
            for (Categoria cat : categorie) {
                System.out.println("    > " + cat.getNome().toUpperCase());
                if (cat.getCampiSpecifici().isEmpty()) {
                    System.out.println("      (nessun campo specifico)");
                } else {
                    cat.getCampiSpecifici().forEach(c -> System.out.println("      - " + c));
                }
            }
        }

        System.out.println("\n  Premi INVIO per tornare al menu principale...");
        scanner.nextLine();
    }

    // ================================================================
    // UTILITY
    // ================================================================

    private boolean chiediObbligatorio() {
        System.out.print("  Il campo è obbligatorio? (s/n): ");
        String risposta = scanner.nextLine().trim();
        return risposta.equalsIgnoreCase("s");
    }

    private void stampaErrore(String msg) {
        System.out.println("  ERRORE: " + msg);
    }

    private void stampaBanner() {
        System.out.println("\n" + SEPARATORE);
        System.out.println(TITOLO_APP);
        System.out.println(SEPARATORE);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Universita' - Ingegneria del Software a.a. 2025-2026");
        System.out.println(SEPARATORE);
    }
}
