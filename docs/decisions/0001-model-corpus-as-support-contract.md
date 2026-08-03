# Modellkorpus als Support-Vertrag

## Status
Accepted

## Kontext
Der Generator wurde bisher mit einzelnen, gezielt konstruierten Modellen und
Smoke-Tests belegt. Es gab keine einzige versionierte, maschinenlesbare
Quelle, die beantwortet: welche INTERLIS-Funktion durch welches Modell und
welchen Test belegt ist, und ob nur semantische Generierung oder auch reale
Persistenz geprüft wurde.

## Entscheidung
`verification/model-corpus.yaml` ist die einzige Wahrheit für unterstützte
Modell-Szenarien. Jedes Szenario referenziert ein reales Modell, eine
Feature-Menge und Erwartungen (Zählwerte, Blocking-Diagnostics,
Mapping-Vertrag, dokumentierte Abweichungen). Der Corpus wird statisch
validiert (`verifyModelCorpusManifest`, läuft in `verificationFast`) und
steuert die erweiterten Tests (Real-ili2db-Smoke, PostgreSQL-Contract). Die
Feature-Matrix wird aus dem Corpus generiert; `SUPPORTED` erfordert einen
realen Datenbank-/Mapping-Vertrag.

## Konsequenzen
- Neue INTERLIS-Konstrukte erfordern einen Corpus-Eintrag, bevor sie als
  unterstützt gelten.
- Die Feature-Matrix ist versioniert und maschinell prüfbar.
- Szenarien mit externen Modell-Repositories (VSADSSMINI) dürfen als
  Infrastruktur-Skip markiert werden, sind aber nie der einzige Beweis.

## Verworfene Alternativen
- Eine Test-Datenbank oder ein Remote-Evidence-Store (überdimensioniert).
- Eine generische Regel-DSL (kein Bedarf).
