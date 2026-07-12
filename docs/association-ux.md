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
  GrailsAssociationRegistryGenerator → InterlisAssociationRegistry.groovy

Generierte Grails-Anwendung (Runtime)
  InterlisAssociationRegistry          → deterministische Registry
  InterlisAssociationRegistrySupport   → Context-Validierung, Domain-Auflösung
  InterlisAssociationQueryService      → Read-only Sections, Paging, Autocomplete
  InterlisAssociationCommandService    → Quick-Link Create/Delete (transaktional)
  InterlisAssociationContextSupport    → Kontextuelle Formulare
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

## Persistenzprinzip

- **Association-Domain bleibt die persistente Wahrheit.** Keine inversen GORM-`hasMany` auf Teilnehmerklassen.
- Related-Lists werden via Criteria-Query über die Association-Domain abgefragt.
- Keine synthetischen Join-Tabellen, FK-Spalten oder Cascade-Regeln.
- Löschen entfernt nur die Association-Domain; Zielobjekte bleiben erhalten.

## Präsentationsmodi

| Modus | Kriterien | UI-Elemente |
|---|---|---|
| `QUICK_LINK` | Binär, `LINK_ENTITY`, keine eigenen Attribute, kein `ordered`/`composition`/`external` | Autocomplete + Zuordnen/Entfernen auf Show-Seite |
| `CONTEXTUAL_FORM` | Hat eigene Attribute, Spezialsemantik | Kontextuelles Create/Edit-Formular der Association-Domain |
| `NARY_CONTEXTUAL_FORM` | Drei oder mehr Rollen | Kontextuelles Formular mit fixierter Rolle |
| `READ_ONLY` | Keine sichere physische Schreibabbildung | Nur Anzeige, keine Mutation |

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
2. Prüfe, ob die Assoziation als `LINK_ENTITY` klassifiziert ist (ili2db bettet attributlose binäre Assoziationen teilweise als FK-Spalten ein → `UNMAPPED`).
3. Prüfe, ob `associationUiMode` auf `editable` oder `auto` steht.

## Testbefehle

```bash
# Unit/Snapshot/Compile-Tests
JAVA_HOME=.../21.0.10-tem ./gradlew test

# Grails Runtime Smoke (braucht grails CLI)
PATH=.../grails-7.0.6/bin:$PATH ./gradlew :target-grails:grailsRuntimeSmokeTest -PgrailsSmokeVersion=7.0.6

# Real ili2db Smoke (braucht Docker + ili2pg)
./gradlew :target-grails:realIli2dbSmokeTest -Pili2pgHome=/path/to/ili2pg-5.6.1

# Browser E2E (braucht Docker + ili2pg + grails + Playwright Chromium)
./gradlew :target-grails:browserE2eTest \
  -PgrailsSmokeVersion=7.0.6 \
  -Pili2pgHome=/path/to/ili2pg-5.6.1 \
  -PbrowserE2eJdbcUrl='jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'

# ili2c-Validierung
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/AssociationCases.ili
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/QuickLinkE2E.ili
java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/ContextualAssociationE2E.ili
```
