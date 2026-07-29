import org.springframework.web.servlet.i18n.FixedLocaleResolver

beans = {
    localeResolver(FixedLocaleResolver, Locale.forLanguageTag("de-CH"))
}
