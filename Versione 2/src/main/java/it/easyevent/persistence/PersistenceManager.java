package it.easyevent.persistence;

import it.easyevent.model.AppData;
import it.easyevent.model.Campo;
import it.easyevent.model.Categoria;
import it.easyevent.model.Configuratore;
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
 * Gestore della persistenza su file JSON (Versione 2).
 * Estende la V1 aggiungendo la serializzazione/deserializzazione
 * della bacheca (proposte APERTE) e del contatore degli ID.
 *
 * Struttura del file JSON:
 * {
 *   "prossimoIdProposta": 3,
 *   "configuratori": [...],
 *   "campiBase": [...],
 *   "campiComuni": [...],
 *   "categorie": [...],
 *   "bacheca": [
 *     {
 *       "id": 1,
 *       "nomeCategoria": "sport",
 *       "usernameCreatore": "mario",
 *       "stato": "APERTA",
 *       "dataPubblicazione": "20/03/2026",
 *       "campi": [
 *         {"nome": "Titolo", "obbligatorio": true, "valore": "Gara ciclistica"},
 *         ...
 *       ]
 *     }
 *   ]
 * }
 *
 * Invariante di classe:
 *   - dataFilePath != null && !dataFilePath.isBlank()
 */
public class PersistenceManager {

    private final String dataFilePath;

    public PersistenceManager(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank())
            throw new IllegalArgumentException("Il percorso del file non può essere null o vuoto.");
        this.dataFilePath = dataFilePath;
    }

    // ================================================================
    // SALVATAGGIO
    // ================================================================

    /**
     * Salva lo stato corrente di AppData su file JSON (V2: include bacheca).
     *
     * @param data stato dell'applicazione, non null
     * @throws IOException in caso di errore di scrittura
     */
    public void salva(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non può essere null.");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Contatore ID proposte
        sb.append("  \"prossimoIdProposta\": ").append(data.getProssimoIdProposta()).append(",\n");

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

        // Bacheca (proposte APERTE)
        sb.append("  \"bacheca\": [\n");
        List<Proposta> proposte = data.getBacheca();
        for (int i = 0; i < proposte.size(); i++) {
            sb.append(serializzaProposta(proposte.get(i), "    "));
            if (i < proposte.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}\n");

        // Scrittura su file
        Path path = Paths.get(dataFilePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString());
    }

    // ================================================================
    // CARICAMENTO
    // ================================================================

    /**
     * Carica lo stato dell'applicazione dal file JSON e popola AppData.
     * Compatibile con file V1 (senza "bacheca"): la bacheca sarà vuota.
     *
     * @param data istanza di AppData da popolare, non null
     * @return true se il file esiste ed è stato caricato
     * @throws IOException in caso di errore di lettura o parsing
     */
    public boolean carica(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non può essere null.");

        Path path = Paths.get(dataFilePath);
        if (!Files.exists(path)) return false;

        String json = Files.readString(path);

        // Contatore ID proposte (opzionale – presente solo in V2)
        int prossimoId = extractIntValue(json, "prossimoIdProposta");
        if (prossimoId > 0) data.setProssimoIdProposta(prossimoId);

        data.setConfiguratori(parseConfiguratori(json));
        data.setCampiBase(parseCampi(json, "campiBase"));
        data.setCampiComuni(parseCampi(json, "campiComuni"));
        data.setCategorie(parseCategorie(json));

        // Bacheca (solo V2 – se assente il file è V1)
        List<Proposta> bacheca = parseBacheca(json);
        if (!bacheca.isEmpty()) {
            data.setBacheca(bacheca);
        }

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

    /**
     * Serializza una proposta con indentazione specificata.
     */
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
            String  username    = extractStringValue(obj, "username");
            String  password    = extractStringValue(obj, "password");
            boolean primoAccesso = extractBoolValue(obj, "primoAccesso");
            if (username != null && password != null)
                result.add(new Configuratore(username, password, primoAccesso));
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
                try {
                    result.add(new Campo(nome, ob, Campo.TipoCampo.valueOf(tipoStr)));
                } catch (IllegalArgumentException ignored) {}
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
                        try {
                            cat.aggiungiCampoSpecifico(new Campo(cn, ob, Campo.TipoCampo.valueOf(tipoStr)));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            result.add(cat);
        }
        return result;
    }

    /**
     * Deserializza la bacheca dal JSON (V2).
     */
    private List<Proposta> parseBacheca(String json) {
        List<Proposta> result = new ArrayList<>();
        String section = extractArraySection(json, "bacheca");
        if (section == null || section.isBlank()) return result;

        for (String obj : splitJsonObjects(section)) {
            int    id               = extractIntValue(obj, "id");
            if (id < 0) continue;
            String nomeCategoria    = extractStringValue(obj, "nomeCategoria");
            String usernameCreatore = extractStringValue(obj, "usernameCreatore");
            String statoStr         = extractStringValue(obj, "stato");
            String dataPubStr       = extractStringValue(obj, "dataPubblicazione");

            if (nomeCategoria == null || usernameCreatore == null || statoStr == null) continue;

            StatoProposta stato;
            try {
                stato = StatoProposta.valueOf(statoStr);
            } catch (IllegalArgumentException e) { continue; }

            LocalDate dataPub = null;
            if (dataPubStr != null && !dataPubStr.isBlank()) {
                try {
                    dataPub = LocalDate.parse(dataPubStr.trim(), Proposta.DATE_FORMAT);
                } catch (Exception ignored) {}
            }

            // Parsing dei campi (snapshot + valori)
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

            Proposta p = new Proposta(id, nomeCategoria, usernameCreatore,
                    campiSnapshot, valori, stato, dataPub);
            result.add(p);
        }
        return result;
    }

    // ================================================================
    // UTILITIES PARSER JSON MINIMALE
    // ================================================================

    private String extractArraySection(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int bracketStart = json.indexOf('[', keyIdx + search.length());
        if (bracketStart < 0) return null;
        int depth = 0, end = bracketStart;
        for (int i = bracketStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') { depth--; if (depth == 0) { end = i; break; } }
        }
        return json.substring(bracketStart + 1, end).trim();
    }

    private List<String> splitJsonObjects(String arrayContent) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (ch == '{') { if (depth == 0) start = i; depth++; }
            else if (ch == '}') { depth--; if (depth == 0 && start >= 0) { result.add(arrayContent.substring(start, i + 1)); start = -1; } }
        }
        return result;
    }

    private String extractStringValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return null;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int quoteStart = obj.indexOf('"', colon + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < obj.length()) {
            if (obj.charAt(quoteEnd) == '"' && obj.charAt(quoteEnd - 1) != '\\') break;
            quoteEnd++;
        }
        return obj.substring(quoteStart + 1, quoteEnd)
          .replace("\\\"", "\"").replace("\\\\", "\\")
          .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private boolean extractBoolValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return false;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return false;
        return obj.substring(colon + 1).trim().startsWith("true");
    }

    /** Estrae un valore intero. Restituisce -1 se non trovato. */
    private int extractIntValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return -1;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return -1;
        String rest = obj.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (Character.isDigit(c)) num.append(c);
            else if (!num.isEmpty()) break;
        }
        if (num.length() == 0) return -1;
        try { return Integer.parseInt(num.toString()); }
        catch (NumberFormatException e) { return -1; }
    }

    public String getDataFilePath() { return dataFilePath; }
}
