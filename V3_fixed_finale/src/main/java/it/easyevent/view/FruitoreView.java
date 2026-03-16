package it.easyevent.view;

import it.easyevent.controller.FruitoreController;
import it.easyevent.model.Notifica;
import it.easyevent.model.Proposta;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Interfaccia testuale (CLI) per il fruitore - Versione 3.
 *
 * Il fruitore puo':
 *   - registrarsi (primo accesso) o effettuare il login
 *   - visualizzare la bacheca per categoria
 *   - aderire a proposte aperte
 *   - accedere allo spazio personale (notifiche) e cancellarle
 *
 * Invariante di classe:
 *   - controller != null
 *   - scanner != null (condiviso con il chiamante se passato, altrimenti locale)
 */
public class FruitoreView {

    private static final String SEP  = "------------------------------------------------------------";
    private static final String SEP2 = "  ----------------------------------------------------------";

    private final FruitoreController controller;
    private final Scanner scanner;

    public FruitoreView(FruitoreController controller, Scanner scanner) {
        if (controller == null)
            throw new IllegalArgumentException("Controller non puo' essere null.");
        if (scanner == null)
            throw new IllegalArgumentException("Scanner non puo' essere null.");
        this.controller = controller;
        this.scanner    = scanner;
    }

    // ================================================================
    // ENTRY POINT
    // ================================================================

    /**
     * Avvia il flusso di interazione per il fruitore.
     * Ritorna quando il fruitore sceglie di uscire.
     */
    public void avvia() {
        stampaBanner();
        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneAccesso()) { System.out.println("\n  Arrivederci."); break; }
            }
            boolean continua = menuPrincipale();
            if (!continua) {
                controller.logout();
                System.out.println("\n  Logout effettuato.");
                System.out.print("  Continuare con un altro account? (s/n): ");
                if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
                    System.out.println("\n  Arrivederci.");
                    break;
                }
            }
        }
    }

    // ================================================================
    // ACCESSO (login o registrazione)
    // ================================================================

    /**
     * Chiede all'utente se vuole effettuare il login o registrarsi.
     * @return true se l'accesso ha avuto successo
     */
    private boolean gestioneAccesso() {
        System.out.println("\n" + SEP);
        System.out.println("  ACCESSO FRUITORE");
        System.out.println(SEP);
        System.out.println("  1. Login  (account esistente)");
        System.out.println("  2. Registrazione  (nuovo account)");
        System.out.println("  0. Torna al menu principale");
        System.out.println();
        System.out.print("  Scelta: ");
        String scelta = scanner.nextLine().trim();
        return switch (scelta) {
            case "1" -> gestioneLogin();
            case "2" -> gestioneRegistrazione();
            default  -> false;
        };
    }

    private boolean gestioneLogin() {
        System.out.println("\n" + SEP);
        System.out.println("  LOGIN FRUITORE");
        System.out.println(SEP);
        System.out.println("  (digita 'annulla' per tornare indietro)");
        System.out.println();
        for (int t = 0; t < 3; t++) {
            System.out.print("  Username: ");
            String username = scanner.nextLine().trim();
            if (username.equalsIgnoreCase("annulla")) return false;
            System.out.print("  Password: ");
            String password = scanner.nextLine().trim();
            if (controller.login(username, password)) {
                int nNotifiche = controller.getNotifiche().size();
                System.out.println("\n  Benvenuto, " + username + "!"
                        + (nNotifiche > 0 ? "  [" + nNotifiche + " notifica/e non letta/e]" : ""));
                return true;
            }
            int rimasti = 2 - t;
            stampaErrore("Credenziali non valide."
                    + (rimasti > 0 ? " Tentativi rimasti: " + rimasti : ""));
            if (rimasti > 0) System.out.println();
        }
        System.out.println("\n  Accesso bloccato: numero massimo di tentativi raggiunto.");
        return false;
    }

    private boolean gestioneRegistrazione() {
        System.out.println("\n" + SEP);
        System.out.println("  REGISTRAZIONE NUOVO FRUITORE");
        System.out.println(SEP);
        System.out.println("  (digita 'annulla' per tornare indietro)");
        System.out.println();
        System.out.print("  Scegli username: ");
        String username = scanner.nextLine().trim();
        if (username.equalsIgnoreCase("annulla")) return false;
        System.out.print("  Scegli password: ");
        String password = scanner.nextLine().trim();
        if (password.equalsIgnoreCase("annulla")) return false;
        System.out.print("  Conferma password: ");
        String conferma = scanner.nextLine().trim();
        if (!password.equals(conferma)) {
            stampaErrore("Le password non coincidono.");
            return false;
        }
        String err = controller.registra(username, password);
        if (!err.isEmpty()) {
            stampaErrore(err);
            return false;
        }
        System.out.println("\n  Registrazione completata. Benvenuto, " + username + "!");
        return true;
    }

    // ================================================================
    // MENU PRINCIPALE FRUITORE
    // ================================================================

    private boolean menuPrincipale() {
        while (true) {
            int nNotifiche = controller.getNotifiche().size();
            System.out.println("\n" + SEP);
            System.out.println("  MENU FRUITORE  [" + controller.getFruitoreCorrente().getUsername() + "]");
            System.out.println(SEP);
            System.out.println("  1. Visualizza bacheca");
            System.out.println("  2. Aderisci a una proposta");
            System.out.println("  3. Spazio personale"
                    + (nNotifiche > 0 ? "  [" + nNotifiche + " notifica/e]" : ""));
            System.out.println("  0. Logout");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> visualizzaBacheca();
                case "2" -> aderisciAProposta();
                case "3" -> spazioPersonale();
                case "0" -> { return false; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // BACHECA
    // ================================================================

    private void visualizzaBacheca() {
        System.out.println("\n" + SEP);
        System.out.println("  BACHECA  -  PROPOSTE APERTE");
        System.out.println(SEP);

        List<String> categorie = controller.getCategorieConProposte();
        if (categorie.isEmpty()) {
            System.out.println("\n  (Nessuna proposta aperta in bacheca)");
            premInvio();
            return;
        }

        for (String nomeCategoria : categorie) {
            List<Proposta> perCat = controller.getBachecaPerCategoria(nomeCategoria);
            System.out.println("\n  CATEGORIA: " + nomeCategoria.toUpperCase()
                    + "  (" + perCat.size() + " proposta/e)");
            System.out.println(SEP2);
            for (Proposta p : perCat) {
                stampaBloccoPropostaBacheca(p);
            }
        }

        System.out.println("\n  Totale proposte aperte: " + controller.getBacheca().size());
        premInvio();
    }

    private void stampaBloccoPropostaBacheca(Proposta p) {
        String titolo = p.getValore("Titolo");
        String termineFmt = p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE);
        boolean iscritto = controller.isIscritto(p.getId());
        int iscritti = p.getAderenti().size();
        int maxPart  = p.getNumeroMaxPartecipanti();
        String maxStr = maxPart < 0 ? "N/D" : String.valueOf(maxPart);

        System.out.println();
        System.out.println("    [ID " + p.getId() + "]  \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\""
                + (iscritto ? "  [ISCRITTO]" : ""));
        System.out.println("    Iscrizioni: " + iscritti + "/" + maxStr
                + "   Termine iscrizione: " + termineFmt);

        // Stampa i principali campi informativi
        String[] campiDaStampare = {"Data", "Ora", "Luogo", "Quota individuale",
                "Data conclusiva", "Durata", "Ora conclusiva", "Compreso nella quota", "Note"};
        for (String campo : campiDaStampare) {
            String val = p.getValore(campo);
            if (!val.isBlank()) System.out.println("    " + campo + ": " + val);
        }
        // Campi specifici non previsti sopra
        for (String nome : p.getValori().keySet()) {
            boolean giaMostrato = false;
            for (String c : campiDaStampare) if (c.equalsIgnoreCase(nome)) { giaMostrato = true; break; }
            if (!giaMostrato && !nome.equalsIgnoreCase("Titolo")
                    && !nome.equalsIgnoreCase(Proposta.CAMPO_TERMINE_ISCRIZIONE)
                    && !nome.equalsIgnoreCase(Proposta.CAMPO_NUM_PARTECIPANTI)) {
                String val = p.getValore(nome);
                if (!val.isBlank()) System.out.println("    " + nome + ": " + val);
            }
        }
    }

    // ================================================================
    // ISCRIZIONE
    // ================================================================

    private void aderisciAProposta() {
        List<Proposta> bacheca = controller.getBacheca();
        if (bacheca.isEmpty()) {
            stampaErrore("Nessuna proposta aperta in bacheca al momento.");
            return;
        }

        // Mostra solo le proposte a cui il fruitore non e' ancora iscritto
        List<Proposta> disponibili = bacheca.stream()
                .filter(p -> !controller.isIscritto(p.getId()))
                .collect(Collectors.toList());

        if (disponibili.isEmpty()) {
            System.out.println("\n  Sei gia' iscritto a tutte le proposte disponibili.");
            premInvio();
            return;
        }

        System.out.println("\n" + SEP);
        System.out.println("  ADERISCI A UNA PROPOSTA");
        System.out.println(SEP);
        System.out.println("  Proposte a cui puoi iscriverti:");
        for (Proposta p : disponibili) {
            String titolo = p.getValore("Titolo");
            int numMax = p.getNumeroMaxPartecipanti();
            String postiStr = numMax < 0
                    ? "N/D"
                    : String.valueOf(numMax - p.getAderenti().size());
            System.out.println("    [ID " + p.getId() + "]"
                    + "  " + p.getNomeCategoria()
                    + "  \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\""
                    + "  Posti rimasti: " + postiStr
                    + "  Termine: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE));
        }

        System.out.print("\n  ID della proposta a cui vuoi aderire (0 per annullare): ");
        String input = scanner.nextLine().trim();
        if (input.equals("0")) return;

        try {
            int id = Integer.parseInt(input);
            String err = controller.aderisci(id);
            if (err.isEmpty()) {
                Proposta p = controller.getPropostaAperta(id);
                String titolo = p != null ? p.getValore("Titolo") : "ID " + id;
                System.out.println("\n  Iscrizione a \"" + titolo + "\" completata.");
            } else {
                stampaErrore(err);
            }
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido: " + input);
        }
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
                for (Notifica n : notifiche) {
                    System.out.println("\n  [ID " + n.getId() + "]  " + n.toString());
                }
            }

            System.out.println();
            System.out.println("  c. Cancella una notifica");
            System.out.println("  t. Cancella tutte le notifiche");
            System.out.println("  0. Torna al menu principale");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "c" -> cancellaNotifica(notifiche);
                case "t" -> cancellaAllNotifiche(notifiche);
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void cancellaNotifica(List<Notifica> notifiche) {
        if (notifiche.isEmpty()) { stampaErrore("Nessuna notifica da cancellare."); return; }
        System.out.print("  ID della notifica da cancellare: ");
        String input = scanner.nextLine().trim();
        try {
            int id = Integer.parseInt(input);
            String err = controller.cancellaNotifica(id);
            if (err.isEmpty()) System.out.println("  Notifica [ID " + id + "] cancellata.");
            else stampaErrore(err);
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido: " + input);
        }
    }

    private void cancellaAllNotifiche(List<Notifica> notifiche) {
        if (notifiche.isEmpty()) { stampaErrore("Nessuna notifica da cancellare."); return; }
        System.out.print("  Cancellare tutte le " + notifiche.size() + " notifiche? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }
        String err = controller.cancellaAllNotifiche();
        if (err.isEmpty()) System.out.println("  Tutte le notifiche cancellate.");
        else stampaErrore(err);
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
        System.out.println("  EasyEvent  -  Front-end Fruitore  (Versione 3)");
        System.out.println(SEP);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Ingegneria del Software  -  A.A. 2025-2026");
        System.out.println(SEP);
    }
}
