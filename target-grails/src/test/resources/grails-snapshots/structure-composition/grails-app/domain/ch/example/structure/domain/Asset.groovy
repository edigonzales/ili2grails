package ch.example.structure.domain

class Asset {

    String name
    Inspection mainInspection
    Attachment optionalAttachment

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        mainInspection: [label: 'MainInspection', qualifiedName: 'StructureCompositionCases.Cases.Asset.MainInspection'],
        name: [label: 'Name', qualifiedName: 'StructureCompositionCases.Cases.Asset.Name'],
        optionalAttachment: [label: 'OptionalAttachment', qualifiedName: 'StructureCompositionCases.Cases.Asset.OptionalAttachment']
    ]

    static hasMany = [parts: Part]

    static mapping = {
        version false
    }

    static constraints = {
        name maxSize: 50
        optionalAttachment nullable: true
    }
}
