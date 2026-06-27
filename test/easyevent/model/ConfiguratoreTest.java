package easyevent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test unit di {@link Configuratore}: gestione credenziali e primo accesso.
 * La password e' confrontata case-sensitive, lo username case-insensitive.
 */
class ConfiguratoreTest {

    @Test
    void configuratore_NuovoAccount_PrimoAccessoVero() {
        Configuratore c = new Configuratore("admin", "pwd");
        assertTrue(c.isPrimoAccesso());
    }

    @Test
    void verificaCredenziali_UsernameCaseInsensitivePasswordEsatta_Vero() {
        Configuratore c = new Configuratore("Admin", "Secret1");
        assertTrue(c.verificaCredenziali("admin", "Secret1"));
    }

    @Test
    void verificaCredenziali_PasswordSbagliata_Falso() {
        Configuratore c = new Configuratore("admin", "Secret1");
        assertFalse(c.verificaCredenziali("admin", "sbagliata"));
    }

    @Test
    void verificaCredenziali_PasswordCaseSensitive_Falso() {
        Configuratore c = new Configuratore("admin", "Secret1");
        assertFalse(c.verificaCredenziali("admin", "secret1"));
    }

    @Test
    void impostaCredenzialiPersonali_AggiornaEDisattivaPrimoAccesso() {
        Configuratore c = new Configuratore("admin", "pwd");
        c.impostaCredenzialiPersonali("nuovoUser", "nuovaPwd");
        assertFalse(c.isPrimoAccesso());
        assertEquals("nuovoUser", c.getUsername());
        assertTrue(c.verificaCredenziali("nuovoUser", "nuovaPwd"));
    }

    @Test
    void impostaCredenziali_NuovoUsernameBlank_LanciaIllegalArgument() {
        Configuratore c = new Configuratore("admin", "pwd");
        assertThrows(IllegalArgumentException.class,
                () -> c.impostaCredenzialiPersonali("  ", "nuovaPwd"));
    }

    @Test
    void equals_StessoUsername_Uguale() {
        assertEquals(new Configuratore("Admin", "pwd1"), new Configuratore("admin", "pwd2"));
    }
}
