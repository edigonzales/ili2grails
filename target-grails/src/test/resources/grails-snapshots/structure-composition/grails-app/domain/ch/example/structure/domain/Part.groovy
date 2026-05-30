package ch.example.structure.domain

class Part {

    String label
    Owner ownerRef

    static mapping = {
        version false
    }

    static constraints = {
        label maxSize: 50
        ownerRef nullable: true
    }
}
