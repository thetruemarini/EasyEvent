package it.easyevent.v5.view;

import it.easyevent.v5.controller.FruitoreController;
import it.easyevent.v5.model.Notifica;
import it.easyevent.v5.model.Proposta;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
/**
 * Interfaccia testuale (CLI) per il fruitore - Versione 5.
 * Identica alla V4; la V5 non aggiunge funzionalita' lato fruitore.
 *
 * Invariante di classe: controller != null, scanner != null
 */
public class FruitoreView {

    private static final String SEP = "------------------------------------------------------------";
    private static final String SEP2 = "  ----------------------------------------------------------";

    private final FruitoreController controller;
    private final Scanner scanner;

    public FruitoreView(FruitoreController controller, Scanner scanner) {
        if (controller == null) {
            throw new IllegalArgumentException("Controller non puo' essere null.");
        }
        if (scanner == null) {
            throw new IllegalArgumentException("Scanner non puo' essere null.");
        }
        this.controller = controller;
        this.scanner = scanner;
    }

    // ================================================================
    // ENTRY POINT
    // ================================================================
    public boolean avvia() {
        stampaBanner();
        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneAccesso()) {
                    return true;
                }
            }
            boolean continua = menuPrincipale();
            if (!continua) {
                controller.logout();
                System.out.println("\n  Logout effettuato.");
                System.out.print("  Continuare con un altro account? (s/n): ");
                if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                    return true;
                }
                System.out.println("\n  Arrivederci.");
                return false;
            }
        }
    }

    // ================================================================
    // ACCESSO (login o registrazione)
    // ================================================================
    private boolean gestioneAccesso() {
        System.out.println("\n" + SEP + "\n  ACCESSO FRUITORE\n" + SEP);
        System.out.println("  1. Login   2. Registrazione   0. Torna");
        System.out.print("  Scelta: ");
        return switch (scanner.nextLine().trim()) {
            case "1" ->
                gestioneLogin();
            case "2" ->
                gestioneRegistrazione();
            default ->
                false;
        };
    }

    private boolean gestioneLogin() {
        System.out.println("\n" + SEP + "\n  LOGIN FRUITORE\n" + SEP);
        System.out.println("  (digita 'annulla' per tornare)");
        for (int t = 0; t < 3; t++) {
            System.out.print("  Username: ");
            String u = scanner.nextLine().trim();
            if (u.equalsIgnoreCase("annulla")) {
                return false;
            }
            System.out.print("  Password: ");
            String p = scanner.nextLine().trim();
            if (controller.login(u, p)) {
                int nN = controller.getNotifiche().size();
                System.out.println("\n  Benvenuto, " + u + "!"
                        + (nN > 0 ? "  [" + nN + " notifica/e non letta/e]" : ""));
                return true;
            }
            int rimasti = 2 - t;
            stampaErrore("Credenziali non valide." + (rimasti > 0 ? " Rimasti: " + rimasti : ""));
        }
        System.out.println("\n  Accesso bloccato.");
        return false;
    }

    private boolean gestioneRegistrazione() {
        System.out.println("\n" + SEP + "\n  REGISTRAZIONE\n" + SEP);
        System.out.println("  (digita 'annulla' per tornare)");
        System.out.print("  Username: ");
        String u = scanner.nextLine().trim();
        if (u.equalsIgnoreCase("annulla")) {
            return false;
        }
        System.out.print("  Password: ");
        String p = scanner.nextLine().trim();
        if (p.equalsIgnoreCase("annulla")) {
            return false;
        }
        System.out.print("  Conferma: ");
        String c = scanner.nextLine().trim();
        if (!p.equals(c)) {
            stampaErrore("Le password non coincidono.");
            return false;
        }
        String err = controller.registra(u, p);
        if (!err.isEmpty()) {
            stampaErrore(err);
            return false;
        }
        System.out.println("\n  Registrazione completata. Benvenuto, " + u + "!");
        return true;
    }

    // ================================================================
    // MENU PRINCIPALE FRUITORE
    // ================================================================
    private boolean menuPrincipale() {
        while (true) {
            int nNotifiche = controller.getNotifiche().size();
            long nIscrizioni = controller.getBacheca().stream()
                    .filter(p -> controller.isIscritto(p.getId())).count();

            System.out.println("\n" + SEP);
            System.out.println("  MENU FRUITORE  [" + controller.getFruitoreCorrente().getUsername() + "]");
            System.out.println(SEP);
            System.out.println("  1. Visualizza bacheca");
            System.out.println("  2. Aderisci a una proposta");
            System.out.println("  3. Disdici iscrizione         [" + nIscrizioni + " iscrizione/i attive]");
            System.out.println("  4. Spazio personale"
                    + (nNotifiche > 0 ? "           [" + nNotifiche + " notifica/e]" : ""));
            System.out.println("  0. Logout");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "1" ->
                    visualizzaBacheca();
                case "2" ->
                    aderisciAProposta();
                case "3" ->
                    disdiciIscrizione();
                case "4" ->
                    spazioPersonale();
                case "0" -> {
                    return false;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // BACHECA
    // ================================================================
    private void visualizzaBacheca() {
        System.out.println("\n" + SEP + "\n  BACHECA  -  PROPOSTE APERTE\n" + SEP);
        List<String> categorie = controller.getCategorieConProposte();
        if (categorie.isEmpty()) {
            System.out.println("\n  (Nessuna proposta aperta in bacheca)");
            premInvio();
            return;
        }
        for (String nomeCat : categorie) {
            List<Proposta> perCat = controller.getBachecaPerCategoria(nomeCat);
            System.out.println("\n  CATEGORIA: " + nomeCat.toUpperCase()
                    + "  (" + perCat.size() + " proposta/e)");
            System.out.println(SEP2);
            perCat.forEach(this::stampaBloccoPropostaBacheca);
        }
        System.out.println("\n  Totale proposte aperte: " + controller.getBacheca().size());
        premInvio();
    }

    private void stampaBloccoPropostaBacheca(Proposta p) {
        String titolo = p.getValore("Titolo");
        boolean iscritto = controller.isIscritto(p.getId());
        int numMax = p.getNumeroMaxPartecipanti();

        System.out.println();
        System.out.println("    [ID " + p.getId() + "]  \""
                + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\""
                + (iscritto ? "  [ISCRITTO]" : ""));
        System.out.println("    Iscrizioni: " + p.getAderenti().size()
                + "/" + (numMax < 0 ? "N/D" : numMax)
                + "   Termine: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE));

        // Campi informativi standard
        String[] campiDaStampare = {
            "Data inizio", "Ora", "Luogo", "Quota individuale",
            "Data conclusiva", "Durata", "Note", "Compreso nella quota"
        };
        for (String campo : campiDaStampare) {
            String v = p.getValore(campo);
            if (!v.isBlank()) {
                System.out.println("    " + campo + ": " + v);
            }
        }
        // Campi specifici non standard
        for (String nome : p.getValori().keySet()) {
            boolean giaMostrato = false;
            for (String s : campiDaStampare) {
                if (s.equalsIgnoreCase(nome)) {
                    giaMostrato = true;
                    break;
                }
            }
            if (!giaMostrato
                    && !nome.equalsIgnoreCase("Titolo")
                    && !nome.equalsIgnoreCase(Proposta.CAMPO_TERMINE_ISCRIZIONE)
                    && !nome.equalsIgnoreCase(Proposta.CAMPO_NUM_PARTECIPANTI)) {
                String v = p.getValore(nome);
                if (!v.isBlank()) {
                    System.out.println("    " + nome + ": " + v);
                }
            }
        }
    }

    // ================================================================
    // ISCRIZIONE
    // ================================================================
    private void aderisciAProposta() {
        List<Proposta> disponibili = controller.getBacheca().stream()
                .filter(p -> !controller.isIscritto(p.getId()))
                .collect(Collectors.toList());

        if (disponibili.isEmpty()) {
            System.out.println("\n  Sei gia' iscritto a tutte le proposte disponibili.");
            premInvio();
            return;
        }

        System.out.println("\n" + SEP + "\n  ADERISCI A UNA PROPOSTA\n" + SEP);
        System.out.println("  Proposte disponibili:");
        for (Proposta p : disponibili) {
            int numMax = p.getNumeroMaxPartecipanti();
            String posti = numMax < 0 ? "N/D" : String.valueOf(numMax - p.getAderenti().size());
            System.out.println("    [ID " + p.getId() + "]  "
                    + p.getNomeCategoria() + "  \""
                    + p.getValore("Titolo") + "\""
                    + "  Posti rimasti: " + posti
                    + "  Termine: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE));
        }
        System.out.print("\n  ID proposta (0 per annullare): ");
        String input = scanner.nextLine().trim();
        if (input.equals("0")) {
            return;
        }

        try {
            int id = Integer.parseInt(input);
            String err = controller.aderisci(id);
            if (err.isEmpty()) {
                Proposta p = controller.getPropostaAperta(id);
                System.out.println("\n  Iscrizione a \""
                        + (p != null ? p.getValore("Titolo") : "ID " + id)
                        + "\" completata.");
            } else {
                stampaErrore(err);
            }
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
        }
    }

    // ================================================================
    // DISDICI ISCRIZIONE (V4)
    // ================================================================
    private void disdiciIscrizione() {
        List<Proposta> iscrittoA = controller.getBacheca().stream()
                .filter(p -> controller.isIscritto(p.getId()))
                .collect(Collectors.toList());

        if (iscrittoA.isEmpty()) {
            System.out.println("\n  Non sei iscritto ad alcuna proposta aperta.");
            premInvio();
            return;
        }

        System.out.println("\n" + SEP + "\n  DISDICI ISCRIZIONE\n" + SEP);
        System.out.println("\n  Proposte a cui sei iscritto:");
        iscrittoA.forEach(p
                -> System.out.println("    [ID " + p.getId() + "]  \""
                        + p.getValore("Titolo") + "\""
                        + "  Termine: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE)));

        System.out.println("\n  NOTA: dopo la disdetta potrai re-iscriverti (rispettando il termine).");
        System.out.print("  ID proposta da cui disdire (0 per annullare): ");
        String input = scanner.nextLine().trim();
        if (input.equals("0")) {
            System.out.println("  Operazione annullata.");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
            return;
        }

        Proposta scelta = iscrittoA.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
        if (scelta == null) {
            stampaErrore("Non sei iscritto a nessuna proposta con ID " + id + ".");
            return;
        }

        System.out.print("  Confermi la disdetta da \""
                + scelta.getValore("Titolo") + "\"? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }

        String err = controller.disdiciIscrizione(id);
        if (err.isEmpty()) {
            System.out.println("\n  Iscrizione disdetta correttamente.");
            System.out.println("  Puoi re-iscriverti entro il termine di iscrizione.");
        } else {
            stampaErrore(err);
        }
        premInvio();
    }

    // ================================================================
    // SPAZIO PERSONALE (notifiche)
    // ================================================================
    private void spazioPersonale() {
        while (true) {
            List<Notifica> notifiche = controller.getNotifiche();
            System.out.println("\n" + SEP);
            System.out.println("  SPAZIO PERSONALE  ["
                    + controller.getFruitoreCorrente().getUsername() + "]");
            System.out.println(SEP);

            if (notifiche.isEmpty()) {
                System.out.println("\n  (Nessuna notifica)");
            } else {
                System.out.println("\n  Notifiche (" + notifiche.size() + "):");
                notifiche.forEach(n
                        -> System.out.println("\n  [ID " + n.getId() + "]  " + n));
            }

            System.out.println("\n  c. Cancella una  t. Cancella tutte  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "c" ->
                    cancellaNotifica(notifiche);
                case "t" ->
                    cancellaAllNotifiche(notifiche);
                case "0" -> {
                    return;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    private void cancellaNotifica(List<Notifica> notifiche) {
        if (notifiche.isEmpty()) {
            stampaErrore("Nessuna notifica.");
            return;
        }
        System.out.print("  ID notifica da cancellare: ");
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            String err = controller.cancellaNotifica(id);
            if (err.isEmpty()) {
                System.out.println("  Notifica [ID " + id + "] cancellata."); 
            }else {
                stampaErrore(err);
            }
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
        }
    }

    private void cancellaAllNotifiche(List<Notifica> notifiche) {
        if (notifiche.isEmpty()) {
            stampaErrore("Nessuna notifica.");
            return;
        }
        System.out.print("  Cancellare tutte le " + notifiche.size() + " notifiche? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Annullato.");
            return;
        }
        String err = controller.cancellaAllNotifiche();
        if (err.isEmpty()) {
            System.out.println("  Tutte le notifiche cancellate."); 
        }else {
            stampaErrore(err);
        }
    }

    // ================================================================
    // UTILITY
    // ================================================================
    private void premInvio() {
        System.out.println();
        System.out.print("  Premi INVIO per tornare al menu...");
        scanner.nextLine();
    }

    private void stampaErrore(String msg) {
        System.out.println("\n  ERRORE: " + msg);
    }

    private void stampaBanner() {
        System.out.println("\n" + SEP);
        System.out.println("  EasyEvent  -  Front-end Fruitore  (Versione 5)");
        System.out.println(SEP);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Ingegneria del Software  -  A.A. 2025-2026");
        System.out.println(SEP);
    }
}
