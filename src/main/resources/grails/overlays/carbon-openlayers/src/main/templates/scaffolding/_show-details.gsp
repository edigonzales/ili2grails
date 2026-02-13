<section class="bx--tile ili-details-tile">
    <h2 class="ili-section-title">Details</h2>
    <dl class="ili-definition-list">
        <g:each in="\${detailColumns ?: []}" var="detailColumn">
            <div class="ili-definition-row">
                <dt>\${message(code: '${propertyName}.' + detailColumn + '.label', default: detailColumn)}</dt>
                <dd>\${detailValues?.get(detailColumn) ?: '-'}</dd>
            </div>
        </g:each>
    </dl>
</section>
