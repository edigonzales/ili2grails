<g:set var="filterValues" value="\${listQuery?.filterValues?.get(filterField.name) ?: [:]}" />
<div class="ili-field-row \${compact ? 'ili-quick-filter' : ''}">
    <label class="form-label" for="filter-\${filterField.name}">
        <g:message code="\${labelPrefix + '.' + filterField.name + '.label'}" default="\${filterField.label ?: filterField.name}" />
    </label>
    <g:if test="\${filterField.type == 'enum'}">
        <select id="filter-\${filterField.name}" name="\${'filter.' + filterField.name}" class="form-select">
            <option value="">Alle</option>
            <g:each in="\${filterField.options ?: []}" var="option">
                <option value="\${option.value}" \${option.value == filterValues?.value ? 'selected' : ''}><g:message code="\${labelPrefix + '.' + filterField.name + '.' + option.value + '.label'}" default="\${option.label}" /></option>
            </g:each>
        </select>
    </g:if>
    <g:elseif test="\${filterField.type == 'boolean'}">
        <g:select id="filter-\${filterField.name}" name="\${'filter.' + filterField.name}"
                  from="\${[[id: '', label: 'Alle'], [id: 'true', label: 'Ja'], [id: 'false', label: 'Nein']]}"
                  optionKey="id" optionValue="label" value="\${filterValues?.value ?: ''}" class="form-select" />
    </g:elseif>
    <g:elseif test="\${filterField.type == 'relationship'}">
        <select id="filter-\${filterField.name}" name="\${'filter.' + filterField.name}"
                class="form-select" data-relationship-url="\${createLink(action: 'relationshipOptions')}"
                data-relationship-field="\${filterField.name}">
            <option value="">Alle</option>
            <g:each in="\${filterOptions?.get(filterField.name)?.results ?: []}" var="option">
                <option value="\${option.id}" \${option.id == filterValues?.value ? 'selected' : ''}>\${option.label}</option>
            </g:each>
        </select>
    </g:elseif>
    <g:elseif test="\${filterField.type == 'number'}">
        <div class="input-group">
            <input type="number" name="\${'filter.' + filterField.name + '.min'}" value="\${filterValues?.min ?: ''}"
                   class="form-control" placeholder="Von" aria-label="Von" />
            <input type="number" name="\${'filter.' + filterField.name + '.max'}" value="\${filterValues?.max ?: ''}"
                   class="form-control" placeholder="Bis" aria-label="Bis" />
        </div>
    </g:elseif>
    <g:elseif test="\${filterField.type == 'date'}">
        <div class="input-group">
            <input type="date" name="\${'filter.' + filterField.name + '.from'}" value="\${filterValues?.from ?: ''}"
                   class="form-control" aria-label="Von" />
            <input type="date" name="\${'filter.' + filterField.name + '.to'}" value="\${filterValues?.to ?: ''}"
                   class="form-control" aria-label="Bis" />
        </div>
    </g:elseif>
    <g:else>
        <input id="filter-\${filterField.name}" type="search" name="\${'filter.' + filterField.name}"
               value="\${filterValues?.value ?: ''}" class="form-control" autocomplete="off" />
    </g:else>
</div>
