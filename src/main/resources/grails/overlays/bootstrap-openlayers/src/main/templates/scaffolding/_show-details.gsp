<section class="card ili-details-tile">
    <div class="card-body">
        <h2 class="ili-section-title h5">Details</h2>
        <dl class="ili-definition-list">
            <g:each in="\${detailColumns ?: []}" var="detailColumn">
                <div class="ili-definition-row">
                    <dt>\${message(code: '${propertyName}.' + detailColumn + '.label', default: detailColumn)}</dt>
                    <dd>\${detailValues?.get(detailColumn) ?: '-'}</dd>
                </div>
            </g:each>
        </dl>
    </div>
</section>
