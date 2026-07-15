<g:each in="${groups ?: []}" var="modelGroup">
    <section class="ili-navigation-model ${singleModel ? 'ili-navigation-model-single' : ''}"
             data-ili-navigation-model="${modelGroup.name}">
        <h2 class="ili-navigation-model-title">${modelGroup.name}</h2>
        <g:each in="${modelGroup.topics ?: []}" var="topicGroup">
            <section class="ili-navigation-topic">
                <h3 class="ili-navigation-topic-title">${topicGroup.label}</h3>
                <ul class="ili-domain-list">
                    <g:each in="${topicGroup.domains ?: []}" var="domain">
                        <g:render template="/interlisUi/domain-link"
                                  model="${[
                                      domain: domain,
                                      showFavorite: showFavorite,
                                      itemClass: 'ili-domain-list-item'
                                  ]}"/>
                    </g:each>
                </ul>
            </section>
        </g:each>
    </section>
</g:each>
