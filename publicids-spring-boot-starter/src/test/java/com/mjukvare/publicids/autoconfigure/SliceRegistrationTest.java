package com.mjukvare.publicids.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

// Slice tests only load the auto-configurations registered for them. Referring to the annotation by class means a
// rename in Spring Boot breaks compilation here instead of silently leaving the codec out of the slice.
class SliceRegistrationTest {

    @Test
    void webMvcTestIncludesAutoConfiguration() {
        // Act
        var result = ImportCandidates.load(AutoConfigureWebMvc.class, getClass().getClassLoader());

        // Assert
        assertThat(result.getCandidates()).contains(PublicIdsAutoConfiguration.class.getName());
    }
}
