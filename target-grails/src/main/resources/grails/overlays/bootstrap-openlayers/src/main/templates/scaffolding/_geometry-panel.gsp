<g:if test="\${geometryFields}">
    <section class="card ili-map-panel">
        <div class="card-body">
            <header class="ili-map-panel-head">
                <h2 class="ili-section-title h5 mb-0">Geometrie</h2>
                <g:if test="\${geometryFields?.size() == 1}">
                    <span class="badge text-bg-secondary">\${geometryKinds?.get(geometryFields[0]) ?: 'GEOMETRY'}</span>
                </g:if>
            </header>

            <g:if test="\${geometryFields?.size() > 1}">
                <ul class="nav nav-tabs js-geometry-tabs" role="tablist">
                    <g:each in="\${geometryFields}" var="geomField" status="geomIndex">
                        <li class="nav-item" role="presentation">
                            <button type="button"
                                    id="geometry-tab-\${geomField}"
                                    class="nav-link js-geometry-tab-btn \${geomIndex == 0 ? 'active' : ''}"
                                    data-geometry-tab-target="\${geomField}"
                                    role="tab"
                                    aria-selected="\${geomIndex == 0 ? 'true' : 'false'}"
                                    aria-controls="geometry-panel-\${geomField}">
                                \${geomField}
                            </button>
                        </li>
                    </g:each>
                </ul>
            </g:if>

            <div class="ili-geometry-panels">
                <g:each in="\${geometryFields}" var="geomField" status="geomIndex">
                    <article class="ili-geometry-tab-panel js-geometry-tab-panel \${geomIndex == 0 ? 'is-active' : ''}"
                             id="geometry-panel-\${geomField}"
                             data-geometry-panel="\${geomField}"
                             role="tabpanel"
                             aria-labelledby="geometry-tab-\${geomField}"
                             tabindex="0"
                             \${geomIndex == 0 ? '' : 'hidden'}>
                        <header class="ili-geometry-header">
                            <strong>\${geomField}</strong>
                            <span class="badge text-bg-secondary">\${geometryKinds?.get(geomField) ?: 'GEOMETRY'}</span>
                        </header>

                        <div class="ili-geometry-editor"
                             data-geometry-field="\${geomField}"
                             data-geometry-kind="\${geometryKinds?.get(geomField) ?: 'GEOMETRY'}"
                             data-geometry-srid="\${geometrySrids?.get(geomField) ?: ''}"
                             data-geometry-mode="\${geometryMode ?: 'edit'}">
                            <g:if test="\${(geometryMode ?: 'edit') == 'view'}">
                                <input type="hidden" class="js-geometry-wkt" value="\${geometryValues?.get(geomField) ?: ''}"/>
                            </g:if>
                            <g:else>
                                <input type="hidden" name="\${geomField}Wkt" value="\${geometryValues?.get(geomField) ?: ''}" class="js-geometry-wkt"/>
                            </g:else>

                            <g:if test="\${(geometryMode ?: 'edit') != 'view'}">
                                <div class="ili-geometry-toolbar">
                                    <div class="ili-geometry-type-picker js-geometry-type-picker">
                                        <label for="geom-type-\${geomField}" class="ili-geometry-type-label form-label">Typ</label>
                                        <select id="geom-type-\${geomField}" class="form-select form-select-sm js-geometry-draw-type">
                                            <option value="Point">Punkt</option>
                                            <option value="LineString">Linie</option>
                                            <option value="Polygon">Polygon</option>
                                        </select>
                                    </div>
                                    <button type="button" class="btn btn-sm btn-outline-primary" data-geometry-action="draw">Zeichnen</button>
                                    <button type="button" class="btn btn-sm btn-outline-secondary" data-geometry-action="modify">Ändern</button>
                                    <button type="button" class="btn btn-sm btn-outline-danger" data-geometry-action="clear">Löschen</button>
                                </div>
                            </g:if>

                            <div class="ili-geometry-map" role="img"
                                 aria-label="Kartenansicht für \${geomField}"></div>
                        </div>
                    </article>
                </g:each>
            </div>
        </div>
    </section>
</g:if>
