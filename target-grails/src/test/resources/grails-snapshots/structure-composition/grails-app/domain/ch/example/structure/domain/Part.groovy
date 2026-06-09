package ch.example.structure.domain

class Part {

    String label
    Owner ownerRef

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        label: [label: 'Label', qualifiedName: 'StructureCompositionCases.Cases.Part.Label'],
        ownerRef: [label: 'OwnerRef', qualifiedName: 'StructureCompositionCases.Cases.Part.OwnerRef']
    ]

    static mapping = {
        version false
    }

    static constraints = {
        label maxSize: 50
        ownerRef nullable: true
    }
}
