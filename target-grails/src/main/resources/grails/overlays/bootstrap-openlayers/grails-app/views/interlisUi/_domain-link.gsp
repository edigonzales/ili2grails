<g:set var="domainUrl" value="${createLink(controller: domain.controller, namespace: domain.namespace, action: 'index')}" />
<li class="${itemClass ?: 'ili-domain-list-item'}"
    data-ili-domain-entry="true"
    data-ili-domain-key="${domain.iliName ?: ''}"
    data-ili-domain-label="${domain.label ?: domain.className}"
    data-ili-domain-class="${domain.className ?: ''}"
    data-ili-domain-topic="${domain.topicName ?: ''}"
    data-ili-domain-model="${domain.modelName ?: ''}"
    data-ili-domain-ili-name="${domain.iliName ?: ''}">
    <a href="${domainUrl}"
       class="ili-domain-link ${domain.controller == params.controller ? 'is-active' : ''}"
       data-ili-domain-link="true"
       data-ili-domain-url="${domainUrl}"
       data-ili-domain-key="${domain.iliName ?: ''}">
        <ili:icon name="folder" cssClass="ili-domain-icon"/>
        <span class="ili-domain-link-label">${domain.label ?: domain.className}</span>
    </a>
    <g:if test="${showFavorite && domain.iliName}">
        <button type="button"
                class="ili-favorite-toggle"
                data-ili-favorite-toggle="true"
                data-ili-domain-key="${domain.iliName}"
                aria-label="Als Favorit markieren"
                title="Als Favorit markieren"
                aria-pressed="false">
            <ili:icon name="star"/>
        </button>
    </g:if>
</li>
