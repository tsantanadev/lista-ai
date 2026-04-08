package com.listaai.infrastructure.adapter.input.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();

        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
    }

    @Test
    void logsMethodAndUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/lists");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(logAppender.list)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage()).isEqualTo("POST /v1/lists");
                });
    }

    @Test
    void delegatesToFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/lists");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
