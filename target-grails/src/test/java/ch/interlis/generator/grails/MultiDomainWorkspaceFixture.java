package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the deliberately explicit Phase-5 reference workspace into a temporary Grails app.
 * This is test infrastructure, not a generator feature or a domain-process DSL.
 */
public final class MultiDomainWorkspaceFixture {

    public static final String MODEL_NAME = "MultiDomainWorkspaceE2E";
    public static final String PARCEL_ILI_NAME = MODEL_NAME + ".Cases.Parcel";
    public static final String BUILDING_ILI_NAME = MODEL_NAME + ".Cases.Building";
    public static final String OWNER_ILI_NAME = MODEL_NAME + ".Cases.Owner";

    private MultiDomainWorkspaceFixture() {
    }

    public static void install(Path appDir) throws IOException {
        write(appDir, "grails-app/controllers/com/example/ParcelWorkspaceController.groovy", """
            package com.example

            class ParcelWorkspaceController {

                static allowedMethods = [index: 'GET', show: 'GET', edit: 'GET', update: 'POST']

                def parcelWorkspaceService
                def parcelWorkspaceCommandService

                def index() {
                    render view: 'index', model: parcelWorkspaceService.indexModel()
                }

                def show(Long id) {
                    Map<String, Object> model = parcelWorkspaceService.showModel(id)
                    if (!model.workspaceRoot) {
                        render status: 404, text: 'Parzelle nicht gefunden'
                        return
                    }
                    render view: 'show', model: model
                }

                def edit(Long id) {
                    Map<String, Object> model = parcelWorkspaceService.editModel(id)
                    if (!model.workspaceRoot) {
                        render status: 404, text: 'Parzelle nicht gefunden'
                        return
                    }
                    render view: 'edit', model: model
                }

                def update(Long id, ParcelWorkspaceCommand command) {
                    if (!command || !command.validate()) {
                        return renderEdit(id, command ?: new ParcelWorkspaceCommand(),
                            commandValidationErrors(command), 422)
                    }

                    try {
                        parcelWorkspaceCommandService.updateWorkspace(id, command)
                        flash.message = 'Workspace erfolgreich gespeichert.'
                        redirect action: 'show', id: id
                    } catch (ParcelWorkspaceCommandException failure) {
                        return renderEdit(id, command, failure.fieldErrors, 422)
                    }
                }

                private Map<String, String> commandValidationErrors(ParcelWorkspaceCommand command) {
                    Map<String, String> errors = [:]
                    command?.errors?.allErrors?.each { error ->
                        String field = error.hasProperty('field') ? error.field : 'workspace'
                        errors[field] = error.defaultMessage ?: 'Bitte prüfen Sie diesen Wert.'
                    }
                    errors
                }

                private Object renderEdit(Long id, ParcelWorkspaceCommand command,
                                          Map<String, String> errors, int status) {
                    Map<String, Object> model = parcelWorkspaceService.editModel(id, command, errors)
                    if (!model.workspaceRoot) {
                        render status: 404, text: 'Parzelle nicht gefunden'
                        return null
                    }
                    render view: 'edit', model: model, status: status
                }
            }
            """);
        write(appDir, "grails-app/controllers/com/example/BuildingEditCommand.groovy", """
            package com.example

            import grails.validation.Validateable

            class BuildingEditCommand implements Validateable {

                Long id
                Long version
                String name

                static constraints = {
                    id nullable: false, min: 1L
                    version nullable: false, min: 0L
                    name nullable: false, blank: false, maxSize: 80
                }
            }
            """);
        write(appDir, "grails-app/controllers/com/example/OwnerEditCommand.groovy", """
            package com.example

            import grails.validation.Validateable

            class OwnerEditCommand implements Validateable {

                Long id
                Long version
                String name

                static constraints = {
                    id nullable: false, min: 1L
                    version nullable: false, min: 0L
                    name nullable: false, blank: false, maxSize: 80
                }
            }
            """);
        write(appDir, "grails-app/controllers/com/example/ParcelWorkspaceCommand.groovy", """
            package com.example

            import grails.validation.Validateable

            class ParcelWorkspaceCommand implements Validateable {

                Long parcelId
                Long parcelVersion
                String parcelNumber
                List<BuildingEditCommand> buildingEdits = []
                List<OwnerEditCommand> ownerEdits = []
                List<Long> removedBuildingIds = []
                List<Long> removedOwnerIds = []

                static constraints = {
                    parcelId nullable: false, min: 1L
                    parcelVersion nullable: false, min: 0L
                    parcelNumber nullable: false, blank: false, maxSize: 50
                }
            }
            """);
        write(appDir, "grails-app/services/com/example/ParcelWorkspaceCommandException.groovy", """
            package com.example

            class ParcelWorkspaceCommandException extends RuntimeException {

                final String code
                final Map<String, String> fieldErrors

                ParcelWorkspaceCommandException(String code, Map<String, String> fieldErrors) {
                    super(code)
                    this.code = code
                    this.fieldErrors = fieldErrors ?: [:]
                }
            }
            """);
        write(appDir, "grails-app/services/com/example/ParcelWorkspaceCommandService.groovy", """
            package com.example

            import com.example.domain.Building
            import com.example.domain.Owner
            import com.example.domain.Parcel
            import grails.gorm.transactions.Transactional
            import org.springframework.dao.DataIntegrityViolationException
            import org.springframework.dao.OptimisticLockingFailureException

            @Transactional
            class ParcelWorkspaceCommandService {

                void updateWorkspace(Long routeParcelId, ParcelWorkspaceCommand command) {
                    Map<String, String> errors = [:]
                    if (!routeParcelId || routeParcelId < 1L) {
                        errors['parcelId'] = 'Die Route-ID muss positiv sein.'
                    }
                    if (!command) {
                        throw failure('invalid-command', [workspace: 'Es wurden keine Workspace-Daten übertragen.'])
                    }
                    if (routeParcelId != command.parcelId) {
                        errors['parcelId'] = 'Die Route-ID und die übertragene Parzellen-ID stimmen nicht überein.'
                    }
                    if (errors) {
                        throw failure('invalid-command', errors)
                    }

                    Parcel parcel = Parcel.get(command.parcelId)
                    if (!parcel) {
                        throw failure('missing-root', [parcelId: 'Die Parzelle wurde nicht gefunden.'])
                    }
                    checkVersion(parcel, command.parcelVersion, 'parcelVersion', errors)

                    List<BuildingEditCommand> buildingEdits = typedBuildingEdits(command.buildingEdits, errors)
                    List<OwnerEditCommand> ownerEdits = typedOwnerEdits(command.ownerEdits, errors)
                    List<Long> removedBuildingIds = positiveUniqueIds(command.removedBuildingIds,
                        'removedBuildingIds', errors)
                    List<Long> removedOwnerIds = positiveUniqueIds(command.removedOwnerIds,
                        'removedOwnerIds', errors)

                    validateDuplicateEdits(buildingEdits, 'buildingEdits', errors)
                    validateDuplicateEdits(ownerEdits, 'ownerEdits', errors)
                    validateEditRemoveOverlap(buildingEdits, removedBuildingIds, 'buildingEdits',
                        'removedBuildingIds', errors)
                    validateEditRemoveOverlap(ownerEdits, removedOwnerIds, 'ownerEdits',
                        'removedOwnerIds', errors)

                    Map<Long, Building> buildings = loadBuildings(parcel, buildingEdits, removedBuildingIds, errors)
                    Map<Long, Owner> owners = loadOwners(parcel, ownerEdits, removedOwnerIds, errors)
                    buildingEdits.eachWithIndex { BuildingEditCommand edit, int index ->
                        Building building = buildings[edit.id]
                        if (building) {
                            checkVersion(building, edit.version, "buildingEdits[${index}].version", errors)
                        }
                    }
                    ownerEdits.eachWithIndex { OwnerEditCommand edit, int index ->
                        Owner owner = owners[edit.id]
                        if (owner) {
                            checkVersion(owner, edit.version, "ownerEdits[${index}].version", errors)
                        }
                    }
                    validateNestedCommands(buildingEdits, 'buildingEdits', errors)
                    validateNestedCommands(ownerEdits, 'ownerEdits', errors)
                    if (errors) {
                        String code = errors.keySet().any { String field ->
                            field == 'parcelVersion' || field.endsWith('.version')
                        } ? 'optimistic-locking' : 'precondition-failed'
                        throw failure(code, errors)
                    }

                    parcel.anumber = command.parcelNumber
                    buildingEdits.each { BuildingEditCommand edit -> buildings[edit.id].aname = edit.name }
                    ownerEdits.each { OwnerEditCommand edit -> owners[edit.id].aname = edit.name }

                    validateDomain(parcel, 'parcel', errors)
                    buildingEdits.eachWithIndex { BuildingEditCommand edit, int index ->
                        validateDomain(buildings[edit.id], "buildingEdits[${index}]", errors)
                    }
                    ownerEdits.eachWithIndex { OwnerEditCommand edit, int index ->
                        validateDomain(owners[edit.id], "ownerEdits[${index}]", errors)
                    }
                    if (errors) {
                        throw failure('validation-failed', errors)
                    }

                    try {
                        parcel.save(failOnError: true, flush: true)
                        buildingEdits.each { BuildingEditCommand edit ->
                            buildings[edit.id].save(failOnError: true, flush: true)
                        }
                        ownerEdits.each { OwnerEditCommand edit ->
                            owners[edit.id].save(failOnError: true, flush: true)
                        }
                        removedBuildingIds.each { Long buildingId -> buildings[buildingId].delete(flush: true) }
                        removedOwnerIds.each { Long ownerId -> owners[ownerId].delete(flush: true) }
                    } catch (OptimisticLockingFailureException conflict) {
                        throw failure('optimistic-locking', [workspace: 'Die Daten wurden zwischenzeitlich geändert. Bitte laden Sie den Workspace neu.'])
                    } catch (DataIntegrityViolationException integrity) {
                        throw failure('integrity', [workspace: 'Die Änderungen verletzen eine fachliche oder relationale Integritätsregel.'])
                    }
                }

                private List<BuildingEditCommand> typedBuildingEdits(List edits, Map<String, String> errors) {
                    if (edits == null) return []
                    edits.eachWithIndex { Object edit, int index ->
                        if (!(edit instanceof BuildingEditCommand)) {
                            errors["buildingEdits[${index}]"] = 'Ungültiger Gebäudedatensatz.'
                        }
                    }
                    edits.findAll { it instanceof BuildingEditCommand }
                }

                private List<OwnerEditCommand> typedOwnerEdits(List edits, Map<String, String> errors) {
                    if (edits == null) return []
                    edits.eachWithIndex { Object edit, int index ->
                        if (!(edit instanceof OwnerEditCommand)) {
                            errors["ownerEdits[${index}]"] = 'Ungültiger Eigentümerdatensatz.'
                        }
                    }
                    edits.findAll { it instanceof OwnerEditCommand }
                }

                private List<Long> positiveUniqueIds(List ids, String field, Map<String, String> errors) {
                    List<Long> result = (ids ?: []).collect { Object value ->
                        try {
                            value as Long
                        } catch (Exception ignored) {
                            null
                        }
                    }
                    if (result.any { !it || it < 1L }) {
                        errors[field] = 'IDs müssen positive Zahlen sein.'
                    }
                    if (result.size() != result.toSet().size()) {
                        errors[field] = 'IDs dürfen nicht doppelt übertragen werden.'
                    }
                    result.findAll { it }
                }

                private void validateDuplicateEdits(List edits, String field, Map<String, String> errors) {
                    List ids = edits.collect { it.id }
                    if (ids.any { !it || it < 1L }) {
                        errors[field] = 'Jeder Datensatz benötigt eine positive ID.'
                    }
                    if (ids.size() != ids.toSet().size()) {
                        errors[field] = 'IDs dürfen nicht doppelt übertragen werden.'
                    }
                }

                private void validateEditRemoveOverlap(List edits, List<Long> removed, String editField,
                                                       String removeField, Map<String, String> errors) {
                    if (edits.collect { it.id }.intersect(removed)) {
                        errors[editField] = 'Ein Datensatz darf nicht gleichzeitig geändert und entfernt werden.'
                        errors[removeField] = 'Ein Datensatz darf nicht gleichzeitig geändert und entfernt werden.'
                    }
                }

                private Map<Long, Building> loadBuildings(Parcel parcel, List<BuildingEditCommand> edits,
                                                           List<Long> removed, Map<String, String> errors) {
                    Map<Long, Building> result = [:]
                    (edits.collect { it.id } + removed).unique().each { Long id ->
                        Building building = Building.get(id)
                        if (!building) {
                            errors['buildingEdits'] = 'Ein Gebäude wurde nicht gefunden.'
                        } else if (building.parcel?.id != parcel.id) {
                            errors['buildingEdits'] = 'Das Gebäude gehört nicht zu dieser Parzelle.'
                            errors['removedBuildingIds'] = 'Fremde Gebäude dürfen nicht entfernt werden.'
                        } else {
                            result[id] = building
                        }
                    }
                    result
                }

                private Map<Long, Owner> loadOwners(Parcel parcel, List<OwnerEditCommand> edits,
                                                     List<Long> removed, Map<String, String> errors) {
                    Map<Long, Owner> result = [:]
                    (edits.collect { it.id } + removed).unique().each { Long id ->
                        Owner owner = Owner.get(id)
                        if (!owner) {
                            errors['ownerEdits'] = 'Ein Eigentümer wurde nicht gefunden.'
                        } else if (owner.parcel?.id != parcel.id) {
                            errors['ownerEdits'] = 'Der Eigentümer gehört nicht zu dieser Parzelle.'
                            errors['removedOwnerIds'] = 'Fremde Eigentümer dürfen nicht entfernt werden.'
                        } else {
                            result[id] = owner
                        }
                    }
                    result
                }

                private void validateNestedCommands(List commands, String prefix, Map<String, String> errors) {
                    commands.eachWithIndex { command, int index ->
                        if (!command.validate()) {
                            command.errors.fieldErrors.each { fieldError ->
                                errors["${prefix}[${index}].${fieldError.field}"] =
                                    fieldError.defaultMessage ?: 'Bitte prüfen Sie diesen Wert.'
                            }
                        }
                    }
                }

                private void validateDomain(Object domain, String prefix, Map<String, String> errors) {
                    if (!domain.validate()) {
                        domain.errors.fieldErrors.each { fieldError ->
                            errors["${prefix}.${fieldError.field}"] =
                                fieldError.defaultMessage ?: 'Bitte prüfen Sie diesen Wert.'
                        }
                    }
                }

                private void checkVersion(Object domain, Long submittedVersion, String field,
                                          Map<String, String> errors) {
                    Long currentVersion = domain.version == null ? null : domain.version as Long
                    if (submittedVersion == null || currentVersion == null || submittedVersion != currentVersion) {
                        errors[field] = 'Der Datensatz ist nicht mehr aktuell.'
                    }
                }

                private ParcelWorkspaceCommandException failure(String code, Map<String, String> errors) {
                    new ParcelWorkspaceCommandException(code, errors)
                }
            }
            """);
        write(appDir, "grails-app/services/com/example/ParcelWorkspaceService.groovy", """
            package com.example

            import com.example.domain.Building
            import com.example.domain.Owner
            import com.example.domain.Parcel
            import ch.interlis.generator.grails.runtime.InterlisUiDescriptorSupport
            import ch.interlis.generator.grails.runtime.InterlisWorkspaceSupport

            class ParcelWorkspaceService {

                static transactional = false
                private static final int MAX_ITEMS = 25
                def grailsApplication

                Map<String, Object> indexModel() {
                    List<Map<String, Object>> rows = Parcel.createCriteria().list {
                        maxResults(MAX_ITEMS)
                        order('anumber', 'asc')
                    }.collect { Parcel parcel ->
                        InterlisWorkspaceSupport.tableRow(
                            [number: parcel.anumber],
                            [number: [controller: 'parcelWorkspace', action: 'show', id: parcel.id.toString()]]
                        )
                    }
                    [
                        workspaceTableSections: [
                            InterlisWorkspaceSupport.tableSection(
                                'parcels', 'Parzellen',
                                [[key: 'number', label: 'Nummer']],
                                rows,
                                'Es sind noch keine Parzellen erfasst.'
                            )
                        ]
                    ]
                }

                Map<String, Object> showModel(Long id) {
                    Parcel parcel = Parcel.get(id)
                    if (!parcel) {
                        return [workspaceRoot: null]
                    }

                    Map<String, Object> descriptor =
                        InterlisUiDescriptorSupport.descriptor(grailsApplication, Parcel)
                    Map<String, Object> model = InterlisWorkspaceSupport.showModel(
                        grailsApplication, Parcel, parcel, descriptor
                    )
                    model.workspaceRoot = parcel
                    model.workspaceTableSections = [
                            relatedSection('buildings', 'Gebäude',
                            Building.createCriteria().list {
                                maxResults(MAX_ITEMS)
                                order('aname', 'asc')
                                eq('parcel', parcel)
                            }, 'building', 'Für diese Parzelle sind keine Gebäude erfasst.'),
                        relatedSection('owners', 'Eigentümer',
                            Owner.createCriteria().list {
                                maxResults(MAX_ITEMS)
                                order('aname', 'asc')
                                eq('parcel', parcel)
                            }, 'owner', 'Für diese Parzelle sind keine Eigentümer erfasst.')
                    ]
                    model
                }

                Map<String, Object> editModel(Long id, ParcelWorkspaceCommand command = null,
                                              Map<String, String> errors = [:]) {
                    Map<String, Object> model = showModel(id)
                    if (!model.workspaceRoot) {
                        return model
                    }

                    Parcel parcel = model.workspaceRoot as Parcel
                    List<Building> buildings = Building.createCriteria().list {
                        order('aname', 'asc')
                        eq('parcel', parcel)
                    }
                    List<Owner> owners = Owner.createCriteria().list {
                        order('aname', 'asc')
                        eq('parcel', parcel)
                    }
                    model.parcelNumberValue = command == null ? parcel.anumber : command.parcelNumber
                    model.buildingEditValues = command == null ?
                        buildings.collect { [id: it.id, version: it.version, name: it.aname] } :
                        (command.buildingEdits ?: []).collect { [id: it.id, version: it.version, name: it.name] }
                    model.ownerEditValues = command == null ?
                        owners.collect { [id: it.id, version: it.version, name: it.aname] } :
                        (command.ownerEdits ?: []).collect { [id: it.id, version: it.version, name: it.name] }
                    model.removedBuildingIds = command?.removedBuildingIds ?: []
                    model.removedOwnerIds = command?.removedOwnerIds ?: []
                    model.validationErrors = errors ?: [:]
                    model
                }

                private Map<String, Object> relatedSection(String key, String title, List records,
                                                            String controller, String emptyMessage) {
                    List<Map<String, Object>> rows = records.collect { Object record ->
                        InterlisWorkspaceSupport.tableRow(
                            [name: record.aname],
                            [name: [controller: controller, action: 'show', id: record.id.toString()]]
                        )
                    }
                    InterlisWorkspaceSupport.tableSection(
                        key, title, [[key: 'name', label: 'Name']],
                        rows, emptyMessage
                    )
                }
            }
            """);
        write(appDir, "grails-app/views/parcelWorkspace/index.gsp", """
            <!DOCTYPE html>
            <html lang="de-CH">
            <head>
                <meta name="layout" content="main"/>
                <title>Parzellen-Workspace</title>
            </head>
            <body>
            <div id="content" role="main" class="ili-page" data-parcel-workspace>
                <section class="ili-page-header">
                    <div>
                        <p class="ili-eyebrow">Fachliche Arbeitsseite</p>
                        <h1 class="ili-page-title">Parzellen-Workspace</h1>
                        <p class="ili-page-subtitle">Parzellen auswählen und zugehörige Fachdaten prüfen.</p>
                    </div>
                </section>
                <g:each in="${workspaceTableSections ?: []}" var="section">
                    <g:render template="/interlisUi/workspace-table" model="${[section: section]}"/>
                </g:each>
            </div>
            </body>
            </html>
            """);
        write(appDir, "grails-app/views/parcelWorkspace/show.gsp", """
            <!DOCTYPE html>
            <html lang="de-CH">
            <head>
                <meta name="layout" content="main"/>
                <title>${workspaceDisplayLabel ?: 'Parzellen-Workspace'}</title>
            </head>
            <body>
            <div id="content" role="main" class="ili-page" data-multi-domain-workspace>
                <g:render template="/interlisUi/workspace-header"
                          model="${[instance: workspaceRoot, displayLabel: workspaceDisplayLabel,
                                    domainLabel: workspaceDomainLabel, controllerName: 'parcel']}"/>
                <g:render template="/interlisUi/workspace-details"
                          model="${[detailSections: workspaceDetailSections,
                                    domainPropertyName: 'parcel']}"/>
                <div class="d-flex justify-content-end mb-3">
                    <g:link class="btn btn-primary" action="edit" id="${workspaceRoot.id}"
                            data-workspace-edit-link="true">Workspace bearbeiten</g:link>
                </div>
                <div class="row g-3 mt-1" data-workspace-related-sections>
                    <g:each in="${workspaceTableSections ?: []}" var="section">
                        <div class="col-12 col-xl-6">
                            <g:render template="/interlisUi/workspace-table" model="${[section: section]}"/>
                        </div>
                    </g:each>
                </div>
            </div>
            </body>
            </html>
            """);
        write(appDir, "grails-app/views/parcelWorkspace/edit.gsp", """
            <!DOCTYPE html>
            <html lang="de-CH">
            <head>
                <meta name="layout" content="main"/>
                <title>Workspace bearbeiten</title>
            </head>
            <body>
            <div id="content" role="main" class="ili-page" data-multi-domain-workspace-edit>
                <section class="ili-page-header">
                    <div>
                        <p class="ili-eyebrow">Fachliche Arbeitsseite</p>
                        <h1 class="ili-page-title">Parzellen-Workspace bearbeiten</h1>
                        <p class="ili-page-subtitle">Parzelle, Gebäude und Eigentümer werden gemeinsam gespeichert.</p>
                    </div>
                    <span class="ili-unsaved-badge" data-unsaved-badge hidden>Ungespeicherte Änderungen</span>
                </section>

                <g:form controller="parcelWorkspace" action="update" id="${workspaceRoot.id}"
                        method="POST" class="ili-form js-dirty-form" data-workspace-edit="true">
                    <g:hiddenField name="parcelId" value="${workspaceRoot.id}"/>
                    <g:hiddenField name="parcelVersion" value="${workspaceRoot.version}"/>

                    <g:if test="${validationErrors}">
                        <div class="alert alert-danger" role="alert" data-validation-summary>
                            <p class="mb-1 fw-semibold">Die Änderungen konnten nicht gespeichert werden.</p>
                            <ul class="mb-0">
                                <g:each in="${validationErrors.entrySet()}" var="entry">
                                    <li>${entry.value}</li>
                                </g:each>
                            </ul>
                        </div>
                    </g:if>

                    <section class="ili-section mb-4" data-workspace-section="parcel">
                        <h2>Parzelle</h2>
                        <div class="mb-3">
                            <label class="form-label" for="parcelNumber">Nummer</label>
                            <g:textField name="parcelNumber" id="parcelNumber" class="form-control ${validationErrors?.parcelNumber ? 'is-invalid' : ''}"
                                         value="${parcelNumberValue}" maxlength="50" required="true"/>
                            <g:if test="${validationErrors?.parcelNumber}"><div class="invalid-feedback" data-field-error="parcelNumber">${validationErrors.parcelNumber}</div></g:if>
                        </div>
                    </section>

                    <section class="ili-section mb-4" data-workspace-section="buildings">
                        <h2>Gebäude</h2>
                        <g:if test="${buildingEditValues}">
                            <g:each in="${buildingEditValues}" var="building" status="i">
                                <div class="row g-2 align-items-end mb-3" data-workspace-row="building">
                                    <g:hiddenField name="buildingEdits[${i}].id" value="${building.id}"/>
                                    <g:hiddenField name="buildingEdits[${i}].version" value="${building.version}"/>
                                    <div class="col-md-8">
                                        <label class="form-label" for="buildingEdits-${i}-name">Name</label>
                                        <g:textField name="buildingEdits[${i}].name" id="buildingEdits-${i}-name"
                                                     class="form-control ${validationErrors?.get('buildingEdits[' + i + '].name') ? 'is-invalid' : ''}"
                                                     value="${building.name}" maxlength="80" required="true"/>
                                        <g:if test="${validationErrors?.get('buildingEdits[' + i + '].name')}"><div class="invalid-feedback" data-field-error="buildingEdits-${i}-name">${validationErrors.get('buildingEdits[' + i + '].name')}</div></g:if>
                                    </div>
                                    <div class="col-md-4 form-check">
                                        <input class="form-check-input" type="checkbox" name="removedBuildingIds"
                                               value="${building.id}" id="remove-building-${i}"
                                               ${removedBuildingIds?.contains(building.id) ? 'checked' : ''}/>
                                        <label class="form-check-label" for="remove-building-${i}">Gebäude entfernen</label>
                                    </div>
                                </div>
                            </g:each>
                        </g:if>
                        <g:else><p class="text-muted">Für diese Parzelle sind keine Gebäude erfasst.</p></g:else>
                    </section>

                    <section class="ili-section mb-4" data-workspace-section="owners">
                        <h2>Eigentümer</h2>
                        <g:if test="${ownerEditValues}">
                            <g:each in="${ownerEditValues}" var="owner" status="i">
                                <div class="row g-2 align-items-end mb-3" data-workspace-row="owner">
                                    <g:hiddenField name="ownerEdits[${i}].id" value="${owner.id}"/>
                                    <g:hiddenField name="ownerEdits[${i}].version" value="${owner.version}"/>
                                    <div class="col-md-8">
                                        <label class="form-label" for="ownerEdits-${i}-name">Name</label>
                                        <g:textField name="ownerEdits[${i}].name" id="ownerEdits-${i}-name"
                                                     class="form-control ${validationErrors?.get('ownerEdits[' + i + '].name') ? 'is-invalid' : ''}"
                                                     value="${owner.name}" maxlength="80" required="true"/>
                                        <g:if test="${validationErrors?.get('ownerEdits[' + i + '].name')}"><div class="invalid-feedback" data-field-error="ownerEdits-${i}-name">${validationErrors.get('ownerEdits[' + i + '].name')}</div></g:if>
                                    </div>
                                    <div class="col-md-4 form-check">
                                        <input class="form-check-input" type="checkbox" name="removedOwnerIds"
                                               value="${owner.id}" id="remove-owner-${i}"
                                               ${removedOwnerIds?.contains(owner.id) ? 'checked' : ''}/>
                                        <label class="form-check-label" for="remove-owner-${i}">Eigentümer entfernen</label>
                                    </div>
                                </div>
                            </g:each>
                        </g:if>
                        <g:else><p class="text-muted">Für diese Parzelle sind keine Eigentümer erfasst.</p></g:else>
                    </section>

                    <div class="ili-form-actions" data-sticky-form-actions>
                        <g:link class="btn btn-outline-secondary" action="show" id="${workspaceRoot.id}">Abbrechen</g:link>
                        <button type="submit" class="btn btn-primary" data-workspace-save>Alles speichern</button>
                    </div>
                </g:form>
            </div>
            </body>
            </html>
            """);
        installGormVersionSupport(appDir);
        appendWorkspaceConfiguration(appDir.resolve("grails-app/conf/application.yml"));
    }

    /** Adds only the technical GORM lock token to this reference fixture's generated domains. */
    public static void installGormVersionSupport(Path appDir) throws IOException {
        for (String domain : new String[]{"Parcel", "Building", "Owner"}) {
            Path file = appDir.resolve("grails-app/domain/com/example/domain/" + domain + ".groovy");
            if (!Files.exists(file)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains("Long version")) {
                continue;
            }
            if (!source.contains("version false")) {
                throw new IOException("Expected generated version mapping in " + file);
            }
            source = source.replace("        version false\n", "");
            String classMarker = "class " + domain + " {\n";
            int classEnd = source.indexOf(classMarker);
            if (classEnd < 0) {
                throw new IOException("Expected generated class declaration in " + file);
            }
            int insertAt = classEnd + classMarker.length();
            source = source.substring(0, insertAt)
                + "\n    Long version\n"
                + source.substring(insertAt);
            Files.writeString(file, source, StandardCharsets.UTF_8);
        }
    }

    public static ModelMetadata referenceMetadata() {
        ModelMetadata metadata = new ModelMetadata(MODEL_NAME);

        ClassMetadata parcel = new ClassMetadata(PARCEL_ILI_NAME);
        parcel.setTableName("workspace_parcel");
        parcel.addAttribute(textAttribute("anumber", 50));

        ClassMetadata building = new ClassMetadata(BUILDING_ILI_NAME);
        building.setTableName("workspace_building");
        building.addAttribute(textAttribute("aname", 80));
        building.addAttribute(referenceAttribute("parcel", PARCEL_ILI_NAME));

        ClassMetadata owner = new ClassMetadata(OWNER_ILI_NAME);
        owner.setTableName("workspace_owner");
        owner.addAttribute(textAttribute("aname", 80));
        owner.addAttribute(referenceAttribute("parcel", PARCEL_ILI_NAME));

        metadata.addClass(parcel);
        metadata.addClass(building);
        metadata.addClass(owner);
        metadata.addRelationship(reference("Building_Parcel", BUILDING_ILI_NAME, PARCEL_ILI_NAME));
        metadata.addRelationship(reference("Owner_Parcel", OWNER_ILI_NAME, PARCEL_ILI_NAME));
        return metadata;
    }

    private static AttributeMetadata textAttribute(String name, int maxLength) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setJavaType("String");
        attribute.setColumnName(name);
        attribute.setMaxLength(maxLength);
        attribute.setMandatory(true);
        return attribute;
    }

    private static AttributeMetadata referenceAttribute(String name, String targetClass) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setJavaType("Long");
        attribute.setColumnName(name + "_id");
        attribute.setForeignKey(true);
        attribute.setReferencedClass(targetClass);
        attribute.setMandatory(false);
        return attribute;
    }

    private static RelationshipMetadata reference(String name, String sourceClass, String targetClass) {
        RelationshipMetadata relationship = new RelationshipMetadata(name);
        relationship.setSourceClass(sourceClass);
        relationship.setTargetClass(targetClass);
        relationship.setSourceAttribute("parcel");
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
        return relationship;
    }

    private static void write(Path appDir, String relativePath, String content) throws IOException {
        Path target = appDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static void appendWorkspaceConfiguration(Path applicationYaml) throws IOException {
        String existing = Files.readString(applicationYaml, StandardCharsets.UTF_8);
        String uiConfiguration = String.join("\n",
            "  ui:",
            "    workspaces:",
            "        - id: parcel-workspace",
            "          label: Parzellen-Workspace",
            "          controller: parcelWorkspace",
            "          action: index",
            "");
        int languageRoot = existing.indexOf("ili2grails:\n");
        if (languageRoot >= 0) {
            int insertionPoint = languageRoot + "ili2grails:\n".length();
            existing = existing.substring(0, insertionPoint)
                + uiConfiguration
                + existing.substring(insertionPoint);
        } else {
            existing = existing + "\nili2grails:\n" + uiConfiguration;
        }
        Files.writeString(applicationYaml, existing, StandardCharsets.UTF_8);
    }
}
