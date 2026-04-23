package com.novatech.cybertech.fixtures.support;

import com.novatech.cybertech.utils.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Optional base class for {@code @WebMvcTest} slices. Subclasses still need to declare
 * {@code @WebMvcTest(value = SomeController.class)} and {@code @Import(TestSecurityConfig.class)};
 * this base only saves the {@code @AutoConfigureMockMvc} + {@code MockMvc} field + {@code asJson}
 * helper boilerplate. If a subclass needs more setup, it can ignore this and inline.
 */
@AutoConfigureMockMvc
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    protected String asJson(final Object value) {
        return TestUtils.asJsonString(value);
    }
}
