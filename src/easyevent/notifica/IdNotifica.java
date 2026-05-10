package easyevent.notifica;

/**
 * Value Object per l'ID di una Notifica. Impedisce a compile-time di confondere
 * un IdNotifica con un IdProposta.
 *
 * Invariante: valore >= 0
 */
public final class IdNotifica {

    private final int valore;

    public IdNotifica(int valore) {
        if (valore < 0) {
            throw new IllegalArgumentException(
                    "L'ID notifica non puo' essere negativo: " + valore);
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
        if (!(obj instanceof IdNotifica)) {
            return false;
        }
        return this.valore == ((IdNotifica) obj).valore;
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
