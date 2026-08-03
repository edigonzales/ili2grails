# Association-UX

Dieses Dokument ist die fachliche Referenz für die generische Darstellung und
Bearbeitung von INTERLIS-Beziehungen. Der aktuelle Generator-, Runtime- und
Buildvertrag steht in der [README](../README.md); er wird hier nicht
dupliziert.

## Leitprinzip

ili2grails erfindet keine zusätzliche Persistenzstruktur. Die von ili2db
gewählte Abbildung bleibt die Wahrheit:

- Eine physische Association-Domain beziehungsweise Link-Tabelle wird als
  eigene persistente Domain gelesen und geschrieben.
- Eine direkte 1:n-Beziehung mit einer FK-Property wie
  `Employee.department` wird genau über diese Property gelesen und geändert.
- Es entstehen keine synthetischen Join-Tabellen, FK-Spalten oder Cascades.

Der Generator kombiniert dafür das physische ili2db-Mapping mit der
INTERLIS-Semantik und erzeugt typisierte `AssociationDescriptor`,
`AssociationContextDescriptor`, `AssociationRoleDescriptor` und
`InverseRelationshipDescriptor`. Die Runtime entscheidet nicht anhand frei
übergebener Klassen- oder Propertynamen.

## Laufzeitvertrag

Beim Plugin-Startup werden die beiden generierten typisierten Registries in
eine injizierte `InterlisRuntimeRegistry` übernommen. Query- und
Command-Services verwenden danach ausschließlich diese Registry. Maps sind an
der GSP-/JSON-Grenze weiterhin normale View-Modelle, aber nicht die Quelle
fachlicher Entscheidungen.

Ein Context hat eine deterministische ID:

```text
<qualified-association-name>::<fixed-role-name>
```

Der Browser übergibt nur die Context-ID und Datensatz-IDs. Association,
Teilnehmerklasse, Rollen, Properties, Kardinalitäten und Schreibfähigkeit
werden serverseitig aus den Deskriptoren aufgelöst.

## Unterstützte Darstellungen

### Read-only Association

Komplexe oder nicht eindeutig schreibbare Associations werden angezeigt,
aber nicht generisch verändert. Das betrifft insbesondere Abbildungen, bei
denen Rollen, Zieltypen oder physische Properties nicht eindeutig auflösbar
sind.

### Quick-Link

Eine binäre `LINK_ENTITY`-Association ohne eigene Fachattribute kann als
Quick-Link bearbeitet werden, wenn der Planner sie als sicher klassifiziert.
Der Command-Service:

1. validiert Context und Zielrolle;
2. lädt und sperrt den Teilnehmer mit expliziter Lock-Semantik;
3. prüft das Zielobjekt und die Authorization-Policy;
4. verhindert Duplikate und Kardinalitätsverletzungen;
5. validiert und speichert die Association-Domain transaktional.

Beim Löschen wird ausschließlich die Association-Domain entfernt. Die
verknüpften Fachobjekte bleiben erhalten. Kompositions- und externe Rollen
werden nicht über den generischen Delete-Pfad gelöscht.

### Kontextuelles Association-Formular

Trägt eine Association eigene Attribute, kann der fixe Teilnehmer aus dem
Context gesetzt und ein normales Formular für die übrigen whitelisted Rollen
und Attribute aufgebaut werden. Nicht im Descriptor enthaltene Felder werden
nicht aus Requestparametern übernommen.

### Direkte inverse 1:n-Beziehung

Beispiel:

```text
INTERLIS: Department 1 ← 0..* Employee
DB:       employee.department → department.t_id
Grails:   Employee.department
```

Eine inverse Beziehung ist nur editierbar, wenn genau eine passende
persistente To-One-Property ermittelt wurde und der Descriptor sie als sicher
markiert. Kompositionen, externe, geordnete, mehrdeutige oder unvollständig
gemappte Beziehungen bleiben read-only.

Beim Zuweisen gilt:

1. Owner und Zieldatensatz werden mit expliziter Lock-Semantik geladen.
2. Ist der Datensatz bereits demselben Owner zugeordnet, ist der Befehl
   idempotent erfolgreich.
3. Bei einem anderen Owner liefert der erste Versuch
   `REASSIGNMENT_CONFIRMATION_REQUIRED` und verändert nichts.
4. Erst eine bestätigte und autorisierte Umteilung setzt die persistente
   FK-Property.
5. Validierungs-, Datenbank- oder Concurrency-Fehler liefern einen typisierten
   Fehler und hinterlassen keine partielle Zuweisung.

## Listen, Suche und Paging

Association- und inverse Relationship-Listen sind serverseitig begrenzt. Ihre
Display-, Search- und Sortierfelder stammen aus den typisierten
Domain-Deskriptoren. Requestparameter wählen nur bereits whitelisted Felder;
sie werden nie direkt als Criteria-Property verwendet.

Mehrere inverse Tabellen auf derselben Show-Seite behalten unabhängigen
Zustand über den jeweiligen Relationship-Namen, beispielsweise:

```text
inverse.employees.q
inverse.employees.max
inverse.employees.offset
inverse.employees.sort
inverse.employees.order
```

## Konfiguration

Generierte Fähigkeiten können in `application.yml` beschriftet oder
eingeschränkt werden:

```yaml
ili2grails:
  ui:
    domains:
      - iliName: GsSimpleModel.Organization.Department
        relationships:
          employees:
            label: Mitarbeitende
            mode: read-only
```

Zulässige Modi sind `auto`, `editable`, `read-only` und `off`. Eine
Konfiguration kann eine generierte Fähigkeit reduzieren, aber keine vom
Generator als unsicher eingestufte Beziehung schreibbar machen.

Die globale CLI-Option `--grails-association-ui` folgt demselben Prinzip. Eine
anwendungseigene `InterlisAuthorizationPolicy` bleibt zusätzlich für jede
Schreiboperation verbindlich.

## Fehler- und Sicherheitsverhalten

- Registry- und Runtime-Descriptor-Validierung ist fail-closed.
- Unbekannte oder fremde Context-IDs werden nicht in Klassen-/Propertynamen
  übersetzt.
- Ownership-Manipulationen liefern keine fremden Association-Daten.
- Nur `LOCK_UNSUPPORTED` darf auf einen normalen Read zurückfallen;
  unerwartete Lockfehler werden als Concurrency-Fehler behandelt.
- Authorization-Denial, Kardinalitätsfehler und Reassignment-Bestätigung
  mutieren keine Domainobjekte.
- Datenintegritäts- und Optimistic-Locking-Fehler werden in stabile
  `CommandCode`-Resultate übersetzt.

## Erweiterungspunkte

Fachspezifische Operationen, Workflows oder zusätzliche Transaktionen gehören
in normale Grails-Services und GORM-Events. Der generische Association-Code ist
kein Workflow-Framework. Autorisierung wird über
`InterlisAuthorizationPolicy` erweitert; projektspezifische Controller können
die typisierten Runtime-Services verwenden oder vollständig eigene
Anwendungsfälle anbieten.

Die zugehörigen Architekturentscheidungen sind:

- [Plan-before-write generation](decisions/0002-plan-before-write-generation.md)
- [Runtime validation fail-closed](decisions/0003-runtime-validation-fail-closed.md)
