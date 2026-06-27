package easyevent.categoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import easyevent.exception.ModificaNonConsentitaException;
import org.junit.jupiter.api.Test;

/**
 * Test unit della gerarchia {@link Campo}: polimorfismo di {@code setObbligatorio}
 * e semantica di uguaglianza per nome (case-insensitive) e tipo.
 */
class CampoTest {

    @Test
    void campoBase_SetObbligatorio_LanciaModificaNonConsentita() {
        CampoBase campo = new CampoBase("Data inizio");
        ModificaNonConsentitaException ex = assertThrows(ModificaNonConsentitaException.class,
                () -> campo.setObbligatorio(false));
        assertEquals(ModificaNonConsentitaException.TipoModifica.CAMPO_BASE_IMMUTABILE,
                ex.getTipoModifica());
    }

    @Test
    void campoBase_SempreObbligatorio() {
        assertTrue(new CampoBase("Data inizio").isObbligatorio());
    }

    @Test
    void campoComune_SetObbligatorio_Consentito() {
        CampoComune campo = new CampoComune("Luogo", false);
        campo.setObbligatorio(true);
        assertTrue(campo.isObbligatorio());
    }

    @Test
    void campoSpecifico_SetObbligatorio_Consentito() {
        CampoSpecifico campo = new CampoSpecifico("Genere musicale", true);
        campo.setObbligatorio(false);
        assertFalse(campo.isObbligatorio());
    }

    @Test
    void campo_NomeBlank_LanciaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new CampoComune("  ", false));
    }

    @Test
    void campo_GetTipo_CoerenteConSottoclasse() {
        assertEquals(Campo.TipoCampo.BASE, new CampoBase("Data inizio").getTipo());
        assertEquals(Campo.TipoCampo.COMUNE, new CampoComune("Luogo", false).getTipo());
        assertEquals(Campo.TipoCampo.SPECIFICO, new CampoSpecifico("Genere", false).getTipo());
    }

    @Test
    void campo_EqualsCaseInsensitiveEStessoTipo() {
        assertEquals(new CampoComune("Luogo", false), new CampoComune("luogo", true));
    }

    @Test
    void campo_EqualsTipoDiverso_NonUguale() {
        // Stesso nome ma tipo diverso => non uguali
        assertNotEquals(new CampoComune("Luogo", false), new CampoSpecifico("Luogo", false));
    }

    @Test
    void campo_IsData_RiconosceCampiData() {
        assertTrue(new CampoComune("Data inizio", false).isData());
        assertTrue(new CampoComune("Termine ultimo di iscrizione", false).isData());
        assertFalse(new CampoComune("Luogo", false).isData());
    }

    @Test
    void campo_IsOra_RiconosceCampoOra() {
        assertTrue(new CampoComune("Ora", false).isOra());
        assertFalse(new CampoComune("Luogo", false).isOra());
    }

    @Test
    void campo_IsInEvidenza_RiconosceCampiBacheca() {
        assertTrue(new CampoComune("Luogo", false).isInEvidenza());
        assertTrue(new CampoComune("Quota individuale", false).isInEvidenza());
        assertFalse(new CampoSpecifico("Genere musicale", false).isInEvidenza());
    }
}
