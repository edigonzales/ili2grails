# Association-UX: Technische Dokumentation

Dokumentation der Association-UX-Erweiterung in ili2grails.  
Verbindliche Spezifikation: [`association-ux-implementation-spec.md`](./association-ux-implementation-spec.md)  
Umsetzungsstand: [`association-ux-implementation-plan.md`](./association-ux-implementation-plan.md)

## Architektur

```
Core-IR (framework-agnostisch)
  ModelMetadata
    └── AssociationMetadata
          ├── AssociationRoleMetadata (Rollen, Kardinalitäten, Flags)
          └── AttributeMetadata (eigene Assoziationsattribute)

target-grails (Planungszeit)
  GrailsRelationshipMapper     → Domain-Mapping (Properties, Spalten)
  GrailsAssociationPlanner      → Pläne (Plans, Contexts, Klassifikation)
  GrailsInverseRelationshipPlanner → sichere umgekehrte 1:n-Editoren
  GrailsAssociationRegistryGenerator → InterlisAssociationRegistry.groovy

Generierte Grails-Anwendung (Runtime)
  InterlisAssociationRegistry          → deterministische Registry
  InterlisAssociationRegistrySupport   → Context-Validierung, Domain-Auflösung
  InterlisAssociationQueryService      → Read-only Sections, Paging, Autocomplete
  InterlisAssociationCommandService    → Quick-Link Create/Delete (transaktional)
  InterlisInverseRelationshipQueryService   → direkte 1:n-Listen und Suche
  InterlisInverseRelationshipCommandService → FK-Zuweisung/Umteilung
  InterlisAssociationContextSupport    → Kontextuelle Formulare
  InterlisInverseRelationshipContextSupport → sichere direkte 1:n-Create-Kontexte
  InterlisNavigationSupport            → Navigationsfilter
```

## Registry-Beispiel (Auszug)

```groovy
// InterlisAssociationRegistry.groovy (generiert)

ASSOCIATIONS = [
    "ContextualAssociationE2E.Data.Beteiligung": [
        associationName: "ContextualAssociationE2E.Data.Beteiligung",
        domainClassName: "Beteiligung",
        storageKind: "LINK_ENTITY",
        writable: true,
        roles: [
            [name: "PersonRole", property: "personRoleId", targetDomainClass: "com.example.domain.Person", min: 0, max: -1],
            [name: "DocumentRole", property: "documentRoleId", targetDomainClass: "com.example.domain.Document", min: 0, max: -1]
        ],
        attributes: [
            [iliName: "Funktion", property: "funktion", type: "String", label: "Funktion"]
        ]
    ]
]

CONTEXTS = [
    "ContextualAssociationE2E.Data.Beteiligung::PersonRole": [
        id: "ContextualAssociationE2E.Data.Beteiligung::PersonRole",
        associationName: "ContextualAssociationE2E.Data.Beteiligung",
        participantDomainClass: "com.example.domain.Person",
        fixedRole: "PersonRole",
        fixedProperty: "personRoleId",
        editableRoles: ["DocumentRole"],
        presentation: "CONTEXTUAL_FORM",
        createMode: "CONTEXTUAL_FORM",
        writable: true,
        removable: true,
        perspectiveMin: 0,
        perspectiveMax: -1
    ]
]

ENTITIES = [
    "com.example.domain.Beteiligung": [kind: "ASSOCIATION", ilikeName: "ContextualAssociationE2E.Data.Beteiligung", showInNavigation: false]
]
```

## Context-ID

Format: `<qualified-association-name>::<fixed-role-name>`

Beispiel: `ContextualAssociationE2E.Data.Beteiligung::PersonRole`

- Der Wert ist deterministisch und URL-encodiert
- Der Client übergibt nur `context` und `ownerId`; alle Klassen-/Property-Namen kommen aus der Registry

## Persistenzprinzip für Link-Tabellen

- **Association-Domain bleibt die persistente Wahrheit.** Für diesen
  Link-Tabellen-Pfad werden keine inversen GORM-`hasMany` auf den Fachklassen
  erfunden.
- Related-Lists werden via Criteria-Query über die Association-Domain abgefragt.
- Keine synthetischen Join-Tabellen, FK-Spalten oder Cascade-Regeln.
- Löschen entfernt nur die Association-Domain; Zielobjekte bleiben erhalten.

Davon getrennt ist der direkte 1:n-Pfad: Wenn die persistente Wahrheit bereits
eine eindeutige FK-Property wie `Employee.department` ist, liest und schreibt
ili2grails genau diese Property. Es wird keine Association-Domain und keine
Verbindungstabelle erfunden.

## Direkte 1:n-Zuweisung

Beispiel aus `GsSimpleModel`:

```text
INTERLIS: Department 1 ← 0..* Employee
DB:       organization_employee.department → organization_department.t_id
Grails:   Employee.department und Department.employees
GUI:      Department zeigt Employees und bietet „Employee zuweisen“
```

Der `GrailsInverseRelationshipPlanner` verwendet dieselbe
`GrailsRelationshipMapper`-Instanz wie die Domain-Generierung. Er erzeugt
`interlisInverseRelationshipMeta` nur, wenn genau eine physische
`MANY_TO_ONE`-Property und ihre eindeutig erzeugte Gegen-Collection gefunden
werden. Kompositionen, externe, geordnete, mehrdeutige oder nicht vollständig
generierte Beziehungen werden nicht als editierbar geplant.

Beim Zuweisen übermittelt der Browser nur Collection-Name und Ziel-ID. Der
Command-Service löst Klasse und Property aus den generierten Metadaten auf:

1. Owner und Employee laden, den Employee best-effort sperren.
2. Aktuelle Zuordnung erneut lesen.
3. Gleicher Owner: idempotenter Erfolg.
4. Anderer Owner ohne Bestätigung: HTTP 409
   `REASSIGNMENT_CONFIRMATION_REQUIRED`.
5. Nach Bestätigung nur `employee.department` ändern, validieren und speichern.

Die Suchresultate lassen bereits dem aktuellen Owner zugeordnete Datensätze weg
und zeigen bei anderen Ownern den bisherigen Anzeigenamen, zum Beispiel
`Anna Keller · aktuell: HR`. Version 1 unterstützt kein Entfernen einer
Zuordnung. `t_basket` wird weder gelesen noch geändert.

### Generische Darstellung in Create/Edit und Show

To-One-Referenzen sind im UI normale fachliche Felder. Sie stehen in der
gemeinsamen Default-Sektion `Basisdaten` und können über `form.sections`
fachlich gruppiert werden. Auf der Show-Seite erscheinen sie in den normalen
Detailsektionen; vorhandene Werte werden als `ili-data-link` gerendert. Die
historische Card `Verknüpfte Datensätze` bleibt nur als kompatibler Fallback für
Links erhalten, die nicht in eine Detailsektion integriert werden können.

Inverse direkte 1:n-Beziehungen werden als Inline-Tabelle dargestellt. Ihre
Spalten, Display-Felder, Suchfelder und whitelisted Sortierfelder kommen aus
dem Descriptor des Zieldomains. Jede Tabelle liest und schreibt ihren Zustand
unabhängig über:

```text
inverse.<collection>.q
inverse.<collection>.max
inverse.<collection>.offset
inverse.<collection>.sort
inverse.<collection>.order
```

Die Initialseite ist begrenzt; weiteres Paging bleibt in derselben Card. Das
alte Browse-Modal ist damit nicht mehr Teil der direkten 1:n-UX. Die JSON-
Endpunkte für Relationship-Optionen und Paging bleiben für bestehende Clients
kompatibel.

Eine sichere direkte 1:n-Tabelle kann kontextuell einen abhängigen Datensatz
erfassen. Der Link übergibt nur `relationshipField` und
`relationshipOwnerId`. `InterlisInverseRelationshipContextSupport` löst das
Ziel und den Owner aus generierten Metadaten auf, prüft Sichtbarkeit und
Schreibbarkeit und setzt die FK nach jedem Binding erneut. Manipulierte Owner-
IDs, unbekannte Felder und nicht direkte oder read-only Beziehungen werden
abgewiesen. Nach erfolgreichem Speichern erfolgt die Rückleitung zur
Elternseite.

Laufzeitkonfiguration:

```yaml
ili2grails:
  ui:
    domains:
      - iliName: GsSimpleModel.Organization.Department
        relationships:
          employees:
            label: Mitarbeitende
            mode: auto
```

`auto`, `editable`, `read-only` und `off` sind erlaubt. Konfiguration kann die
generierte Sicherheit nur beibehalten oder einschränken, nie erweitern.
`--grails-association-ui` bleibt die obere Grenze.

## Präsentationsmodi

| Modus | Kriterien | UI-Elemente |
|---|---|---|
| `QUICK_LINK` | Binär, `LINK_ENTITY`, keine eigenen Attribute, kein `ordered`/`composition`/`external` | Autocomplete + Zuordnen/Entfernen auf Show-Seite |
| `CONTEXTUAL_FORM` | Hat eigene Attribute, Spezialsemantik | Kontextuelles Create/Edit-Formular der Association-Domain |
| `NARY_CONTEXTUAL_FORM` | Drei oder mehr Rollen | Kontextuelles Formular mit fixierter Rolle |
| `READ_ONLY` | Keine sichere physische Schreibabbildung | Nur Anzeige, keine Mutation |

## Speichersarten

| StorageKind | Beschreibung | Schreibbar |
|---|---|---|
| `LINK_ENTITY` | Eigene Link-Tabelle mit FK-Spalten | Ja (wenn QUICK/CONTEXTUAL_FORM) |
| `EMBEDDED_FOREIGN_KEY` | FK-Spalten in Teilnehmerklassen eingebettet (ili2db `--smart2Inheritance`) | Nein (read-only) |
| `UNMAPPED` | Keine ableitbare physische Abbildung | Nein (read-only) |

## Spezialsemantik

### EXTERNAL
- **Planner:** Blockiert QUICK_LINK; Klassifiziert CONTEXTUAL_FORM
- **CommandService:** `hasExternalRole()`-Guard blockiert Delete; Create nur über kontextuelles Formular
- **Real-ili2pg:** Wie alle attributlosen binären Assoziationen eingebettet → `EMBEDDED_FOREIGN_KEY` → read-only

### Komposition (`-<#>`)
- **Planner:** Blockiert QUICK_LINK; Klassifiziert CONTEXTUAL_FORM
- **CommandService:** `hasCompositionRole()`-Guard blockiert Delete; kein Cascade-Delete
- **Real-ili2pg:** Wie EXTERNAL → `EMBEDDED_FOREIGN_KEY` → read-only

### ORDERED
- **Planner:** Blockiert QUICK_LINK; Klassifiziert CONTEXTUAL_FORM
- **Physische Abbildung:** ili2pg legt keine Reihenfolgespalte an; `ordered`-Flag stammt ausschliesslich aus ili2c
- **Real-ili2pg:** Attributlose binäre Assoziation → `EMBEDDED_FOREIGN_KEY` → read-only
- **Testmodell:** `OrderedAssociation` in `AssociationCases.ili` (H2-Fixture: LINK_ENTITY, real: EMBEDDED_FOREIGN_KEY)

### EMBEDDED_FOREIGN_KEY
- **Erkennung:** Assoziationsklasse existiert in ili2db-Metadaten, hat aber keine physische Tabelle (FK-Spalten wurden in Teilnehmerklassen eingebettet)
- **Planner:** Klassifiziert `EMBEDDED_FOREIGN_KEY` (statt UNMAPPED) für Assoziationen ohne Link-Tabelle
- **Association-Registry-Status:** Weiterhin read-only mit Diagnose `EMBEDDED_FK_ASSOCIATION`
- **Separater 1:n-Pfad:** Eine eindeutig gemappte reguläre
  `MANY_TO_ONE`-Property kann über `GrailsInverseRelationshipPlanner`
  zuweisbar sein. Unsichere Registry-Kontexte werden dadurch nicht freigeschaltet.

## Sicherheitsregeln

- **Context-Validierung:** `requireContext()` prüft Context-ID, Teilnehmer-Klasse, Association-Existenz, feste Rolle und Property.
- **Owner-Verifikation:** Jede Edit/Delete-Aktion prüft, dass `associationInstance.fixedProperty.id == participantId`.
- **Keine Mass-Assignment:** Feste Rolle wird nach `bindData()` serverseitig erneut gesetzt.
- **Keine Open Redirects:** Rückleitung wird aus Registry+Owner-ID abgeleitet, keine freie `returnUrl`.
- **HTTP-Methoden:** Mutationen ausschliesslich über POST/DELETE.
- **Property-Whitelisting:** Sortierfelder werden gegen GORM-Metadaten geprüft (`safeSort`).

## Extension Points (Autorisierung)

Überschreibbare Methoden im CommandService:

```groovy
protected boolean canCreateAssociation(Object participant, Map<String, Object> context)
protected boolean canDeleteAssociation(Object participant, Object associationInstance, Map<String, Object> context)
```

Standard: `true`. Fachprojekte können durch Service-Subclassing Autorisierung pro Context implementieren.

## Performance

- **Paging:** Sections initial auf 10 Zeilen, Page-API maximal 100, Autocomplete Default 25.
- **Fetch-Join:** Counterpart-Zielobjekte werden per `FetchMode.JOIN` im Criteria-Query mitgeladen (verhindert N+1).
- **Count:** Separate Count-Query, nicht durch vollständiges Laden aller Zeilen.
- **Registry:** Statisch, kein Rebuild pro Request.
- **Autocomplete:** Debounced (250ms), `AbortController` bricht laufende Requests ab.

## Kardinalitätsprüfung

- **Binary Create:** `count + 1 <= perspectiveMax`; bei Max=-1 (unbounded) keine Prüfung.
- **Binary Delete:** `count - 1 >= perspectiveMin`; bei Min=0 keine Prüfung.
- **Locking:** Best-effort pessimistisch via `type.lock(id)`; Fallback auf `get()` mit DB-Constraints.
- **Konflikt:** `DataIntegrityViolationException` → 409 Conflict, `OptimisticLockingFailureException` → 409 Conflict.

## Konkurrenz

Die Kardinalitätsprüfung verwendet einen Count-Query vor dem Insert/Delete (time-of-check-to-time-of-use).
Im Fall einer gleichzeitigen Verletzung greifen die Datenbank-Constraints (Unique, Foreign Key, NOT NULL).
Der `lockOrGet()`-Mechanismus versucht best-effort pessimistisches Locking, fällt aber bei fehlender
Unterstützung auf ein einfaches `get()` zurück.

## CLI-Optionen

```
--grails-association-ui <auto|off|read-only|editable>
--grails-association-page-size <1..100>
--grails-association-navigation <auto|show|hide>
```

| Option | Default | Beschreibung |
|---|---|---|
| `association-ui` | `auto` | `editable` = Schreiben erlaubt, `read-only` = nur Anzeige, `off` = keine Sections |
| `association-page-size` | `10` | Seitengrösse für Association-Listen (1-100) |
| `association-navigation` | `auto` | `auto` = technische Controller ausblenden wenn Kontextzugriff, `show` = alle anzeigen, `hide` = alle ausblenden |

## Troubleshooting

### Association-Domain Create/Edit wirft "Grails Runtime Exception"

**Ursache:** Der Controller `create()`/`edit()`-Aufruf ohne Context-Parameter (`associationContext`, `associationOwnerId`).
**Lösung (Phase 6):** `formModelWithContext()` liefert immer `hiddenRelationshipFields`, `fixedRelationshipLabels` und `associationContextState` (Default: leere Liste/null). Die GSP-Templates verwenden `\${g:if}` für optionale Blöcke.

### Assoziationsabschnitt erscheint nicht auf der Show-Seite

1. Prüfe, ob die Registry für den Teilnehmer `contextsForParticipant` enthält.
2. Prüfe `associationUiMode` (darf nicht `off` sein).
3. Prüfe Log auf `associationModel failed for ...` Warnungen.

### Quick-Link "Zuordnen" wird nicht angezeigt

1. Prüfe in der Registry, ob `createMode == "QUICK"` und `writable == true`.
2. Prüfe, ob die Assoziation als `LINK_ENTITY` klassifiziert ist (ili2db bettet attributlose binäre Assoziationen teilweise als FK-Spalten ein → `EMBEDDED_FOREIGN_KEY`).
3. Prüfe, ob `associationUiMode` auf `editable` oder `auto` steht.
4. Prüfe, ob die Rollen `ordered`, `external` oder `composition` haben (diese blockieren QUICK_LINK).

### Assoziation wird als read-only angezeigt

1. Prüfe `storageKind` in der Registry: `EMBEDDED_FOREIGN_KEY` und `UNMAPPED` sind immer read-only.
2. Bei `EMBEDDED_FOREIGN_KEY`: ili2db hat die FK-Spalten in Teilnehmerklassen eingebettet (`--smart2Inheritance`). Die Association-Registry schreibt diesen Kontext nicht.
3. Bei externen Rollen (`external: true`): Schreibschutz durch `hasExternalRole()`-Guard.

### Department zeigt keine zuweisbaren Employees

1. Prüfe, ob `Department.interlisInverseRelationshipMeta.employees` generiert
   wurde.
2. Prüfe `--grails-association-ui`: `off` blendet aus, `read-only` zeigt ohne
   Zuweisungsformular.
3. Prüfe in `application.yml` den exakten generierten Collection-Namen und einen
   Modus aus `auto`, `editable`, `read-only`, `off`.
4. Fehlen die Metadaten, war die Beziehung nicht eindeutig sicher, etwa wegen
   Komposition, `EXTERNAL`, `ORDERED`, Mehrdeutigkeit, fehlender Domainklasse
   oder fehlender physischer FK-Spalte. Das lässt sich nicht per YAML
   freischalten.

### INTERLIS ORDERED wird nicht als Reihenfolge dargestellt

Die `ORDERED`-Semantik wird vom Planner korrekt erfasst und blockiert QUICK_LINK. Eine physische Reihenfolgespalte wird von ili2pg nicht angelegt; die geordnete Darstellung und Schreibfunktion sind zukünftige Erweiterungen.

## Testbefehle

```bash
# Unit/Snapshot/Compile-Tests
JAVA_HOME=.../21.0.10-tem ./gradlew test

# Grails Runtime Smoke (braucht grails CLI)
PATH=.../grails-7.0.6/bin:$PATH ./gradlew :target-grails:grailsRuntimeSmokeTest

# Real ili2db Smoke (braucht Docker + ili2pg)
./gradlew :target-grails:realIli2dbSmokeTest -Pili2pgHome=/path/to/ili2pg-5.6.1

# Browser E2E (braucht Docker + ili2pg + grails + Playwright Chromium)
./gradlew :target-grails:browserE2eTest \
  -Pili2pgHome=/path/to/ili2pg-5.6.1 \
  -PbrowserE2eJdbcUrl='jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'

# ili2c-Validierung
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/AssociationCases.ili
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/QuickLinkE2E.ili
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/ContextualAssociationE2E.ili
```
