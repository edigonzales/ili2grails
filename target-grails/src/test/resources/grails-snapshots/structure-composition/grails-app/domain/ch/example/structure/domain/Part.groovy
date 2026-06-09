package ch.example.structure.domain

class Part {

    String label
    Owner ownerRef

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        label: [label: 'Label', qualifiedName: 'StructureCompositionCases.Cases.Part.Label'],
        ownerRef: [label: 'OwnerRef', qualifiedName: 'StructureCompositionCases.Cases.Part.OwnerRef']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['label'],
        searchFields: ['label']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        ownerRef: [targetClass: 'Owner', semanticKind: 'REFERENCE_ATTRIBUTE', label: 'OwnerRef', sourceAttribute: 'OwnerRef', targetRole: 'OwnerRef', mandatory: false]
    ]

    static mapping = {
        version false
    }

    static constraints = {
        label maxSize: 50
        ownerRef nullable: true
    }
}
