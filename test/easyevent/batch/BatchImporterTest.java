package easyevent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.core.AppData;
import easyevent.categoria.Categoria;
import easyevent.exception.PersistenzaException;
import easyevent.proposta.Proposta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test di integrazione di {@link BatchImporter}: combina parsing e lettura di
 * file temporanei. Il callback di salvataggio e' iniettato come lambda no-op,
 * cosi' il parsing si testa senza scrivere davvero su disco.
 */
class BatchImporterTest {

    /** Data fissa dell'importazione: rende i test indipendenti dal giorno di esecuzione. */
    private static final LocalDate OGGI = LocalDate.of(2026, 6, 1);

    @TempDir
    Path tempDir;

    private final BatchImporter.SalvaCallback noop = () -> {
    };

    /** Scrive un file batch nel tempDir e restituisce il percorso. */
    private String scriviFile(String nome, String... righe) throws Exception {
        Path file = tempDir.resolve(nome);
        Files.write(file, List.of(righe));
        return file.toString();
    }

    /** Riga PROPOSTA con tutti i campi base valorizzati validamente rispetto a oggi. */
    private String rigaPropostaValida(String categoria) {
        String termine = OGGI.plusDays(30).format(Proposta.DATE_FORMAT);
        String dataInizio = OGGI.plusDays(40).format(Proposta.DATE_FORMAT);
        String dataConc = OGGI.plusDays(41).format(Proposta.DATE_FORMAT);
        return "PROPOSTA | " + categoria
                + " | Titolo = Concerto"
                + " | Numero di partecipanti = 10"
                + " | Termine ultimo di iscrizione = " + termine
                + " | Luogo = Arena"
                + " | Data inizio = " + dataInizio
                + " | Ora = 21:00"
                + " | Quota individuale = 20"
                + " | Data conclusiva = " + dataConc;
    }

    // ================================================================
    // Costruttore
    // ================================================================
    @Test
    void costruttore_AppDataNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchImporter(null, "admin", noop, OGGI));
    }

    @Test
    void costruttore_UsernameBlank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchImporter(new AppData(), "  ", noop, OGGI));
    }

    @Test
    void costruttore_CallbackNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchImporter(new AppData(), "admin", null, OGGI));
    }

    @Test
    void costruttore_DataImportazioneNull_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchImporter(new AppData(), "admin", noop, null));
    }

    // ================================================================
    // Apertura file
    // ================================================================
    @Test
    void importa_FileInesistente_LanciaPersistenzaException() {
        BatchImporter imp = new BatchImporter(new AppData(), "admin", noop, OGGI);
        PersistenzaException ex = assertThrows(PersistenzaException.class,
                () -> imp.importa(tempDir.resolve("non_esiste.txt").toString()));
        assertEquals(PersistenzaException.TipoErrore.FILE_NON_TROVATO, ex.getTipoErrore());
    }

    @Test
    void importa_PercorsoNonRegolare_LanciaPersistenzaException() {
        BatchImporter imp = new BatchImporter(new AppData(), "admin", noop, OGGI);
        // passo una directory invece di un file
        assertThrows(PersistenzaException.class, () -> imp.importa(tempDir.toString()));
    }

    // ================================================================
    // Elaborazione righe
    // ================================================================
    @Test
    void importa_RigaCampoComune_AggiungeCampoAppData() throws Exception {
        AppData app = new AppData();
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String file = scriviFile("batch.txt", "CAMPO_COMUNE | Sito web | no");
        imp.importa(file);
        assertTrue(app.esisteCampoComune("Sito web"));
    }

    @Test
    void importa_RigaCategoriaConCampiSpecifici_CreaCategoria() throws Exception {
        AppData app = new AppData();
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String file = scriviFile("batch.txt",
                "CATEGORIA | Concerti | CAMPO_SPECIFICO | Genere | no");
        imp.importa(file);
        assertTrue(app.esisteCategoria("Concerti"));
        assertTrue(app.getCategoria("Concerti").contieneCampo("Genere"));
    }

    @Test
    void importa_RigaProposta_PubblicaInBacheca() throws Exception {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String file = scriviFile("batch.txt", rigaPropostaValida("Concerti"));
        BatchRisultato r = imp.importa(file);
        assertEquals(1, r.getNumSuccessi());
        assertEquals(1, app.getBacheca().size());
    }

    @Test
    void importa_RigaCommentoOVuota_Ignorata() throws Exception {
        AppData app = new AppData();
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String file = scriviFile("batch.txt",
                "# questo e' un commento",
                "",
                "CAMPO_COMUNE | Sito web | no");
        BatchRisultato r = imp.importa(file);
        // solo la riga CAMPO_COMUNE conta
        assertEquals(1, r.getRigheTotali());
    }

    @Test
    void importa_RigaPropostaNonValida_ProduceErrore() throws Exception {
        AppData app = new AppData();
        app.inizializzaCampiBase();
        app.aggiungiCategoria(new Categoria("Concerti"));
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        // manca il Titolo (e altri campi base obbligatori)
        String file = scriviFile("batch.txt",
                "PROPOSTA | Concerti | Numero di partecipanti = 10");
        BatchRisultato r = imp.importa(file);
        assertTrue(r.getNumErrori() >= 1);
        assertTrue(app.getBacheca().isEmpty());
    }

    @Test
    void importa_CategoriaDuplicata_ProduceWarningMaNonInterrompe() throws Exception {
        AppData app = new AppData();
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String file = scriviFile("batch.txt",
                "CATEGORIA | Concerti",
                "CATEGORIA | Concerti");
        BatchRisultato r = imp.importa(file);
        assertEquals(1, r.getNumSuccessi());
        assertTrue(r.getNumWarnings() >= 1);
    }

    @Test
    void importa_SalvaCallbackInvocata_QuandoCiSonoSuccessi() throws Exception {
        AppData app = new AppData();
        AtomicInteger contatore = new AtomicInteger(0);
        BatchImporter imp = new BatchImporter(app, "admin", contatore::incrementAndGet, OGGI);
        String file = scriviFile("batch.txt", "CAMPO_COMUNE | Sito web | no");
        imp.importa(file);
        assertTrue(contatore.get() > 0);
    }

    @Test
    void importaMultipli_AggregaRisultati() throws Exception {
        AppData app = new AppData();
        BatchImporter imp = new BatchImporter(app, "admin", noop, OGGI);
        String f1 = scriviFile("b1.txt", "CAMPO_COMUNE | Sito web | no");
        String f2 = scriviFile("b2.txt", "CAMPO_COMUNE | Telefono | si");
        BatchRisultato r = imp.importaMultipli(List.of(f1, f2));
        assertEquals(2, r.getRigheTotali());
        assertEquals(2, r.getNumSuccessi());
    }
}
