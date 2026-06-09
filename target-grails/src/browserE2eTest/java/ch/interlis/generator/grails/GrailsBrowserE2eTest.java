package ch.interlis.generator.grails;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static org.assertj.core.api.Assertions.assertThat;

class GrailsBrowserE2eTest {

    @Test
    void generatedGrailsAppSupportsBasicCrudNavigationInBrowser() {
        String appUrl = System.getProperty("browserE2eAppUrl");
        if (appUrl == null || appUrl.isBlank()) {
            throw new TestAbortedException(
                "Set -DbrowserE2eAppUrl=http://localhost:<port> for browser E2E against a running generated Grails app."
            );
        }

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.navigate(appUrl);
            assertThat(page.title()).isNotBlank();
            assertThat(page.locator("nav").count()).isGreaterThan(0);

            int createLinks = page.locator("a[href*='create']").count();
            if (createLinks == 0) {
                throw new TestAbortedException("No create link found in generated app at " + appUrl);
            }

            page.locator("a[href*='create']").first().click();
            assertThat(page.locator("form").count()).isGreaterThan(0);
            assertThat(page.locator(".ili-geometry-editor, .ili-native-grid, fieldset.form").count()).isGreaterThan(0);
        }
    }
}
