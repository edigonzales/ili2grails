<g:if test="\${associationContextState}">
    <div class="ili-context-summary card mb-3">
        <div class="card-body">
            <h5 class="card-title">Kontext</h5>
            <p class="card-text">
                \${associationContextState.ownerLabel ?: 'Kein Kontext'}
            </p>
        </div>
    </div>
</g:if>
