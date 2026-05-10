package easyevent.proposta;

/**
 * Value Object che rappresenta l'identificatore univoco di una Proposta.
 *
 * Primitive Obsession fix: un int nudo permette operazioni prive di senso nel
 * dominio (sommare due ID, confrontare un IdProposta con un IdNotifica). Questo
 * tipo le rende impossibili a compile-time.
 *
 * Invariante: valore >= 0
 */
public final class IdProposta {

    private final int valore;

    public IdProposta(int valore) {
        if (valore < 0) {
            throw new IllegalArgumentException(
                    "L'ID proposta non puo' essere negativo: " + valore);
        }
        this.valore = valore;
    }

    public int getValore() {
        return valore;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IdProposta)) {
            return false;
        }
        return this.valore == ((IdProposta) obj).valore;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(valore);
    }

    @Override
    public String toString() {
        return String.valueOf(valore);
    }
}
