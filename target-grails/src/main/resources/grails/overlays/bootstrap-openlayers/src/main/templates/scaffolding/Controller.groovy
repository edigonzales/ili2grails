<%=packageName ? "package ${packageName}" : ''%>

import ch.interlis.generator.grails.runtime.InterlisCrudControllerSupport

class ${className}Controller extends InterlisCrudControllerSupport<${className}> {

    ${className}Service ${propertyName}Service

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", relationshipOptions: "GET"]

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

    @Override
    protected Class<${className}> domainType() {
        return ${className}
    }

    @Override
    protected Object crudService() {
        return ${propertyName}Service
    }
}
