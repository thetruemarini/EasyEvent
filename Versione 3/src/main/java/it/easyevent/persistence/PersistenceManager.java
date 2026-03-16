package it.easyevent.persistence;

import it.easyevent.model.AppData;
import it.easyevent.model.Campo;
import it.easyevent.model.Categoria;
import it.easyevent.model.Configuratore;
import it.easyevent.model.Fruitore;
import it.easyevent.model.Notifica;
import it.easyevent.model.Proposta;
import it.easyevent.model.StatoProposta;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestore della persistenza su file JSON (Versione 3).
 * Estende la V2 aggiungendo:
 *   - serializzazione/deserializzazione dei fruitori (con notifiche)
 *   - archivio proposte (tutte gli stati; in V2 era "bacheca" solo APERTE)
 *   - aderenti e storicoStati per ogni proposta
 *   - prossimoIdNotifica
 *
 * Compatibilita' retroattiva: legge file V2 ("bacheca" invece di "archivio");
 * le proposte V2 avranno aderenti e storico vuoti.
 *
 * Struttura del file JSON V3:
 * {
 *   "prossimoIdProposta": N,
 *   "prossimoIdNotifica": N,
 *   "configuratori": [...],
 *   "fruitori": [
 *     { "username": "alice", "password": "alice123",
 *       "notifiche": [{"id":1,"testo":"...","dataCreazione":"09/03/2026"}] }
 *   ],
 *   "campiBase": [...],
 *   "campiComuni": [...],
 *   "categorie": [...],
 *   "archivio": [
 *     {
 *       "id": 1, "nomeCategoria": "sport", "usernameCreatore": "mario",
 *       "stato": "CONFERMATA", "dataPubblicazione": "01/03/2026",
 *       "storicoStati": [
 *         {"stato":"APERTA","data":"01/03/2026"},
 *         {"stato":"CONFERMATA","data":"10/03/2026"}
 *       ],
 *       "aderenti": ["alice","bob"],
 *       "campi": [{"nome":"Titolo","obbligatorio":true,"valore":"Gara ciclistica"}]
 *     }
 *   ]
 * }
 */
public class PersistenceManager {

    private final String dataFilePath;

    public PersistenceManager(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank())
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");
        this.dataFilePath = dataFilePath;
    }

    // ================================================================
    // SALVATAGGIO
    // ================================================================

    public void salva(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non puo' essere null.");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        sb.append("  \"prossimoIdProposta\": ").append(data.getProssimoIdProposta()).append(",\n");
        sb.append("  \"prossimoIdNotifica\": ").append(data.getProssimoIdNotifica()).append(",\n");

        // Configuratori
        sb.append("  \"configuratori\": [\n");
        List<Configuratore> confs = data.getConfiguratori();
        for (int i = 0; i < confs.size(); i++) {
            Configuratore c = confs.get(i);
            sb.append("    {");
            sb.append("\"username\": ").append(jsonStr(c.getUsername())).append(", ");
            sb.append("\"password\": ").append(jsonStr(c.getPassword())).append(", ");
            sb.append("\"primoAccesso\": ").append(c.isPrimoAccesso());
            sb.append("}");
            if (i < confs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Fruitori (V3)
        sb.append("  \"fruitori\": [\n");
        List<Fruitore> fruitori = data.getFruitori();
        for (int i = 0; i < fruitori.size(); i++) {
            sb.append(serializzaFruitore(fruitori.get(i), "    "));
            if (i < fruitori.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Campi base
        sb.append("  \"campiBase\": [\n");
        List<Campo> cb = data.getCampiBase();
        for (int i = 0; i < cb.size(); i++) {
            sb.append("    ").append(serializzaCampo(cb.get(i)));
            if (i < cb.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Campi comuni
        sb.append("  \"campiComuni\": [\n");
        List<Campo> cc = data.getCampiComuni();
        for (int i = 0; i < cc.size(); i++) {
            sb.append("    ").append(serializzaCampo(cc.get(i)));
            if (i < cc.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Categorie
        sb.append("  \"categorie\": [\n");
        List<Categoria> cats = data.getCategorie();
        for (int i = 0; i < cats.size(); i++) {
            Categoria cat = cats.get(i);
            sb.append("    {\n");
            sb.append("      \"nome\": ").append(jsonStr(cat.getNome())).append(",\n");
            sb.append("      \"campiSpecifici\": [\n");
            List<Campo> cs = cat.getCampiSpecifici();
            for (int j = 0; j < cs.size(); j++) {
                sb.append("        ").append(serializzaCampo(cs.get(j)));
                if (j < cs.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }");
            if (i < cats.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Archivio (tutte le proposte pubblicate - V3)
        sb.append("  \"archivio\": [\n");
        List<Proposta> proposte = data.getArchivio();
        for (int i = 0; i < proposte.size(); i++) {
            sb.append(serializzaProposta(proposte.get(i), "    "));
            if (i < proposte.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}\n");

        Path path = Paths.get(dataFilePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString());
    }

    // ================================================================
    // CARICAMENTO
    // ================================================================

    public boolean carica(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non puo' essere null.");

        Path path = Paths.get(dataFilePath);
        if (!Files.exists(path)) return false;

        String json = Files.readString(path);

        int prossimoId = extractIntValue(json, "prossimoIdProposta");
        if (prossimoId > 0) data.setProssimoIdProposta(prossimoId);

        int prossimoIdNotifica = extractIntValue(json, "prossimoIdNotifica");
        if (prossimoIdNotifica > 0) data.setProssimoIdNotifica(prossimoIdNotifica);

        data.setConfiguratori(parseConfiguratori(json));
        data.setFruitori(parseFruitori(json));          // V3

        // Ricalcola prossimoIdNotifica come salvaguardia: se il campo mancava nel JSON
        // (es. file V2 migrato o file corrotto), il contatore resterebbe a 1 e le nuove
        // notifiche generate da aggiornaTransizioni() colliderebbero con quelle esistenti.
        // Logica analoga a setArchivio() per prossimoIdProposta.
        int maxIdNotifica = data.getFruitori().stream()
                .flatMap(f -> f.getNotifiche().stream())
                .mapToInt(it.easyevent.model.Notifica::getId)
                .max().orElse(0);
        if (maxIdNotifica >= data.getProssimoIdNotifica()) {
            data.setProssimoIdNotifica(maxIdNotifica + 1);
        }
        data.setCampiBase(parseCampi(json, "campiBase"));
        data.setCampiComuni(parseCampi(json, "campiComuni"));
        data.setCategorie(parseCategorie(json));

        // Prova prima "archivio" (V3), poi "bacheca" (compat. V2)
        List<Proposta> archivio = parseArchivio(json, "archivio");
        if (archivio.isEmpty()) archivio = parseArchivio(json, "bacheca");
        // Avvisa se il file contiene proposte RITIRATE (producibili solo da V4):
        // vengono caricate correttamente ma non sono gestibili da questa versione.
        long nRitirate = archivio.stream()
                .filter(p -> p.getStato() == StatoProposta.RITIRATA)
                .count();
        if (nRitirate > 0) {
            System.err.println("[PersistenceManager] Attenzione: " + nRitirate
                + " proposta/e in stato RITIRATA trovata/e nel file di dati."
                + " Il ritiro e' una funzionalita' della Versione 4:"
                + " queste proposte saranno visualizzate in archivio ma non modificabili.");
        }
        if (!archivio.isEmpty()) data.setArchivio(archivio);

        return true;
    }

    // ================================================================
    // SERIALIZZAZIONE HELPERS
    // ================================================================

    private String serializzaCampo(Campo c) {
        return "{\"nome\": " + jsonStr(c.getNome())
                + ", \"obbligatorio\": " + c.isObbligatorio()
                + ", \"tipo\": " + jsonStr(c.getTipo().name()) + "}";
    }

    private String serializzaFruitore(Fruitore f, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"username\": ").append(jsonStr(f.getUsername())).append(",\n");
        sb.append(indent).append("  \"password\": ").append(jsonStr(f.getPassword())).append(",\n");
        sb.append(indent).append("  \"notifiche\": [\n");
        List<Notifica> notifiche = f.getNotifiche();
        for (int i = 0; i < notifiche.size(); i++) {
            Notifica n = notifiche.get(i);
            sb.append(indent).append("    {");
            sb.append("\"id\": ").append(n.getId()).append(", ");
            sb.append("\"testo\": ").append(jsonStr(n.getTesto())).append(", ");
            sb.append("\"dataCreazione\": ").append(jsonStr(n.getDataCreazione().format(Notifica.DATE_FORMAT)));
            sb.append("}");
            if (i < notifiche.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("  ]\n");
        sb.append(indent).append("}");
        return sb.toString();
    }

    private String serializzaProposta(Proposta p, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"id\": ").append(p.getId()).append(",\n");
        sb.append(indent).append("  \"nomeCategoria\": ").append(jsonStr(p.getNomeCategoria())).append(",\n");
        sb.append(indent).append("  \"usernameCreatore\": ").append(jsonStr(p.getUsernameCreatore())).append(",\n");
        sb.append(indent).append("  \"stato\": ").append(jsonStr(p.getStato().name())).append(",\n");
        String dataPub = p.getDataPubblicazione() != null
                ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "";
        sb.append(indent).append("  \"dataPubblicazione\": ").append(jsonStr(dataPub)).append(",\n");

        // Storico stati (V3)
        sb.append(indent).append("  \"storicoStati\": [\n");
        List<Proposta.CambioStato> storico = p.getStoricoStati();
        for (int i = 0; i < storico.size(); i++) {
            Proposta.CambioStato cs = storico.get(i);
            sb.append(indent).append("    {");
            sb.append("\"stato\": ").append(jsonStr(cs.stato.name())).append(", ");
            sb.append("\"data\": ").append(jsonStr(cs.data.format(Proposta.DATE_FORMAT)));
            sb.append("}");
            if (i < storico.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("  ],\n");

        // Aderenti (V3)
        sb.append(indent).append("  \"aderenti\": [");
        List<String> aderenti = p.getAderenti();
        for (int i = 0; i < aderenti.size(); i++) {
            sb.append(jsonStr(aderenti.get(i)));
            if (i < aderenti.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        // Campi (snapshot + valori)
        sb.append(indent).append("  \"campi\": [\n");
        LinkedHashMap<String, Boolean> snapshot = p.getCampiSnapshot();
        Map<String, String> valori = p.getValori();
        List<String> nomi = new ArrayList<>(snapshot.keySet());
        for (int i = 0; i < nomi.size(); i++) {
            String nome = nomi.get(i);
            boolean ob  = snapshot.get(nome);
            String val  = valori.getOrDefault(nome, "");
            sb.append(indent).append("    {");
            sb.append("\"nome\": ").append(jsonStr(nome)).append(", ");
            sb.append("\"obbligatorio\": ").append(ob).append(", ");
            sb.append("\"valore\": ").append(jsonStr(val));
            sb.append("}");
            if (i < nomi.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(indent).append("  ]\n");
        sb.append(indent).append("}");
        return sb.toString();
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ================================================================
    // PARSING HELPERS
    // ================================================================

    private List<Configuratore> parseConfiguratori(String json) {
        List<Configuratore> result = new ArrayList<>();
        String section = extractArraySection(json, "configuratori");
        if (section == null || section.isBlank()) return result;
        for (String obj : splitJsonObjects(section)) {
            String  username     = extractStringValue(obj, "username");
            String  password     = extractStringValue(obj, "password");
            boolean primoAccesso = extractBoolValue(obj, "primoAccesso");
            if (username != null && password != null)
                result.add(new Configuratore(username, password, primoAccesso));
        }
        return result;
    }

    private List<Fruitore> parseFruitori(String json) {
        List<Fruitore> result = new ArrayList<>();
        String section = extractArraySection(json, "fruitori");
        if (section == null || section.isBlank()) return result;
        for (String obj : splitJsonObjects(section)) {
            String username = extractStringValue(obj, "username");
            String password = extractStringValue(obj, "password");
            if (username == null || password == null) continue;
            List<Notifica> notifiche = parseNotifiche(obj);
            result.add(new Fruitore(username, password, notifiche));
        }
        return result;
    }

    private List<Notifica> parseNotifiche(String json) {
        List<Notifica> result = new ArrayList<>();
        String section = extractArraySection(json, "notifiche");
        if (section == null || section.isBlank()) return result;
        for (String obj : splitJsonObjects(section)) {
            int    id    = extractIntValue(obj, "id");
            String testo = extractStringValue(obj, "testo");
            String dataStr = extractStringValue(obj, "dataCreazione");
            // id < 0 indica che extractIntValue non ha trovato un valore valido:
            // saltare la notifica e' piu' sicuro che assegnarle id=0, perche'
            // cancellaNotifica(0) usa removeIf(id==0) e rimuoverebbe TUTTE le
            // notifiche con id=0 con una sola chiamata.
            if (id < 0 || testo == null || dataStr == null) continue;
            try {
                LocalDate data = LocalDate.parse(dataStr.trim(), Notifica.DATE_FORMAT);
                result.add(new Notifica(id, testo, data));
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<Campo> parseCampi(String json, String arrayName) {
        List<Campo> result = new ArrayList<>();
        String section = extractArraySection(json, arrayName);
        if (section == null || section.isBlank()) return result;
        for (String obj : splitJsonObjects(section)) {
            String  nome    = extractStringValue(obj, "nome");
            boolean ob      = extractBoolValue(obj, "obbligatorio");
            String  tipoStr = extractStringValue(obj, "tipo");
            if (nome != null && tipoStr != null) {
                try { result.add(new Campo(nome, ob, Campo.TipoCampo.valueOf(tipoStr))); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private List<Categoria> parseCategorie(String json) {
        List<Categoria> result = new ArrayList<>();
        String section = extractArraySection(json, "categorie");
        if (section == null || section.isBlank()) return result;
        for (String obj : splitJsonObjects(section)) {
            String nome = extractStringValue(obj, "nome");
            if (nome == null) continue;
            Categoria cat = new Categoria(nome);
            String csSection = extractArraySection(obj, "campiSpecifici");
            if (csSection != null && !csSection.isBlank()) {
                for (String campoObj : splitJsonObjects(csSection)) {
                    String  cn      = extractStringValue(campoObj, "nome");
                    boolean ob      = extractBoolValue(campoObj, "obbligatorio");
                    String  tipoStr = extractStringValue(campoObj, "tipo");
                    if (cn != null && tipoStr != null) {
                        try { cat.aggiungiCampoSpecifico(new Campo(cn, ob, Campo.TipoCampo.valueOf(tipoStr))); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            result.add(cat);
        }
        return result;
    }

    private List<Proposta> parseArchivio(String json, String arrayName) {
        List<Proposta> result = new ArrayList<>();
        String section = extractArraySection(json, arrayName);
        if (section == null || section.isBlank()) return result;

        for (String obj : splitJsonObjects(section)) {
            int    id               = extractIntValue(obj, "id");
            String nomeCategoria    = extractStringValue(obj, "nomeCategoria");
            String usernameCreatore = extractStringValue(obj, "usernameCreatore");
            String statoStr         = extractStringValue(obj, "stato");
            String dataPubStr       = extractStringValue(obj, "dataPubblicazione");

            if (nomeCategoria == null || usernameCreatore == null || statoStr == null) continue;

            StatoProposta stato;
            try { stato = StatoProposta.valueOf(statoStr); }
            catch (IllegalArgumentException e) { continue; }

            // BOZZA e VALIDA sono stati interni di sessione: non vengono mai persistiti
            // in un file corretto. Se compaiono nel JSON (file corrotto o manomesso),
            // caricarli nell'archivio violerebbe l'invariante di AppData. Si scartano.
            if (stato == StatoProposta.BOZZA || stato == StatoProposta.VALIDA) {
                System.err.println("[PersistenceManager] Proposta con stato interno '"
                        + stato + "' trovata in archivio e scartata (non dovrebbe essere persistita).");
                continue;
            }

            LocalDate dataPub = null;
            if (dataPubStr != null && !dataPubStr.isBlank()) {
                try { dataPub = LocalDate.parse(dataPubStr.trim(), Proposta.DATE_FORMAT); }
                catch (Exception ignored) {}
            }

            // Storico stati (V3)
            List<Proposta.CambioStato> storico = new ArrayList<>();
            String storicoSection = extractArraySection(obj, "storicoStati");
            if (storicoSection != null && !storicoSection.isBlank()) {
                for (String csObj : splitJsonObjects(storicoSection)) {
                    String    csStatoStr = extractStringValue(csObj, "stato");
                    String    csDataStr  = extractStringValue(csObj, "data");
                    if (csStatoStr == null || csDataStr == null) continue;
                    try {
                        StatoProposta csStato = StatoProposta.valueOf(csStatoStr);
                        LocalDate     csData  = LocalDate.parse(csDataStr.trim(), Proposta.DATE_FORMAT);
                        storico.add(new Proposta.CambioStato(csStato, csData));
                    } catch (Exception ignored) {}
                }
            }

            // Aderenti (V3)
            // Parsing corretto: NON si usa split(",") perché uno username potrebbe
            // contenere una virgola. Si scansiona carattere per carattere rispettando
            // le virgolette e il conteggio pari/dispari di backslash consecutivi.
            List<String> aderenti = new ArrayList<>();
            String aderSection = extractArraySection(obj, "aderenti");
            if (aderSection != null && !aderSection.isBlank()) {
                int ai = 0;
                while (ai < aderSection.length()) {
                    int qStart = aderSection.indexOf('"', ai);
                    if (qStart < 0) break;
                    int qEnd = qStart + 1;
                    while (qEnd < aderSection.length()) {
                        if (aderSection.charAt(qEnd) == '"') {
                            // Conta backslash consecutivi: se pari -> virgoletta di chiusura
                            int bs = 0, check = qEnd - 1;
                            while (check >= qStart + 1 && aderSection.charAt(check) == '\\') { bs++; check--; }
                            if (bs % 2 == 0) break;
                        }
                        qEnd++;
                    }
                    if (qEnd < aderSection.length()) {
                        String uname = aderSection.substring(qStart + 1, qEnd)
                                .replace("\\\"", "\"").replace("\\\\", "\\");
                        if (!uname.isBlank()) aderenti.add(uname);
                    }
                    ai = qEnd + 1;
                }
            }

            // Campi (snapshot + valori)
            LinkedHashMap<String, Boolean> campiSnapshot = new LinkedHashMap<>();
            LinkedHashMap<String, String>  valori        = new LinkedHashMap<>();
            String campiSection = extractArraySection(obj, "campi");
            if (campiSection != null && !campiSection.isBlank()) {
                for (String campoObj : splitJsonObjects(campiSection)) {
                    String  nome = extractStringValue(campoObj, "nome");
                    boolean ob   = extractBoolValue(campoObj, "obbligatorio");
                    String  val  = extractStringValue(campoObj, "valore");
                    if (nome != null) {
                        campiSnapshot.put(nome, ob);
                        valori.put(nome, val != null ? val : "");
                    }
                }
            }

            // Il costruttore ora lancia IllegalArgumentException su dati corrotti
            // (id negativo, nomeCategoria/usernameCreatore null, stato null).
            // Catturiamo l'eccezione, logghiamo il problema e saltiamo la proposta
            // invece di far crashare l'intera procedura di caricamento.
            try {
                result.add(new Proposta(id, nomeCategoria, usernameCreatore,
                        campiSnapshot, valori, stato, dataPub, aderenti, storico));
            } catch (IllegalArgumentException e) {
                System.err.println("[PersistenceManager] Proposta scartata (dati corrotti nel JSON): "
                        + e.getMessage() + " — oggetto: " + obj.substring(0, Math.min(80, obj.length())));
            }
        }
        return result;
    }

    // ================================================================
    // UTILITIES PARSER JSON MINIMALE
    // ================================================================

    /**
     * Cerca la posizione della chiave JSON come PROPRIETA' dell'oggetto,
     * ignorando le occorrenze che compaiono all'interno di valori stringa.
     *
     * Problema risolto: indexOf("\"chiave\"") trova la sequenza ovunque nel JSON,
     * anche dentro un valore come: "Note": "vedi il campo \"data\" qui"
     * In quel caso estrae il valore del campo Note come se fosse "data", corrompendo
     * silenziosamente i dati al caricamento.
     *
     * Soluzione: si scorre il json mantenendo il flag inString (attivato/disattivato
     * dalle virgolette non escaped). Solo fuori dalle stringhe si controlla se la
     * sequenza "\"key\"" e' seguita (dopo spazi opzionali) da ':' — che e' il segnale
     * che si tratta di una chiave JSON reale, non di un valore.
     *
     * @param json      stringa JSON da scorrere
     * @param key       nome della chiave da trovare (senza virgolette)
     * @param fromIndex posizione da cui iniziare la ricerca
     * @return indice dell'apertura '"' della chiave, oppure -1 se non trovata
     */
    private int findKeyPosition(String json, String key, int fromIndex) {
        String search = "\"" + key + "\"";
        int searchLen = search.length();
        boolean inString = false;
        int i = fromIndex;
        while (i < json.length()) {
            char ch = json.charAt(i);
            if (ch == '"') {
                // Conta backslash consecutivi per sapere se la virgoletta e' escaped
                int bs = 0, check = i - 1;
                while (check >= fromIndex && json.charAt(check) == '\\') { bs++; check--; }
                if (bs % 2 == 0) {
                    if (inString) {
                        inString = false;
                        i++;
                        continue;
                    }
                    // Siamo fuori da una stringa: verifica se inizia la sequenza "key"
                    if (i + searchLen <= json.length()
                            && json.substring(i, i + searchLen).equals(search)) {
                        // Verifica che dopo la chiave ci sia ':' (con spazi opzionali)
                        int j = i + searchLen;
                        while (j < json.length() && (json.charAt(j) == ' ' || json.charAt(j) == '\n'
                                || json.charAt(j) == '\r' || json.charAt(j) == '\t')) j++;
                        if (j < json.length() && json.charAt(j) == ':') {
                            return i;  // chiave trovata come proprieta'
                        }
                    }
                    // Non e' la chiave cercata: entra nella stringa
                    inString = true;
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * Estrae il contenuto dell'array JSON associato alla chiave indicata.
     * Usa findKeyPosition per trovare la chiave solo come proprieta' JSON,
     * evitando falsi match dentro valori stringa.
     * Gestisce '[' e ']' dentro valori quotati senza sbilanciarsi.
     * Restituisce null se la chiave non esiste, non e' seguita da '[',
     * o se il JSON e' malformato (array non chiuso).
     */
    private String extractArraySection(String json, String key) {
        int keyIdx = findKeyPosition(json, key, 0);
        if (keyIdx < 0) return null;
        // Cerca '[' dopo la chiave, saltando spazi e il ':'
        int bracketStart = -1;
        for (int i = keyIdx + key.length() + 2; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') { bracketStart = i; break; }
            if (c != ':' && c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
        }
        if (bracketStart < 0) return null;
        int depth = 0, end = -1;   // end = -1 indica array non ancora chiuso
        boolean inString = false;
        for (int i = bracketStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                int bs = 0, check = i - 1;
                while (check >= 0 && json.charAt(check) == '\\') { bs++; check--; }
                if (bs % 2 == 0) inString = !inString;
            } else if (!inString) {
                if (ch == '[') depth++;
                else if (ch == ']') { depth--; if (depth == 0) { end = i; break; } }
            }
        }
        // JSON malformato: ']' di chiusura non trovato -> restituisce null
        // (invece di lanciare StringIndexOutOfBoundsException con substring(x+1, x))
        if (end < 0) return null;
        return json.substring(bracketStart + 1, end).trim();
    }

    /**
     * Suddivide il contenuto di un array JSON in una lista di oggetti JSON ({...}).
     * Gestisce '{' e '}' dentro valori quotati senza spezzare oggetti.
     */
    private List<String> splitJsonObjects(String arrayContent) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (ch == '"') {
                int bs = 0, check = i - 1;
                while (check >= 0 && arrayContent.charAt(check) == '\\') { bs++; check--; }
                if (bs % 2 == 0) inString = !inString;
            } else if (!inString) {
                if (ch == '{') { if (depth == 0) start = i; depth++; }
                else if (ch == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        result.add(arrayContent.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Estrae il valore stringa associato alla chiave JSON indicata.
     * Usa findKeyPosition per evitare falsi match dentro valori stringa.
     * Gestisce virgolette escaped e backslash escaped nel valore.
     */
    private String extractStringValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) return null;
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) return null;
        int quoteStart = obj.indexOf('"', colon + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < obj.length()) {
            if (obj.charAt(quoteEnd) == '"') {
                int bs = 0, check = quoteEnd - 1;
                while (check >= quoteStart + 1 && obj.charAt(check) == '\\') { bs++; check--; }
                if (bs % 2 == 0) break;
            }
            quoteEnd++;
        }
        return obj.substring(quoteStart + 1, quoteEnd)
                  .replace("\\\"", "\"").replace("\\\\", "\\")
                  .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    /**
     * Estrae il valore booleano associato alla chiave JSON indicata.
     * Usa findKeyPosition per evitare falsi match dentro valori stringa.
     */
    private boolean extractBoolValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) return false;
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) return false;
        return obj.substring(colon + 1).trim().startsWith("true");
    }

    /**
     * Estrae il valore intero associato alla chiave JSON indicata.
     * Usa findKeyPosition per evitare falsi match dentro valori stringa.
     */
    private int extractIntValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) return -1;
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) return -1;
        String rest = obj.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (Character.isDigit(c)) num.append(c);
            else if (num.length() > 0) break;
        }
        if (num.length() == 0) return -1;
        try { return Integer.parseInt(num.toString()); }
        catch (NumberFormatException e) { return -1; }
    }

    public String getDataFilePath() { return dataFilePath; }
}
