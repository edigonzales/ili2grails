package ch.example.gssimple

import ch.interlis.generator.grails.runtime.InterlisCrudControllerSupport
import ch.interlis.generator.grails.runtime.InterlisAssociationQueryService
import ch.interlis.generator.grails.runtime.InterlisAssociationCommandService

class EmployeeController extends InterlisCrudControllerSupport<Employee> {

    EmployeeService employeeService
    InterlisAssociationQueryService interlisAssociationQueryService
    InterlisAssociationCommandService interlisAssociationCommandService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", relationshipOptions: "GET",
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
    protected Class<Employee> domainType() {
        return Employee
    }

    @Override
    protected Object crudService() {
        return employeeService
    }

    @Override
    protected Object associationQueryService() {
        return interlisAssociationQueryService
    }

    @Override
    protected Object associationCommandService() {
        return interlisAssociationCommandService
    }
}
