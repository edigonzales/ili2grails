package ch.example.structure.domain

class AssetDocument {

    Asset assetRole
    Document documentRole

    static mapping = {
        version false
    }

    static constraints = {
        assetRole nullable: true
        documentRole nullable: true
    }
}
