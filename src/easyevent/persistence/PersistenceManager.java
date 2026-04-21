package easyevent.persistence;

import easyevent.model.AppData;
import easyevent.model.Campo;
import easyevent.model.Categoria;
import easyevent.model.Configuratore;
import easyevent.model.Fruitore;
import easyevent.model.Notifica;
import easyevent.model.Proposta;
import easyevent.model.StatoProposta;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Gestore della persistenza su file JSON (Versione 5).
 * Identico alla V4 nel formato; piena compatibilità con file V4/V3/V2/V1.
 * La V5 non introduce nuovi campi nel file di persistenza.
 *
 * Invariante: dataFilePath != null && !dataFilePath.isBlank()
 */
public class PersistenceManager {

    private final String dataFilePath;

    public PersistenceManager(String dataFilePath) {
        if (dataFilePath == null || dataFilePath.isBlank()) {
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");
        }
        this.dataFilePath = dataFilePath;
    }

    // ================================================================
    // SALVATAGGIO
    // ================================================================
    public void salva(AppData data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("AppData non puo' essere null.");
        }

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
            if (i < confs.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Fruitori
        sb.append("  \"fruitori\": [\n");
        List<Fruitore> fruitori = data.getFruitori();
        for (int i = 0; i < fruitori.size(); i++) {
            sb.append(serializzaFruitore(fruitori.get(i), "    "));
            if (i < fruitori.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Campi base
        sb.append("  \"campiBase\": [\n");
        List<Campo> cb = data.getCampiBase();
        for (int i = 0; i < cb.size(); i++) {
            sb.append("    ").append(serializzaCampo(cb.get(i)));
            if (i < cb.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Campi comuni
        sb.append("  \"campiComuni\": [\n");
        List<Campo> cc = data.getCampiComuni();
        for (int i = 0; i < cc.size(); i++) {
            sb.append("    ").append(serializzaCampo(cc.get(i)));
            if (i < cc.size() - 1) {
                sb.append(",");
            }
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
                if (j < cs.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("      ]\n");
            sb.append("    }");
            if (i < cats.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Archivio
        sb.append("  \"archivio\": [\n");
        List<Proposta> proposte = data.getArchivio();
        for (int i = 0; i < proposte.size(); i++) {
            sb.append(serializzaProposta(proposte.get(i), "    "));
            if (i < proposte.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Path path = Paths.get(dataFilePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, sb.toString());
    }

    // ================================================================
    // CARICAMENTO
    // ================================================================
    public boolean carica(AppData data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("AppData non puo' essere null.");
        }
        Path path = Paths.get(dataFilePath);
        if (!Files.exists(path)) {
            return false;
        }
        String json = Files.readString(path);

        int prossimoId = extractIntValue(json, "prossimoIdProposta");
        if (prossimoId > 0) {
            data.setProssimoIdProposta(prossimoId);
        }
        int prossimoIdNotifica = extractIntValue(json, "prossimoIdNotifica");
        if (prossimoIdNotifica > 0) {
            data.setProssimoIdNotifica(prossimoIdNotifica);
        }

        data.setConfiguratori(parseConfiguratori(json));
        data.setFruitori(parseFruitori(json));

        int maxIdNotifica = data.getFruitori().stream()
                .flatMap(f -> f.getNotifiche().stream())
                .mapToInt(Notifica::getId).max().orElse(0);
        if (maxIdNotifica >= data.getProssimoIdNotifica()) {
            data.setProssimoIdNotifica(maxIdNotifica + 1);
        }

        data.setCampiBase(parseCampi(json, "campiBase"));
        data.setCampiComuni(parseCampi(json, "campiComuni"));
        data.setCategorie(parseCategorie(json));

        List<Proposta> archivio = parseArchivio(json, "archivio");
        if (archivio.isEmpty()) {
            archivio = parseArchivio(json, "bacheca");
        }
        if (!archivio.isEmpty()) {
            data.setArchivio(archivio);
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
            if (i < notifiche.size() - 1) {
                sb.append(",");
            }
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

        sb.append(indent).append("  \"storicoStati\": [\n");
        List<Proposta.CambioStato> storico = p.getStoricoStati();
        for (int i = 0; i < storico.size(); i++) {
            Proposta.CambioStato cs = storico.get(i);
            sb.append(indent).append("    {");
            sb.append("\"stato\": ").append(jsonStr(cs.stato.name())).append(", ");
            sb.append("\"data\": ").append(jsonStr(cs.data.format(Proposta.DATE_FORMAT)));
            sb.append("}");
            if (i < storico.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("  ],\n");

        sb.append(indent).append("  \"aderenti\": [");
        List<String> aderenti = p.getAderenti();
        for (int i = 0; i < aderenti.size(); i++) {
            sb.append(jsonStr(aderenti.get(i)));
            if (i < aderenti.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("],\n");

        sb.append(indent).append("  \"campi\": [\n");
        LinkedHashMap<String, Boolean> snapshot = p.getCampiSnapshot();
        Map<String, String> valori = p.getValori();
        List<String> nomi = new ArrayList<>(snapshot.keySet());
        for (int i = 0; i < nomi.size(); i++) {
            String nome = nomi.get(i);
            boolean ob = snapshot.get(nome);
            String val = valori.getOrDefault(nome, "");
            sb.append(indent).append("    {");
            sb.append("\"nome\": ").append(jsonStr(nome)).append(", ");
            sb.append("\"obbligatorio\": ").append(ob).append(", ");
            sb.append("\"valore\": ").append(jsonStr(val));
            sb.append("}");
            if (i < nomi.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("  ]\n");
        sb.append(indent).append("}");
        return sb.toString();
    }

    private String jsonStr(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ================================================================
    // PARSING HELPERS
    // ================================================================
    private List<Configuratore> parseConfiguratori(String json) {
        List<Configuratore> result = new ArrayList<>();
        String section = extractArraySection(json, "configuratori");
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            String u = extractStringValue(obj, "username");
            String p = extractStringValue(obj, "password");
            boolean pa = extractBoolValue(obj, "primoAccesso");
            if (u != null && p != null) {
                result.add(new Configuratore(u, p, pa));
            }
        }
        return result;
    }

    private List<Fruitore> parseFruitori(String json) {
        List<Fruitore> result = new ArrayList<>();
        String section = extractArraySection(json, "fruitori");
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            String u = extractStringValue(obj, "username");
            String p = extractStringValue(obj, "password");
            if (u == null || p == null) {
                continue;
            }
            result.add(new Fruitore(u, p, parseNotifiche(obj)));
        }
        return result;
    }

    private List<Notifica> parseNotifiche(String json) {
        List<Notifica> result = new ArrayList<>();
        String section = extractArraySection(json, "notifiche");
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            int id = extractIntValue(obj, "id");
            String testo = extractStringValue(obj, "testo");
            String dataStr = extractStringValue(obj, "dataCreazione");
            if (id < 0 || testo == null || dataStr == null) {
                continue;
            }
            try {
                result.add(new Notifica(id, testo, LocalDate.parse(dataStr.trim(), Notifica.DATE_FORMAT)));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private List<Campo> parseCampi(String json, String arrayName) {
        List<Campo> result = new ArrayList<>();
        String section = extractArraySection(json, arrayName);
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            String nome = extractStringValue(obj, "nome");
            boolean ob = extractBoolValue(obj, "obbligatorio");
            String tipoStr = extractStringValue(obj, "tipo");
            if (nome != null && tipoStr != null) {
                try {
                    result.add(new Campo(nome, ob, Campo.TipoCampo.valueOf(tipoStr)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result;
    }

    private List<Categoria> parseCategorie(String json) {
        List<Categoria> result = new ArrayList<>();
        String section = extractArraySection(json, "categorie");
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            String nome = extractStringValue(obj, "nome");
            if (nome == null) {
                continue;
            }
            Categoria cat = new Categoria(nome);
            String csSection = extractArraySection(obj, "campiSpecifici");
            if (csSection != null && !csSection.isBlank()) {
                for (String campoObj : splitJsonObjects(csSection)) {
                    String cn = extractStringValue(campoObj, "nome");
                    boolean ob = extractBoolValue(campoObj, "obbligatorio");
                    String ts = extractStringValue(campoObj, "tipo");
                    if (cn != null && ts != null) {
                        try {
                            cat.aggiungiCampoSpecifico(new Campo(cn, ob, Campo.TipoCampo.valueOf(ts)));
                        } catch (IllegalArgumentException ignored) {
                        }
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
        if (section == null || section.isBlank()) {
            return result;
        }
        for (String obj : splitJsonObjects(section)) {
            int id = extractIntValue(obj, "id");
            String nomeCategoria = extractStringValue(obj, "nomeCategoria");
            String usernameCreatore = extractStringValue(obj, "usernameCreatore");
            String statoStr = extractStringValue(obj, "stato");
            String dataPubStr = extractStringValue(obj, "dataPubblicazione");
            if (nomeCategoria == null || usernameCreatore == null || statoStr == null) {
                continue;
            }
            StatoProposta stato;
            try {
                stato = StatoProposta.valueOf(statoStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (stato == StatoProposta.BOZZA || stato == StatoProposta.VALIDA) {
                continue;
            }

            LocalDate dataPub = null;
            if (dataPubStr != null && !dataPubStr.isBlank()) {
                try {
                    dataPub = LocalDate.parse(dataPubStr.trim(), Proposta.DATE_FORMAT);
                } catch (Exception ignored) {
                }
            }

            List<Proposta.CambioStato> storico = new ArrayList<>();
            String storicoSection = extractArraySection(obj, "storicoStati");
            if (storicoSection != null && !storicoSection.isBlank()) {
                for (String csObj : splitJsonObjects(storicoSection)) {
                    String csStatoStr = extractStringValue(csObj, "stato");
                    String csDataStr = extractStringValue(csObj, "data");
                    if (csStatoStr == null || csDataStr == null) {
                        continue;
                    }
                    try {
                        storico.add(new Proposta.CambioStato(
                                StatoProposta.valueOf(csStatoStr),
                                LocalDate.parse(csDataStr.trim(), Proposta.DATE_FORMAT)));
                    } catch (Exception ignored) {
                    }
                }
            }

            List<String> aderenti = new ArrayList<>();
            String aderSection = extractArraySection(obj, "aderenti");
            if (aderSection != null && !aderSection.isBlank()) {
                int ai = 0;
                while (ai < aderSection.length()) {
                    int qStart = aderSection.indexOf('"', ai);
                    if (qStart < 0) {
                        break;
                    }
                    int qEnd = qStart + 1;
                    while (qEnd < aderSection.length()) {
                        if (aderSection.charAt(qEnd) == '"') {
                            int bs = 0, check = qEnd - 1;
                            while (check >= qStart + 1 && aderSection.charAt(check) == '\\') {
                                bs++;
                                check--;
                            }
                            if (bs % 2 == 0) {
                                break;
                            }
                        }
                        qEnd++;
                    }
                    if (qEnd < aderSection.length()) {
                        String uname = aderSection.substring(qStart + 1, qEnd)
                                .replace("\\\"", "\"").replace("\\\\", "\\");
                        if (!uname.isBlank()) {
                            aderenti.add(uname);
                        }
                    }
                    ai = qEnd + 1;
                }
            }

            LinkedHashMap<String, Boolean> campiSnapshot = new LinkedHashMap<>();
            LinkedHashMap<String, String> valori = new LinkedHashMap<>();
            String campiSection = extractArraySection(obj, "campi");
            if (campiSection != null && !campiSection.isBlank()) {
                for (String campoObj : splitJsonObjects(campiSection)) {
                    String nome = extractStringValue(campoObj, "nome");
                    boolean ob = extractBoolValue(campoObj, "obbligatorio");
                    String val = extractStringValue(campoObj, "valore");
                    if (nome != null) {
                        campiSnapshot.put(nome, ob);
                        valori.put(nome, val != null ? val : "");
                    }
                }
            }
            try {
                result.add(new Proposta(id, nomeCategoria, usernameCreatore,
                        campiSnapshot, valori, stato, dataPub, aderenti, storico));
            } catch (IllegalArgumentException e) {
                System.err.println("[PersistenceManager] Proposta scartata: " + e.getMessage());
            }
        }
        return result;
    }

    // ================================================================
    // UTILITIES PARSER JSON MINIMALE (identico V4)
    // ================================================================
    private int findKeyPosition(String json, String key, int fromIndex) {
        String search = "\"" + key + "\"";
        int searchLen = search.length();
        boolean inString = false;
        int i = fromIndex;
        while (i < json.length()) {
            char ch = json.charAt(i);
            if (ch == '"') {
                int bs = 0, check = i - 1;
                while (check >= fromIndex && json.charAt(check) == '\\') {
                    bs++;
                    check--;
                }
                if (bs % 2 == 0) {
                    if (inString) {
                        inString = false;
                        i++;
                        continue;
                    }
                    if (i + searchLen <= json.length() && json.substring(i, i + searchLen).equals(search)) {
                        int j = i + searchLen;
                        while (j < json.length() && (json.charAt(j) == ' ' || json.charAt(j) == '\n'
                                || json.charAt(j) == '\r' || json.charAt(j) == '\t')) {
                            j++;
                        }
                        if (j < json.length() && json.charAt(j) == ':') {
                            return i;
                        }
                    }
                    inString = true;
                }
            }
            i++;
        }
        return -1;
    }

    private String extractArraySection(String json, String key) {
        int keyIdx = findKeyPosition(json, key, 0);
        if (keyIdx < 0) {
            return null;
        }
        int bracketStart = -1;
        for (int i = keyIdx + key.length() + 2; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                bracketStart = i;
                break;
            }
            if (c != ':' && c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                break;
            }
        }
        if (bracketStart < 0) {
            return null;
        }
        int depth = 0, end = -1;
        boolean inString = false;
        for (int i = bracketStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                int bs = 0, check = i - 1;
                while (check >= 0 && json.charAt(check) == '\\') {
                    bs++;
                    check--;
                }
                if (bs % 2 == 0) {
                    inString = !inString;
                }
            } else if (!inString) {
                if (ch == '[') {
                    depth++;
                } else if (ch == ']') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
        }
        if (end < 0) {
            return null;
        }
        return json.substring(bracketStart + 1, end).trim();
    }

    private List<String> splitJsonObjects(String arrayContent) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (ch == '"') {
                int bs = 0, check = i - 1;
                while (check >= 0 && arrayContent.charAt(check) == '\\') {
                    bs++;
                    check--;
                }
                if (bs % 2 == 0) {
                    inString = !inString;
                }
            } else if (!inString) {
                if (ch == '{') {
                    if (depth == 0) {
                        start = i;

                    }
                    depth++;
                } else if (ch == '}') {
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

    private String extractStringValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) {
            return null;
        }
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) {
            return null;
        }
        int quoteStart = obj.indexOf('"', colon + 1);
        if (quoteStart < 0) {
            return null;
        }
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < obj.length()) {
            if (obj.charAt(quoteEnd) == '"') {
                int bs = 0, check = quoteEnd - 1;
                while (check >= quoteStart + 1 && obj.charAt(check) == '\\') {
                    bs++;
                    check--;
                }
                if (bs % 2 == 0) {
                    break;
                }
            }
            quoteEnd++;
        }
        return obj.substring(quoteStart + 1, quoteEnd)
                .replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private boolean extractBoolValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) {
            return false;
        }
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) {
            return false;
        }
        return obj.substring(colon + 1).trim().startsWith("true");
    }

    private int extractIntValue(String obj, String key) {
        int keyIdx = findKeyPosition(obj, key, 0);
        if (keyIdx < 0) {
            return -1;
        }
        int colon = obj.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) {
            return -1;
        }
        String rest = obj.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        if (num.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getDataFilePath() {
        return dataFilePath;
    }
}
