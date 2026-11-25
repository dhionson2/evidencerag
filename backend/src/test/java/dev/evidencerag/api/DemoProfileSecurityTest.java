package dev.evidencerag.api;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProfileSecurityTest {
    @Test void administrativeDocumentRoutesAreExcludedWhenDemoProfileIsActive() {
        Profile profile = DocumentController.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!demo");
    }
}
