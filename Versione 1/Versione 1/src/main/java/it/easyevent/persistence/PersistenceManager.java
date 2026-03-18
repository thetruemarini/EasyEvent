package it.easyevent.persistence;

import it.easyevent.model.AppData;
import it.easyevent.model.Campo;
import it.easyevent.model.Categoria;
import it.easyevent.model.Configuratore;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestore della persistenza su file JSON (implementazione manuale senza librerie esterne).
 * Salva e carica lo stato completo dell'applicazione (Versione 1).
 *
 * Il file ha la seguente struttura:
 * {
 *   "configuratori": [...],
 *   "campiBase": [...],
 *   "campiComuni": [...],
 *   "categorie": [...]
 * }
 *
 * Invariante di classe:
 * - dataFilePath != null && !dataFilePath.isBlank()
 */
public class PersistenceManager {

    private final String dataFilePath;

    /**
     * @param dataFilePath percorso del file JSON di persistenza
     * @throws IllegalArgumentException se dataFilePath è null o blank
     */
    public PersistenceManager(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank()) {
            throw new IllegalArgumentException("Il percorso del file non può essere null o vuoto.");
        }
        this.dataFilePath = dataFilePath;
    }

    // ================================================================
    // SALVATAGGIO
    // ================================================================

    /**
     * Salva lo stato corrente di AppData su file JSON.
     *
     * @param data stato dell'applicazione, non null
     * @throws IOException in caso di errore di scrittura
     */
    public void salva(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non può essere null.");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

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
        sb.append("  ]\n");

        sb.append("}\n");

        // Scrittura su file (crea directory se non esiste)
        Path path = Paths.get(dataFilePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, sb.toString());
    }

    // ================================================================
    // CARICAMENTO
    // ================================================================

    /**
     * Carica lo stato dell'applicazione dal file JSON e lo imposta su AppData.
     *
     * @param data istanza di AppData da popolare, non null
     * @return true se il file esiste ed è stato caricato, false se il file non esiste
     * @throws IOException in caso di errore di lettura o parsing
     */
    public boolean carica(AppData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("AppData non può essere null.");

        Path path = Paths.get(dataFilePath);
        if (!Files.exists(path)) {
            return false;
        }

        String json = Files.readString(path);

        // Parse configuratori
        List<Configuratore> configuratori = parseConfiguratori(json);
        data.setConfiguratori(configuratori);

        // Parse campi base
        List<Campo> campiBase = parseCampi(json, "campiBase");
        data.setCampiBase(campiBase);

        // Parse campi comuni
        List<Campo> campiComuni = parseCampi(json, "campiComuni");
        data.setCampiComuni(campiComuni);

        // Parse categorie
        List<Categoria> categorie = parseCategorie(json);
        data.setCategorie(categorie);

        return true;
    }

    // ================================================================
    // HELPERS SERIALIZZAZIONE
    // ================================================================

    private String serializzaCampo(Campo c) {
        return "{\"nome\": " + jsonStr(c.getNome()) +
                ", \"obbligatorio\": " + c.isObbligatorio() +
                ", \"tipo\": " + jsonStr(c.getTipo().name()) + "}";
    }

    private String jsonStr(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ================================================================
    // HELPERS PARSING (parser JSON minimale)
    // ================================================================

    private List<Configuratore> parseConfiguratori(String json) {
        List<Configuratore> result = new ArrayList<>();
        String section = extractArraySection(json, "configuratori");
        if (section == null || section.isBlank()) return result;

        List<String> objects = splitJsonObjects(section);
        for (String obj : objects) {
            String username = extractStringValue(obj, "username");
            String password = extractStringValue(obj, "password");
            boolean primoAccesso = extractBoolValue(obj, "primoAccesso");
            if (username != null && password != null) {
                result.add(new Configuratore(username, password, primoAccesso));
            }
        }
        return result;
    }

    private List<Campo> parseCampi(String json, String arrayName) {
        List<Campo> result = new ArrayList<>();
        String section = extractArraySection(json, arrayName);
        if (section == null || section.isBlank()) return result;

        List<String> objects = splitJsonObjects(section);
        for (String obj : objects) {
            String nome = extractStringValue(obj, "nome");
            boolean obbligatorio = extractBoolValue(obj, "obbligatorio");
            String tipoStr = extractStringValue(obj, "tipo");
            if (nome != null && tipoStr != null) {
                try {
                    Campo.TipoCampo tipo = Campo.TipoCampo.valueOf(tipoStr);
                    result.add(new Campo(nome, obbligatorio, tipo));
                } catch (IllegalArgumentException e) {
                    // tipo non valido, ignora
                }
            }
        }
        return result;
    }

    private List<Categoria> parseCategorie(String json) {
        List<Categoria> result = new ArrayList<>();
        String section = extractArraySection(json, "categorie");
        if (section == null || section.isBlank()) return result;

        // Splitta per oggetti di primo livello (ogni categoria è un oggetto)
        List<String> objects = splitJsonObjects(section);
        for (String obj : objects) {
            String nome = extractStringValue(obj, "nome");
            if (nome == null) continue;
            Categoria cat = new Categoria(nome);
            // Estrai campiSpecifici
            String csSection = extractArraySection(obj, "campiSpecifici");
            if (csSection != null && !csSection.isBlank()) {
                List<String> campiObjs = splitJsonObjects(csSection);
                for (String campoObj : campiObjs) {
                    String cn = extractStringValue(campoObj, "nome");
                    boolean ob = extractBoolValue(campoObj, "obbligatorio");
                    String tipoStr = extractStringValue(campoObj, "tipo");
                    if (cn != null && tipoStr != null) {
                        try {
                            Campo.TipoCampo tipo = Campo.TipoCampo.valueOf(tipoStr);
                            cat.aggiungiCampoSpecifico(new Campo(cn, ob, tipo));
                        } catch (IllegalArgumentException e) {
                            // ignora
                        }
                    }
                }
            }
            result.add(cat);
        }
        return result;
    }

    /**
     * Estrae il contenuto di un array JSON dato il nome della chiave.
     */
    private String extractArraySection(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int bracketStart = json.indexOf('[', keyIdx + search.length());
        if (bracketStart < 0) return null;
        int depth = 0;
        int end = bracketStart;
        for (int i = bracketStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        return json.substring(bracketStart + 1, end).trim();
    }

    /**
     * Splitta una lista JSON di oggetti {...} in stringhe individuali.
     */
    private List<String> splitJsonObjects(String arrayContent) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    /**
     * Estrae il valore stringa di una chiave JSON semplice.
     */
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

    /**
     * Estrae il valore booleano di una chiave JSON semplice.
     */
    private boolean extractBoolValue(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return false;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return false;
        String rest = obj.substring(colon + 1).trim();
        return rest.startsWith("true");
    }

    public String getDataFilePath() {
        return dataFilePath;
    }
}
