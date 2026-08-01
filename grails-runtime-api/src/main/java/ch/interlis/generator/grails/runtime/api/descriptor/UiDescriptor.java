package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable UI descriptor of a domain, resolved from the domain registry plus
 * runtime properties and overrides. The web layer converts this descriptor
 * into a view model map at the GSP boundary.
 */
public record UiDescriptor(
    DomainDescriptor domain,
    String appTitle,
    String appLogo,
    String appLogoIcon,
    ListDescriptor list,
    FormDescriptor form,
    DetailDescriptor detail
) {

    public UiDescriptor {
        domain = java.util.Objects.requireNonNull(domain, "domain");
        list = list == null ? new ListDescriptor(null, null, null, null, null, null, null, null) : list;
        form = form == null ? new FormDescriptor(null) : form;
        detail = detail == null ? new DetailDescriptor(null) : detail;
    }
}
