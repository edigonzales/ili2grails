# ili2grails – P0-Spezifikation für einen LLM-Coding-Agenten

**Dokumenttyp:** Verbindliche Implementierungs- und Abnahmespezifikation  
**Ziel-Repository:** `https://github.com/edigonzales/ili2grails`  
**Ziel-Branch:** aktueller Default-Branch `main`; Umsetzung auf einem neuen Arbeits-Branch  
**Stand:** 30. Juli 2026  
**Technologien:** Java 17, Groovy 4, Grails 7, GORM/Hibernate, PostgreSQL/PostGIS, ili2c und ili2pg  
**Fokus:** Backend, Core-IR, Reader, Merger, Grails-Persistenzplanung und reale Persistenztests  
**Nicht Gegenstand:** visuelles UI/UX-Redesign

---

## 1. Auftrag

Implementiere die folgenden fünf P0-Arbeitspakete vollständig und in der vorgegebenen Reihenfolge:

1. **P0-A – Deterministischer, diagnostizierbarer Metadata-Merger**
2. **P0-B – Trennung von GORM-Persistenzbeziehungen und rein navigativen/inversen UI-Beziehungen**
3. **P0-C – Kombinierter Grails-/GORM-/PostgreSQL-/ili2pg-Vertragstest**
4. **P0-D – Sichere und korrekte Behandlung dynamischer SQL-Identifier**
5. **P0-E – Präzise Modellauswahl: Root-Modell plus echte Abhängigkeiten statt aller Modelle eines Schemas**

Das Ziel ist kein kosmetisches Refactoring. Die Änderung muss fachlich falsche Persistenzableitungen verhindern:

- Kein `first match wins` im Core-Merge.
- Keine zufällige Zuordnung aufgrund einer Listen-, HashMap- oder JDBC-Reihenfolge.
- Keine inverse Related-Section als automatische GORM-Collection.
- Keine geratenen `mappedBy`, `belongsTo` oder schreibbaren Associations.
- Keine ungequoteten dynamischen SQL-Identifier.
- Keine automatische Generierung unabhängiger Modelle aus demselben ili2db-Schema.
- Ein realer End-to-End-Vertrag muss beweisen, dass die generierte Grails-Anwendung gegen die tatsächliche ili2pg-Datenbank korrekt persistiert.

P0 ist erst abgeschlossen, wenn alle obligatorischen Tests und Abnahmekriterien dieser Spezifikation erfüllt sind.

---

## 2. Verbindliche Arbeitsweise

### 2.1 Baseline

Vor jeder Produktionsänderung:

```bash
git switch main
git pull --ff-only
git status --short
git switch -c refactor/p0-backend-correctness
./gradlew clean test --no-daemon
```

Sofern lokal verfügbar:

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsRuntimeSmokeTest --no-daemon

PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:realIli2dbSmokeTest --no-daemon
```

Erstelle vor der ersten Produktionsänderung:

```text
docs/implementation/p0-backend-correctness-progress.md
```

Die Datei enthält:

- Git-Commit und Branch;
- Datum, OS und Java-Version;
- Grails-Version und Pfad, falls vorhanden;
- ili2pg-Version und Pfad, falls vorhanden;
- Testanzahl je Modul;
- Skips, Failures und bekannte Infrastrukturprobleme;
- die exakten ausgeführten Befehle.

### 2.2 Commit-Disziplin

Erstelle getrennte Commits. Empfohlene Reihenfolge:

```text
docs: record P0 backend correctness baseline
refactor(core): quote and validate dynamic SQL identifiers
refactor(core): restrict metadata reads to root model dependencies
feat(core): add deterministic metadata matchers and diagnostics
refactor(core): replace in-reader enrichment with MetadataMerger
refactor(grails): separate GORM collections from inverse UI plans
test(grails): verify generated runtime against real ili2pg PostgreSQL
docs: complete P0 backend correctness acceptance
```

Jeder Commit muss kompilieren und seine relevanten Tests bestehen.

### 2.3 Verbotene Abkürzungen

Unzulässig sind insbesondere:

- bei mehreren Kandidaten den ersten auswählen;
- Kandidaten lexikografisch sortieren und den ersten als fachliche Entscheidung verwenden;
- alle Modelle aus `t_ili2db_model` weiterhin lesen und erst nach der Generierung filtern;
- normale inverse `MANY_TO_ONE`-Beziehungen weiterhin als `static hasMany` erzeugen;
- den neuen Vertragstest nur mit direktem JDBC-SQL ausführen;
- Schema-, Tabellen- oder Spaltennamen ungeprüft in SQL konkatenieren;
- `catch (Exception)` verwenden, um Matching-, Persistenz- oder Locking-Probleme still zu ignorieren;
- Snapshot-Dateien pauschal neu erzeugen;
- bestehende Tests löschen oder deaktivieren;
- `EMBEDDED_FOREIGN_KEY` in P0 schreibbar machen;
- die komplette Core-IR auf Records/Immutability umstellen;
- die Grails-Runtime bereits als Plugin extrahieren;
- UI, CSS, Layout oder visuelle Komponenten umgestalten.

---

## 3. Ausgangslage und Hotspots

Vor der Umsetzung sind mindestens diese Dateien vollständig zu lesen:

```text
core/src/main/java/ch/interlis/generator/metadata/MetadataReader.java
core/src/main/java/ch/interlis/generator/reader/Ili2cModelReader.java
core/src/main/java/ch/interlis/generator/reader/Ili2dbMetadataReader.java
core/src/main/java/ch/interlis/generator/model/ModelMetadata.java
core/src/main/java/ch/interlis/generator/model/ClassMetadata.java
core/src/main/java/ch/interlis/generator/model/AttributeMetadata.java
core/src/main/java/ch/interlis/generator/model/RelationshipMetadata.java
core/src/main/java/ch/interlis/generator/model/AssociationMetadata.java

target-grails/src/main/java/ch/interlis/generator/grails/GrailsRelationshipMapper.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsDomainGenerator.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsInverseRelationshipPlanner.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsAssociationPlanner.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsCrudGenerator.java

target-grails/src/realIli2dbSmokeTest/java/ch/interlis/generator/grails/RealIli2dbSmokeTest.java
target-grails/src/grailsRuntimeSmokeTest/java/ch/interlis/generator/grails/GrailsRuntimeSmokeTest.java
```

### 3.1 Aktuelle Kernprobleme

`MetadataReader` übernimmt heute Orchestrierung, Attribut-Matching, Relationship-Matching, Merge, Typ-Postprocessing und Association-Synchronisierung. Die Methoden `findAttribute()`, `matchRelationship()` und `findMatchingRelationship()` erlauben normalisierte Fallbacks, erkennen aber keine mehrdeutigen Kandidatenmengen.

`Ili2dbMetadataReader` verwendet einen String-Schemanamen in SQL-Templates und erweitert die Auswahl über `resolveRelevantModelNames()` um weitere Modelle aus `t_ili2db_model`.

`GrailsRelationshipMapper.map()` mischt zwei Konzepte:

- echte GORM-Persistenz-Collections;
- inverse Related-Sections für die Navigation.

`GrailsDomainGenerator` rendert jede `DomainCollection` als `static hasMany`. Dadurch wird eine reine Navigationsentscheidung zu einer ORM-Entscheidung.

`GrailsInverseRelationshipPlanner` leitet inverse Related-Sections aus `ownerMapping.collections()` ab und ist deshalb an die künstliche GORM-Collection gekoppelt.

Die bestehenden Realtests prüfen viele Ebenen, aber nicht durchgehend den tatsächlichen Runtime-Service einer generierten Grails-App gegen ein echtes ili2pg-/PostgreSQL-Schema.

---

## 4. Zielarchitektur

```text
Ili2cModelReader
  └─ Ili2cReadResult
      ├─ semantic ModelMetadata
      ├─ TransferDescription
      └─ ModelSelection

Ili2dbMetadataReader
  └─ physical ModelMetadata für exakt ModelSelection.includedModelNames

MetadataMerger
  ├─ AttributeMatcher
  ├─ RelationshipMatcher
  ├─ MetadataMergeResult
  └─ strukturierte Diagnostics

MetadataPostProcessor
  ├─ fehlende Typ-Fallbacks
  └─ konsistente Association-Synchronisierung

MetadataValidator
  └─ Core-IR-Invarianten und Blocking Gate

GrailsRelationshipMapper
  └─ ausschliesslich GORM-Persistenzplanung

GrailsInverseRelationshipPlanner
  └─ ausschliesslich inverse/navigationale Related-Sections

GrailsDomainGenerator
  ├─ persistente Properties
  ├─ echte Kompositions-Collections
  ├─ belongsTo
  └─ mappedBy

GrailsPostgresContractTest
  └─ generierte App + Runtime-Services + GORM + echtes ili2pg-Schema
```

---

# 5. P0-A – Deterministischer Metadata-Merger

## 5.1 Ziel und Invarianten

Der Merger muss:

1. deterministisch sein;
2. Input-Reihenfolgen ignorieren;
3. pro Match einen stabilen Grund liefern;
4. Mehrdeutigkeiten explizit diagnostizieren;
5. ein physisches Element höchstens einmal verwenden;
6. bei Ambiguität keine semantischen Felder auf ein zufälliges physisches Element kopieren;
7. semantische und physische Herkunft erhalten;
8. vor der Grails-Generierung einen blockierenden Fehler liefern, wenn die physische Persistenzabbildung nicht eindeutig ist.

## 5.2 Neue Paketstruktur

```text
core/src/main/java/ch/interlis/generator/metadata/merge/
  MetadataMerger.java
  MetadataMergeResult.java
  MetadataMergeException.java
  MetadataMergePolicy.java
  MergeDiagnostic.java
  MergeDiagnosticCode.java
  MergeSeverity.java
  MatchCandidate.java
  MatchDecision.java
  MatchReason.java
  MergeTokenNormalizer.java
  AttributeMatcher.java
  RelationshipMatcher.java

core/src/main/java/ch/interlis/generator/metadata/
  MetadataPostProcessor.java
  MetadataValidator.java
```

Tests:

```text
core/src/test/java/ch/interlis/generator/metadata/merge/
  MetadataMergerTest.java
  AttributeMatcherTest.java
  RelationshipMatcherTest.java
  MergeTokenNormalizerTest.java
  MetadataMergeAmbiguityTest.java

core/src/test/java/ch/interlis/generator/metadata/
  MetadataPostProcessorTest.java
  MetadataValidatorTest.java
```

## 5.3 Diagnostics

### `MergeSeverity`

```java
public enum MergeSeverity {
    INFO,
    WARNING,
    ERROR,
    FATAL
}
```

- `INFO`: erwartete Abweichung ohne Einschränkung.
- `WARNING`: Ergebnis bleibt nutzbar, aber nicht vollständig angereichert.
- `ERROR`: Standardgenerierung wird blockiert.
- `FATAL`: Merge kann nicht sinnvoll abgeschlossen werden.

### `MergeDiagnosticCode`

Mindestens:

```java
public enum MergeDiagnosticCode {
    MODEL_NAME_MISMATCH,
    CLASS_ONLY_IN_PHYSICAL,
    CLASS_ONLY_IN_SEMANTIC,
    ATTRIBUTE_UNMATCHED,
    ATTRIBUTE_AMBIGUOUS,
    ATTRIBUTE_PHYSICAL_REUSED,
    RELATIONSHIP_UNMATCHED,
    RELATIONSHIP_AMBIGUOUS,
    RELATIONSHIP_PHYSICAL_REUSED,
    ASSOCIATION_ROLE_UNRESOLVED,
    ASSOCIATION_ROLE_DUPLICATE,
    DUPLICATE_CANONICAL_RELATIONSHIP,
    MERGE_INVARIANT_VIOLATION
}
```

### `MergeDiagnostic`

```java
public record MergeDiagnostic(
    MergeSeverity severity,
    MergeDiagnosticCode code,
    String message,
    String semanticElement,
    String physicalElement,
    Map<String, String> details
) {
    public MergeDiagnostic {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean isBlocking() {
        return severity == MergeSeverity.ERROR
            || severity == MergeSeverity.FATAL;
    }
}
```

Regeln:

- `semanticElement` und `physicalElement` enthalten qualifizierte Namen.
- `details` hat stabile maschinenlesbare Schlüssel.
- Tests dürfen nicht Meldungstext parsen.
- Keine JDBC-Passwörter oder Secrets in Diagnostics.

## 5.4 Match-Typen

### `MatchReason`

```java
public enum MatchReason {
    EXACT_QUALIFIED_NAME,
    EXACT_NAME,
    EXACT_SOURCE_ATTRIBUTE,
    EXACT_TARGET_ROLE,
    EXACT_PHYSICAL_NAME,
    EXACT_COLUMN_NAME,
    NORMALIZED_FULL_TOKEN,
    NORMALIZED_ID_SUFFIX,
    NO_MATCH
}
```

### `MatchCandidate<T>`

```java
public record MatchCandidate<T>(
    T physical,
    T semantic,
    MatchReason reason,
    int priority,
    String token
) {
}
```

### `MatchDecision<T>`

```java
public record MatchDecision<T>(
    Status status,
    T physical,
    T semantic,
    MatchReason reason,
    String token,
    List<MatchCandidate<T>> candidates
) {
    public enum Status {
        MATCHED,
        UNMATCHED,
        AMBIGUOUS,
        PHYSICAL_ALREADY_USED
    }

    public MatchDecision {
        candidates = candidates == null
            ? List.of()
            : List.copyOf(candidates);
    }

    public boolean matched() {
        return status == Status.MATCHED;
    }
}
```

## 5.5 Token-Normalisierung

### API

```java
public final class MergeTokenNormalizer {

    public Set<NormalizedToken> tokens(String value);

    public String normalizeExact(String value);

    public record NormalizedToken(
        String value,
        MatchReason reason
    ) {
    }
}
```

### Regeln

1. `null`/blank → leere Menge.
2. `trim()`.
3. Lowercase mit `Locale.ROOT`.
4. Bindestrich zu Unterstrich.
5. vollständigen Wert aufnehmen.
6. bei qualifiziertem Namen letztes Punktsegment aufnehmen.
7. `_id` entfernen und Herkunft `NORMALIZED_ID_SUFFIX` behalten.
8. CamelCase-`Id` nur nach einer klaren Wortgrenze behandeln.
9. Ein normales Wort, das zufällig auf `id` endet, nicht abschneiden.
10. Letztes Unterstrichsegment nur als schwachen Token aufnehmen.
11. Leere Tokens verwerfen.
12. Stabile Reihenfolge und keine Duplikate.

Obligatorischer Regressionstest: `grid` darf nicht zu `gr` werden.

## 5.6 `AttributeMatcher`

### API

```java
public final class AttributeMatcher {

    public AttributeMatcher(MergeTokenNormalizer normalizer);

    public List<MatchDecision<AttributeMetadata>> match(
        ClassMetadata physicalClass,
        ClassMetadata semanticClass
    );
}
```

### Matchphasen

1. `EXACT_QUALIFIED_NAME`
2. `EXACT_NAME`
3. `EXACT_COLUMN_NAME`
4. `NORMALIZED_FULL_TOKEN`
5. `NORMALIZED_ID_SUFFIX`

Pro Phase:

- nur noch nicht gematchte Elemente verwenden;
- Kandidatengraph bilden;
- nur eine Komponente mit genau einem semantischen und einem physischen Element akzeptieren;
- 1:n, n:1 oder n:m ist `AMBIGUOUS`;
- bei Ambiguität nicht in schwächeren Phasen weitersuchen;
- Sortierung nur für stabile Ausgabe, nie als fachlicher Tie-Breaker.

### Feld-Precedence beim Attribut-Merge

| Feld | Regel |
|---|---|
| `name` | physischer kanonischer Name bleibt erhalten |
| `qualifiedName` | semantisch, falls vorhanden |
| `columnName`, `sqlName`, `dbType` | physisch |
| `primaryKey`, `foreignKey` | physisch |
| `referencedClass` | physisch, danach semantisch validieren |
| `mandatory` | physisch OR semantisch |
| `coreType` | semantisch, falls nicht `UNKNOWN`, sonst physisch |
| `javaType` | semantisch, falls vorhanden, sonst physisch |
| `iliType`, `domainName` | semantisch |
| `enumType` | semantisch, sonst physisch |
| `maxLength` | engerer bekannter Wert; Widerspruch diagnostizieren |
| `minValue`, `maxValue` | semantisch |
| `precision`, `scale` | semantisch, sonst physisch |
| `cardinalityMin`, `cardinalityMax` | semantisch |
| `ordered` | physisch OR semantisch |
| `unit`, `documentation` | semantisch |
| `labels` | Merge; semantische Sprachwerte überschreiben |
| `geometry` | physisch OR semantisch |
| `geometryKind` | physisch, wenn konkret, sonst semantisch |
| `geometrySrid` | physisch |
| `geometryHasZ/M` | physisch, falls bekannt, sonst semantisch |
| `allowEmptyGeometry` | semantisch, falls bekannt, sonst physisch |

Empfohlene private Methoden:

```java
private AttributeMetadata mergeAttribute(
    AttributeMetadata physical,
    AttributeMetadata semantic
);

private boolean mergeMandatory(
    AttributeMetadata physical,
    AttributeMetadata semantic
);

private Integer narrowerMaxLength(
    Integer physical,
    Integer semantic
);

private void mergeLabels(
    Map<String, String> target,
    Map<String, String> semantic
);
```

Unmatched-Regeln:

- physisches Attribut ohne semantischen Match bleibt erhalten;
- semantisches Attribut ohne physische Spalte wird nicht blind in eine persistente Klasse eingefügt;
- bei nichtpersistenten/abstrakten Klassen darf es semantisch erhalten bleiben;
- persistenzrelevantes unmatched Attribut einer physischen Klasse ist mindestens `WARNING`, bei erwarteter Spalte `ERROR`.

## 5.7 `RelationshipMatcher`

### API

```java
public final class RelationshipMatcher {

    public RelationshipMatcher(MergeTokenNormalizer normalizer);

    public List<MatchDecision<RelationshipMetadata>> match(
        ModelMetadata physical,
        ModelMetadata semantic
    );
}
```

### Vorausfilter

Kandidaten dürfen nur verglichen werden, wenn:

- `sourceClass` identisch;
- `targetClass` identisch;
- vorhandene `associationName` nicht widersprechen;
- Relationship-Arten kompatibel sind.

Kompatibel:

- `ILI2DB_FK` ↔ `REFERENCE_ATTRIBUTE`
- `ILI2DB_FK` ↔ `ASSOCIATION_ROLE`
- physischer FK ↔ `COMPOSITION_ATTRIBUTE`, aber nur bei eindeutigem Source-/Target-/Rollenbezug

Nicht kompatibel:

- zwei semantische Relationships miteinander;
- zwei physische Relationships miteinander;
- unterschiedliche Source-/Target-Klassen;
- widersprüchliche Association-Namen.

### Phasen

1. Association-Name plus exakter Rollenname
2. exaktes `sourceAttribute`
3. exaktes `physicalName`
4. exaktes `targetRoleName`
5. exakter Relationship-Name
6. normalisierter vollständiger Token
7. normalisierter ID-Suffix-Token

Auch hier: nur eindeutige 1:1-Komponenten akzeptieren.

### Physische Wiederverwendung

Führe einen stabilen Identity-Key ein:

```java
record RelationshipIdentity(
    String sourceClass,
    String targetClass,
    String sourceAttribute,
    String physicalName,
    String targetRoleName,
    RelationshipMetadata.SemanticKind semanticKind
) {
}
```

Ein physisches Relationship darf nur einmal konsumiert werden.

### Merge-Precedence

| Feld | Quelle |
|---|---|
| `sourceClass`, `targetClass` | physisch; Identität muss semantisch passen |
| `sourceAttribute`, `targetAttribute`, `physicalName` | physisch |
| `type`, `semanticKind` | semantisch |
| `associationName`, Rollen-/Opposite-Namen | semantisch |
| `cardinality`, `mandatory`, `ordered`, `external`, `composition` | semantisch |
| `semanticName` | semantisch |
| `source` | `ili2db+ili2c` |
| `mergeReason`, `mergeConfidence`, `mergeToken` | Matchentscheidung |

Bei Ambiguität:

- keinen Kandidaten auswählen;
- physische Relationships unverändert erhalten;
- semantisches Relationship separat erhalten;
- `RELATIONSHIP_AMBIGUOUS` mit Severity `ERROR`;
- Standardgenerierung blockieren.

## 5.8 Merge-Policy und Resultat

### `MetadataMergePolicy`

```java
public enum MetadataMergePolicy {
    STRICT,
    DIAGNOSTIC
}
```

- `STRICT`: blockierende Diagnostics führen nach vollständiger Auswertung zu einer Exception.
- `DIAGNOSTIC`: Resultat bleibt inspizierbar; Caller muss Diagnostics auswerten.
- Kein `LENIENT_FIRST_MATCH` implementieren.

### `MetadataMergeResult`

```java
public record MetadataMergeResult(
    ModelMetadata metadata,
    List<MergeDiagnostic> diagnostics
) {
    public MetadataMergeResult {
        Objects.requireNonNull(metadata, "metadata");
        diagnostics = diagnostics == null
            ? List.of()
            : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream()
            .anyMatch(MergeDiagnostic::isBlocking);
    }

    public List<MergeDiagnostic> blockingDiagnostics() {
        return diagnostics.stream()
            .filter(MergeDiagnostic::isBlocking)
            .toList();
    }

    public void throwIfBlocking() {
        if (hasBlockingDiagnostics()) {
            throw new MetadataMergeException(blockingDiagnostics());
        }
    }
}
```

### `MetadataMergeException`

```java
public final class MetadataMergeException
        extends RuntimeException {

    private final List<MergeDiagnostic> diagnostics;

    public MetadataMergeException(
        List<MergeDiagnostic> diagnostics
    ) {
        super(buildMessage(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<MergeDiagnostic> diagnostics() {
        return diagnostics;
    }
}
```

Die Message ist kompakt, die vollständige Diagnose bleibt strukturiert zugänglich.

## 5.9 `MetadataMerger`

### API

```java
public final class MetadataMerger {

    public MetadataMerger(
        AttributeMatcher attributeMatcher,
        RelationshipMatcher relationshipMatcher,
        MetadataPostProcessor postProcessor,
        MetadataValidator validator
    );

    public static MetadataMerger defaultMerger();

    public MetadataMergeResult merge(
        ModelMetadata physical,
        ModelMetadata semantic
    );

    public ModelMetadata mergeStrict(
        ModelMetadata physical,
        ModelMetadata semantic
    );
}
```

### Exakter Ablauf

`merge()` muss:

1. Argumente validieren.
2. Modellnamen vergleichen.
3. einen separaten Ziel-Snapshot erzeugen;
4. physische Modellinformationen als Basis übernehmen;
5. ILI-Version, Modellversion, Dokumentation und Labels semantisch anreichern;
6. Klassen nach qualifiziertem Namen deterministisch bearbeiten;
7. pro Klasse Attribute matchen;
8. nur eindeutige Attribut-Matches mergen;
9. unmatched/ambiguous Attribute diagnostizieren;
10. Enums eindeutig vereinigen;
11. Relationships global matchen;
12. nur eindeutige Relationship-Matches mergen;
13. unmatched physische Relationships behalten;
14. unmatched semantische Relationships mit korrekter Herkunft behalten;
15. Associations aus semantischer Sicht übernehmen;
16. `MetadataPostProcessor.process()` ausführen;
17. `MetadataValidator.validate()` ausführen;
18. alle Diagnostics zusammenführen;
19. Diagnostics stabil sortieren nach Severity, Code, semanticElement, physicalElement;
20. ein immutable `MetadataMergeResult` zurückgeben.

### Mutation

- semantischen Input niemals mutieren;
- physischen Input nicht als Resultat weiterverwenden;
- bei Bedarf `ModelMetadataCopier` einführen;
- keine Input-Listen für Determinismus in-place sortieren;
- Tests müssen Unverändertheit von Klassen, Attributen, Relationships und Associations prüfen.

## 5.10 `MetadataPostProcessor`

Aus `MetadataReader` extrahieren:

```java
public final class MetadataPostProcessor {

    public void process(ModelMetadata metadata);

    void inferMissingTypes(ModelMetadata metadata);

    void synchronizeAssociations(ModelMetadata metadata);

    String resolveAssociationName(
        ModelMetadata metadata,
        RelationshipMetadata relationship
    );

    AssociationRoleMetadata toAssociationRole(
        RelationshipMetadata relationship
    );

    boolean isAssociationRoleAttribute(
        AttributeMetadata attribute,
        List<RelationshipMetadata> roleRelationships
    );
}
```

Regeln:

- keine fuzzy Matchlogik mehr;
- nur kanonische Relationships verwenden;
- keine Association-Rolle aus einem ambiguous Relationship ableiten;
- Rollennamen innerhalb einer Association eindeutig halten;
- deterministische Rollenreihenfolge;
- physische Association-Tabelleninformationen erhalten.

## 5.11 `MetadataValidator`

```java
public final class MetadataValidator {

    public List<MergeDiagnostic> validate(
        ModelMetadata metadata
    );
}
```

Mindestens diese Invarianten prüfen:

1. Klassen-Map-Key entspricht `ClassMetadata.name`.
2. Attributnamen innerhalb einer Klasse eindeutig.
3. Physische Spaltennamen innerhalb einer Klasse case-insensitiv eindeutig.
4. Relationship Source/Target referenzieren bekannte Klassen, ausser klarer external-Fall.
5. Kein kanonisches Relationship doppelt.
6. Association-Rollennamen innerhalb einer Association eindeutig.
7. Association-Rollen haben bekannte Zielklasse oder klare external-Diagnose.
8. `source == ili2db+ili2c` besitzt physische Evidence.
9. `MergeConfidence.EXACT/MEDIUM` besitzt `mergeReason` und Token.
10. Primärschlüssel innerhalb einer physischen Klasse eindeutig.
11. Keine zwei persistente Properties auf derselben physischen Spalte.
12. Gemergte Relationship-Kardinalität widerspricht nicht unkommentiert der DB-Nullability.

## 5.12 Umbau `MetadataReader`

Bestehende API behalten:

```java
public ModelMetadata readMetadata(String modelName)
    throws SQLException, Ili2cFailure
```

Neue API:

```java
public MetadataReadResult readMetadataResult(
    String modelName,
    MetadataMergePolicy mergePolicy
) throws SQLException, Ili2cFailure;
```

Neuer Typ:

```java
public record MetadataReadResult(
    ModelMetadata metadata,
    List<MergeDiagnostic> diagnostics,
    ModelSelection modelSelection
) {
}
```

Delegation:

```java
public ModelMetadata readMetadata(String modelName)
        throws SQLException, Ili2cFailure {
    return readMetadataResult(
        modelName,
        MetadataMergePolicy.STRICT
    ).metadata();
}
```

Neue Reihenfolge mit ili2c:

1. Modell einmal kompilieren.
2. semantischen Snapshot lesen.
3. `ModelSelection` bestimmen.
4. physischen Snapshot nur für die Auswahl lesen.
5. `MetadataMerger.merge()`.
6. Policy anwenden.
7. Resultat zurückgeben.

Ohne ili2c:

1. `ModelSelection.rootOnly(modelName)`.
2. physischen Snapshot nur für Root lesen.
3. Postprocessing und Validator.
4. Resultat zurückgeben.

Nach P0 darf `MetadataReader` keine eigenen Methoden für Tokenisierung, `findAttribute()` oder `matchRelationship()` mehr enthalten.

## 5.13 P0-A-Testmatrix

### `MergeTokenNormalizerTest`

- `owner_fk`
- `owner_id`
- `ownerId`
- `Model.Topic.Class.Owner`
- `semantic-owner`
- `grid`
- `invalid`
- `id`
- Gross-/Kleinschreibung
- `null`/blank

### `AttributeMatcherTest`

- exakter Qualified Name;
- exakter Name;
- exakte Column;
- eindeutiger ID-Fallback;
- zwei physische Kandidaten → ambiguous;
- zwei semantische Kandidaten → ambiguous;
- Reihenfolge umkehren → identisches Resultat;
- schwacher Token überschreibt keinen exakten Match.

### `RelationshipMatcherTest`

- exaktes Source-Attribut;
- exakte Association-Rolle;
- physisch abweichender Rollenname;
- zwei FKs Source→gleiche Target-Class;
- zwei Association-Rollen auf derselben Target-Class;
- physisches Relationship bereits verwendet;
- eindeutiger normalisierter Match;
- mehrdeutiger normalisierter Match;
- Reihenfolge umkehren → identisches Resultat.

### `MetadataMergerTest`

- `SimpleAddressModel`;
- `CoreIrTestModel`;
- `AssociationCases`;
- physische Spalten bleiben;
- semantische Kardinalitäten bleiben;
- keine Input-Mutation;
- Diagnostics stabil sortiert;
- STRICT wirft;
- DIAGNOSTIC liefert Resultat.

### Neue Fixture

```text
test-models/MergeAmbiguityCases.ili
```

Sie enthält:

- zwei Referenzen derselben Source auf dieselbe Target-Class;
- ähnliche Attribut-/Rollennamen;
- einen absichtlich mehrdeutigen Fall;
- einen eindeutigen Physical-Mismatch-Fall;
- Association mit zwei Rollen auf derselben Zielklasse.

Ergänze passende H2-/ili2db-Fixture-Daten.

Bestehende zu grosszügige Assertions wie `hasSizeGreaterThanOrEqualTo()` oder `isIn("String", "Object")` sind dort, wo der Vertrag eindeutig ist, zu verschärfen.

---

# 6. P0-B – Persistenz und inverse UI-Beziehungen trennen

## 6.1 Ziel

Nach P0-B gilt:

- `GrailsRelationshipMapper` beschreibt nur Persistenz.
- Eine inverse Related-Section ist keine GORM-Collection.
- Normale eingehende `MANY_TO_ONE`-Relationships erzeugen kein `static hasMany` auf der Zielklasse.
- `GrailsInverseRelationshipPlanner` plant Related-Sections direkt aus Core-Relationships und den tatsächlichen Child-Properties.
- Echte to-many-Kompositionen dürfen `hasMany` erzeugen, aber nur bei eindeutigem physischem Mapping.
- Mehrere Beziehungen zwischen denselben Klassen bleiben getrennt.

## 6.2 Neue Persistenztypen

`DomainMapping` soll fachlich verschärft werden:

```java
public record DomainMapping(
    ClassMetadata classMetadata,
    List<DomainProperty> properties,
    List<PersistentCollection> collections,
    List<DomainOwnership> belongsTo,
    List<PersistenceDiagnostic> diagnostics
) {
    public DomainMapping {
        properties = List.copyOf(properties);
        collections = List.copyOf(collections);
        belongsTo = List.copyOf(belongsTo);
        diagnostics = List.copyOf(diagnostics);
    }
}
```

Bevorzugter neuer Collection-Typ:

```java
public record PersistentCollection(
    String name,
    String elementType,
    String mappedByProperty,
    CollectionKind kind,
    RelationshipMetadata relationship
) {
}

public enum CollectionKind {
    COMPOSITION
}
```

Keine Art `REFERENCE_NAVIGATION` oder `RELATED_SECTION` in diesem Persistenztyp.

### `PersistenceDiagnostic`

```java
public record PersistenceDiagnostic(
    Severity severity,
    Code code,
    String ownerClass,
    String relationshipName,
    String message
) {
    public enum Severity {
        WARNING,
        ERROR
    }

    public enum Code {
        COMPOSITION_COLLECTION_UNRESOLVED,
        COMPOSITION_MAPPED_BY_AMBIGUOUS,
        RELATIONSHIP_PROPERTY_AMBIGUOUS,
        DUPLICATE_PHYSICAL_COLUMN_MAPPING
    }
}
```

## 6.3 Änderung `GrailsRelationshipMapper.map()`

Entferne den aktuellen Block, der über `relationshipsByTarget` normale `MANY_TO_ONE`-Relationships in `DomainCollection` umwandelt.

Der Fall:

```text
Child.owner -> Owner
```

liefert nur auf `Child` eine persistente Property. Auf `Owner` wird keine GORM-Collection erzeugt.

### Neue Hilfs-API

```java
public List<RelationshipMetadata> incomingRelationships(
    String targetClassName
);

public List<RelationshipMetadata> outgoingRelationships(
    String sourceClassName
);

public PropertyResolution resolvePropertyForRelationship(
    ClassMetadata sourceClass,
    RelationshipMetadata relationship
);
```

```java
public record PropertyResolution(
    Status status,
    DomainProperty property,
    List<DomainProperty> candidates
) {
    public enum Status {
        RESOLVED,
        NOT_FOUND,
        AMBIGUOUS
    }

    public PropertyResolution {
        candidates = candidates == null
            ? List.of()
            : List.copyOf(candidates);
    }
}
```

`resolvePropertyForRelationship()` darf nie den ersten Kandidaten wählen.

## 6.4 Echte Kompositions-Collections

Eine to-many `COMPOSITION_ATTRIBUTE`-Collection darf nur persistiert werden, wenn:

1. Owner generiert wird;
2. Child generiert wird;
3. Kardinalität to-many ist;
4. physische Speicherung belegt ist;
5. genau ein Child-Property auf Owner zeigt;
6. keine zweite FK-Property denselben Collection-Match mehrdeutig macht.

Bevorzugtes Mapping:

```text
Owner.parts          semantische Komposition 0..*
Part.owner_id        physischer MANY_TO_ONE-FK
```

Generiert:

```groovy
class Owner {
    static hasMany = [parts: Part]
    static mappedBy = [parts: 'owner']
}

class Part {
    Owner owner
    static belongsTo = [owner: Owner]
}
```

Ist `mappedBy` nicht eindeutig:

- nicht raten;
- `COMPOSITION_MAPPED_BY_AMBIGUOUS`;
- keine falsche Join-Tabelle erzeugen;
- STRICT-Generierung blockieren oder Collection bewusst nicht persistent erzeugen;
- Verhalten mit Real-ili2db-Fixture belegen.

## 6.5 Umbau `GrailsInverseRelationshipPlanner`

Keine Abhängigkeit mehr von:

```java
ownerMapping.collections()
```

Neuer Ablauf:

```java
private List<GrailsInverseRelationshipPlan> buildPlans() {
    List<GrailsInverseRelationshipPlan> result = new ArrayList<>();

    for (RelationshipMetadata relationship : eligibleRelationships()) {
        ClassMetadata ownerClass =
            metadata.getClass(relationship.getTargetClass());
        ClassMetadata relatedClass =
            metadata.getClass(relationship.getSourceClass());

        PropertyResolution resolution =
            relationshipMapper.resolvePropertyForRelationship(
                relatedClass,
                relationship
            );

        if (resolution.status()
                != PropertyResolution.Status.RESOLVED) {
            continue;
        }

        result.add(toPlan(
            ownerClass,
            relatedClass,
            resolution.property(),
            relationship
        ));
    }

    return sortedImmutable(result);
}
```

### `eligibleRelationships()`

Nur:

- `MANY_TO_ONE`;
- `ILI2DB_FK` oder `REFERENCE_ATTRIBUTE`;
- nicht `ASSOCIATION_ROLE`;
- nicht Komposition;
- nicht external;
- nicht ordered;
- Source/Target sind generierte reguläre persistente Klassen;
- physische Evidence vorhanden.

### Mehrere FKs zur selben Klasse

```text
Journey.departureStation -> Station
Journey.arrivalStation   -> Station
```

Muss zwei Pläne ergeben:

```text
Station.departingJourneys
Station.arrivingJourneys
```

Anforderungen:

- unterschiedliche `collectionPropertyName`;
- unterschiedliche `relatedPropertyName`;
- kein `hasMany` auf `Station`;
- Query-Service trennt beide Beziehungen korrekt.

## 6.6 `GrailsDomainGenerator`

Neue oder extrahierte Methoden:

```java
private void renderHasMany(
    StringBuilder sb,
    List<PersistentCollection> collections
);

private void renderMappedBy(
    StringBuilder sb,
    List<PersistentCollection> collections
);

private void renderBelongsTo(
    StringBuilder sb,
    List<DomainOwnership> ownerships
);
```

Regeln:

- `hasMany` nur aus `PersistentCollection`;
- `mappedBy` nur bei nichtblankem `mappedByProperty`;
- deterministisch nach Collection-Name sortieren;
- `belongsTo` nur für echte Ownership/Komposition;
- `interlisInverseRelationshipMeta` bleibt UI-/Runtime-Metadaten und erzeugt nie `hasMany`.

Beispiel:

```groovy
static mappedBy = [parts: 'owner']
```

## 6.7 `GrailsInverseRelationshipPlan`

Falls noch nicht vorhanden, ergänze eine explizite Herkunft:

```java
public record GrailsInverseRelationshipPlan(
    String ownerIliClassName,
    String collectionPropertyName,
    String relatedIliClassName,
    String relatedDomainQualifiedName,
    String relatedPropertyName,
    String relationshipName,
    String label,
    String relatedLabel,
    boolean mandatory,
    boolean visible,
    boolean writable,
    boolean persistentCollectionBacked
) {
}
```

Normale inverse Referenzen:

```text
persistentCollectionBacked = false
```

## 6.8 P0-B-Testmatrix

### `GrailsRelationshipMapperTest`

Mindestens:

1. `normalReferenceDoesNotCreateInversePersistentCollection`
2. `twoReferencesToSameTargetDoNotCreateHasMany`
3. `compositionManyCreatesPersistentCollection`
4. `compositionManyResolvesMappedBy`
5. `ambiguousCompositionMappedByProducesDiagnostic`
6. `normalReferenceStillCreatesTypedChildProperty`
7. `normalReferenceStillCreatesInverseUiPlan`
8. `associationRoleDoesNotCreateInverseGormCollection`

### `GrailsDomainGeneratorTest`

Normale Referenz:

- Child enthält typed Property;
- Owner enthält kein `static hasMany`;
- Owner kann inverse UI-Metadaten enthalten.

Zwei FKs:

- kein `hasMany`;
- zwei getrennte inverse Metadaten;
- generierter Code kompiliert.

Komposition:

- `static hasMany`;
- `static mappedBy`;
- `static belongsTo`, sofern physisch korrekt;
- keine ungewollte Join-Tabelle im Realtest.

### Snapshot-Regeln

Jede Änderung einzeln im Progress-Dokument begründen. Beispiel:

```markdown
- Foo.groovy: inverse hasMany entfernt; Related-Section wird query-basiert geplant.
- Owner.groovy: mappedBy ergänzt; reale Child-FK-Spalte wurde eindeutig aufgelöst.
```

Keine globale Snapshot-Aktualisierung ohne Einzelprüfung.

---

# 7. P0-C – Kombinierter Grails-/PostgreSQL-/ili2pg-Vertragstest

## 7.1 Ziel

Der neue Test muss die ausgelieferte Kette vollständig prüfen:

```text
.ili-Modell
→ ili2pg-Import
→ physischer Reader
→ semantischer Reader
→ ModelSelection
→ MetadataMerger
→ Grails-Generator
→ temporäre echte Grails-App
→ generierte Domains und Runtime
→ GORM/Hibernate
→ echtes PostgreSQL-/PostGIS-Schema
→ Runtime-Service-Aufrufe
```

Direktes JDBC-SQL ist nur für Infrastruktur, Testdaten-Setup oder unabhängige Verifikation erlaubt. Es darf nicht die zu testenden Runtime-Services ersetzen.

## 7.2 Neuer Source Set

In `target-grails/build.gradle`:

```groovy
sourceSets {
    grailsPostgresContractTest {
        java.srcDir 'src/grailsPostgresContractTest/java'
        compileClasspath += sourceSets.main.output +
            sourceSets.test.output +
            configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    grailsPostgresContractTestImplementation
        .extendsFrom testImplementation
    grailsPostgresContractTestRuntimeOnly
        .extendsFrom testRuntimeOnly
}

tasks.register('grailsPostgresContractTest', Test) {
    description = 'Runs generated Grails/GORM contracts against a real ili2pg PostgreSQL schema.'
    group = 'verification'
    testClassesDirs =
        sourceSets.grailsPostgresContractTest.output.classesDirs
    classpath =
        sourceSets.grailsPostgresContractTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter tasks.named('grailsRuntimeSmokeTest')
    systemProperty 'ili2pgHome',
        project.findProperty('ili2pgHome')
            ?: '/Users/stefan/apps/ili2pg-5.5.1'
    systemProperty 'contractJdbcUrl',
        project.findProperty('contractJdbcUrl')
            ?: 'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'
    systemProperty 'contractTestRequired',
        project.findProperty('contractTestRequired')
            ?: 'false'
    outputs.upToDateWhen { false }
}
```

Produktions-Harness:

```text
target-grails/src/grailsPostgresContractTest/java/
  ch/interlis/generator/grails/GrailsPostgresContractTest.java
```

## 7.3 Infrastrukturmodus

### Lokal

Bei `contractTestRequired=false` darf fehlende Infrastruktur mit einer klaren `TestAbortedException` gemeldet werden.

### Obligatorischer CI-/Release-Modus

Bei `contractTestRequired=true`:

- fehlende Grails-CLI ist Fehler;
- fehlendes ili2pg ist Fehler;
- fehlendes Docker/Compose ist Fehler;
- nicht erreichbare PostgreSQL-DB ist Fehler;
- kein erfolgreicher Skip.

Fehlermeldungen müssen das konkrete fehlende Werkzeug nennen.

## 7.4 Dediziertes Testmodell

Erstelle:

```text
test-models/P0PersistenceContract.ili
```

Es muss mindestens enthalten:

### Reguläre Klassen

- `Station`
- `Journey`
- `Person`
- `Document`
- `Parcel`
- mindestens eine Component-/Structure-Klasse

### Zwei direkte Referenzen zur selben Zielklasse

```text
Journey.DepartureStation -> Station
Journey.ArrivalStation   -> Station
```

Die physische DB muss zwei unterscheidbare FK-Spalten besitzen.

### Normale 1:n-Referenz

```text
Document.Owner -> Person
```

Keine Komposition.

### To-many-Komposition

Owner mit mehreren Components. Verwende ein Modellmuster, das mit den im Projekt verwendeten ili2pg-Flags deterministisch eine Child-FK-Abbildung erzeugt. Belege die tatsächliche Tabellen-/Spaltenstruktur im Testreport.

### Link-Entity-Association

Binäre Association, die nach echtem ili2pg-Import als Link-Tabelle vorliegt und Quick-Link-fähig ist. Falls ein attributloser Fall eingebettet wird, verwende einen nachweislich als Link-Tabelle abgebildeten Fall, zum Beispiel analog zum bestehenden erweiterten Topic.

### Attributed Association

Association mit eigenem Attribut, die als echte Association-Domain erzeugt wird.

## 7.5 Java-Harness-Ablauf

`GrailsPostgresContractTest` muss:

1. Voraussetzungen prüfen.
2. Compose-PostgreSQL starten oder vorhandene Projekt-DB verwenden.
3. Bereitschaft mit Timeout prüfen.
4. einen eindeutigen Schema-Namen erzeugen.
5. eventuell vorhandenes Schema gleicher Bezeichnung löschen.
6. ili2pg-Import mit den im Projekt etablierten Flags ausführen.
7. `MetadataReader.readMetadataResult(..., STRICT)` ausführen.
8. sicherstellen, dass keine blockierenden Diagnostics bestehen.
9. temporäre Grails-App mit der echten Grails-CLI erstellen.
10. `GrailsTemplateOverlayInstaller` ausführen.
11. `GrailsCrudGenerator` ausführen.
12. Scaffolding mit den Registry-Klassennamen erzeugen.
13. Test-Konfiguration auf PostgreSQL und das eindeutige Schema ausrichten.
14. dynamisch eine Spock-Integration-Spec schreiben.
15. `./gradlew integrationTest` in der temporären App ausführen.
16. bei Fehler vollständige Prozessausgabe sichern.
17. Schema im `finally` löschen.
18. temporäre App bei Fehler nicht kommentarlos löschen; Diagnosepfad reporten.

## 7.6 Generierte Spock-Spec

Pfad in der temporären App:

```text
src/integration-test/groovy/com/example/
  P0PostgresPersistenceContractSpec.groovy
```

Empfohlen:

```groovy
@Integration
@Rollback
class P0PostgresPersistenceContractSpec
        extends Specification {
    InterlisAssociationCommandService interlisAssociationCommandService
    InterlisInverseRelationshipCommandService interlisInverseRelationshipCommandService
    InterlisInverseRelationshipQueryService interlisInverseRelationshipQueryService
}
```

Die Java-Harness-Klasse ermittelt konkrete generierte Klassennamen über `TargetNameRegistry` und rendert sie in die Spec. Keine hartcodierten Annahmen über normalisierte Klassennamen.

## 7.7 Obligatorische Contract-Testfälle

### Test 1 – Normale direkte Referenz

1. Owner speichern.
2. Child speichern und Owner-Property setzen.
3. Hibernate-Session flushen und leeren.
4. Child neu laden.
5. Owner-ID prüfen.
6. Beweisen, dass Owner keine synthetische persistente `hasMany`-Collection benötigt.

### Test 2 – Zwei FKs zur selben Target-Class

1. Departure-Station und Arrival-Station speichern.
2. Journey mit unterschiedlichen Properties speichern.
3. Session leeren.
4. beide IDs exakt prüfen.
5. beide inversen Query-Pläne ausführen.
6. Departure-Resultate dürfen Arrival nicht enthalten und umgekehrt.

### Test 3 – Quick-Link über Runtime-Service

1. Participant und Target speichern.
2. `createQuickLink()` aufrufen.
3. `success == true`, Status 201 und Association-ID prüfen.
4. Association-Domain über GORM neu laden.
5. beide Rollen prüfen.
6. zweiter identischer Aufruf → `DUPLICATE_LINK`.
7. `deleteLink()` aufrufen.
8. Link gelöscht.
9. Participant und Target existieren weiter.

### Test 4 – Ownership-Manipulation

1. Link gehört Participant A.
2. Löschversuch über Participant B.
3. `OWNERSHIP_MISMATCH`.
4. Link bleibt vorhanden.

### Test 5 – Inverse Assign/Reassign

1. Owner A, Owner B und Related speichern.
2. `assign()` auf A.
3. Reload: Property zeigt auf A.
4. Reassign auf B ohne Bestätigung → `REASSIGNMENT_CONFIRMATION_REQUIRED`.
5. Reassign mit Bestätigung.
6. Reload: Property zeigt auf B.

### Test 6 – Validierung und Rollback

1. ungültige Domain/Association erzeugen;
2. Runtime-Service aufrufen;
3. `VALIDATION_FAILED` oder fachlich spezifischen Fehler prüfen;
4. keine partielle Zeile bleibt persistiert.

### Test 7 – Kardinalität

1. Modellfall mit begrenztem Maximum verwenden;
2. zulässige Zuordnung speichern;
3. nächste unzulässige Zuordnung → `CARDINALITY_MAX_EXCEEDED`;
4. DB-Zustand bleibt unverändert.

### Test 8 – Concurrent/Optimistic Locking

Mindestens ein reproduzierbarer Test mit zwei Sessions oder zwei geladenen Versionen. Prüfe den vorgesehenen Fehlercode und dass kein stiller Datenverlust entsteht.

### Test 9 – Komposition

1. Owner und mehrere Components speichern;
2. Session leeren;
3. Collection und Child-Owner prüfen;
4. keine ungewollte Join-Tabelle;
5. Cascade-Verhalten nur soweit testen, wie es bewusst unterstützt und dokumentiert ist.

## 7.8 Diagnoseartefakte

Bei jedem Lauf:

```text
target-grails/build/reports/grails-postgres-contract/
  environment.txt
  generated-app-path.txt
  metadata-diagnostics.json
  integration-test-output.log
  generated-domain-summary.md
  database-mapping-summary.md
```

JDBC-Passwort redigieren.

`database-mapping-summary.md` enthält für die Testklassen:

- ILI-Klasse;
- DB-Tabelle;
- Domainklasse;
- Property;
- physische Spalte;
- Relationship-Art;
- hasMany ja/nein;
- mappedBy;
- belongsTo.

## 7.9 CI-/Script-Integration

Falls keine geeignete obligatorische CI existiert, erstelle:

```text
scripts/run-p0-contract-tests.sh
```

Mit:

```bash
#!/usr/bin/env bash
set -euo pipefail
```

Das Script prüft Voraussetzungen und ruft den Task mit `-PcontractTestRequired=true` auf.

---

# 8. P0-D – Sichere SQL-Identifier

## 8.1 Ziel

Prepared Statements schützen Werte, nicht Identifier. Deshalb werden alle dynamischen Schema-, Tabellen- und Spaltennamen als Identifier-Typen behandelt, validiert und quoted.

## 8.2 Neue Klassen

```text
core/src/main/java/ch/interlis/generator/reader/sql/
  SqlIdentifier.java
  SqlIdentifierKind.java
  SqlIdentifierRenderer.java
  QualifiedSqlName.java
  InvalidSqlIdentifierException.java
```

### `SqlIdentifierKind`

```java
public enum SqlIdentifierKind {
    USER_SUPPLIED,
    DATABASE_DISCOVERED,
    INTERNAL_CONSTANT
}
```

### `SqlIdentifier`

```java
public final class SqlIdentifier {

    private final String value;
    private final SqlIdentifierKind kind;

    public static SqlIdentifier userSupplied(String value);

    public static SqlIdentifier discovered(String value);

    public static SqlIdentifier internal(String value);

    public String value();

    public SqlIdentifierKind kind();

    public boolean requiresQuoting();
}
```

#### Validierung `USER_SUPPLIED`

- nicht null/blank;
- kein Punkt;
- kein NUL;
- keine Steuerzeichen;
- kein Semikolon;
- keine SQL-Kommentarsequenzen `--`, `/*`, `*/`;
- Länge 1 bis 128;
- erlaubte Zeichen: Buchstaben, Ziffern nach erstem Zeichen, `_`, `$`, `-`;
- erstes Zeichen Buchstabe oder `_`.

#### `DATABASE_DISCOVERED`

- nicht null/blank;
- kein NUL;
- breiter Zeichensatz erlaubt;
- beim Rendering Quotes korrekt verdoppeln.

#### `INTERNAL_CONSTANT`

Nur:

```regex
[A-Za-z_][A-Za-z0-9_]*
```

Verletzung ist Programmierfehler.

### `SqlIdentifierRenderer`

```java
public final class SqlIdentifierRenderer {

    private final String quote;

    public static SqlIdentifierRenderer from(
        DatabaseMetaData metadata
    ) throws SQLException;

    public String quote(SqlIdentifier identifier);

    public String qualify(
        SqlIdentifier schema,
        SqlIdentifier object
    );
}
```

Regeln:

- Quote-String aus `DatabaseMetaData.getIdentifierQuoteString()`;
- blanken Quote-String korrekt behandeln;
- enthaltene Quotezeichen verdoppeln;
- null-Schema → nur Objekt;
- niemals `schema + "." + table`.

### `QualifiedSqlName`

```java
public record QualifiedSqlName(
    SqlIdentifier schema,
    SqlIdentifier object
) {
    public String render(SqlIdentifierRenderer renderer) {
        return renderer.qualify(schema, object);
    }
}
```

## 8.3 Umbau `Ili2dbMetadataReader`

Felder:

```java
private final Connection connection;
private final SqlIdentifier schema;
private final SqlIdentifierRenderer identifierRenderer;
```

Konstruktor oder Factory muss die Renderer-Initialisierung über JDBC-Metadaten durchführen.

Empfohlene Factory:

```java
public static Ili2dbMetadataReader create(
    Connection connection,
    String schemaName
) throws SQLException;
```

Interne Helper:

```java
private String metaTable(String tableName);

private String qualifiedDiscoveredTable(String tableName);

private String quotedDiscoveredColumn(String columnName);
```

Entferne die `{schema}`-Stringersetzung. Beispiel:

```java
String sql = "SELECT tag, setting FROM " +
    metaTable("t_ili2db_settings");
```

Dynamische Enum-Tabelle:

```java
String sql = "SELECT * FROM " +
    qualifiedDiscoveredTable(enumTableName);
```

Suche im gesamten Core-Modul nach:

- `+ schemaName`
- `+ tableName`
- `+ columnName`
- `.replace("{schema}"`
- `String.format`/`formatted` mit Identifiern

Jeden Fund klassifizieren und über den Renderer führen, sofern es SQL ist.

Raw Namen in `ModelMetadata`, `ClassMetadata` und `AttributeMetadata` bleiben ungequoted. Quoting ist eine SQL-Rendering-Aufgabe.

## 8.4 Tests

```text
core/src/test/java/ch/interlis/generator/reader/sql/
  SqlIdentifierTest.java
  SqlIdentifierRendererTest.java
  QualifiedSqlNameTest.java
```

Obligatorische Fälle:

- `public`
- `MySchema`
- `my-schema`
- `_private`
- `schema$1`
- `public.other` → reject
- `public;DROP TABLE x` → reject
- `a--comment` → reject
- `a/*x*/` → reject
- blank und NUL → reject
- discovered Identifier mit Leerzeichen → quoted
- discovered Identifier mit Quotezeichen → escaped
- PostgreSQL-Quote `"`
- H2-Quote
- null-Schema
- uppercase Schema behält Case.

Integrationstest:

- H2-Schema mit Grossbuchstaben oder Bindestrich anlegen;
- ili2db-Metatabellen darin anlegen;
- Reader erfolgreich ausführen.

---

# 9. P0-E – Präzise Modellauswahl

## 9.1 Ziel

`readMetadata("RootModel")` liest nur:

```text
RootModel
+ transitive echte Imports/Dependencies
```

Unabhängige Modelle im selben Schema sind ausgeschlossen.

## 9.2 Neue Typen

```text
core/src/main/java/ch/interlis/generator/metadata/selection/
  ModelSelection.java
  ModelSelectionResolver.java
  ModelSelectionSource.java
```

### `ModelSelectionSource`

```java
public enum ModelSelectionSource {
    ILI2C_DEPENDENCY_GRAPH,
    ROOT_ONLY_FALLBACK
}
```

### `ModelSelection`

```java
public record ModelSelection(
    String rootModelName,
    Set<String> includedModelNames,
    ModelSelectionSource source
) {
    public ModelSelection {
        Objects.requireNonNull(rootModelName, "rootModelName");
        includedModelNames = Collections.unmodifiableSet(
            new LinkedHashSet<>(includedModelNames)
        );
        if (!includedModelNames.contains(rootModelName)) {
            throw new IllegalArgumentException(
                "includedModelNames must contain root model"
            );
        }
    }

    public static ModelSelection rootOnly(String rootModelName);

    public boolean includes(String modelName);
}
```

### `ModelSelectionResolver`

```java
public final class ModelSelectionResolver {

    public ModelSelection fromTransferDescription(
        TransferDescription td,
        String rootModelName
    );

    Set<String> transitiveImports(Model root);
}
```

## 9.3 Dependency-Algorithmus

1. Root exakt über `TransferDescription.getElement(rootModelName)` auflösen.
2. Nicht gefunden → Fehler.
3. Root aufnehmen.
4. direkte Imports über die reale ili2c-Metamodel-API lesen.
5. transitiv traversieren.
6. Cycle-Schutz über qualifizierte Modellnamen.
7. Predefined-/Type-Modelle nicht pauschal als physische Zielmodelle aufnehmen.
8. stabile Reihenfolge: Root zuerst, dann Dependencies stabil/topologisch oder lexikografisch.
9. keine Heuristik über „alle Modelle in TransferDescription“.
10. keine Reflection, wenn die verwendete ili2c-Version eine stabile Import-API anbietet.

Der Agent muss die konkrete API der im Build verwendeten ili2c-Version im Code/JAR prüfen und die gewählte Traversierung in Javadoc dokumentieren.

## 9.4 `Ili2cModelReader`

Bevorzugter neuer Typ:

```java
public record Ili2cReadResult(
    ModelMetadata metadata,
    ModelSelection modelSelection,
    TransferDescription transferDescription
) {
}
```

Neue Methode:

```java
public Ili2cReadResult read(
    String modelName
) throws Ili2cFailure;
```

Sie darf das Modell nur einmal kompilieren.

Alternativ mindestens:

```java
public ModelSelection resolveModelSelection(
    String modelName
) throws Ili2cFailure;
```

Keine doppelte Repository-Auflösung oder Doppelkompilierung in `MetadataReader`.

## 9.5 `Ili2dbMetadataReader`

Neue primäre API:

```java
public ModelMetadata readMetadata(
    ModelSelection selection
) throws SQLException;
```

Kompatibilität:

```java
public ModelMetadata readMetadata(String modelName)
        throws SQLException {
    return readMetadata(ModelSelection.rootOnly(modelName));
}
```

`resolveRelevantModelNames()` ist zu entfernen oder auf reine Validierung zu reduzieren.

Neue Methoden:

```java
private Set<String> availableDatabaseModels()
    throws SQLException;

private Set<String> effectiveModelNames(
    ModelSelection selection,
    Set<String> availableDatabaseModels
);
```

Regeln:

- Root muss in DB vorhanden sein;
- nur Schnittmenge aus Auswahl und verfügbaren DB-Modellen lesen;
- fehlende benötigte Dependency diagnostizieren;
- unabhängige DB-Modelle nur als Info erfassen, nie hinzufügen;
- `buildModelPrefixes()` erhält nur die enthaltenen Modellnamen.

## 9.6 DB-only-Fallback

Ohne Modell-Datei und ohne Repository:

- `ModelSelection.rootOnly(modelName)`;
- keine Aufnahme sämtlicher DB-Modelle;
- Warning: Dependency-Graph nicht verfügbar, Root-only wird gelesen.

Keinen neuen `--include-model`-CLI-Parameter in P0 einführen, sofern kein bestehender Use Case ihn zwingend verlangt.

## 9.7 Fixture und Tests

Erstelle:

```text
test-models/ModelSelectionRoot.ili
test-models/ModelSelectionDependency.ili
test-models/ModelSelectionUnrelated.ili
```

Oder eine technisch gleichwertige Repository-Fixture.

Tests:

1. Root importiert Dependency.
2. Dependency importiert transitive Dependency.
3. Unrelated liegt im selben Repository und in `t_ili2db_model`.
4. Selection enthält Root plus Dependencies.
5. Unrelated ist ausgeschlossen.
6. Cycle wird sicher behandelt.
7. DB-only enthält nur Root.
8. Missing Root ist Fehler.
9. Missing Dependency liefert klare Diagnostic.
10. Ergebnisreihenfolge ist deterministisch.
11. generierte Grails-/Django-Artefakte enthalten keine Unrelated-Klassen.

---

# 10. Übergreifende CLI- und Diagnoseintegration

## 10.1 `MetadataReaderService`

Bestehende Methode darf bleiben:

```java
ModelMetadata read(MetadataCommandOptions options)
```

Intern soll sie über ein Resultat arbeiten:

```java
MetadataReadResult readResult(
    MetadataCommandOptions options
) throws SQLException, Ili2cFailure;
```

Konsolenausgabe ergänzen:

```text
Selected models:
  - RootModel
  - DependencyModel

Metadata diagnostics:
  INFO    ...
  WARNING ...
```

Bei blockierenden Diagnostics:

- keine Target-Generierung starten;
- kompakte Zusammenfassung auf stderr;
- strukturierte Details im Exception-/Reportpfad;
- Exit-Code ungleich 0;
- keine halbfertigen Target-Dateien schreiben.

## 10.2 `GenerateCommand`

Der Aufruf:

```java
ModelMetadata metadata = new MetadataReaderService().read(...)
```

darf kompatibel bleiben. Die Service-Implementierung muss aber garantieren, dass nur ein erfolgreich validiertes STRICT-Resultat zurückkommt.

Falls die Diagnostik separat ausgegeben wird, darf sie nicht als „Unexpected error“ mit vollem Stacktrace für erwartete Merge-Konflikte erscheinen. Führe einen gezielten Catch für `MetadataMergeException` ein:

```java
catch (MetadataMergeException e) {
    printDiagnostics(e.diagnostics());
    return ExitCode.DATAERR;
}
```

Falls Picocli `DATAERR` nicht direkt anbietet, verwende einen dokumentierten stabilen Exit-Code.

## 10.3 Diagnose-JSON

Das bestehende Metadata-JSON-Format darf nicht ungefragt gebrochen werden.

Falls ein zusätzlicher Output ohne Scope-Explosion möglich ist, unterstütze:

```text
--diagnostics-json <path>
```

Ansonsten müssen die Contract-Testreports Diagnostics als JSON serialisieren.

---

# 11. Kompatibilität und bewusste Nicht-Ziele

## 11.1 Bestehende Core-IR

Nicht entfernen:

- `RelationshipMetadata.MergeReason`
- `RelationshipMetadata.MergeConfidence`
- bestehende JSON-Felder
- bestehende öffentliche Getter/Setter, soweit nicht zwingend

Neue Diagnostics ergänzen den Vertrag.

## 11.2 Keine vollständige Immutability-Migration

Zulässig:

- interne Copier/Builder;
- immutable Result-Records;
- defensive Copies in neuen Records.

Nicht zulässig:

- alle Modellklassen zu Records konvertieren;
- sämtliche Setter entfernen;
- JSON-Vertrag ohne Migration brechen.

## 11.3 Keine Plugin-Extraktion

Die Grails-Runtime bleibt in P0 im Overlay. Eine spätere Plugin-Auslagerung ist ein separates Arbeitspaket.

## 11.4 Kein schreibbares Embedded-FK-Mapping

`EMBEDDED_FOREIGN_KEY` bleibt read-only, solange der konkrete physische Owner-/FK-Pfad nicht separat bewiesen und implementiert ist.

## 11.5 Keine UI-Neugestaltung

Erlaubt sind nur technische Anpassungen, damit Related-Sections nach Entfernung künstlicher `hasMany`-Collections funktional bleiben.

---

# 12. Ausführliche Teststrategie

## 12.1 Normale Unit-/Integrationstests

```bash
./gradlew clean test --rerun-tasks --no-daemon
```

Alle Module müssen grün sein:

- `core`
- `target-grails`
- `target-django`
- `cli`

## 12.2 Grails Runtime Smoke

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsRuntimeSmokeTest \
  --rerun-tasks --no-daemon
```

Muss beweisen:

- temporäre Grails-App wird erstellt;
- generierte Domains/Enums kompilieren;
- `generate-all` funktioniert;
- Registry und Runtime kompilieren;
- neue Mapper-/Planner-APIs brechen Templates nicht.

## 12.3 Real ili2db Smoke

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:realIli2dbSmokeTest \
  --rerun-tasks --no-daemon
```

Muss weiterhin abdecken:

- Structure-/Composition-Fälle;
- AssociationCases;
- grosses Modell, soweit Repository verfügbar;
- Naming-Kollisionen;
- generierte Groovy-Kompilation;
- neue ModelSelection.

## 12.4 Neuer PostgreSQL Contract

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsPostgresContractTest \
  -PcontractTestRequired=true \
  --rerun-tasks --no-daemon
```

Kein Skip zulässig.

## 12.5 Browser-E2E

Sofern Infrastruktur vorhanden:

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:browserE2eTest \
  --rerun-tasks --no-daemon
```

Es werden keine visuellen Änderungen erwartet. Der Test dient als Regression für Related-Sections und Form-Flows.

## 12.6 Statische Abschlusschecks

```bash
git diff --check
git status --short
```

Zusätzlich nach problematischen Restmustern suchen:

```bash
rg 'first\(|findFirst\(\).*orElse' core/src/main/java/ch/interlis/generator/metadata
rg '\{schema\}' core/src/main/java
rg 'schemaName\s*\+' core/src/main/java
rg 'relationshipsByTarget' target-grails/src/main/java/ch/interlis/generator/grails/GrailsRelationshipMapper.java
rg 'static hasMany' target-grails/src/test/resources/grails-snapshots
```

Jeder Treffer fachlich prüfen; die Befehle sind keine automatische Fehlerdefinition.

---

# 13. Risikoorientierte Zusatztests

Diese Tests sind stark empfohlen und bei vertretbarem Aufwand umzusetzen.

## 13.1 Property-basierte Matcher-Tests

Eigenschaften:

- Permutation der Inputs ändert Matchentscheidungen nicht.
- Irrelevanter Kandidat ändert exakten Match nicht.
- Gleichwertiger zusätzlicher Kandidat erzeugt Ambiguität.
- Schwächerer Token verdrängt keinen stärkeren Match.
- Ein physisches Element wird nie doppelt konsumiert.

## 13.2 Mutation Testing

PIT gezielt auf:

```text
MetadataMerger
AttributeMatcher
RelationshipMatcher
ModelSelectionResolver
SqlIdentifierRenderer
GrailsRelationshipMapper
GrailsInverseRelationshipPlanner
```

Mutationen an Vergleichen, Matchphasen und Ambiguitätszweigen müssen erkannt werden.

## 13.3 JaCoCo

Branch Coverage als Diagnose. Empfohlene Mindestwerte:

- Matcher/Merger: 90 % Branch Coverage
- SQL-Identifier: 95 %
- ModelSelection: 90 %
- Grails Relationship-/Inverse-Planner: 90 %

Keine produktive Logik nur zur Coverage-Erhöhung hinzufügen.

---

# 14. Fachliche Entscheidungsregeln

## 14.1 Physische und semantische Wahrheit

- ili2db/JDBC ist Wahrheit über die physische Persistenz.
- ili2c ist Wahrheit über fachliche Semantik.
- Eine semantische Relationship wird nur dann schreibbare GORM-Persistenz, wenn die physische Abbildung eindeutig nachgewiesen ist.
- Semantische-only Relationships dürfen Dokumentation/read-only unterstützen.
- Physische-only FKs dürfen als technische Referenz erhalten bleiben, müssen aber als nicht semantisch angereichert erkennbar sein.

## 14.2 Fail closed

Bei Unsicherheit:

- keine schreibbare Association;
- kein `hasMany`;
- kein `belongsTo`;
- kein geratenes `mappedBy`;
- kein zufälliger Merge;
- blockierende Diagnostic statt falschem Code.

## 14.3 Determinismus

Resultate dürfen nicht abhängen von:

- JDBC-Resultset-Reihenfolge;
- HashMap-Iteration;
- Reihenfolge der Klassen in `TransferDescription`;
- Reihenfolge der Relationships in Listen;
- Reihenfolge von Association-Rollen im Input.

Sortierung ist nur Präsentation, kein Tie-Breaker.

## 14.4 Keine doppelte physische Wahrheit

Pro physischer FK-/Association-Rollenspalte höchstens ein kanonisches gemergtes Relationship.

Die bestehende Redundanz zwischen:

- `ModelMetadata.relationships`
- `ClassMetadata.relationships`
- `AssociationMetadata.roles`

bleibt vorerst möglich, muss aber konsistent synchronisiert und validiert werden.

## 14.5 GORM ist nicht Navigation

Eine Related-Records-Liste bedeutet nicht automatisch `static hasMany`. `hasMany` ist eine ORM-Entscheidung; Navigation wird über den Query-Service realisiert.

---

# 15. Definition of Done

## 15.1 Merger

- [ ] `MetadataReader` enthält keine fuzzy Matchlogik mehr.
- [ ] Matchphasen sind explizit.
- [ ] Kein first-match-wins.
- [ ] Kein physisches Element wird mehrfach verwendet.
- [ ] Ambiguitäten liefern strukturierte Diagnostics.
- [ ] STRICT blockiert die Generierung.
- [ ] DIAGNOSTIC liefert ein inspizierbares Resultat.
- [ ] physische Spalten bleiben bei eindeutigen Fällen erhalten.
- [ ] semantische Kardinalitäten bleiben erhalten.
- [ ] Association-Synchronisierung verwendet nur kanonische Relationships.
- [ ] Input-Permutation verändert Output und Diagnostics nicht.

## 15.2 Persistenz/UI-Trennung

- [ ] normale inverse `MANY_TO_ONE` erzeugt kein `hasMany`.
- [ ] Related-Sections funktionieren query-basiert weiter.
- [ ] zwei FKs zur selben Zielklasse sind getrennt.
- [ ] echte Kompositions-Collections haben eindeutiges `mappedBy`, wo nötig.
- [ ] kein unbeabsichtigtes Join-Table-Mapping.
- [ ] generierte Domains kompilieren in echter Grails-App.

## 15.3 Realer Vertrag

- [ ] generierte App läuft gegen echtes ili2pg-PostgreSQL-Schema.
- [ ] Quick-Link über Runtime-Service getestet.
- [ ] inverse Assign/Reassign über Runtime-Service getestet.
- [ ] zwei FKs über GORM getestet.
- [ ] Validierung/Rollback getestet.
- [ ] Kardinalität getestet.
- [ ] Concurrent-/Optimistic-Locking-Fall getestet.
- [ ] Kompositionspersistenz getestet.
- [ ] obligatorischer Modus ist nicht skipbar.
- [ ] Reports enthalten keine Secrets.

## 15.4 SQL-Identifier

- [ ] Schema-Identifier validiert.
- [ ] dynamische Tabellen-/Spaltennamen quoted.
- [ ] keine `{schema}`-Ersetzung mehr.
- [ ] Injection-artige Inputs abgelehnt.
- [ ] uppercase und Bindestrichschema getestet.
- [ ] raw physische Namen bleiben unquoted in der IR.

## 15.5 Modellauswahl

- [ ] Root plus transitive Imports.
- [ ] Unrelated-Modelle ausgeschlossen.
- [ ] DB-only liest Root-only.
- [ ] Missing Root ist Fehler.
- [ ] Missing Dependency ist diagnostiziert.
- [ ] stabile Reihenfolge.
- [ ] kein pauschales Hinzufügen aus `t_ili2db_model`.

## 15.6 Qualität

- [ ] alle bestehenden Tests grün oder fachlich einzeln angepasst.
- [ ] keine deaktivierten Tests.
- [ ] keine neuen unkommentierten Skips.
- [ ] keine UI-/CSS-Änderungen.
- [ ] `git diff --check` grün.
- [ ] Progress-Dokument vollständig.
- [ ] technische Doku aktualisiert.

---

# 16. Erwarteter Abschlussbericht des Coding-Agenten

Der Agent liefert am Ende exakt diese Struktur:

```markdown
## Ergebnis

Kurze fachliche Zusammenfassung.

## Baseline

Commit, Java/Grails/ili2pg und ursprüngliche Testergebnisse.

## Geänderte Architektur

- Metadata-Merge
- Model Selection
- SQL Identifier
- Grails Persistence/UI Split
- Real Contract Test

## Neue Klassen

| Pfad | Klasse | Verantwortung |
|---|---|---|

## Geänderte Klassen

| Pfad | Änderung | Kompatibilitätswirkung |
|---|---|---|

## Diagnostics und Failure Policy

Alle Codes mit Severity und Auswirkung.

## Snapshot-Änderungen

Jede Datei einzeln mit fachlicher Begründung.

## Testresultate

Exakte Befehle, Anzahl, Skips, Failures und Resultat.

## Reale DB-Verträge

Welche Tabellen-/FK-/mappedBy-Abbildungen wurden tatsächlich bewiesen?

## Bewusste Nicht-Ziele

Bestätigung: kein UI-Redesign, keine Plugin-Extraktion,
keine vollständige IR-Immutability, kein schreibbares Embedded-FK.

## Verbleibende Risiken

Nur echte Restpunkte.

## Commits

Liste in Reihenfolge.
```

---

# 17. Schlussanweisung

Diese P0-Arbeit ist ein Korrektheitsumbau. Bevorzuge:

- eindeutige Entscheidungen vor Heuristiken;
- strukturierte Diagnostics vor blossen Logs;
- reale Persistenztests vor reiner Source-Kompilation;
- fail-closed/read-only vor geratenem Schreibverhalten;
- explizite Persistenzpläne vor impliziter GORM-Magie;
- kleine, prüfbare Commits vor einer Grossrefaktorierung.

Das bestehende Produkt soll nicht neu erfunden werden. P0 soll sicherstellen, dass der bereits umfangreiche Backend- und Grails-Code auf einer deterministischen, physisch belegten und frameworkgerechten Persistenzgrundlage steht.
