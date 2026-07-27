<%=packageName ? "package ${packageName}" : ''%>

import ch.interlis.generator.grails.runtime.InterlisCrudControllerSupport
import ch.interlis.generator.grails.runtime.InterlisAssociationQueryService
import ch.interlis.generator.grails.runtime.InterlisAssociationCommandService
import ch.interlis.generator.grails.runtime.InterlisInverseRelationshipQueryService
import ch.interlis.generator.grails.runtime.InterlisInverseRelationshipCommandService

class ${className}Controller extends InterlisCrudControllerSupport<${className}> {

    ${className}Service ${propertyName}Service
    InterlisAssociationQueryService interlisAssociationQueryService
    InterlisAssociationCommandService interlisAssociationCommandService
    InterlisInverseRelationshipQueryService interlisInverseRelationshipQueryService
    InterlisInverseRelationshipCommandService interlisInverseRelationshipCommandService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", relationshipOptions: "GET",
                             relationshipCollectionPage: "GET", relationshipCollectionOptions: "GET",
                             relationshipAssign: "POST",
                             associationPage: "GET", associationOptions: "GET",
                             associationCreate: "POST", associationDelete: "DELETE"]

    def index(Integer max, Integer offset) {
        super.index(max, offset)
    }

    def show(Long id) {
        super.show(id)
    }

    def create() {
        super.create()
    }

    def save() {
        super.save()
    }

    def edit(Long id) {
        super.edit(id)
    }

    def update(Long id) {
        super.update(id)
    }

    def delete(Long id) {
        super.delete(id)
    }

    def relationshipOptions() {
        super.relationshipOptions()
    }

    def relationshipCollectionPage(Long id) {
        super.relationshipCollectionPage(id)
    }

    def relationshipCollectionOptions(Long id) {
        super.relationshipCollectionOptions(id)
    }

    def relationshipAssign(Long id) {
        super.relationshipAssign(id)
    }

    def associationPage(Long id) {
        super.associationPage(id)
    }

    def associationOptions(Long id) {
        super.associationOptions(id)
    }

    def associationCreate(Long id) {
        super.associationCreate(id)
    }

    def associationDelete(Long id) {
        super.associationDelete(id)
    }

    @Override
    protected Class<${className}> domainType() {
        return ${className}
    }

    @Override
    protected Object crudService() {
        return ${propertyName}Service
    }

    @Override
    protected Object associationQueryService() {
        return interlisAssociationQueryService
    }

    @Override
    protected Object associationCommandService() {
        return interlisAssociationCommandService
    }

    @Override
    protected Object inverseRelationshipQueryService() {
        return interlisInverseRelationshipQueryService
    }

    @Override
    protected Object inverseRelationshipCommandService() {
        return interlisInverseRelationshipCommandService
    }
}
