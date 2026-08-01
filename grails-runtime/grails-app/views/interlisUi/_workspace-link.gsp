<li class="${itemClass ?: 'ili-domain-list-item'}">
    <g:link controller="${workspace.controller}" action="${workspace.action}"
            class="ili-domain-link ili-workspace-link"
            data-ili-workspace-link="${workspace.id}">
        <span class="ili-domain-link-label">
            <ili:icon name="grid-3x3-gap" cssClass="me-1"/>
            <span>${workspace.label}</span>
        </span>
    </g:link>
</li>
