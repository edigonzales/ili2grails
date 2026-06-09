<%=packageName ? "package ${packageName}" : ''%>

import ch.interlis.generator.grails.runtime.InterlisCrudControllerSupport

class ${className}Controller extends InterlisCrudControllerSupport<${className}> {

    ${className}Service ${propertyName}Service

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", relationshipOptions: "GET"]

    @Override
    protected Class<${className}> domainType() {
        return ${className}
    }

    @Override
    protected Object crudService() {
        return ${propertyName}Service
    }
}
