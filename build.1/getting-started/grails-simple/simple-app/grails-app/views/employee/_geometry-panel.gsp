<g:if test="${geometryFields}">
    <section class="card ili-map-panel">
        <div class="card-body">
            <header class="ili-map-panel-head">
                <h2 class="ili-section-title h5 mb-0"><g:message code="ili2grails.geometry.title" default="Geometrie"/></h2>
                <g:if test="${geometryFields?.size() == 1}">
                    <span class="badge text-bg-secondary">${geometryKinds?.get(geometryFields[0]) ?: 'GEOMETRY'}</span>
                </g:if>
            </header>

            <g:if test="${geometryFields?.size() > 1}">
                <ul class="nav nav-tabs js-geometry-tabs" role="tablist">
                    <g:each in="${geometryFields}" var="geomField" status="geomIndex">
                        <li class="nav-item" role="presentation">
                            <button type="button"
                                    id="geometry-tab-${geomField}"
                                    class="nav-link js-geometry-tab-btn ${geomIndex == 0 ? 'active' : ''}"
                                    data-geometry-tab-target="${geomField}"
                                    role="tab"
                                    aria-selected="${geomIndex == 0 ? 'true' : 'false'}"
                                    aria-controls="geometry-panel-${geomField}">
                                ${geomField}
                            </button>
                        </li>
                    </g:each>
                </ul>
            </g:if>

            <div class="ili-geometry-panels">
                <g:each in="${geometryFields}" var="geomField" status="geomIndex">
                    <article class="ili-geometry-tab-panel js-geometry-tab-panel ${geomIndex == 0 ? 'is-active' : ''}"
                             id="geometry-panel-${geomField}"
                             data-geometry-panel="${geomField}"
                             role="tabpanel"
                             aria-labelledby="geometry-tab-${geomField}"
                             tabindex="0"
                             ${geomIndex == 0 ? '' : 'hidden'}>
                        <header class="ili-geometry-header">
                            <strong>${geomField}</strong>
                            <span class="badge text-bg-secondary">${geometryKinds?.get(geomField) ?: 'GEOMETRY'}</span>
                        </header>

                        <div class="ili-geometry-editor"
                             data-geometry-field="${geomField}"
                             data-geometry-kind="${geometryKinds?.get(geomField) ?: 'GEOMETRY'}"
                             data-geometry-srid="${geometrySrids?.get(geomField) ?: ''}"
                             data-geometry-mode="${geometryMode ?: 'edit'}">
                            <g:if test="${(geometryMode ?: 'edit') == 'view'}">
                                <input type="hidden" class="js-geometry-wkt" value="${geometryValues?.get(geomField) ?: ''}"/>
                            </g:if>
                            <g:else>
                                <input type="hidden" name="${geomField}Wkt" value="${geometryValues?.get(geomField) ?: ''}" class="js-geometry-wkt"/>
                            </g:else>

                            <g:if test="${(geometryMode ?: 'edit') != 'view'}">
                                <div class="ili-geometry-toolbar">
                                    <div class="ili-geometry-type-picker js-geometry-type-picker">
                                        <label for="geom-type-${geomField}" class="ili-geometry-type-label form-label"><g:message code="ili2grails.geometry.type" default="Typ"/></label>
                                        <select id="geom-type-${geomField}" class="form-select form-select-sm js-geometry-draw-type">
                                            <option value="Point"><g:message code="ili2grails.geometry.point" default="Punkt"/></option>
                                            <option value="LineString"><g:message code="ili2grails.geometry.line" default="Linie"/></option>
                                            <option value="Polygon"><g:message code="ili2grails.geometry.polygon" default="Polygon"/></option>
                                        </select>
                                    </div>
                                    <button type="button" class="btn btn-sm btn-outline-primary" data-geometry-action="draw"><g:message code="ili2grails.geometry.draw" default="Zeichnen"/></button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary" data-geometry-action="modify"><g:message code="ili2grails.geometry.modify" default="Ändern"/></button>
                                    <button type="button" class="btn btn-sm btn-outline-danger" data-geometry-action="clear"><g:message code="ili2grails.geometry.clear" default="Löschen"/></button>
                                </div>
                            </g:if>

                            <div class="ili-geometry-map" role="img"
                                 aria-label="${message(code: 'ili2grails.geometry.map', args: [geomField], default: 'Kartenansicht für ' + geomField)}"></div>
                        </div>
                    </article>
                </g:each>
            </div>
        </div>
    </section>
</g:if>
