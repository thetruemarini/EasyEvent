package easyevent.categoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.exception.ElementoGiaEsistenteException;
import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link Categoria}: gestione dei campi specifici con unicita'
 * del nome (case-insensitive) e protezione della collezione interna.
 */
class CategoriaTest {

    @Test
    void categoria_NomeBlank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new Categoria("  "));
    }

    @Test
    void aggiungiCampoSpecifico_Nuovo_LoAggiunge() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        assertTrue(cat.contieneCampo("Genere musicale"));
        assertEquals(1, cat.getCampiSpecifici().size());
    }

    @Test
    void aggiungiCampoSpecifico_DuplicatoCaseInsensitive_LanciaElementoGiaEsistente() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        ElementoGiaEsistenteException ex = assertThrows(ElementoGiaEsistenteException.class,
                () -> cat.aggiungiCampoSpecifico(new CampoSpecifico("genere musicale", true)));
        assertEquals(ElementoGiaEsistenteException.TipoElemento.CAMPO_SPECIFICO, ex.getTipoElemento());
    }

    @Test
    void aggiungiCampoSpecifico_Null_LanciaIllegalArgument() {
        Categoria cat = new Categoria("Concerti");
        assertThrows(IllegalArgumentException.class, () -> cat.aggiungiCampoSpecifico(null));
    }

    @Test
    void rimuoviCampoSpecifico_Esistente_RestituisceTrue() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        assertTrue(cat.rimuoviCampoSpecifico("Genere musicale"));
        assertFalse(cat.contieneCampo("Genere musicale"));
    }

    @Test
    void rimuoviCampoSpecifico_Inesistente_RestituisceFalse() {
        Categoria cat = new Categoria("Concerti");
        assertFalse(cat.rimuoviCampoSpecifico("Inesistente"));
    }

    @Test
    void contieneCampo_PresenteCaseInsensitive_RestituisceTrue() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        assertTrue(cat.contieneCampo("GENERE MUSICALE"));
    }

    @Test
    void modificaObbligatorieta_CampoEsistente_RestituisceTrue() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        assertTrue(cat.modificaObbligatorietaCampoSpecifico("Genere musicale", true));
        assertTrue(cat.getCampoSpecifico("Genere musicale").isObbligatorio());
    }

    @Test
    void modificaObbligatorieta_CampoInesistente_RestituisceFalse() {
        Categoria cat = new Categoria("Concerti");
        assertFalse(cat.modificaObbligatorietaCampoSpecifico("Inesistente", true));
    }

    @Test
    void getCampiSpecifici_RestituisceListaNonModificabile() {
        Categoria cat = new Categoria("Concerti");
        assertThrows(UnsupportedOperationException.class,
                () -> cat.getCampiSpecifici().add(new CampoSpecifico("X", false)));
    }

    @Test
    void repOk_DopoOperazioniValide_RestaVero() {
        Categoria cat = new Categoria("Concerti");
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Genere musicale", false));
        cat.aggiungiCampoSpecifico(new CampoSpecifico("Artista", true));
        cat.rimuoviCampoSpecifico("Artista");
        assertTrue(cat.repOk());
    }
}
