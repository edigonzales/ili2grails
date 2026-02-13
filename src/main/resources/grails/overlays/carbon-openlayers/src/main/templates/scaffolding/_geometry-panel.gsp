<g:if test="\${geometryFields}">
    <section class="bx--tile ili-map-panel">
        <header class="ili-map-panel-head">
            <h2 class="ili-section-title">Geometrie</h2>
            <g:if test="\${geometryFields?.size() == 1}">
                <bx-tag type="gray">\${geometryKinds?.get(geometryFields[0]) ?: 'GEOMETRY'}</bx-tag>
            </g:if>
        </header>

        <g:if test="\${geometryFields?.size() > 1}">
            <bx-tabs class="js-geometry-tabs" value="\${geometryFields[0]}">
                <g:each in="\${geometryFields}" var="geomField" status="geomIndex">
                    <bx-tab value="\${geomField}" \${geomIndex == 0 ? 'selected' : ''}>\${geomField}</bx-tab>
                </g:each>
            </bx-tabs>
        </g:if>

        <div class="ili-geometry-panels">
            <g:each in="\${geometryFields}" var="geomField" status="geomIndex">
                <article class="ili-geometry-tab-panel js-geometry-tab-panel \${geomIndex == 0 ? 'is-active' : ''}"
                         data-geometry-panel="\${geomField}"
                         \${geomIndex == 0 ? '' : 'hidden'}>
                    <header class="ili-geometry-header">
                        <strong>\${geomField}</strong>
                        <bx-tag type="cool-gray">\${geometryKinds?.get(geomField) ?: 'GEOMETRY'}</bx-tag>
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
                                    <label for="geom-type-\${geomField}" class="ili-geometry-type-label">Typ</label>
                                    <select id="geom-type-\${geomField}" class="bx--select-input js-geometry-draw-type">
                                        <option value="Point">Punkt</option>
                                        <option value="LineString">Linie</option>
                                        <option value="Polygon">Polygon</option>
                                    </select>
                                </div>
                                <bx-btn kind="secondary" data-geometry-action="draw">Zeichnen</bx-btn>
                                <bx-btn kind="tertiary" data-geometry-action="modify">Ändern</bx-btn>
                                <bx-btn kind="danger-ghost" data-geometry-action="clear">Löschen</bx-btn>
                            </div>
                        </g:if>

                        <div class="ili-geometry-map"></div>
                    </div>
                </article>
            </g:each>
        </div>
    </section>
</g:if>
