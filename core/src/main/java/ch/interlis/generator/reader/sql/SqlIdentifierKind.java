package ch.interlis.generator.reader.sql;

/**
 * Herkunft eines SQL-Identifiers.
 *
 * <ul>
 *   <li>{@link #USER_SUPPLIED}: Eingabe von aussen (z.&nbsp;B. Schemaname). Wird streng validiert.</li>
 *   <li>{@link #DATABASE_DISCOVERED}: Name wurde aus der Datenbank gelesen (Metatabellen,
 *       JDBC-Metadaten). Breiter Zeichensatz erlaubt, beim Rendering immer korrekt gequotet.</li>
 *   <li>{@link #INTERNAL_CONSTANT}: Fest verdrahteter Name im Code (z.&nbsp;B. {@code t_ili2db_settings}).
 *       Verletzung des Musters ist ein Programmierfehler.</li>
 * </ul>
 */
public enum SqlIdentifierKind {
    USER_SUPPLIED,
    DATABASE_DISCOVERED,
    INTERNAL_CONSTANT
}
