# ili2grails: Fachlich nutzbare INTERLIS-Assoziationen in der generierten Grails-Anwendung

**Status:** Verbindliche Implementationsspezifikation und Arbeitsanweisung für einen LLM-Coding-Agenten  
**Ziel-Repository:** `edigonzales/ili2grails`  
**Vorgesehener Pfad im Repository:** `./docs/association-ux-implementation-spec.md`  
**Begleitender Umsetzungsplan:** `./docs/association-ux-implementation-plan.md`  
**Stand der Spezifikation:** 2026-07-11  
**Primärer Implementationsbereich:** `target-grails`  
**Betroffene Querschnittsbereiche:** `core` nur bei nachgewiesener Metadatenlücke, `cli`, Test-Fixtures, Dokumentation und generierte Grails-Runtime

---

## 1. Verbindlichkeit dieses Dokuments

Dieses Dokument ist die verbindliche Referenz für die Umsetzung.

Der Coding-Agent MUSS vor jeder Implementationsphase:

1. dieses Dokument vollständig lesen;
2. alle im Repository vorhandenen `AGENTS.md`, Skills, Entwicklungsrichtlinien und Build-Hinweise lesen;
3. den aktuellen Codebestand prüfen und die hier genannten Klassen- und Dateipfade gegen den tatsächlichen Stand abgleichen;
4. den Umsetzungsstand in `./docs/association-ux-implementation-plan.md` aktualisieren;
5. alle bereits implementierten Phasen und deren Tests verifizieren;
6. erst danach Code ändern.

Bei einem Widerspruch gilt folgende Priorität:

1. Sicherheits- und Repository-Richtlinien;
2. dieses Dokument;
3. der dokumentierte Umsetzungsplan;
4. bestehende Implementationsdetails;
5. spontane Annahmen des Coding-Agenten.

Der Coding-Agent darf dieses Dokument nicht stillschweigend uminterpretieren, den Umfang reduzieren oder Anforderungen als „später“ markieren, nur weil die Umsetzung anspruchsvoll ist. Offene technische Fragen sind im Umsetzungsplan als Entscheidungspunkt festzuhalten. Eine konservative, korrekte und getestete Umsetzung ist einer scheinbar eleganten, aber semantisch falschen Abkürzung vorzuziehen.

---

## 2. Auftrag an den Coding-Agenten

Erweitere `ili2grails` so, dass im INTERLIS-Modell vorhandene `ASSOCIATION`-Definitionen in der generierten Grails-Anwendung fachlich sinnvoll, effizient, bequem und sicher verwendet werden können.

Die Assoziation darf nicht bloss als technische CRUD-Tabelle erscheinen. Benutzer sollen Beziehungen aus der Perspektive der beteiligten Fachobjekte sehen und bearbeiten können.

Beispiele:

- Auf der Detailseite eines Geschäfts erscheint ein Abschnitt **Dossiers**.
- Auf der Detailseite eines Dossiers erscheint die zugehörige Beziehung zum **Geschäft**.
- Eine Beteiligungsassoziation mit Attributen wird als Liste von Beteiligungen mit Person, Funktion und Gültigkeitszeitraum dargestellt.
- Eine Selbstassoziation verwendet ihre Rollennamen, beispielsweise **Vorgänger** und **Nachfolger**.
- Eine n-äre Assoziation wird nicht fälschlich als gewöhnliche Many-to-Many-Beziehung vereinfacht.
- `ORDERED`, `EXTERNAL` und Kompositionsrollen werden nicht ignoriert oder durch falsche GORM-Cascade-Regeln ersetzt.

Die persistente Wahrheit bleibt die von ili2db erzeugte physische Abbildung. Für explizite Assoziationstabellen bleibt die generierte Association-Domain das persistente Link-Objekt. Die neue Funktionalität ist eine fachliche Projektion und Bedienoberfläche darüber.

---

## 3. Problem und Motivation

### 3.1 Fachliches Problem

INTERLIS-Assoziationen sind eigenständige fachliche Beziehungen. Sie können:

- zwei oder mehr Rollen besitzen;
- Rollen mit eigenen Namen und Kardinalitäten besitzen;
- dieselbe Zielklasse in mehreren Rollen verwenden;
- eigene Attribute besitzen;
- geordnet sein;
- externe Rollen besitzen;
- Aggregations- oder Kompositionssemantik ausdrücken;
- als eigene Tabelle oder in einer optimierten ili2db-Abbildung gespeichert werden.

Eine generische CRUD-Anwendung, die lediglich die Association-Klasse als eigene Menüposition zeigt, ist technisch bedienbar, aber fachlich unbefriedigend. Benutzer denken in der Regel nicht in „Zeilen der Tabelle `GeschaeftDossier`“, sondern in „Dossiers dieses Geschäfts“.

### 3.2 Technisches Problem

Eine naive Abbildung als GORM-`hasMany` oder `manyToMany` ist nicht ausreichend und teilweise falsch:

- eigene Assoziationsattribute würden verloren gehen;
- n-äre Assoziationen lassen sich nicht abbilden;
- Selbstassoziationen mit zwei Rollen derselben Klasse werden mehrdeutig;
- ili2db-Tabellen- und Spaltennamen können von GORM-Konventionen abweichen;
- `ORDERED`, `EXTERNAL` und Komposition werden verfälscht;
- GORM könnte synthetische Join-Tabellen oder FK-Spalten erwarten, die nicht existieren;
- Cascade-Verhalten könnte Daten löschen, die INTERLIS-semantisch nicht gelöscht werden dürfen.

### 3.3 Gewünschtes Ergebnis

Die Anwendung soll:

- Beziehungen auf den Detailseiten der beteiligten Objekte anzeigen;
- Gegenobjekte serverseitig suchen und paginieren;
- einfache binäre Link-Assoziationen direkt hinzufügen und entfernen;
- komplexere Assoziationen kontextuell über die Association-Domain erfassen;
- die aktuelle Rolle vorfüllen und gegen Manipulation schützen;
- eigene Assoziationsattribute anzeigen und bearbeiten;
- Kardinalitäten korrekt berücksichtigen;
- die technische Association-Domain als Fallback erhalten;
- nicht hilfreiche technische Association-Menüpunkte standardmässig ausblenden;
- alle Entscheidungen deterministisch aus der Core-IR und der Grails-Target-Planung ableiten.

---

## 4. Aktueller Architekturstand, den die Umsetzung respektieren muss

Der Coding-Agent MUSS den tatsächlichen aktuellen Stand prüfen. Die folgenden Punkte beschreiben den bei Erstellung dieser Spezifikation analysierten Stand.

### 4.1 Core-IR

Die Core-IR ist der stabile, framework-agnostische Vertrag. Relevant sind insbesondere:

- `ModelMetadata`
- `ClassMetadata`
- `AssociationMetadata`
- `AssociationRoleMetadata`
- `AttributeMetadata`
- `RelationshipMetadata`

`AssociationMetadata` hält bereits:

- den qualifizierten Assoziationsnamen;
- die Assoziationsklasse;
- physische Tabellen- und SQL-Namen;
- Rollen;
- eigene Attribute.

`AssociationRoleMetadata` hält bereits:

- Rollenname;
- Zielklasse;
- Gegenrollenname;
- Kardinalität;
- `mandatory`;
- `ordered`;
- `external`;
- `composition`;
- semantische und physische Namen;
- Merge-Diagnostik.

**Grundregel:** Die Grails-Erweiterung liest diese Informationen aus der Core-IR. Sie darf keine ili2c- oder ili2db-Readerlogik im Target nachbauen.

### 4.2 `GrailsRelationshipMapper`

Aktueller Pfad:

```text
target-grails/src/main/java/ch/interlis/generator/grails/GrailsRelationshipMapper.java
```

Die Klasse:

- entscheidet, welche Klassen als Grails-Domain generiert werden;
- bildet normale Referenzen und Kompositionen auf Properties und Collections ab;
- liest Association-Rollen bevorzugt aus `AssociationMetadata`;
- bildet Association-Rollen als Properties auf der Association-Domain ab;
- erzeugt bewusst keine inversen Collections für Association-Rollen.

Diese konservative Persistenzentscheidung ist beizubehalten.

Die neue Association-UX darf nicht durch eine pauschale Änderung von `GrailsRelationshipMapper.map(...)` entstehen, die auf allen beteiligten Klassen `hasMany` erzeugt. Stattdessen soll eine separate Association-Planungs- und Runtime-Schicht entstehen.

### 4.3 `GrailsDomainGenerator`

Aktueller Pfad:

```text
target-grails/src/main/java/ch/interlis/generator/grails/GrailsDomainGenerator.java
```

Die Klasse erzeugt unter anderem:

- Domain-Properties;
- GORM-Mapping;
- Constraints;
- `geometryMeta`;
- `interlisFieldMeta`;
- `interlisDisplayMeta`;
- `interlisRelationshipMeta`.

Die neuen Association-Informationen sollen nicht als unstrukturierte Sonderfälle in diese bestehenden Maps gequetscht werden. Es ist eine eigenständige, generierte Association-Registry vorgesehen.

### 4.4 `GrailsCrudGenerator`

Aktueller Pfad:

```text
target-grails/src/main/java/ch/interlis/generator/grails/GrailsCrudGenerator.java
```

Die Methode:

```java
public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException
```

orchestriert derzeit insbesondere Enum- und Domain-Generierung sowie Projektanpassungen. Die neue Registry-Generierung ist hier deterministisch einzuhängen.

### 4.5 Template-Overlay und Runtime

Aktueller Overlay-Wurzelpfad:

```text
target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/
```

Relevante bestehende Runtime-Klassen:

```text
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy
```

Relevante Templates:

```text
src/main/templates/scaffolding/Controller.groovy
src/main/templates/scaffolding/show.gsp
src/main/templates/scaffolding/_form.gsp
src/main/templates/scaffolding/_relationship-fields.gsp
```

Relevante Assets:

```text
grails-app/assets/javascripts/ili-form-ux.js
grails-app/assets/stylesheets/ili-modern.css
grails-app/views/layouts/main.gsp
```

Die Runtime ist der richtige Ort für wiederverwendbare Grails-Logik. Generierte Fachcontroller sollen dünn bleiben.

### 4.6 Bestehende Relationship-Auswahl

`InterlisRelationshipOptions.optionPage(...)` stellt bereits:

- serverseitige Suche;
- Paging;
- Sortierung;
- Display-Label-Auflösung;
- Fallback auf bestehende Select-Optionen

bereit.

Diese Funktionalität ist zu refaktorieren und für Association-Rollen wiederzuverwenden. Es darf kein zweiter, abweichender Autocomplete-Stack entstehen.

### 4.7 Bestehende Tests

Relevante Testbereiche sind unter anderem:

```text
target-grails/src/test/java/ch/interlis/generator/grails/
target-grails/src/grailsRuntimeSmokeTest/java/ch/interlis/generator/grails/
target-grails/src/realIli2dbSmokeTest/java/ch/interlis/generator/grails/
target-grails/src/browserE2eTest/java/ch/interlis/generator/grails/
target-grails/src/test/resources/grails-snapshots/
test-models/AssociationCases.ili
```

`AssociationCases.ili` deckt bereits wichtige Fälle ab:

- Assoziation ohne Attribute;
- Assoziation mit eigenem Attribut;
- zwei Rollen derselben Zielklasse;
- abweichende physische Rollenspalten;
- `EXTERNAL`;
- `COMPOSITE`;
- Assoziation in erweitertem Topic.

Diese Testbasis ist zu erweitern, nicht zu ersetzen.

---

## 5. Verbindliche Architekturprinzipien

### 5.1 Core-first und Target-Trennung

- Die Core-IR bleibt framework-agnostisch.
- Grails-spezifische UI-, Controller-, Service- und Naming-Entscheidungen bleiben im Modul `target-grails`.
- Eine Core-IR-Erweiterung ist nur zulässig, wenn ein konkreter Test beweist, dass eine fachlich notwendige Information fehlt.
- Vor einer Core-IR-Erweiterung muss der Umsetzungsplan die Lücke, Herkunft, JSON-Kompatibilität und Auswirkungen auf andere Targets dokumentieren.

### 5.2 Association-Domain bleibt persistente Wahrheit

Bei einer expliziten Assoziationstabelle bleibt die generierte Association-Domain massgebend.

Nicht zulässig:

```groovy
static hasMany = [dossiers: Dossier]
static belongsTo = [geschaeft: Geschaeft]
```

wenn diese Deklarationen eine von ili2db abweichende Join-Struktur oder Cascade-Semantik erzeugen.

Zulässig ist eine fachliche, runtime-geladene Related-List ohne persistente GORM-Collection.

### 5.3 Keine erfundenen Datenbankstrukturen

Der Generator darf nicht erfinden:

- FK-Spalten;
- Join-Tabellen;
- Sortierspalten;
- Unique-Constraints;
- Cascade-Regeln;
- Association-OIDs;
- Gegenrollen;
- direkte Many-to-Many-Mappings.

Fehlt eine sichere physische Abbildung, muss die UI read-only sein oder auf das bestehende generische CRUD-Fallback verweisen.

### 5.4 SSR statt SPA

Die Anwendung bleibt serverseitig gerendert:

- Grails/GSP;
- Bootstrap als technische Basis;
- progressive JavaScript-Erweiterung;
- keine neue SPA;
- keine zusätzliche Frontend-Buildchain;
- keine CDN-Abhängigkeiten.

### 5.5 Ruhige, fachliche UI

- keine neue Kartenwand;
- keine starken Schatten;
- kleine Radien;
- Tabellen und klar getrennte Abschnitte;
- rote Akzente nur zurückhaltend;
- keine technischen Tabellen- oder Klassennamen als primäre Benutzerbegriffe;
- keine horizontale Hauptseiten-Scrollbar;
- mobile und schmale Layouts müssen funktionieren.

### 5.6 Sicheres Fallback

Wenn die Association-Planung nicht eindeutig ist:

- keine schreibbare kontextuelle UI erzeugen;
- Diagnose in generierte Registry und Tests aufnehmen;
- bestehendes Association-CRUD weiterhin generieren;
- optional einen read-only Abschnitt oder Link zum Association-CRUD zeigen.

---

## 6. Ziel-UX

### 6.1 Binäre Assoziation ohne eigene Attribute

Beispiel:

```ili
ASSOCIATION GeschaeftDossier =
  Geschaeft -- {1} Geschaeft;
  Dossier   -- {0..*} Dossier;
END GeschaeftDossier;
```

Detailseite Geschäft:

```text
Geschäft: Umbenennung Flurname «Chräbsbach»
────────────────────────────────────────────────────────

Stammdaten
  Titel       Umbenennung Flurname «Chräbsbach»
  Status      In Bearbeitung

Dossiers                                              4
────────────────────────────────────────────────────────
Bezeichnung                 Status            Aktionen
Antrag Gemeinde             Eingegangen       Öffnen  Entfernen
Stellungnahmen              In Bearbeitung    Öffnen  Entfernen
Beschluss                   Abgeschlossen     Öffnen  Entfernen

[Dossier zuordnen]
```

Die Aktion **Dossier zuordnen**:

- kennt das Geschäft bereits;
- fragt nur das Gegenobjekt ab;
- verwendet serverseitigen Autocomplete;
- lädt nie alle Dossiers;
- prüft die Kardinalität;
- erstellt die Association-Domain;
- kehrt zur Geschäft-Detailseite zurück.

Detailseite Dossier:

```text
Geschäft
────────────────────────────────────────────────────────
Umbenennung Flurname «Chräbsbach»

[Geschäft öffnen] [Zuordnung ändern]
```

Die Darstellung wird aus der Perspektive des aktuellen Rollenendes abgeleitet.

### 6.2 Assoziation mit eigenen Attributen

Beispiel:

```ili
ASSOCIATION Beteiligung =
  Geschaeft -- {1} Geschaeft;
  Person    -- {1} Person;

  Funktion : MANDATORY TEXT*100;
  Von      : INTERLIS.XMLDate;
  Bis      : INTERLIS.XMLDate;
END Beteiligung;
```

Detailseite Geschäft:

```text
Beteiligte                                              2
──────────────────────────────────────────────────────────────
Person                 Funktion              Von         Bis
Anna Muster            Gesuchstellerin       12.06.2026  –
Peter Beispiel         Sachbearbeiter        14.06.2026  –

[Beteiligung hinzufügen]
```

**Beteiligung hinzufügen** öffnet ein kontextuelles Formular der Association-Domain:

- Rolle `Geschaeft` ist vorgefüllt;
- die vorgefüllte Rolle ist nicht frei manipulierbar;
- Person wird über Autocomplete gewählt;
- eigene Attribute werden mit den bestehenden Grails-Formmechanismen gerendert;
- nach Speichern erfolgt eine sichere Rückleitung zur Geschäft-Detailseite.

### 6.3 Selbstassoziation

Beispiel:

```ili
ASSOCIATION GeschaeftNachfolge =
  Vorgaenger -- {0..1} Geschaeft;
  Nachfolger -- {0..*} Geschaeft;
END GeschaeftNachfolge;
```

Auf derselben Geschäft-Detailseite erscheinen zwei getrennte Perspektiven:

```text
Vorgänger
────────────────────────────────────────
Geschäft A                              Öffnen

Nachfolger
────────────────────────────────────────
Geschäft C                              Öffnen  Entfernen
Geschäft D                              Öffnen  Entfernen
```

Die UI darf nicht bloss zweimal „Geschäft“ anzeigen. Der Kontext wird durch den festen Rollennamen identifiziert.

### 6.4 n-äre Assoziation

Bei drei oder mehr Rollen:

- Related-List auf jedem beteiligten Objekt;
- aktuelle Rolle wird im Kontext fixiert;
- alle übrigen Rollen werden im Association-Formular angezeigt;
- keine falsche binäre Many-to-Many-Projektion;
- Association-Domain bleibt sichtbar und editierbar;
- Quick-Link ist nicht zulässig.

### 6.5 `EXTERNAL`

Für externe Rollen gilt:

- Zielobjekt darf ausgewählt oder angezeigt werden, sofern physisch und autorisiert verfügbar;
- kein Cascade-Delete;
- kein automatisches Erstellen des Zielobjekts;
- Quick-Link nur, wenn die gesamte Speicherabbildung sicher ist;
- im Zweifel kontextuelles Formular oder read-only.

### 6.6 Komposition

Für eine Kompositionsrolle gilt:

- keine generische „bestehendes Objekt beliebig zuordnen“-Semantik;
- kein Entfernen, das verwaiste Komponenten erzeugt;
- keine automatische Cascade-Regel erfinden;
- initial konservativ als kontextuelles Formular oder read-only behandeln;
- erst nach nachgewiesener physischer und fachlicher Abbildung eine eingebettete Child-UX aktivieren.

### 6.7 `ORDERED`

Für geordnete Rollen gilt:

- Reihenfolge anzeigen;
- keine Sortierspalte erfinden;
- Schreibfunktion nur aktivieren, wenn die physische Reihenfolge eindeutig aus Metadaten ableitbar ist;
- ansonsten read-only mit Diagnose;
- spätere Schreibfunktion transaktional und konfliktarm implementieren.

---

## 7. Funktionsumfang und Priorisierung

### 7.1 Muss-Funktionalität

Die vollständige Umsetzung MUSS mindestens leisten:

1. Association-Planung aus `AssociationMetadata`;
2. deterministisch generierte Association-Registry;
3. Related-Sections auf den Show-Seiten beteiligter Domains;
4. serverseitig paginierte Association-Listen;
5. serverseitig paginierte Gegenobjekt-Suche;
6. Quick-Link für sichere binäre Link-Assoziationen ohne eigene Attribute;
7. kontextuelles Association-Formular für Assoziationen mit Attributen;
8. Selbstassoziationen;
9. n-äre Assoziationen über Association-Formulare;
10. sichere Rückleitung nach Create/Edit/Delete;
11. Kardinalitätsprüfung für binäre Assoziationen;
12. verständliche Fehler;
13. Navigation ohne technische Association-Menüflut;
14. read-only/Fallback für nicht sicher schreibbare Fälle;
15. Unit-, Integrations-, Real-ili2db- und Browser-E2E-Tests;
16. Dokumentation und Fortschrittsprotokoll.

### 7.2 Soll-Funktionalität

- Filter und Sortierung in Related-Lists;
- Anzahl Beziehungen im Abschnittskopf;
- direkte Links zum Gegenobjekt und zur Association-Domain;
- leere Zustände;
- barrierearme Bedienung;
- ausgewählte Option bleibt bei Validierungsfehlern sichtbar;
- keine N+1-Abfrage über unbeschränkte Datenmengen;
- Extension Points für spätere Autorisierung.

### 7.3 Nicht-Ziele

Nicht Teil dieses Auftrags:

- Authentisierung;
- konkrete Fachrollen oder Berechtigungsmodelle;
- Audit-Log;
- vollständige Topologie- oder INTERLIS-Gesamtvalidierung bei jeder UI-Aktion;
- Ersatz von ili2db;
- Migration bestehender Datenbanken;
- neue SPA;
- generische Workflow-Engine;
- pauschale direkte Many-to-Many-GORM-Abbildung;
- unkontrollierte Cascade-Deletes.

---

## 8. Zielarchitektur

```text
Core-IR
  ModelMetadata
    └── AssociationMetadata
          ├── AssociationRoleMetadata
          └── AttributeMetadata

target-grails: Planungszeit
  GrailsRelationshipMapper
  GrailsAssociationPlanner
    ├── GrailsAssociationPlan
    ├── GrailsAssociationRolePlan
    ├── GrailsAssociationAttributePlan
    └── GrailsAssociationContextPlan
  GrailsAssociationRegistryGenerator
  GrailsDomainGenerator
  GrailsCrudGenerator

Generierte Grails-Anwendung
  ch.interlis.generator.grails.generated.InterlisAssociationRegistry
  ch.interlis.generator.grails.runtime.InterlisAssociationRegistrySupport
  InterlisAssociationQueryService
  InterlisAssociationCommandService
  InterlisAssociationContextSupport
  InterlisCrudControllerSupport
  InterlisRelationshipOptions
  InterlisNavigationSupport

GSP
  show.gsp
    └── _association-sections.gsp
          ├── read-only / related list
          ├── quick add
          └── contextual form link

  _form.gsp
    └── _relationship-fields.gsp
          └── fixed contextual role hidden/read-only

JavaScript
  ili-form-ux.js
    └── gemeinsamer Relationship-/Association-Autocomplete
```

---

## 9. Neue Planungsmodelle im Grails-Target

Alle folgenden Klassen liegen standardmässig in:

```text
target-grails/src/main/java/ch/interlis/generator/grails/
```

Unterpakete sind zulässig, falls sie konsistent eingeführt und alle Tests/Imports angepasst werden. Eine halbherzige Mischung ist nicht zulässig.

### 9.1 `AssociationStorageKind`

Neue Enum:

```java
public enum AssociationStorageKind {
    LINK_ENTITY,
    EMBEDDED_FOREIGN_KEY,
    UNMAPPED
}
```

Semantik:

- `LINK_ENTITY`: Eine physisch gemappte Association-Domain besitzt die Rollen-FKs.
- `EMBEDDED_FOREIGN_KEY`: Die Assoziation ist physisch in einer beteiligten Klasse eingebettet.
- `UNMAPPED`: Keine hinreichend sichere physische Schreibabbildung.

In den ersten Phasen MUSS `LINK_ENTITY` vollständig funktionieren. `EMBEDDED_FOREIGN_KEY` darf erst schreibbar werden, wenn die Zuordnung durch Tests mit echter ili2db-Struktur bewiesen ist.

### 9.2 `AssociationPresentationKind`

Neue Enum:

```java
public enum AssociationPresentationKind {
    QUICK_LINK,
    RELATED_TO_ONE,
    RELATED_LIST,
    CONTEXTUAL_FORM,
    NARY_CONTEXTUAL_FORM,
    READ_ONLY
}
```

Bedeutung:

- `QUICK_LINK`: binär, keine eigenen Attribute, sicher schreibbar;
- `RELATED_TO_ONE`: aus aktueller Perspektive höchstens ein Gegenobjekt;
- `RELATED_LIST`: mehrere Gegenobjekte;
- `CONTEXTUAL_FORM`: Association-Domain mit Attributen oder Spezialsemantik;
- `NARY_CONTEXTUAL_FORM`: drei oder mehr Rollen;
- `READ_ONLY`: Anzeige, aber keine sichere Mutation.

Ein Kontext kann eine Anzeigeart und separat eine Create-Strategie besitzen. Falls dies die Implementierung vereinfacht, darf zusätzlich eine Enum `AssociationCreateMode` eingeführt werden:

```java
public enum AssociationCreateMode {
    NONE,
    QUICK,
    CONTEXTUAL_FORM
}
```

### 9.3 `GrailsAssociationRolePlan`

Verbindliche Zielstruktur:

```java
public record GrailsAssociationRolePlan(
    String roleName,
    String roleLabel,
    String domainPropertyName,
    String targetIliClassName,
    String targetDomainClassName,
    String targetDomainQualifiedName,
    Integer minCardinality,
    Integer maxCardinality,
    boolean mandatory,
    boolean ordered,
    boolean external,
    boolean composition,
    String physicalName,
    String semanticName
) {
    public boolean isUnbounded();
    public boolean isToOne();
    public boolean isToMany();
}
```

Methodensemantik:

```java
public boolean isUnbounded() {
    return maxCardinality != null && maxCardinality == -1;
}

public boolean isToOne() {
    return maxCardinality != null && maxCardinality == 1;
}

public boolean isToMany() {
    return maxCardinality == null || maxCardinality == -1 || maxCardinality > 1;
}
```

`null` bedeutet unbekannt, nicht automatisch `0`, `1` oder unbeschränkt.

### 9.4 `GrailsAssociationAttributePlan`

Verbindliche Zielstruktur:

```java
public record GrailsAssociationAttributePlan(
    String iliName,
    String domainPropertyName,
    String javaType,
    String coreType,
    String label,
    String documentation,
    String unit,
    boolean mandatory,
    Integer maxLength,
    String minInclusive,
    String maxInclusive,
    Integer precision,
    Integer scale,
    boolean geometry,
    String geometryKind,
    Integer geometrySrid,
    String enumType
) {
}
```

Die Struktur soll nur echte Association-Attribute enthalten:

- keine Primärschlüssel;
- keine Rollen-FK-Attribute;
- keine technischen ili2db-Spalten;
- keine doppelt als Rolle und Attribut dargestellten Felder.

Die Zuordnung muss anhand des bereits von `GrailsRelationshipMapper` erzeugten `DomainMapping` und der Relationship-Metadaten erfolgen, nicht allein anhand heuristischer Namensvergleiche.

### 9.5 `GrailsAssociationContextPlan`

Ein Kontext beschreibt eine Assoziation aus der Perspektive genau einer festen Rolle.

```java
public record GrailsAssociationContextPlan(
    String contextId,
    String messageCode,
    String defaultLabel,
    String participantIliClassName,
    String participantDomainClassName,
    String participantDomainQualifiedName,
    String fixedRoleName,
    String fixedRolePropertyName,
    List<String> editableRoleNames,
    List<String> editableRolePropertyNames,
    Integer perspectiveMinCardinality,
    Integer perspectiveMaxCardinality,
    AssociationPresentationKind presentationKind,
    AssociationCreateMode createMode,
    boolean writable,
    boolean removable,
    boolean showAssociationObjectLink,
    List<String> diagnostics
) {
}
```

#### Stabiler `contextId`

Der `contextId` MUSS deterministisch und kollisionsfrei sein.

Empfohlen:

```text
<qualified-association-name>::<fixed-role-name>
```

Beispiel:

```text
AssociationCases.Base.SameTargetAssociation::PrimaryPerson
```

Der Wert wird URL-encodiert, aber nicht zufällig gehasht. Er muss in Testausgaben verständlich bleiben.

#### Perspektivkardinalität

Bei einer binären Assoziation gilt:

- der aktuelle Kontext fixiert Rolle A;
- die Anzahl erreichbarer Gegenobjekte wird durch die Kardinalität der anderen Rolle B bestimmt;
- daher stammen `perspectiveMinCardinality` und `perspectiveMaxCardinality` aus Rolle B.

Diese Regel ist mit expliziten Tests abzusichern. Der Agent darf die Kardinalität nicht intuitiv „irgendwie“ von der festen Rolle übernehmen.

Bei n-ären Assoziationen ist eine vereinfachte globale Zählprüfung nicht ohne Weiteres zulässig. Die Anzeige darf funktionieren; schreibende Kardinalitätsprüfungen müssen konservativ bleiben und im Umsetzungsplan dokumentiert werden.

### 9.6 `GrailsAssociationPlan`

Verbindliche Zielstruktur:

```java
public record GrailsAssociationPlan(
    String associationName,
    String associationIliClassName,
    String associationDomainClassName,
    String associationDomainQualifiedName,
    String associationControllerName,
    String associationViewPath,
    String physicalTable,
    String physicalSqlName,
    AssociationStorageKind storageKind,
    boolean physicalMappingPresent,
    boolean writable,
    boolean showInNavigation,
    List<GrailsAssociationRolePlan> roles,
    List<GrailsAssociationAttributePlan> attributes,
    List<GrailsAssociationContextPlan> contexts,
    List<String> diagnostics
) {
    public boolean isBinary();
    public boolean isNary();
    public boolean hasOwnAttributes();
    public Optional<GrailsAssociationRolePlan> role(String roleName);
}
```

Listen sind im Konstruktor defensiv zu kopieren. Die Reihenfolge MUSS deterministisch sein:

- Rollen nach Rollenname und Zielklasse;
- Attribute nach Domain-Property-Name;
- Kontexte nach `contextId`;
- Diagnosen lexikografisch.

---

## 10. `GrailsAssociationPlanner`

Neue Klasse:

```text
target-grails/src/main/java/ch/interlis/generator/grails/GrailsAssociationPlanner.java
```

### 10.1 Öffentliche API

```java
public final class GrailsAssociationPlanner {

    public static GrailsAssociationPlanner forMetadata(
        ModelMetadata metadata,
        GenerationConfig config,
        TargetNameRegistry registry,
        GrailsRelationshipMapper relationshipMapper
    );

    public List<GrailsAssociationPlan> plans();

    public List<GrailsAssociationContextPlan> contextsForParticipant(
        String participantIliClassName
    );

    public Optional<GrailsAssociationPlan> findPlan(String associationName);

    public boolean showDomainInNavigation(String iliClassName);

    public boolean isAssociationDomain(String iliClassName);
}
```

Falls `GrailsRelationshipMapper` nicht von aussen übergeben werden soll, darf der Planner ihn intern mit denselben Objekten aufbauen. Es darf aber nicht für Planner und Domain-Generator je eine abweichende Mapper-Konfiguration entstehen.

### 10.2 Interne Hauptmethoden

Mindestens folgende private Methoden oder äquivalente klar getrennte Funktionen sind vorzusehen:

```java
private GrailsAssociationPlan buildPlan(AssociationMetadata association);

private AssociationStorageKind resolveStorageKind(
    AssociationMetadata association,
    ClassMetadata associationClass,
    GrailsRelationshipMapper.DomainMapping domainMapping
);

private List<GrailsAssociationRolePlan> buildRolePlans(
    AssociationMetadata association,
    ClassMetadata associationClass,
    GrailsRelationshipMapper.DomainMapping domainMapping
);

private Optional<GrailsRelationshipMapper.DomainProperty> findRoleProperty(
    AssociationMetadata association,
    AssociationRoleMetadata role,
    GrailsRelationshipMapper.DomainMapping domainMapping
);

private List<GrailsAssociationAttributePlan> buildAttributePlans(
    AssociationMetadata association,
    GrailsRelationshipMapper.DomainMapping domainMapping,
    List<GrailsAssociationRolePlan> roles
);

private List<GrailsAssociationContextPlan> buildContextPlans(
    AssociationMetadata association,
    List<GrailsAssociationRolePlan> roles,
    List<GrailsAssociationAttributePlan> attributes,
    AssociationStorageKind storageKind
);

private AssociationPresentationKind resolvePresentationKind(
    GrailsAssociationRolePlan fixedRole,
    List<GrailsAssociationRolePlan> otherRoles,
    List<GrailsAssociationAttributePlan> attributes,
    AssociationStorageKind storageKind
);

private AssociationCreateMode resolveCreateMode(
    List<GrailsAssociationRolePlan> roles,
    List<GrailsAssociationAttributePlan> attributes,
    AssociationStorageKind storageKind
);

private boolean isQuickLinkEligible(
    List<GrailsAssociationRolePlan> roles,
    List<GrailsAssociationAttributePlan> attributes,
    AssociationStorageKind storageKind
);

private String defaultContextLabel(
    GrailsAssociationRolePlan fixedRole,
    List<GrailsAssociationRolePlan> otherRoles
);

private String contextMessageCode(
    AssociationMetadata association,
    AssociationRoleMetadata fixedRole
);
```

### 10.3 Rolle zu generierter Domain-Property auflösen

Der Planner MUSS die tatsächlich generierte Property verwenden.

Vorgehen:

1. Association-Klasse via `association.getAssociationClass()` und `metadata.getClass(...)` ermitteln.
2. `relationshipMapper.map(associationClass)` aufrufen.
3. In `DomainMapping.properties()` eine Property suchen, deren Relationship:
   - `semanticKind == ASSOCIATION_ROLE`;
   - `associationName` zur Association passt;
   - `targetRoleName` zum Rollennamen passt.
4. Fallbacks nur in dieser Reihenfolge:
   - `physicalName`;
   - `sourceAttribute`;
   - semantischer Name;
   - Zielklasse plus eindeutiger Rollenname.
5. Bei Mehrdeutigkeit:
   - nicht irgendeine Property wählen;
   - Diagnose `AMBIGUOUS_ROLE_PROPERTY`;
   - Kontext read-only;
   - Unit-Test ergänzen.

### 10.4 Quick-Link-Kriterien

`isQuickLinkEligible(...)` darf nur `true` liefern, wenn alle Bedingungen erfüllt sind:

- genau zwei Rollen;
- `storageKind == LINK_ENTITY`;
- Association-Domain physisch gemappt und generiert;
- beide Rollen besitzen eindeutig aufgelöste Domain-Properties;
- keine eigenen Association-Attribute;
- keine Rolle ist `ordered`;
- keine Rolle ist `composition`;
- externe Rollen sind initial nicht quick-link-fähig, ausser ein Real-ili2db-Test beweist den sicheren Fall;
- beide Zielklassen sind als persistente Grails-Domains verfügbar;
- keine Merge-Diagnose mit Confidence `NONE`;
- Association-UI-Modus erlaubt Schreiben.

### 10.5 Fallback-Entscheidung

Wenn Quick-Link nicht möglich, aber die Association-Domain vollständig persistierbar ist:

- `createMode = CONTEXTUAL_FORM`;
- Related-List darf editierbar sein;
- aktuelle feste Rolle wird im Association-Formular vorgefüllt.

Wenn die Association-Domain nicht sicher persistierbar ist:

- `createMode = NONE`;
- `presentationKind = READ_ONLY`;
- `writable = false`;
- Diagnose ausgeben.

---

## 11. Generierte Association-Registry

### 11.1 Generator

Neue Klasse:

```text
target-grails/src/main/java/ch/interlis/generator/grails/GrailsAssociationRegistryGenerator.java
```

Öffentliche API:

```java
public final class GrailsAssociationRegistryGenerator {

    public void generate(
        ModelMetadata metadata,
        GenerationConfig config,
        TargetNameRegistry registry,
        GrailsAssociationPlanner planner
    ) throws IOException;

    Path targetPath(GenerationConfig config);

    String renderRegistry(
        List<GrailsAssociationPlan> plans,
        GenerationConfig config
    );
}
```

Zielpfad in der generierten Anwendung:

```text
src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy
```

Fester Paketname:

```groovy
package ch.interlis.generator.grails.generated
```

Der feste Paketname ist absichtlich gewählt, damit die kopierte Runtime ohne dynamischen Import auf die Registry zugreifen kann.

### 11.2 Inhalt der generierten Registry

Zielstruktur:

```groovy
package ch.interlis.generator.grails.generated

final class InterlisAssociationRegistry {

    static final Map<String, Map<String, Object>> ASSOCIATIONS = [
        // deterministisch generiert
    ]

    static final Map<String, Map<String, Object>> CONTEXTS = [
        // contextId -> context descriptor
    ]

    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [
        // voll qualifizierter Grails-Domain-Klassenname -> contextIds
    ]

    static final Map<String, Map<String, Object>> ENTITIES = [
        // Domainklasse -> kind/showInNavigation/iliName
    ]

    static Map<String, Object> association(String associationName) {
        return ASSOCIATIONS[associationName]
    }

    static Map<String, Object> context(String contextId) {
        return CONTEXTS[contextId]
    }

    static List<Map<String, Object>> contextsForParticipant(String domainClassName) {
        return (CONTEXT_IDS_BY_PARTICIPANT[domainClassName] ?: [])
            .collect { String id -> CONTEXTS[id] }
            .findAll { it != null }
    }

    static boolean showInNavigation(String domainClassName) {
        Map entity = ENTITIES[domainClassName]
        return entity == null || entity.showInNavigation != false
    }

    private InterlisAssociationRegistry() {
    }
}
```

### 11.3 Descriptor-Inhalt

Association-Map:

```groovy
[
    associationName: 'AssociationCases.Base.AssociationWithAttribute',
    iliClassName: 'AssociationCases.Base.AssociationWithAttribute',
    domainClassName: 'AssociationWithAttribute',
    domainClassQualifiedName: 'ch.example.association.domain.AssociationWithAttribute',
    controllerName: 'associationWithAttribute',
    viewPath: 'associationWithAttribute',
    physicalTable: 'associationwithattribute',
    storageKind: 'LINK_ENTITY',
    writable: true,
    showInNavigation: false,
    roles: [
        [
            name: 'PersonRole',
            label: 'PersonRole',
            property: 'personRoleId',
            targetIliClass: 'AssociationCases.Base.Person',
            targetDomainClass: 'ch.example.association.domain.Person',
            min: 0,
            max: -1,
            ordered: false,
            external: false,
            composition: false
        ]
    ],
    attributes: [
        [
            iliName: 'RoleNote',
            property: 'roleNote',
            type: 'String',
            coreType: 'TEXT',
            label: 'RoleNote',
            mandatory: false,
            maxLength: 30
        ]
    ],
    diagnostics: []
]
```

Context-Map:

```groovy
[
    id: 'AssociationCases.Base.AssociationWithAttribute::PersonRole',
    associationName: 'AssociationCases.Base.AssociationWithAttribute',
    participantDomainClass: 'ch.example.association.domain.Person',
    fixedRole: 'PersonRole',
    fixedProperty: 'personRoleId',
    editableRoles: ['DocumentRole'],
    editableProperties: ['documentRoleId'],
    defaultLabel: 'Documents',
    messageCode: 'interlis.association.associationCasesBaseAssociationWithAttribute.personRole.label',
    presentation: 'CONTEXTUAL_FORM',
    createMode: 'CONTEXTUAL_FORM',
    writable: true,
    removable: true,
    perspectiveMin: 0,
    perspectiveMax: -1
]
```

### 11.4 Determinismus

Der Generator MUSS:

- Maps in stabiler Reihenfolge ausgeben;
- Strings korrekt für Groovy escapen;
- `null` als `null`, nicht als leeren String ausgeben;
- `-1` für unbounded erhalten;
- keine Speicheradressen oder zufälligen IDs verwenden;
- Snapshot-Tests besitzen;
- generierten Groovy-Code kompilieren.

### 11.5 Integration in `GrailsCrudGenerator`

`GrailsCrudGenerator.generate(...)` soll konzeptionell so aussehen:

```java
public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
    Files.createDirectories(config.getOutputDir());

    TargetNameRegistry registry =
        TargetNameRegistry.forMetadata(metadata, config);

    GrailsRelationshipMapper relationshipMapper =
        GrailsRelationshipMapper.forMetadata(metadata, config, registry);

    GrailsAssociationPlanner associationPlanner =
        GrailsAssociationPlanner.forMetadata(
            metadata,
            config,
            registry,
            relationshipMapper
        );

    enumGenerator.generate(metadata, config, registry);
    domainGenerator.generate(metadata, config, registry, relationshipMapper);
    associationRegistryGenerator.generate(
        metadata,
        config,
        registry,
        associationPlanner
    );

    // bestehende Projektanpassungen
}
```

Es ist zulässig, Überladungen einzuführen, um bestehende Aufrufer kompatibel zu halten.

Nicht zulässig ist, dass `GrailsDomainGenerator` und `GrailsAssociationPlanner` verschiedene `TargetNameRegistry`- oder Mapper-Instanzen mit abweichenden Entscheidungen verwenden.

---

## 12. Konfiguration

### 12.1 `GenerationConfig`

Erweitere `GenerationConfig` mindestens um:

```java
public static final String ASSOCIATION_UI_OFF = "off";
public static final String ASSOCIATION_UI_READ_ONLY = "read-only";
public static final String ASSOCIATION_UI_EDITABLE = "editable";
public static final String ASSOCIATION_UI_AUTO = "auto";
```

Felder:

```java
private final String associationUiMode;
private final int associationPageSize;
private final boolean hideContextualAssociationControllers;
```

Getter:

```java
public String getAssociationUiMode();
public int getAssociationPageSize();
public boolean isHideContextualAssociationControllers();
public boolean isAssociationUiEnabled();
public boolean isAssociationUiEditable();
```

Builder:

```java
public Builder associationUiMode(String mode);
public Builder associationPageSize(int pageSize);
public Builder hideContextualAssociationControllers(boolean hide);
```

Defaults:

- `associationUiMode = auto`;
- `associationPageSize = 10`;
- `hideContextualAssociationControllers = true`.

Semantik `auto`:

- Bootstrap-Overlay: Association-UI aktiv und bei sicherer Abbildung editierbar;
- Default-Scaffolding ohne Overlay: Registry darf generiert werden, aber keine Annahme über vorhandene Templates; Funktion read-only oder deaktiviert, bis explizit unterstützt.

Page-Size:

- Minimum 1;
- Maximum 100;
- ungültige Werte führen zu `IllegalArgumentException`;
- Runtime begrenzt nochmals defensiv.

### 12.2 CLI

Erweitere die CLI-Optionen:

```text
--grails-association-ui <auto|off|read-only|editable>
--grails-association-page-size <1..100>
--grails-association-navigation <auto|show|hide>
```

Mapping:

- `auto`: technische Association-Controller mit guten Kontexten ausblenden;
- `show`: alle Controller anzeigen;
- `hide`: alle Association-Controller ausblenden, aber direkte Kontextlinks erhalten.

Die README und `--help`-Ausgabe sind anzupassen.

---

## 13. Runtime: Registry-Support

Neue Overlay-Datei:

```text
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationRegistrySupport.groovy
```

### 13.1 API

```groovy
package ch.interlis.generator.grails.runtime

import ch.interlis.generator.grails.generated.InterlisAssociationRegistry

final class InterlisAssociationRegistrySupport {

    static List<Map<String, Object>> contextsForParticipant(Class domainType)

    static Map<String, Object> requireContext(
        Class participantType,
        String contextId
    )

    static Map<String, Object> requireAssociation(
        String associationName
    )

    static Class resolveDomainClass(
        def grailsApplication,
        String qualifiedClassName
    )

    static Class resolveAssociationClass(
        def grailsApplication,
        Map<String, Object> context
    )

    static Map<String, Object> role(
        Map<String, Object> association,
        String roleName
    )

    static List<Map<String, Object>> editableRoles(
        Map<String, Object> association,
        Map<String, Object> context
    )

    static boolean isAssociationDomain(Class domainType)

    static boolean showInNavigation(Class domainType)

    private InterlisAssociationRegistrySupport()
}
```

### 13.2 Sicherheitsregeln

`requireContext(...)` MUSS prüfen:

- Kontext existiert;
- `participantDomainClass` entspricht exakt `participantType.name`;
- Association existiert;
- feste Rolle existiert;
- feste Property gehört zur Association-Domain;
- Kontext ist nicht manipuliert.

Client-Parameter dürfen niemals enthalten oder bestimmen:

- frei wählbaren Domain-Klassennamen;
- frei wählbaren Property-Namen;
- Tabellenname;
- HQL-Klassenname;
- physische Spalte.

Der Client übergibt nur:

- `context`;
- aktuelle Objekt-ID;
- Zielobjekt-ID beziehungsweise Association-ID;
- fachliche Formularwerte.

Alle Klassen- und Property-Namen kommen ausschliesslich aus der generierten Registry.

---

## 14. Runtime: Query-Service

Neue Overlay-Datei:

```text
grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy
```

Die Datei muss in `GrailsTemplateOverlayInstaller.MANAGED_FILES` aufgenommen werden.

### 14.1 Grundstruktur

```groovy
package ch.interlis.generator.grails.runtime

class InterlisAssociationQueryService {

    static transactional = false

    def grailsApplication

    List<Map<String, Object>> sections(
        Class participantType,
        Serializable participantId,
        Integer maxPerSection
    )

    Map<String, Object> page(
        Class participantType,
        Serializable participantId,
        String contextId,
        Integer max,
        Integer offset,
        String sort,
        String order
    )

    Map<String, Object> optionPage(
        Class participantType,
        String contextId,
        String roleName,
        String query,
        Integer max,
        Integer offset
    )

    Map<String, Object> describeAssociationRow(
        Map<String, Object> association,
        Map<String, Object> context,
        Object associationInstance
    )
}
```

### 14.2 `sections(...)`

Ablauf:

1. Teilnehmerobjekt laden; bei nicht vorhandenem Objekt leere Liste oder definierte NotFound-Semantik.
2. Kontexte via Registry ermitteln.
3. Jeden Kontext stabil sortiert verarbeiten.
4. Pro Kontext höchstens `associationPageSize` Datensätze laden.
5. Count separat ermitteln.
6. Abschnittsmodell erzeugen.

Rückgabemodell pro Abschnitt:

```groovy
[
    contextId: '...',
    label: 'Dossiers',
    messageCode: '...',
    presentation: 'RELATED_LIST',
    createMode: 'QUICK',
    writable: true,
    removable: true,
    total: 4,
    max: 10,
    offset: 0,
    more: false,
    rows: [...],
    columns: [...],
    emptyMessage: 'Keine Dossiers zugeordnet.'
]
```

### 14.3 `page(...)`

Die Methode MUSS:

- den Kontext mit `requireContext` validieren;
- Teilnehmerobjekt über `participantType.get(id)` laden;
- Association-Klasse aus Registry auflösen;
- nur Association-Instanzen laden, deren feste Rollenproperty auf genau dieses Teilnehmerobjekt verweist;
- serverseitig paginieren;
- `max` auf 1..100 begrenzen;
- `offset >= 0`;
- Sortierfelder whitelisten;
- standardmässig nach Association-ID aufsteigend sortieren;
- Rollen als To-One fetch-joinen, soweit GORM/Hibernate dies sicher unterstützt;
- Count ohne vollständiges Laden bestimmen.

Keine String-Konkatenation mit ungeprüften Clientwerten in HQL.

Zulässige dynamische Namen stammen aus der Registry und werden vor Nutzung gegen GORM-Metadaten geprüft.

### 14.4 Zeilenmodell

Beispiel:

```groovy
[
    associationId: '17',
    associationLabel: 'Gesuchstellerin - Anna Muster',
    counterparts: [
        [
            role: 'Person',
            property: 'personRoleId',
            id: '5',
            label: 'Anna Muster',
            controller: 'person',
            showUrl: '/person/show/5'
        ]
    ],
    attributes: [
        [
            property: 'funktion',
            label: 'Funktion',
            value: 'Gesuchstellerin'
        ]
    ],
    editUrl: '/beteiligung/edit/17?...',
    deleteAllowed: true
]
```

URLs sollen vorzugsweise im GSP mit `createLink` erzeugt werden. Der Service darf stattdessen Controller, Action und ID als strukturierte Werte liefern.

### 14.5 `optionPage(...)`

Diese Methode darf die bestehende Suchlogik nicht duplizieren.

Refaktoriere `InterlisRelationshipOptions` so, dass folgende Methode existiert:

```groovy
static Map<String, Object> optionPageForTargetType(
    def grailsApplication,
    Class targetType,
    String query,
    Integer max,
    Integer offset
)
```

Die bestehende Methode:

```groovy
static Map<String, Object> optionPage(
    def grailsApplication,
    Class domainType,
    String field,
    String query,
    Integer max,
    Integer offset,
    Collection<String> geometryFields
)
```

soll:

1. Zieltyp auflösen;
2. an `optionPageForTargetType(...)` delegieren.

Association-Optionen lösen den Zieltyp über Registry-Rolle auf und delegieren ebenfalls an dieselbe Methode.

Damit bleiben:

- Display-Felder;
- Suchfelder;
- Sortierung;
- Paging;
- Label-Fallbacks

einheitlich.

---

## 15. Runtime: Command-Service

Neue Overlay-Datei:

```text
grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationCommandService.groovy
```

### 15.1 Grundstruktur

```groovy
package ch.interlis.generator.grails.runtime

import grails.gorm.transactions.Transactional

@Transactional
class InterlisAssociationCommandService {

    def grailsApplication

    Map<String, Object> createQuickLink(
        Class participantType,
        Serializable participantId,
        String contextId,
        String targetRoleName,
        Serializable targetId
    )

    Map<String, Object> deleteLink(
        Class participantType,
        Serializable participantId,
        String contextId,
        Serializable associationId
    )

    protected void validateCreateCardinality(
        Map<String, Object> association,
        Map<String, Object> context,
        Object participant
    )

    protected void validateDeleteCardinality(
        Map<String, Object> association,
        Map<String, Object> context,
        Object participant,
        Object associationInstance
    )

    protected Object loadRequired(
        Class type,
        Serializable id,
        String fieldLabel
    )

    protected void assignRole(
        Object associationInstance,
        String propertyName,
        Object value
    )

    protected void verifyAssociationBelongsToParticipant(
        Object associationInstance,
        Map<String, Object> context,
        Object participant
    )
}
```

### 15.2 `createQuickLink(...)`

Nur zulässig, wenn:

- Kontext existiert und zum Teilnehmercontroller gehört;
- `createMode == QUICK`;
- Kontext schreibbar;
- Association-Speicherart `LINK_ENTITY`;
- Zielrolle in `editableRoles`;
- Zielobjekt existiert;
- feste Rolle und Zielrolle nicht dieselbe Property sind;
- Kardinalität nicht verletzt wird.

Ablauf:

1. Teilnehmerobjekt laden.
2. Gegenobjekt laden.
3. Association-Domain instanziieren.
4. feste Rollenproperty setzen.
5. Zielrollenproperty setzen.
6. `validate()` aufrufen.
7. bei Fehlern strukturierte Fehlermeldungen zurückgeben;
8. `save(flush: true, failOnError: false)`;
9. Save-Fehler prüfen;
10. Ergebnis mit Association-ID zurückgeben.

Keine automatische Erzeugung des Zielobjekts.

### 15.3 Duplikate

Es darf nicht pauschal angenommen werden, dass dieselbe Rollenpaarung nur einmal existieren darf.

Regel:

- Ein Duplikat wird nur verhindert, wenn dies aus Kardinalität, explizitem Constraint oder sicherer physischer Unique-Information folgt.
- Ohne solche Information dürfen zwei Association-Instanzen mit denselben Rollenwerten fachlich zulässig sein, insbesondere wenn eigene Attribute oder OIDs existieren.
- Quick-Link ohne eigene Attribute darf optional doppelte identische Links verhindern, aber nur, wenn die Spezifikation/IR dies eindeutig erlaubt. Diese Entscheidung ist als Test und Umsetzungsentscheidung zu dokumentieren.

Konservative Default-Entscheidung: keine erfundene globale Unique-Regel.

### 15.4 Binäre Kardinalitätsprüfung

Für einen binären Kontext:

- feste Rolle A;
- Gegenrolle B;
- Anzahl Links für Objekt A = Count der Association-Instanzen mit `a.fixedProperty = participant`;
- zulässige Anzahl Gegenobjekte ergibt sich aus Kardinalität von B.

Create:

```text
count + 1 <= B.max
```

wenn `B.max` bekannt und nicht `-1`.

Delete:

```text
count - 1 >= B.min
```

wenn `B.min` bekannt.

Bei unbekannter Kardinalität:

- keine erfundene Restriktion;
- GORM-/DB-Validierung bleibt aktiv;
- Diagnose protokollieren.

Für n-äre Assoziationen darf dieselbe vereinfachte Prüfung nicht ungeprüft verwendet werden.

### 15.5 Konkurrenz

Vor einer Max-Kardinalitätsprüfung soll der Teilnehmerdatensatz innerhalb der Transaktion gesperrt werden, wenn GORM dies portabel unterstützt:

```groovy
Object participant = participantType.lock(participantId)
```

Ist dies nicht zuverlässig möglich:

- dokumentiere die Race-Condition;
- verlasse dich zusätzlich auf vorhandene DB-Constraints;
- fange `DataIntegrityViolationException`;
- liefere verständlichen Konfliktstatus.

### 15.6 `deleteLink(...)`

Ablauf:

1. Kontext validieren.
2. Teilnehmer laden.
3. Association-Instanz laden.
4. prüfen, dass deren feste Rollenproperty exakt auf den Teilnehmer zeigt.
5. `removable` prüfen.
6. Kompositions- und External-Regeln prüfen.
7. Min-Kardinalität prüfen.
8. Association-Instanz löschen.
9. Zielobjekt niemals mitlöschen.
10. referenzielle Fehler in verständliche Meldung übersetzen.

Manipulationsschutz:

Ein Benutzer darf nicht über `/person/associationDelete/1?associationId=99` eine Association löschen, die zu Person 2 gehört.

---

## 16. Kontextuelle Association-Formulare

Einfache Quick-Links werden direkt erstellt. Alle komplexeren Fälle verwenden die bestehende Association-Domain und deren Scaffold-Formular.

### 16.1 Neue Runtime-Klasse

```text
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationContextSupport.groovy
```

API:

```groovy
final class InterlisAssociationContextSupport {

    static Map<String, Object> prepareCreateContext(
        def grailsApplication,
        Class associationType,
        Map params
    )

    static Map<String, Object> prepareEditContext(
        def grailsApplication,
        Class associationType,
        Object associationInstance,
        Map params
    )

    static void applyFixedRole(
        Object associationInstance,
        Map<String, Object> contextState
    )

    static Map<String, Object> redirectTarget(
        Map<String, Object> contextState
    )

    static List<String> hiddenRelationshipFields(
        Map<String, Object> contextState
    )

    static void verifyContextMatchesAssociation(
        Class associationType,
        Map<String, Object> context
    )

    private InterlisAssociationContextSupport()
}
```

### 16.2 Erlaubte Kontextparameter

Formulare dürfen folgende strukturierte Parameter transportieren:

```text
associationContext=<contextId>
associationOwnerId=<id>
associationReturnController=<aus Registry abgeleitet oder serverseitig gespeichert>
associationReturnAction=show
associationReturnId=<id>
```

Besser ist, nur `associationContext` und `associationOwnerId` zu senden und die Rückleitung vollständig serverseitig aus Registry und Teilnehmerklasse abzuleiten.

**Nicht zulässig:** ungeprüfter `returnUrl`-Parameter wegen Open-Redirect-Risiko.

### 16.3 Create-Ablauf

1. Benutzer klickt in Teilnehmer-Show auf „Beteiligung hinzufügen“.
2. Link zeigt auf den Association-Controller `create`.
3. Parameter enthalten Kontext-ID und Owner-ID.
4. `InterlisCrudControllerSupport.create()` erkennt Kontext.
5. `InterlisAssociationContextSupport.prepareCreateContext(...)`:
   - validiert, dass `domainType()` die Association-Domain ist;
   - löst feste Teilnehmerklasse auf;
   - lädt Owner;
   - setzt feste Rollenproperty;
   - markiert diese Property als hidden/read-only.
6. Formular zeigt:
   - festen Owner als lesbare Zusammenfassung;
   - übrige Rollen als Autocomplete;
   - eigene Association-Attribute;
   - Geometrieattribute über bestehenden Editor.
7. Speichern setzt die feste Rolle serverseitig erneut.
8. Nach Speichern Redirect zurück zum Owner-Show.

Die feste Rolle darf nicht allein durch Hidden-Field-Binding geschützt werden. Der Server setzt sie nach `bindData` erneut aus dem validierten Kontext.

### 16.4 Edit-Ablauf

- Kontext kann über Row-Aktion mitgegeben werden.
- Association-Instanz muss zur festen Rolle/Owner-ID passen.
- feste Rollenproperty bleibt unveränderbar.
- übrige Rollen und eigene Attribute sind editierbar.
- nach Update Rückleitung zum Owner.

### 16.5 Löschen komplexer Association

- Row-Aktion kann über Participant-Controller und `InterlisAssociationCommandService.deleteLink(...)` laufen;
- alternativ Association-Controller mit sicherem Kontext;
- eine einzige zentrale Verifikationslogik verwenden;
- keine doppelte, leicht auseinanderlaufende Sicherheitsprüfung.

---

## 17. Erweiterung von `InterlisCrudControllerSupport`

Aktueller Pfad:

```text
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy
```

### 17.1 Service-Zugriff

Der generierte Controller soll zusätzlich erhalten:

```groovy
InterlisAssociationQueryService interlisAssociationQueryService
InterlisAssociationCommandService interlisAssociationCommandService
```

Der Support kann abstrakte Getter definieren:

```groovy
protected abstract Object associationQueryService()
protected abstract Object associationCommandService()
```

oder die geerbte Injection verwenden. Die gewählte Variante muss in einem echten Grails-Runtime-Test bewiesen werden.

Bevorzugt ist analog zum bestehenden `crudService()` eine explizite, testbare Übergabe.

### 17.2 Neue Actions

```groovy
def associationPage(Long id)

def associationOptions(Long id)

def associationCreate(Long id)

def associationDelete(Long id)
```

Optional:

```groovy
def associationSection(Long id)
```

für serverseitiges Nachladen eines GSP-Fragments.

### 17.3 `allowedMethods`

Controller-Template erweitern:

```groovy
static allowedMethods = [
    save: "POST",
    update: "PUT",
    delete: "DELETE",
    relationshipOptions: "GET",
    associationPage: "GET",
    associationOptions: "GET",
    associationCreate: "POST",
    associationDelete: "DELETE"
]
```

Keine Mutation über GET.

### 17.4 `show(Long id)`

Erweitern:

```groovy
Map<String, Object> model = [:]
model.putAll(geometryModel(instance))
model.putAll(relationshipModel(instance))
model.putAll(detailModel(instance))
model.putAll(associationModel(instance))
respond instance, model: model
```

Neue Methode:

```groovy
protected Map<String, Object> associationModel(T instance) {
    if (instance == null) {
        return [associationSections: []]
    }
    return [
        associationSections:
            associationQueryService().sections(
                domainType(),
                instance.id as Serializable,
                associationPageSize()
            )
    ]
}
```

Association-Fehler dürfen nicht die gesamte Show-Seite mit HTTP 500 zerstören. Ein sicherer Diagnoseabschnitt ist zulässig, aber Fehler dürfen nicht still verschluckt werden.

### 17.5 Create/Save/Edit/Update kontextfähig machen

Neue Hilfsmethoden:

```groovy
protected Map<String, Object> associationContextState(T instance)

protected void applyAssociationContext(T instance, Map<String, Object> state)

protected Map<String, Object> contextualRedirectTarget(
    T instance,
    Map<String, Object> state
)

protected boolean redirectToAssociationOwnerIfPresent(
    T instance,
    Map<String, Object> state
)
```

Bei Validierungsfehlern muss der Kontext im Formularmodell erhalten bleiben.

### 17.6 HTTP-Status

Empfohlene Statuswerte:

- ungültiger Kontext: `400 Bad Request`;
- Teilnehmer oder Ziel nicht gefunden: `404 Not Found`;
- Association gehört nicht zum Teilnehmer: `404` oder `403`, konsistent dokumentieren;
- Kardinalitätskonflikt: `409 Conflict`;
- Validierungsfehler: `422 Unprocessable Entity` für JSON, normales Formular mit Fehlern für HTML;
- erfolgreicher Quick-Link: Redirect/`201 Created`;
- erfolgreiches Entfernen: Redirect/`204` für API.

---

## 18. Controller-Template

Pfad:

```text
src/main/templates/scaffolding/Controller.groovy
```

Zielstruktur:

```groovy
import ch.interlis.generator.grails.runtime.InterlisAssociationCommandService
import ch.interlis.generator.grails.runtime.InterlisAssociationQueryService
import ch.interlis.generator.grails.runtime.InterlisCrudControllerSupport

class ${className}Controller
    extends InterlisCrudControllerSupport<${className}> {

    ${className}Service ${propertyName}Service
    InterlisAssociationQueryService interlisAssociationQueryService
    InterlisAssociationCommandService interlisAssociationCommandService

    static final String interlisDomainClassName =
        '${packageName ? packageName + "." : ""}${className}'

    // Actions delegieren an super

    @Override
    protected Object associationQueryService() {
        return interlisAssociationQueryService
    }

    @Override
    protected Object associationCommandService() {
        return interlisAssociationCommandService
    }
}
```

`interlisDomainClassName` wird von der Navigation verwendet.

Der Runtime-Smoke-Test MUSS beweisen, dass die Services injiziert werden und die Anwendung startet.

---

## 19. GSP-Templates

### 19.1 Neue Dateien

```text
src/main/templates/scaffolding/_association-sections.gsp
src/main/templates/scaffolding/_association-quick-add.gsp
src/main/templates/scaffolding/_association-row-actions.gsp
src/main/templates/scaffolding/_association-context-summary.gsp
```

Die Zahl der Templates darf angepasst werden, solange die Verantwortlichkeiten sauber bleiben.

Alle neuen Dateien müssen in `GrailsTemplateOverlayInstaller.MANAGED_FILES` aufgenommen werden.

### 19.2 `show.gsp`

Nach dem Detail-/Geometriebereich und vor der Danger Zone:

```gsp
<g:render template="association-sections"
          model="${[
              associationSections: associationSections,
              owner: this.${propertyName},
              ownerPropertyName: '${propertyName}'
          ]}"/>
```

Die Sektionen sind normale, klar getrennte Inhaltsabschnitte. Keine unnötigen Bootstrap-Cards mit Schatten.

### 19.3 `_association-sections.gsp`

Anforderungen:

- nur rendern, wenn Kontexte vorhanden;
- Überschrift mit Label und Count;
- Empty State;
- Tabelle für Listen;
- kompakte Darstellung für To-One;
- „Mehr anzeigen“ bei weiterer Seite;
- Quick-Add nur bei `createMode == QUICK`;
- Kontextformular-Link bei `createMode == CONTEXTUAL_FORM`;
- Aktionen nur bei erlaubtem Kontext;
- Tabellenheader aus strukturierten Column-Metadaten;
- keine rohen Property- oder Klassennamen als Standardlabel, wenn Rollen-/Feldlabel vorhanden;
- `aria-labelledby` und eindeutige IDs.

### 19.4 Quick-Add-Form

Beispiel:

```gsp
<g:form controller="${controllerName}"
        action="associationCreate"
        id="${owner.id}"
        method="POST"
        class="ili-association-quick-form">
    <g:hiddenField name="context" value="${section.contextId}"/>
    <g:hiddenField name="role" value="${section.quickTargetRole}"/>

    <div class="ili-relationship-picker js-relationship-picker">
        <input type="search"
               class="form-control form-control-sm js-relationship-search"
               data-relationship-context="${section.contextId}"
               data-relationship-role="${section.quickTargetRole}"
               data-relationship-url="${createLink(action: 'associationOptions', id: owner.id)}"
               data-relationship-select="association-target-${section.domId}"
               autocomplete="off"/>

        <select name="targetId"
                id="association-target-${section.domId}"
                class="form-select"
                data-relationship-optional="false">
        </select>
    </div>

    <button type="submit" class="btn btn-primary btn-sm">
        Zuordnen
    </button>
</g:form>
```

CSRF-Schutz der Grails-Formmechanismen ist beizubehalten.

### 19.5 `_relationship-fields.gsp`

Erweitere das bestehende Template um:

- `hiddenRelationshipFields`;
- `fixedRelationshipLabels`;
- `associationContextState`.

Wenn ein Feld fixiert ist:

- kein editierbarer Picker;
- Hidden Field für ID nur als Transport;
- serverseitige erneute Setzung bleibt zwingend;
- lesbare Zusammenfassung;
- bei fehlendem Owner klarer Fehler.

Beispiel:

```gsp
<g:if test="${hiddenRelationshipFields?.contains(relationshipField)}">
    <g:hiddenField name="${relationshipField}.id"
                   value="${relationshipValues?.get(relationshipField)}"/>
    <div class="ili-fixed-relationship">
        <span class="ili-fixed-relationship-label">
            ${fixedRelationshipLabels?.get(relationshipField)}
        </span>
    </div>
</g:if>
<g:else>
    <!-- bestehender Picker -->
</g:else>
```

### 19.6 Form-Kontext transportieren

`_form.gsp` erhält Hidden Fields:

```gsp
<g:if test="${associationContextState}">
    <g:hiddenField name="associationContext"
                   value="${associationContextState.contextId}"/>
    <g:hiddenField name="associationOwnerId"
                   value="${associationContextState.ownerId}"/>
</g:if>
```

Keine ungeprüfte Return-URL.

---

## 20. JavaScript

Pfad:

```text
grails-app/assets/javascripts/ili-form-ux.js
```

### 20.1 Bestehenden Autocomplete generalisieren

Die bestehende Funktion `relationshipUrl(input, offset)` soll zusätzlich unterstützen:

```text
data-relationship-context
data-relationship-role
```

Zielverhalten:

```javascript
function relationshipUrl(input, offset) {
    var baseUrl = input.getAttribute("data-relationship-url");
    if (!baseUrl) {
        return null;
    }

    var url = new URL(baseUrl, window.location.origin);
    var field = input.getAttribute("data-relationship-field");
    var context = input.getAttribute("data-relationship-context");
    var role = input.getAttribute("data-relationship-role");

    if (field) {
        url.searchParams.set("field", field);
    }
    if (context) {
        url.searchParams.set("context", context);
    }
    if (role) {
        url.searchParams.set("role", role);
    }

    url.searchParams.set("q", input.value || "");
    url.searchParams.set("max", "25");
    url.searchParams.set("offset", String(offset || 0));

    return url.toString();
}
```

Die Funktion muss mit relativen Context-Paths funktionieren. Tests dürfen nicht nur `/` als Deployment-Context annehmen.

### 20.2 Weitere Anforderungen

- Debounce 250 ms beibehalten oder begründet ändern;
- bestehende servergerenderte Optionen bleiben Fallback;
- Request-Abbruch via `AbortController` ist erwünscht, damit alte Antworten neue Suchresultate nicht überschreiben;
- Lade- und Fehlerstatus barrierearm anzeigen;
- keine unbeschränkte Ergebnisliste;
- Auswahl per Tastatur muss funktionieren;
- Quick-Add-Form nur absenden, wenn Ziel gewählt;
- keine doppelte Event-Initialisierung;
- Progressive Enhancement: ohne JavaScript bleibt ein serverseitig befülltes Select mit begrenzten Optionen nutzbar.

### 20.3 Optionale Fragment-Pagination

Falls Related-Lists per Fetch nachgeladen werden:

- Server liefert GSP-Fragment oder JSON;
- bestehende Links bleiben ohne JavaScript benutzbar;
- History/URL-Verhalten dokumentieren;
- kein clientseitiges Reimplementieren der Tabellenlogik.

---

## 21. Navigation

### 21.1 Problem

Das aktuelle Layout baut die Navigation aus allen Controller-Klassen. Dadurch erscheinen technische Association-Controller gleichrangig neben Fachklassen.

### 21.2 Neue Runtime-Klasse

```text
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy
```

API:

```groovy
final class InterlisNavigationSupport {

    static List<Map<String, Object>> menuEntries(def grailsApplication)

    static Class domainTypeForController(def controllerArtefact)

    static boolean showController(
        def controllerArtefact,
        Class domainType
    )

    static String defaultLabel(
        def controllerArtefact,
        Class domainType
    )

    private InterlisNavigationSupport()
}
```

### 21.3 Filterlogik

- Nicht-Domain-Controller wie `urlMappings` ausblenden.
- Domainklasse über `interlisDomainClassName` des generierten Controllers auflösen.
- Registry `showInNavigation(domainType.name)` auswerten.
- Association-Domains standardmässig ausblenden, wenn:
  - mindestens ein kontextueller Einstieg existiert;
  - Association physisch sicher erreichbar bleibt;
  - Konfiguration nicht `show` verlangt.
- Association-Domain anzeigen, wenn:
  - keine brauchbaren Kontexte existieren;
  - Diagnose/Fallback nötig;
  - Konfiguration `show`;
  - n-äre oder administrative Verwendung bewusst einen Menüpunkt verlangt.

### 21.4 `main.gsp`

Ersetze die direkte Controller-Sammlung durch:

```gsp
<g:set var="viewMenuEntries"
       value="${ch.interlis.generator.grails.runtime.InterlisNavigationSupport.menuEntries(grailsApplication)}"/>
```

Keine Reflection-Fehler dürfen das Layout zerstören. Bei unbekannten Controllern konservativ anzeigen.

---

## 22. Labels und fachliche Benennung

### 22.1 Priorität

Für Context- und Rollenlabels:

1. explizite Message-Code-Überschreibung;
2. vorhandene Core-IR-Labels;
3. Rollenname;
4. Zielklassenlabel;
5. normalisierter Klassenname.

Technische Suffixe wie `Id`, `_id`, `RoleId` sollen nicht als sichtbare Standardlabels erscheinen.

### 22.2 Message Codes

Generiere oder verwende stabile Codes:

```text
interlis.association.<normalizedAssociation>.<normalizedFixedRole>.label
interlis.association.<normalizedAssociation>.create.label
interlis.association.<normalizedAssociation>.empty
interlis.association.<normalizedAssociation>.remove.confirm
```

Es ist nicht zwingend, automatisch `messages.properties` zu schreiben. Die GSPs müssen `message(..., default: ...)` verwenden, damit Fachprojekte überschreiben können.

### 22.3 Display-Labels von Zielobjekten

Weiterhin über `interlisDisplayMeta` und `InterlisRelationshipOptions.optionLabel(...)`.

Keine neue `toString()`-Pflicht für generierte Domains.

---

## 23. Persistenz- und GORM-Regeln

### 23.1 Kein persistentes inverses `hasMany` für Association-Rollen

`GrailsRelationshipMapper` soll weiterhin keine inversen GORM-Collections für Association-Rollen erzeugen.

Related-Lists werden über die Association-Domain abgefragt.

### 23.2 Association-Properties

Die Association-Domain enthält typisierte Rollenproperties und Spaltenmapping.

Beispiel:

```groovy
class AssociationWithAttribute {

    Document documentRoleId
    Person personRoleId
    String roleNote

    static mapping = {
        table 'associationwithattribute'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            documentRoleId column: 'document_role_id'
            personRoleId column: 'person_role_id'
            roleNote column: 'role_note'
        }
    }
}
```

Die neue Funktionalität muss exakt diese Properties verwenden.

### 23.3 Versioning

Viele ili2db-Tabellen besitzen kein Grails-`version`-Feld. Die bestehende Entscheidung `version false` bleibt. Es dürfen keine Version-Spalten erfunden werden.

Für konkurrierende Association-Änderungen:

- Transaktion;
- Datenbankconstraints;
- optionales Locking;
- verständliche Konflikte.

### 23.4 Delete

Löschen der Association-Domain löscht nur den Link-Datensatz.

Nie automatisch löschen:

- Person;
- Geschäft;
- Dossier;
- andere Zielobjekte.

Kompositionsfälle werden gesondert und konservativ behandelt.

---

## 24. Spezialfälle

### 24.1 Zwei Rollen derselben Zielklasse

Der Planner muss über Rollennamen unterscheiden.

Tests:

- `PrimaryPerson`;
- `SecondaryPerson`.

Erwartung:

- zwei Context-IDs;
- zwei verschiedene feste Properties;
- eindeutige Labels;
- sichere Zugehörigkeitsprüfung;
- kein Zusammenfallen nach Zielklasse.

### 24.2 Abweichender physischer Rollenname

Beispiel:

- semantisch `SemanticOwner`;
- physisch `owner_fk`.

Erwartung:

- Planner verwendet die vom Relationship-Mapping tatsächlich erzeugte Property;
- Registry führt semantischen Rollenname und Domain-Property getrennt;
- Query/Command nutzen Domain-Property;
- UI nutzt semantisches Label;
- Snapshot beweist korrektes Spaltenmapping.

### 24.3 Vererbung und erweiterte Topics

- Target-Namen weiterhin über `TargetNameRegistry`;
- qualifizierte ILI-Namen in Registry;
- Kollisionen vermeiden;
- Association in erweitertem Topic muss auf Basisklassen und erweiterte Klassen korrekt verweisen;
- keine String-SimpleName-Heuristik für Identität.

### 24.4 Abstrakte Klassen

- keine direkt instanziierbare Zielauswahl für abstrakte Domain;
- konkrete Subtypen berücksichtigen, falls bestehende GORM-Vererbung dies unterstützt;
- andernfalls read-only/Fallback;
- Entscheidung testen und dokumentieren.

### 24.5 Nicht persistierbare Zielklasse

- kein Quick-Link;
- Related-List nur, wenn Association-Domain und Zielwerte lesbar sind;
- Diagnose `TARGET_DOMAIN_NOT_GENERATED`;
- bestehendes CRUD-Fallback nicht kaputtmachen.

### 24.6 Geometrieattribute auf Association

Kontextuelles Association-Formular verwendet den vorhandenen Geometrie-Editor.

Quick-Link ist bei eigenen Geometrieattributen nicht zulässig.

### 24.7 Enum-Attribute auf Association

Kontextuelles Formular verwendet die generierte Enum-Domain-/Property-Abbildung und vorhandene Fields-Templates.

### 24.8 Leere Association

Eine Association ohne Datensätze zeigt:

```text
Keine Dossiers zugeordnet.

[Dossier zuordnen]
```

Keine leere Tabelle mit nur Header.

---

## 25. Fehlerbehandlung

### 25.1 Strukturierte Command-Ergebnisse

Empfohlen:

```groovy
[
    success: true,
    associationId: '17',
    messageCode: 'interlis.association.created'
]
```

Fehler:

```groovy
[
    success: false,
    status: 409,
    code: 'CARDINALITY_MAX_EXCEEDED',
    message: 'Es kann höchstens ein Geschäft zugeordnet werden.',
    fieldErrors: [:]
]
```

Alternativ können typisierte Runtime-Exceptions verwendet werden:

```groovy
class InterlisAssociationException extends RuntimeException {
    String code
    int status
    Map<String, Object> details
}
```

Unterklassen:

```text
AssociationContextNotFoundException
AssociationTargetNotFoundException
AssociationOwnershipException
AssociationCardinalityException
AssociationReadOnlyException
```

Der Agent soll eine einzige konsistente Strategie wählen.

### 25.2 Benutzertexte

Keine Stacktraces oder HQL-Details in Flash-Meldungen.

Beispiele:

- „Die Zuordnung konnte nicht erstellt werden.“
- „Für dieses Objekt ist bereits die maximal zulässige Anzahl Zuordnungen vorhanden.“
- „Die Zuordnung gehört nicht zu diesem Datensatz.“
- „Die Zuordnung kann nicht entfernt werden, weil mindestens eine Beziehung bestehen muss.“
- „Diese Assoziation ist in der generischen Oberfläche nur lesbar.“

### 25.3 Logging

Serverlog enthält:

- Association-Name;
- Context-ID;
- Teilnehmerklasse und ID;
- Fehlercode;
- Root Cause ohne Secrets.

Keine Passwörter, JDBC-URLs mit Credentials oder vollständige Formdaten loggen.

---

## 26. Performance

### 26.1 Paging

- Related-List initial höchstens `associationPageSize`, Default 10;
- API maximal 100;
- Autocomplete Default 25;
- Counts serverseitig;
- kein `list()` ohne Begrenzung auf grossen Association-/Zieltabellen.

### 26.2 N+1 vermeiden

Bei einer Related-List mit mehreren To-One-Rollen:

- Rollen nach Möglichkeit mit Fetch-Join laden;
- alternativ IDs in Page laden und Zielobjekte batchweise auflösen;
- nicht pro Tabellenzelle eine Einzelquery;
- SQL-/Hibernate-Statistik in Integrationstest oder manueller Diagnose prüfen.

Akzeptanz:

- Anzahl Queries wächst nicht linear mit `rows × roles`;
- eine Page mit 10 Links und 2 Rollen darf nicht 21+ Einzelqueries erzeugen, sofern Fetch-Join technisch möglich ist.

### 26.3 Registry

Registry ist statisch und wird nicht pro Request neu aufgebaut.

Keine Reflection über alle Domains bei jedem Abschnitt, wenn die Registry die Information bereits enthält.

### 26.4 Autocomplete

- Suche nur über erlaubte Textfelder;
- `%query%` wie bestehend;
- später optional starts-with, aber nicht in diesem Auftrag nötig;
- keine ungeprüften Sortierfelder.

---

## 27. Sicherheit

### 27.1 Mass Assignment

Bei kontextuellen Formularen darf der feste Owner nicht durch Request-Parameter überschrieben werden.

Ablauf:

```text
bindData(instance, params)
→ serverseitig validierten Kontext laden
→ feste Rollenproperty erneut setzen
→ validieren
→ speichern
```

### 27.2 Insecure Direct Object Reference

Jede Edit/Delete-Aktion prüft:

```text
associationInstance.<fixedRoleProperty>.id == participantId
```

Nicht nur Association-ID vertrauen.

### 27.3 Open Redirect

Keine freie `returnUrl`.

Rückleitung aus:

- Context-ID;
- Teilnehmerklasse aus Registry;
- Teilnehmer-ID.

### 27.4 CSRF

Mutationen ausschliesslich über Grails-Formulare/POST/PUT/DELETE.

### 27.5 Autorisierungs-Extension-Point

Auch wenn konkrete Autorisierung ausserhalb des Auftrags liegt, Services sollen später überschreibbar sein.

Empfohlen:

```groovy
protected boolean canReadAssociation(
    Object participant,
    Map context
)

protected boolean canCreateAssociation(
    Object participant,
    Map context
)

protected boolean canDeleteAssociation(
    Object participant,
    Object associationInstance,
    Map context
)
```

Default kann `true` sein, aber zentral, nicht in GSP verstreut.

---

## 28. Barrierefreiheit und Bedienung

- Tabellen mit `<caption>` oder `aria-labelledby`;
- Buttons mit verständlichen Labels;
- Delete-Bestätigung;
- Autocomplete mit `role=listbox`, `role=option`, `aria-selected`;
- Fokus nach Validierungsfehler auf Fehlerzusammenfassung;
- Tastaturbedienung des Pickers;
- Suchfeld und Select korrekt beschriftet;
- Farbe nicht als einziges Statussignal;
- mobile Tabellen dürfen kontrolliert scrollen, aber keine horizontale Hauptseiten-Scrollbar erzeugen;
- feste Beziehung im Formular als Text plus Hidden Field;
- Ladezustand für asynchrone Suche.

---

## 29. Änderungen an bestehenden Klassen

### 29.1 `GrailsRelationshipMapper`

Erlaubte Änderungen:

- Konstruktion/Instanz zur Wiederverwendung zugänglich machen;
- Hilfsmethode für erzeugte Association-Domain-Mappings;
- kleine Refactorings, um Planner und Domain-Generator dieselben Entscheidungen nutzen zu lassen.

Nicht erlaubte Änderung:

- pauschale inverse `hasMany` für Association-Rollen;
- synthetische Join-Tabellen;
- Änderung der bestehenden Reference-/Composition-Semantik ohne Tests.

Neue optionale API:

```java
public Optional<DomainMapping> mappingFor(String iliClassName);

public List<RelationshipMetadata> effectiveRelationships();
```

Nur hinzufügen, wenn tatsächlich benötigt. Keine unnötige öffentliche API.

### 29.2 `GrailsDomainGenerator`

Mögliche Signatur:

```java
public void generate(
    ModelMetadata metadata,
    GenerationConfig config,
    TargetNameRegistry registry,
    GrailsRelationshipMapper relationshipMapper
) throws IOException;
```

Bestehende Überladung behalten:

```java
public void generate(
    ModelMetadata metadata,
    GenerationConfig config,
    TargetNameRegistry registry
) throws IOException
```

und intern delegieren.

Keine Association-Registry direkt im Domain-Generator rendern.

Optional `interlisEntityMeta` pro Domain:

```groovy
static final Map<String, Object> interlisEntityMeta = [
    iliName: '...',
    kind: 'ASSOCIATION',
    showInNavigation: false
]
```

Nur, wenn Navigation/Runtime dies nachweislich vereinfacht. Die zentrale Registry bleibt die kanonische UI-Metadatenquelle.

### 29.3 `GrailsTemplateOverlayInstaller`

`MANAGED_FILES` ergänzen um alle neuen:

- Runtime-Klassen;
- Services;
- GSP-Templates.

Tests müssen prüfen:

- Dateien werden kopiert;
- wiederholte Installation ist deterministisch;
- alte Dateien werden aktualisiert;
- keine nicht verwalteten Fachprojektdateien gelöscht.

### 29.4 `InterlisRelationshipOptions`

Refactoring wie in Kapitel 14.5.

Bestehende Methoden und bestehendes Verhalten müssen durch Regressionstests geschützt sein.

### 29.5 `InterlisTableModel`

Nur erweitern, wenn Association-Zeilen dieselbe Spalten-/Label-Logik sinnvoll wiederverwenden können.

Keine Association-spezifischen Sonderfälle in generische Methoden stopfen, wenn dies die Klasse unverständlich macht.

### 29.6 `GrailsBrowserE2eTest`

Bestehenden SimpleAddress-Test erhalten.

Zusätzlichen Association-Test einführen oder sauber separieren:

```java
@Test
void generatedGrailsAppSupportsContextualAssociationUxInBrowser()
```

Testmodell `AssociationCases.ili`.

---

## 30. Teststrategie

Unit-Tests allein reichen nicht. Jede Phase benötigt angemessene Tests auf mehreren Ebenen.

### 30.1 Testebenen

1. **Unit-Tests**
   - Planner;
   - Klassifikation;
   - Kardinalität;
   - Registry-Rendering;
   - Naming;
   - Escaping;
   - Konfiguration.

2. **Generierte-Output-/Snapshot-Tests**
   - Registry;
   - Association-Domains;
   - Controller-/Template-Overlay;
   - deterministische Ausgabe.

3. **Compile-Tests**
   - generierte Groovy-Dateien;
   - temporäre Grails-App;
   - `compileGroovy`;
   - `generate-all`.

4. **Service-/Integrationstests in echter Grails-App**
   - Query;
   - Create;
   - Delete;
   - Context Binding;
   - Redirect;
   - Kardinalität;
   - Manipulationsschutz.

5. **Real-ili2db-Smoke-Test**
   - ili2pg-Schemaimport;
   - echte Tabellen-/Spaltennamen;
   - Association-Rollen;
   - echte Persistenz.

6. **Browser-E2E mit Playwright**
   - sichtbare Bedienung;
   - Autocomplete;
   - Create/Edit/Delete;
   - beide Perspektiven;
   - Selbstassoziation;
   - Fehlermeldung.

### 30.2 Neue Unit-Testklassen

#### `GrailsAssociationPlannerTest`

Pfad:

```text
target-grails/src/test/java/ch/interlis/generator/grails/GrailsAssociationPlannerTest.java
```

Mindestens folgende Tests:

```java
void binaryAssociationWithoutAttributesBecomesQuickLink();

void associationWithAttributeUsesContextualForm();

void sameTargetRolesProduceDistinctContexts();

void physicalRoleNameMapsToGeneratedDomainProperty();

void naryAssociationUsesNaryContextualForm();

void externalAssociationIsNotQuickLinkByDefault();

void compositionAssociationIsNotQuickLink();

void orderedAssociationIsNotQuickLinkWithoutOrderMapping();

void associationWithoutPhysicalClassIsReadOnly();

void participantPerspectiveUsesOppositeRoleCardinality();

void contextsAreDeterministicallySorted();

void ambiguousRolePropertyCreatesDiagnosticAndReadOnlyContext();

void associationControllerIsHiddenOnlyWhenContextualAccessExists();
```

#### `GrailsAssociationRegistryGeneratorTest`

```java
void rendersDeterministicGroovyRegistry();

void escapesQuotesBackslashesAndNewlines();

void emitsContextsByParticipant();

void emitsEntityNavigationMetadata();

void generatedRegistryCompilesWithGroovyCompiler();

void emptyAssociationSetProducesValidRegistry();
```

#### `GenerationConfigTest`

```java
void associationDefaultsAreStable();

void rejectsInvalidAssociationPageSize();

void editableModeEnablesWrites();

void readOnlyModeDisablesWrites();
```

### 30.3 Bestehende Mapper-Tests

`GrailsRelationshipMapperTest` muss weiterhin beweisen:

- Association-Domain erhält Rollenproperties;
- Teilnehmerdomain erhält keine persistente Association-Collection;
- normale Reference-Inverse-Collection bleibt unverändert;
- Composition-Verhalten bleibt unverändert.

Neuer Regressionstest:

```java
void associationUxPlanningDoesNotAddPersistentCollectionsToParticipants();
```

### 30.4 Snapshot-Tests

`GrailsGeneratedOutputSnapshotTest` erweitern.

Zusätzliche Snapshot-Datei:

```text
target-grails/src/test/resources/grails-snapshots/association-cases/
  src/main/groovy/ch/interlis/generator/grails/generated/
    InterlisAssociationRegistry.groovy
```

Optional auch SimpleAddress-Registry.

Snapshots nur mit:

```bash
UPDATE_GRAILS_SNAPSHOTS=true ./gradlew :target-grails:test
```

aktualisieren, nachdem die inhaltliche Änderung manuell geprüft wurde.

Der Agent darf Snapshots nicht blind aktualisieren, nur um Tests grün zu machen.

### 30.5 Overlay-Tests

`GrailsTemplateOverlayInstallerTest` erweitern:

- neue Services kopiert;
- neue Templates kopiert;
- Registry wird nicht vom Overlay überschrieben;
- JS/CSS bleiben eingebunden;
- wiederholte Installation.

### 30.6 Grails-Runtime-Smoke-Test

`GrailsRuntimeSmokeTest` erweitern:

1. temporäre Grails-App;
2. Overlay installieren;
3. Domains und Registry generieren;
4. `generate-all` für Association- und Teilnehmerdomains;
5. prüfen:
   - `_association-sections.gsp` vorhanden;
   - Controller-Actions vorhanden;
   - Registry kompiliert;
   - Services werden erkannt;
6. `./gradlew compileGroovy`;
7. mindestens ein `test-app`-Integrationstest, der Query/Create/Delete ausführt.

Falls für Integrationstests zusätzliche Testquellen in die temporäre App geschrieben werden, sollen Hilfsmethoden klar benannt sein:

```java
private void writeAssociationIntegrationSpec(Path appDir);

private void runGrailsTests(Path appDir);
```

### 30.7 Real-ili2db-Smoke-Test

Erweitere den bestehenden Realtest um `AssociationCases`.

Prüfen:

- Association-Klassen/Tabellen erkannt;
- Rollenproperties zeigen auf korrekte FK-Spalten;
- Registry-Kontexte vorhanden;
- Quick-Link-Klassifikation stimmt;
- tatsächliches Insert in Association-Tabelle;
- tatsächliche Query von Teilnehmerperspektive;
- Delete entfernt nur Link;
- Zielobjekte bleiben bestehen.

### 30.8 Browser-E2E

Mindestszenario 1: Quick-Link

1. Person erstellen.
2. Parcel erstellen.
3. Person-Show öffnen.
4. Abschnitt für Parcel-Beziehung sichtbar.
5. Parcel per Autocomplete suchen.
6. Zuordnen.
7. Row sichtbar.
8. Parcel öffnen.
9. Gegenperspektive sichtbar.
10. Link entfernen.
11. Person und Parcel existieren weiterhin.

Mindestszenario 2: Association mit Attribut

1. Person erstellen.
2. Document erstellen.
3. Person-Show öffnen.
4. „Association hinzufügen“ klicken.
5. feste Person-Rolle ist vorgefüllt und nicht editierbar.
6. Document wählen.
7. `RoleNote` erfassen.
8. speichern.
9. Rückkehr zur Person-Show.
10. Note und Document sichtbar.
11. Association bearbeiten.
12. Note ändern.
13. entfernen.

Mindestszenario 3: Selbstassoziation

1. zwei Personen erstellen;
2. `PrimaryPerson`-Kontext und `SecondaryPerson`-Kontext getrennt sichtbar;
3. Link anlegen;
4. Rollenrichtung korrekt;
5. kein Vertauschen.

Mindestszenario 4: Manipulationsschutz

1. Link für Owner A erstellen.
2. Delete-Request über Owner B versuchen.
3. Request wird abgelehnt.
4. Link bleibt bestehen.

### 30.9 INTERLIS-Modell- und Transferdatenvalidierung

Jede vom Agenten neu erstellte oder geänderte `.ili`-Datei MUSS mit ili2c geprüft werden.

Bevorzugter lokaler Pfad:

```text
/Users/stefan/apps/ili2c-5.6.8/ili2c.jar
```

Beispiel:

```bash
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar \
  test-models/AssociationCases.ili
```

Falls der lokale Pfad nicht verfügbar ist, muss eine entsprechende Version aus `jars.interlis.ch` verwendet werden. Die Prüfung darf nicht einfach entfallen.

Jede neu erstellte oder geänderte XTF-Testdatei MUSS mit ilivalidator geprüft werden.

Lokaler Pfad:

```text
/Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar
```

Beispiel:

```bash
java -jar /Users/stefan/apps/ilivalidator-1.15.0/ilivalidator-1.15.0.jar \
  --modeldir "test-models;https://models.interlis.ch/" \
  path/to/testdata.xtf
```

Die tatsächlich ausgeführten Befehle und Resultate sind im Umsetzungsplan zu dokumentieren.

### 30.10 Standard-Testbefehle

Mindestens:

```bash
./gradlew test
```

Gezielt:

```bash
./gradlew :core:test
./gradlew :target-grails:test
```

Opt-in:

```bash
./gradlew :target-grails:grailsRuntimeSmokeTest
./gradlew :target-grails:realIli2dbSmokeTest
./gradlew :target-grails:browserE2eTest
```

Mit lokalen Pfaden:

```bash
./gradlew :target-grails:realIli2dbSmokeTest \
  -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1

./gradlew :target-grails:browserE2eTest \
  -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1 \
  -PbrowserE2eJdbcUrl='jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'
```

Fehlende lokale Tools dürfen bei bestehenden Opt-in-Tests zu einem dokumentierten Skip führen. Die Phase gilt aber erst als vollständig, wenn der Agent die geforderten Real-/E2E-Tests in einer geeigneten Umgebung tatsächlich ausgeführt oder den nicht behebbaren Infrastrukturblocker transparent dokumentiert hat. Unit-Tests allein genügen nicht.

---

## 31. Testmodell-Erweiterungen

### 31.1 Bestehendes `AssociationCases.ili`

Das bestehende Modell soll beibehalten werden.

Zusätzliche Fälle dürfen ergänzt werden:

```ili
ASSOCIATION OrderedAssociation =
  Owner -- {1} Person;
  (ORDERED) Documents -- {0..*} Document;
END OrderedAssociation;
```

Die exakte INTERLIS-Syntax ist vor Änderung mit Referenz und ili2c zu prüfen. Keine unvalidierte Syntax in das Repository schreiben.

Optional n-är:

```ili
ASSOCIATION TernaryAssociation =
  PersonRole -- {1} Person;
  ParcelRole -- {1} Parcel;
  DocumentRole -- {0..1} Document;
  Note : TEXT*50;
END TernaryAssociation;
```

### 31.2 Fixtures

`MetadataTestFixtures.createAssociationCasesIli2dbFixture(...)` muss die physische Abbildung konsistent spiegeln.

Keine Fake-Spalten erfinden, die ili2pg real anders erzeugt. Neue Fixture-Namen sind anhand eines echten ili2pg-Schemaimports zu prüfen.

---

## 32. Dokumentation

### 32.1 README

Ergänzen:

- Was Association-UX leistet;
- welche Fälle quick-editierbar sind;
- wann Association-CRUD als Fallback erscheint;
- CLI-Optionen;
- Grenzen bei `ORDERED`, Komposition und optimierten ili2db-Abbildungen;
- Testbefehle.

### 32.2 Technische Dokumentation

Neue oder erweiterte Doku:

```text
docs/association-ux.md
```

Inhalt:

- Architekturdiagramm;
- Registry-Beispiel;
- Context-ID;
- Persistenzprinzip;
- Sicherheitsregeln;
- Extension Points;
- Troubleshooting;
- Merge-Diagnostik.

### 32.3 Generierter Merge-Report

Optional, aber empfohlen:

Erweitere den bestehenden Merge-Report um Association-UX-Klassifikation:

```text
Association
Storage kind
Writable
Presentation
Context
Fixed role
Property
Target role
Cardinality
Diagnostics
```

Keine Änderung der bestehenden Reportfelder brechen.

---

## 33. Umsetzungsplan-Datei

Der Agent MUSS in Phase 0 erstellen:

```text
./docs/association-ux-implementation-plan.md
```

Vorlage:

```markdown
# Association UX Implementation Plan

## Status

| Phase | Status | Beginn | Abschluss | Tests | Bemerkungen |
|---|---|---:|---:|---|---|
| Phase 0 | IN_PROGRESS |  |  |  |  |
| Phase 1 | NOT_STARTED |  |  |  |  |

Zulässige Statuswerte:

- `NOT_STARTED`
- `IN_PROGRESS`
- `BLOCKED`
- `DONE`

## Baseline

- Commit/Branch:
- Java:
- Gradle:
- Grails:
- ili2c:
- ilivalidator:
- ili2pg:
- Docker:
- Playwright:

## Entscheidungen

### ADR-001: ...
- Kontext:
- Entscheidung:
- Alternativen:
- Konsequenzen:

## Risiken

| Risiko | Wahrscheinlichkeit | Auswirkung | Massnahme | Status |
|---|---|---|---|---|

## Phase-Protokolle

### Phase 0
- Geänderte Dateien:
- Ausgeführte Tests:
- Resultate:
- Offene Punkte:
- Abnahme:

## Abschluss-Checkliste

- [ ] Alle Phasen DONE
- [ ] `./gradlew test`
- [ ] Grails Runtime Smoke
- [ ] Real ili2db Smoke
- [ ] Browser E2E
- [ ] ili2c für alle geänderten Modelle
- [ ] ilivalidator für alle geänderten XTF
- [ ] README
- [ ] docs/association-ux.md
- [ ] Keine deaktivierten Tests
- [ ] Keine ungeklärten High-Risk-Punkte
```

Der Plan ist nach jedem grösseren Schritt zu aktualisieren, nicht erst am Schluss.

---

# Phase 0 – Baseline, Analyse und Umsetzungsplan

## Ziel

Eine reproduzierbare Baseline herstellen, bevor funktionaler Code geändert wird.

## Aufgaben

1. Repository vollständig analysieren.
2. Aktuellen Branch und Status dokumentieren.
3. Relevante Klassen und Tests gegen diese Spezifikation abgleichen.
4. `docs/association-ux-implementation-plan.md` erstellen.
5. Bestehende Tests ausführen.
6. `AssociationCases.ili` mit ili2c validieren.
7. Bestehende generierte Association-Snapshots prüfen.
8. Offene Abweichungen zwischen Spezifikation und aktuellem Code dokumentieren.
9. Keine Funktionalität implementieren.

## Zu prüfende Dateien

```text
core/src/main/java/ch/interlis/generator/model/AssociationMetadata.java
core/src/main/java/ch/interlis/generator/model/AssociationRoleMetadata.java
core/src/main/java/ch/interlis/generator/model/RelationshipMetadata.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsRelationshipMapper.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsDomainGenerator.java
target-grails/src/main/java/ch/interlis/generator/grails/GrailsCrudGenerator.java
target-grails/src/main/java/ch/interlis/generator/grails/TargetNameRegistry.java
target-grails/src/main/java/ch/interlis/generator/grails/GenerationConfig.java
target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/
target-grails/src/test/
target-grails/src/grailsRuntimeSmokeTest/
target-grails/src/realIli2dbSmokeTest/
target-grails/src/browserE2eTest/
test-models/AssociationCases.ili
```

## Tests und Gate

```bash
./gradlew test
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar \
  test-models/AssociationCases.ili
```

Phase 0 ist DONE, wenn:

- Baseline grün;
- Plan-Datei vorhanden;
- Architekturabweichungen dokumentiert;
- keine uncommitted unbeabsichtigten Änderungen;
- kein funktionaler Scope vorgezogen.

## Agenten-Prompt für Phase 0

> Lies `./docs/association-ux-implementation-spec.md` vollständig und behandle es als verbindliche Referenz. Lies alle `AGENTS.md`- und Skill-Dateien. Analysiere den aktuellen Stand des gesamten Repositories, insbesondere Core-IR, Grails-Relationship-Mapping, Generatoren, Overlay-Runtime und alle Test-Source-Sets. Erstelle `./docs/association-ux-implementation-plan.md` anhand der Vorlage aus der Spezifikation. Führe die bestehende Testsuite aus und validiere `test-models/AssociationCases.ili` mit ili2c. Implementiere in dieser Phase noch keine Association-UX. Dokumentiere Baseline, Abweichungen, Risiken, konkrete Klassenpfade, Testresultate und offene Entscheidungen. Beende die Phase nur mit grüner Baseline oder einem präzise dokumentierten Infrastrukturblocker.

---

# Phase 1 – Association-Planungsmodell

## Ziel

Die Grails-spezifische, deterministische Klassifikation von Associations implementieren, ohne Runtime- oder UI-Änderung.

## Neue Dateien

```text
AssociationStorageKind.java
AssociationPresentationKind.java
AssociationCreateMode.java
GrailsAssociationRolePlan.java
GrailsAssociationAttributePlan.java
GrailsAssociationContextPlan.java
GrailsAssociationPlan.java
GrailsAssociationPlanner.java
GrailsAssociationPlannerTest.java
```

## Aufgaben

1. Records/Enums implementieren.
2. Planner aus Core-IR und `GrailsRelationshipMapper.DomainMapping` aufbauen.
3. Rollenproperty eindeutig auflösen.
4. Attribute von Rollen-FKs trennen.
5. Kontexte pro fester Rolle erzeugen.
6. Gegenrollenkardinalität korrekt als Perspektivkardinalität verwenden.
7. Quick-Link-Kriterien implementieren.
8. Diagnosen statt riskanter Fallbacks.
9. Deterministische Reihenfolge.
10. Bestehenden Mapper unverändert semantisch halten.

## Akzeptanztests

Alle in Kapitel 30.2 aufgeführten Planner-Tests.

Zusätzlich:

```java
void planDoesNotMutateCoreMetadata();

void planUsesTargetNameRegistryForQualifiedDomainNames();

void rolePropertiesMatchGeneratedAssociationDomainProperties();
```

## Gate

```bash
./gradlew :target-grails:test
./gradlew test
```

Phase 1 ist DONE, wenn:

- alle Planner-Fälle grün;
- keine UI-Änderung;
- keine Core-IR-Änderung ohne ADR;
- bestehende Snapshots unverändert oder bewusst dokumentiert;
- Umsetzungsplan aktualisiert.

## Agenten-Prompt für Phase 1

> Lies `./docs/association-ux-implementation-spec.md` vollständig, insbesondere Kapitel 5 sowie 9 bis 10, und lies den aktuellen `./docs/association-ux-implementation-plan.md`. Verifiziere zuerst Phase 0. Implementiere ausschliesslich Phase 1: die Grails-spezifischen Association-Planungsmodelle und `GrailsAssociationPlanner` bis auf die beschriebenen Klassen und Methoden. Nutze `AssociationMetadata`, `AssociationRoleMetadata`, `TargetNameRegistry` und die tatsächlich von `GrailsRelationshipMapper` erzeugten Domain-Properties. Erzeuge keine inversen GORM-Collections und ändere noch keine Runtime oder GSPs. Schreibe vollständige Unit-Tests für binäre, attributierte, selbstreferenzierende, n-äre, externe, kompositorische, geordnete, physisch abweichende und nicht gemappte Fälle. Führe `:target-grails:test` und die Gesamttests aus. Aktualisiere den Umsetzungsplan mit Dateien, Entscheidungen, Tests und Restpunkten. Fahre nicht mit Phase 2 fort.

---

# Phase 2 – Registry-Generierung und Konfiguration

## Ziel

Die vollständigen Association-Pläne als deterministische Groovy-Registry in die generierte Grails-Anwendung schreiben.

## Neue Dateien

```text
GrailsAssociationRegistryGenerator.java
GrailsAssociationRegistryGeneratorTest.java
```

## Änderungen

```text
GenerationConfig.java
GrailsCrudGenerator.java
GrailsDomainGenerator.java, falls gemeinsame Mapper-Instanz nötig
CLI-Options-Mapping
GrailsGeneratedOutputSnapshotTest.java
Grails-Snapshots
```

## Aufgaben

1. Registry-Generator implementieren.
2. Festen Zielpfad verwenden.
3. `ASSOCIATIONS`, `CONTEXTS`, `CONTEXT_IDS_BY_PARTICIPANT`, `ENTITIES`.
4. Hilfsmethoden generieren.
5. Groovy-Escaping.
6. Leeres Modell unterstützen.
7. Konfigurationsfelder und Validierung.
8. CLI-Mapping, soweit ohne Runtime bereits sinnvoll.
9. Registry in `GrailsCrudGenerator.generate(...)` einhängen.
10. Generated-Groovy-Compile-Test.
11. Snapshots erweitern.

## Gate

```bash
./gradlew :target-grails:test
./gradlew :target-grails:grailsRuntimeSmokeTest
./gradlew test
```

Der Runtime-Smoke muss mindestens die Registry kompilieren, auch wenn noch keine UI sie verwendet.

## Agenten-Prompt für Phase 2

> Lies `./docs/association-ux-implementation-spec.md` vollständig und analysiere den gesamten aktuellen Code, nicht nur die in Phase 2 genannten Dateien. Verifiziere die abgeschlossenen Phasen im Umsetzungsplan. Implementiere Phase 2: `GrailsAssociationRegistryGenerator`, die Association-Konfiguration in `GenerationConfig`, die deterministische Integration in `GrailsCrudGenerator` und die notwendigen CLI-Verbindungen. Die Registry muss im festen Paket `ch.interlis.generator.grails.generated` entstehen, stabil sortiert sein und kompilieren. Erweitere Snapshot- und Compile-Tests. Aktualisiere Snapshots nur nach manueller inhaltlicher Prüfung. Verändere noch keine Show-Seite und keine schreibende Runtime. Führe Unit-, Gesamt- und Grails-Runtime-Smoke-Tests aus und dokumentiere alles in `./docs/association-ux-implementation-plan.md`. Fahre nicht mit Phase 3 fort.

---

# Phase 3 – Read-only Related-Sections

## Ziel

Associations aus der Perspektive beteiligter Fachobjekte auf der Show-Seite anzeigen, zunächst ohne Mutation.

## Neue Overlay-Dateien

```text
InterlisAssociationRegistrySupport.groovy
InterlisAssociationQueryService.groovy
_association-sections.gsp
_association-row-actions.gsp
```

## Änderungen

```text
GrailsTemplateOverlayInstaller.java
InterlisCrudControllerSupport.groovy
Controller.groovy
show.gsp
ili-modern.css
GrailsTemplateOverlayInstallerTest.java
GrailsRuntimeSmokeTest.java
```

## Aufgaben

1. Registry-Support mit strikter Context-Prüfung.
2. Query-Service.
3. `associationModel(instance)`.
4. Related-Sections in Show.
5. Paging und Count.
6. Gegenobjekt-Labels.
7. Links zu Gegenobjekten und Association-Domain.
8. Empty States.
9. read-only bei unsicherem Kontext.
10. keine unbeschränkten Queries.
11. keine Mutationsbuttons.

## Tests

- Registry-Kontext gehört zur Teilnehmerklasse;
- Query filtert feste Rollenproperty;
- Self-Association-Kontexte getrennt;
- Page Size;
- Count;
- nicht vorhandener Owner;
- falscher Kontext;
- Runtime kompiliert;
- Browser-Smoke zeigt Abschnitte bei Seed-Daten oder Integrationstest.

## Gate

```bash
./gradlew :target-grails:test
./gradlew :target-grails:grailsRuntimeSmokeTest
./gradlew :target-grails:realIli2dbSmokeTest
./gradlew test
```

## Agenten-Prompt für Phase 3

> Lies die gesamte Datei `./docs/association-ux-implementation-spec.md` und den vollständigen Umsetzungsplan. Verifiziere Phasen 0 bis 2 samt Tests. Implementiere nur Phase 3: Registry-Runtime, read-only Query-Service und Related-Sections auf den generierten Show-Seiten. Verwende die Association-Domain als Abfragequelle, validiere Context-IDs strikt und begrenze jede Liste. Erzeuge keine GORM-`hasMany`-Association-Collections und noch keine Create/Delete-Actions. Ergänze Overlay-Installer, Controller-Support, Templates, CSS sowie Unit-, Runtime- und Real-ili2db-Tests. Prüfe insbesondere Selbstassoziationen und physisch abweichende Rollenspalten. Dokumentiere Query-Verhalten und mögliche N+1-Risiken. Führe alle geforderten Tests aus und aktualisiere den Umsetzungsplan. Fahre nicht mit Phase 4 fort.

---

# Phase 4 – Quick-Link für einfache binäre Associations

## Ziel

Sichere binäre Link-Assoziationen ohne eigene Attribute direkt aus dem Teilnehmerobjekt hinzufügen und entfernen.

## Neue Dateien

```text
InterlisAssociationCommandService.groovy
_association-quick-add.gsp
```

## Änderungen

```text
InterlisAssociationQueryService.groovy
InterlisCrudControllerSupport.groovy
InterlisRelationshipOptions.groovy
Controller.groovy
_association-sections.gsp
ili-form-ux.js
ili-modern.css
GrailsRuntimeSmokeTest.java
GrailsBrowserE2eTest.java
```

## Aufgaben

1. Gemeinsame Target-Type-Option-Page extrahieren.
2. `associationOptions`.
3. `associationCreate`.
4. `associationDelete`.
5. Quick-Add-GSP.
6. JS-Datenattribute `context` und `role`.
7. serverseitige Context-/Owner-Verifikation.
8. binäre Max-/Min-Kardinalität.
9. Transaktion und Konfliktbehandlung.
10. Zielobjekt niemals löschen.
11. verständliche Flash-Meldungen.
12. Playwright-E2E für Quick-Link.

## Gate

```bash
./gradlew :target-grails:test
./gradlew :target-grails:grailsRuntimeSmokeTest
./gradlew :target-grails:realIli2dbSmokeTest
./gradlew :target-grails:browserE2eTest
./gradlew test
```

## Agenten-Prompt für Phase 4

> Lies `./docs/association-ux-implementation-spec.md` vollständig und verifiziere alle vorherigen Phasen aus `./docs/association-ux-implementation-plan.md`. Implementiere nur Phase 4: transaktionale Quick-Link-Erstellung und Link-Löschung für vom Planner explizit als `QUICK` klassifizierte binäre `LINK_ENTITY`-Associations ohne eigene Attribute. Refaktorisiere den bestehenden Relationship-Autocomplete zu einer gemeinsamen Target-Type-Suche, statt Logik zu duplizieren. Prüfe Context, Owner, Zielrolle, Zugehörigkeit und binäre Kardinalität serverseitig. Verwende nur POST/DELETE für Mutationen. Das Löschen darf ausschliesslich die Association-Domain entfernen. Ergänze umfassende Unit-/Grails-/Real-ili2db-/Playwright-Tests einschliesslich Manipulationsversuch über einen falschen Owner. Aktualisiere den Umsetzungsplan und fahre nicht mit Phase 5 fort.

---

# Phase 5 – Kontextuelle Formulare, Association-Attribute und n-äre Associations

## Ziel

Komplexe Associations über die bestehende Association-Domain bequem und kontextgesichert bearbeiten.

## Neue Datei

```text
InterlisAssociationContextSupport.groovy
_association-context-summary.gsp
```

## Änderungen

```text
InterlisCrudControllerSupport.groovy
_relationship-fields.gsp
_form.gsp
create.gsp
edit.gsp
_association-sections.gsp
GrailsRuntimeSmokeTest.java
GrailsBrowserE2eTest.java
```

## Aufgaben

1. Context-Create-Link von Teilnehmer-Show zum Association-Controller.
2. feste Rolle vorfüllen;
3. feste Rolle serverseitig nach Binding erneut setzen;
4. feste Rolle nicht editierbar anzeigen;
5. übrige Rollen mit bestehendem Autocomplete;
6. eigene Attribute über `<f:all>`;
7. Geometrie-/Enum-Attribute weiter unterstützen;
8. sicherer Redirect ohne freie URL;
9. Context bei Validierungsfehler behalten;
10. Edit aus Related-Row;
11. n-äre Formulare;
12. Selbstassoziationen mit richtiger Rollenrichtung;
13. Browser-E2E mit `AssociationWithAttribute`.

## Gate

Wie Phase 4 plus ili2c, falls Modell verändert.

## Agenten-Prompt für Phase 5

> Lies die vollständige Spezifikation `./docs/association-ux-implementation-spec.md` und den aktuellen Umsetzungsplan. Verifiziere alle vorangehenden Phasen und Tests. Implementiere Phase 5: kontextuelle Create/Edit-Formulare für Association-Domains mit eigenen Attributen, Selbstassoziationen und n-äre Associations. Die feste Teilnehmerrolle muss aus der Registry validiert, serverseitig geladen, nach jedem Binding erneut gesetzt und im Formular nur lesbar dargestellt werden. Verwende keine freie Return-URL. Nutze die bestehenden Relationship-, Fields- und Geometry-Mechanismen für übrige Rollen und Attribute. Ergänze Runtime-, Integrations-, Real-ili2db- und Browser-E2E-Tests, insbesondere für `AssociationWithAttribute`, zwei Rollen derselben Zielklasse und eine n-äre Association. Validere jedes geänderte INTERLIS-Modell mit ili2c. Aktualisiere den Umsetzungsplan und fahre nicht mit Phase 6 fort.

---

# Phase 6 – Navigation, Kardinalität, Fehler und Performance-Härtung

## Ziel

Die neue UX produktionsnah konsolidieren.

## Neue Datei

```text
InterlisNavigationSupport.groovy
```

## Änderungen

```text
main.gsp
Controller.groovy
InterlisAssociationQueryService.groovy
InterlisAssociationCommandService.groovy
InterlisCrudControllerSupport.groovy
ili-form-ux.js
ili-modern.css
README.md
docs/association-ux.md
```

## Aufgaben

1. Association-Controller-Navigation filtern.
2. Fallback-Controller sichtbar halten.
3. Kardinalitätsmeldungen und Statuscodes.
4. Delete-Min-Kardinalität.
5. Pessimistisches Locking oder dokumentierte Alternative.
6. N+1-Analyse und Fetch-Strategie.
7. Sort-/Filter-Whitelist.
8. Accessibility.
9. AbortController im Autocomplete.
10. Responsive Layout.
11. Diagnose-Logging.
12. Dokumentation.

## Tests

- Navigation auto/show/hide;
- unbekannter Controller;
- falscher Context;
- Max/Min;
- konkurrierender Create mindestens als Integrationstest;
- Query-Anzahl oder Fetch-Strategie;
- Tastatur-/ARIA-Prüfungen soweit automatisierbar;
- Context-Path-Deployment im JS-Test oder Browser-E2E.

## Agenten-Prompt für Phase 6

> Lies `./docs/association-ux-implementation-spec.md` vollständig und verifiziere den gesamten bisherigen Umsetzungsstand. Implementiere ausschliesslich Phase 6: Navigation, robuste Kardinalitäts- und Fehlerbehandlung, Performance- und Accessibility-Härtung. Blende technische Association-Controller nur aus, wenn ein sicherer kontextueller Zugang existiert; bewahre Fallbacks. Prüfe und dokumentiere Query-Anzahlen, verhindere unbeschränkte Listen und whiteliste alle dynamischen Sortier-/Property-Zugriffe. Ergänze sichere Konfliktbehandlung, Delete-Min-Prüfung, Autocomplete-Request-Abbruch und responsive GSP/CSS-Regeln. Aktualisiere README und `docs/association-ux.md`. Führe die komplette Testmatrix aus und aktualisiere den Umsetzungsplan. Fahre nicht mit Phase 7 fort.

---

# Phase 7 – Spezialsemantik: `EXTERNAL`, Komposition, `ORDERED`, eingebettete FK-Abbildung

## Ziel

Spezialfälle explizit und semantisch korrekt behandeln. Nicht jeder Fall muss schreibbar werden; jeder Fall muss korrekt klassifiziert und verständlich dargestellt werden.

## Aufgaben

1. `EXTERNAL`:
   - kein Cascade;
   - Ziel nicht automatisch erzeugen;
   - sichere Auswahl oder read-only.

2. Komposition:
   - kein generischer Quick-Link;
   - Parent-/Child-Lifecycle analysieren;
   - nur bei bewiesener Abbildung schreiben;
   - sonst contextual/read-only.

3. `ORDERED`:
   - physische Ordnungsinformation suchen;
   - keine Spalte erfinden;
   - read-only, wenn nicht vorhanden;
   - bei vorhandener Mappinginformation transaktionales Reordering.

4. `EMBEDDED_FOREIGN_KEY`:
   - echte ili2pg-Varianten erzeugen;
   - Core-IR-/Relationship-Merge prüfen;
   - Planner-Klassifikation;
   - direkter Property-Editor auf owning side;
   - inverse Related-List auf anderer Seite;
   - keine Association-Link-Domain voraussetzen.

5. Merge-Report erweitern.

## Zwingende Vorbedingung

Vor schreibender Unterstützung muss ein echter ili2pg-Schemaimport die physische Abbildung belegen.

## Tests

- Real-ili2db ist zwingend;
- ili2c für Modelle;
- ggf. ilivalidator für Seed-XTF;
- Browser-E2E für mindestens einen unterstützten Spezialfall;
- read-only-Test für nicht unterstützte Fälle.

## Agenten-Prompt für Phase 7

> Lies die gesamte Datei `./docs/association-ux-implementation-spec.md` und den vollständigen Umsetzungsplan. Verifiziere Phasen 0 bis 6. Analysiere in Phase 7 die Spezialsemantik `EXTERNAL`, Komposition, `ORDERED` und eingebettete ili2db-FK-Abbildungen anhand echter ili2pg-Schemas. Erfinde keine Spalten, Cascade-Regeln oder Reihenfolgen. Ein Fall darf ausdrücklich read-only bleiben, wenn die physische oder fachliche Abbildung nicht zweifelsfrei ist; dies muss jedoch deterministisch klassifiziert, diagnostiziert, getestet und dokumentiert sein. Schreibfunktion ist nur mit Real-ili2db-Test zulässig. Validiere alle Modelle mit ili2c und alle XTF-Daten mit ilivalidator. Aktualisiere Merge-Report, Dokumentation, Tests und Umsetzungsplan. Fahre nicht mit Phase 8 fort.

---

# Phase 8 – Abschluss, vollständige Regression und Abnahme

## Ziel

Die Funktion als konsistente, dokumentierte und vollständig getestete Erweiterung abschliessen.

## Aufgaben

1. Alle TODOs dieses Dokuments prüfen.
2. Alle Phasen im Plan auditieren.
3. Öffentliche API und Paketstruktur aufräumen.
4. Keine toten experimentellen Klassen.
5. Keine duplizierte Autocomplete-/Registry-Logik.
6. README und technische Doku finalisieren.
7. CLI-Help prüfen.
8. Snapshot-Diffs manuell prüfen.
9. Gesamte Testmatrix.
10. Browser-E2E mindestens zweimal auf frischer DB ausführen.
11. geänderte INTERLIS-Modelle mit ili2c.
12. geänderte XTF-Dateien mit ilivalidator.
13. Abschlussbericht im Umsetzungsplan.
14. offene Punkte klar als „Future“ ausweisen, nicht als versteckte Defekte.

## Vollständige Befehle

```bash
./gradlew clean test

./gradlew :target-grails:grailsRuntimeSmokeTest

./gradlew :target-grails:realIli2dbSmokeTest \
  -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1

./gradlew :target-grails:browserE2eTest \
  -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1 \
  -PbrowserE2eJdbcUrl='jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'

java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar \
  test-models/AssociationCases.ili
```

Weitere Modelle einzeln validieren.

## Agenten-Prompt für Phase 8

> Lies `./docs/association-ux-implementation-spec.md` ein letztes Mal vollständig und gleiche jede Muss-Anforderung mit dem aktuellen Repository ab. Lies den gesamten `./docs/association-ux-implementation-plan.md` und verifiziere jede als DONE markierte Phase anhand von Code und Tests. Führe die komplette Unit-, Snapshot-, Compile-, Grails-Runtime-, Real-ili2db- und Browser-E2E-Testmatrix aus. Validiere alle geänderten INTERLIS-Modelle mit ili2c und alle geänderten XTF-Dateien mit ilivalidator. Behebe Regressionen, entferne tote oder duplizierte Implementationen, prüfe Dokumentation und CLI-Hilfe und erstelle im Umsetzungsplan einen präzisen Abschlussbericht mit Testbefehlen und Resultaten. Markiere die Gesamtumsetzung nur als abgeschlossen, wenn alle Definition-of-Done-Punkte erfüllt sind. Nimm keine neuen, nicht spezifizierten Features auf.

---

## 34. Globale Definition of Done

Die Gesamtfunktion gilt nur als fertig, wenn alle Punkte erfüllt sind.

### Architektur

- [ ] Core-IR bleibt framework-agnostisch.
- [ ] Association-Domain bleibt persistente Wahrheit.
- [ ] Keine synthetischen Join-Tabellen.
- [ ] Keine pauschalen Association-`hasMany`.
- [ ] Planner und Domain-Generator verwenden dieselben Naming-/Mapping-Entscheidungen.
- [ ] Registry ist deterministisch.
- [ ] Unsichere Fälle werden read-only statt geraten.

### Funktion

- [ ] Related-Sections auf Teilnehmer-Show.
- [ ] serverseitiges Paging.
- [ ] einheitlicher Autocomplete.
- [ ] Quick-Link für sichere binäre Fälle.
- [ ] kontextuelle Formulare mit Association-Attributen.
- [ ] Selbstassoziationen.
- [ ] n-äre Associations.
- [ ] sichere Redirects.
- [ ] sichere Delete-Zugehörigkeitsprüfung.
- [ ] binäre Max-/Min-Kardinalität.
- [ ] Navigation ohne technische Menüflut.
- [ ] Fallback-CRUD bleibt erreichbar.

### Qualität

- [ ] verständliche Fehler;
- [ ] keine Mass-Assignment-Lücke;
- [ ] keine Open Redirects;
- [ ] keine Mutation über GET;
- [ ] keine unbeschränkten Relationship-/Association-Listen;
- [ ] keine offensichtliche N+1-Explosion;
- [ ] responsive;
- [ ] barrierearme Grundbedienung;
- [ ] keine externen CDNs.

### Tests

- [ ] Planner-Unit-Tests;
- [ ] Registry-Tests;
- [ ] Snapshot-Tests;
- [ ] Groovy-Compile;
- [ ] Grails Runtime Smoke;
- [ ] Service-Integration;
- [ ] Real ili2db Smoke;
- [ ] Browser E2E;
- [ ] Manipulationsschutz;
- [ ] Kardinalität;
- [ ] Selbstassoziation;
- [ ] Association-Attribute;
- [ ] n-är;
- [ ] ili2c;
- [ ] ilivalidator bei XTF.

### Dokumentation

- [ ] README;
- [ ] `docs/association-ux.md`;
- [ ] `docs/association-ux-implementation-plan.md`;
- [ ] ausgeführte Tests dokumentiert;
- [ ] offene Spezialfälle dokumentiert;
- [ ] keine irreführenden Behauptungen über unterstützte ili2db-Flavours.

---

## 35. Verbotene Abkürzungen

Der Coding-Agent darf nicht:

- nur eine Combobox auf der Association-CRUD-Seite ergänzen und dies als vollständige Association-UX deklarieren;
- Association-Rollen allein über Zielklassennamen identifizieren;
- Self-Associations zusammenfassen;
- alle Ziele mit `targetType.list()` laden;
- ungeprüfte Client-Property-Namen in Criteria/HQL einsetzen;
- eine freie Return-URL akzeptieren;
- feste Rollen nur per Hidden Field schützen;
- Zielobjekte beim Entfernen eines Links löschen;
- Snapshots blind aktualisieren;
- Tests deaktivieren;
- Real-/E2E-Tests durch zusätzliche Unit-Tests ersetzen;
- unvalidierte `.ili`- oder XTF-Dateien committen;
- Core-IR um Grails-UI-Felder erweitern;
- eine SPA oder neue Frontend-Toolchain einführen;
- `ORDERED` durch Sortierung nach ID vortäuschen;
- Komposition als gewöhnliche Many-to-Many behandeln;
- `EXTERNAL` ignorieren;
- unbekannte Fälle schreibbar machen.

---

## 36. Empfohlene Abschlusszusammenfassung des Coding-Agenten

Nach jeder Phase:

```markdown
## Phase X abgeschlossen

### Implementiert
- ...

### Geänderte Dateien
- `...`

### Architekturentscheidungen
- ...

### Tests
- `./gradlew ...` – PASS
- `java -jar ...ili2c.jar ...` – PASS

### Nicht durchgeführt
- ... mit präzisem Grund

### Offene Risiken
- ...

### Nächste Phase
- Phase X+1 gemäss `./docs/association-ux-implementation-spec.md`
```

Kein „alles erledigt“, wenn ein Test nur geskippt oder nicht verfügbar war. Skip, Infrastrukturblocker und fachlicher Restpunkt sind klar zu unterscheiden.

---

## 37. Zukunftskapitel ausserhalb des verbindlichen Scopes

Diese Punkte dürfen dokumentiert, aber nicht ungefragt in die aktuelle Umsetzung aufgenommen werden:

- projektspezifische Association-Dashboards;
- Bulk-Zuordnungen;
- Drag-and-drop-Reordering für `ORDERED`;
- rollenbasierte Autorisierung pro Context;
- Audit-History;
- Association-Diff und XTF-Export;
- Inline-Modal statt kontextueller Formularseite;
- optimierte DataLoader-/Batch-API;
- fachmodellspezifische Filter;
- direkte Karteninteraktion für räumliche Associations.

Die implementierte Architektur soll diese Weiterentwicklung ermöglichen, ohne sie jetzt vorwegzunehmen.

---

## 38. Merksatz

> INTERLIS-Assoziationen werden nicht zu bequemen GORM-Many-to-Many-Beziehungen umgedeutet.  
> Die physische Association-Domain bleibt die Wahrheit; `ili2grails` erzeugt daraus sichere, kardinalitäts- und rollenbewusste fachliche Sichten und Aktionen auf den beteiligten Objekten.
