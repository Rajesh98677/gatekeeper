package com.rajesh.gatekeeper.management.service;


import com.rajesh.gatekeeper.management.entity.ControlPlaneAuditRecord;
import com.rajesh.gatekeeper.management.entity.Organization;
import com.rajesh.gatekeeper.management.repo.ControlPlaneAuditRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final String ACTION = "CREATE";
    private static final String RESOURCE_TYPE = "API_KEY";
    private static final String RESOURCE_ID = "key-123";
    private static final String DETAILS = "created a new api key";

    @Mock
    private ControlPlaneAuditRecordRepository auditRecordRepository;

    @Mock
    private Organization organization;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private ControlPlaneAuditRecord recordAndCapture(long startedAt) {
        auditService.record(organization, ACTION, RESOURCE_TYPE, RESOURCE_ID, startedAt, DETAILS);

        ArgumentCaptor<ControlPlaneAuditRecord> captor =
                ArgumentCaptor.forClass(ControlPlaneAuditRecord.class);
        verify(auditRecordRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "password", List.of()));
    }

    private void bindRequestWithCorrelationId(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (correlationId != null) {
            request.addHeader("X-Correlation-ID", correlationId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("saves a record populated with the supplied values")
    void record_populatesFieldsFromArguments() {
        authenticateAs("alice");
        bindRequestWithCorrelationId("corr-1");

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertSame(organization, saved.getOrganization());
        assertEquals(ACTION, saved.getAction());
        assertEquals(RESOURCE_TYPE, saved.getResourceType());
        assertEquals(RESOURCE_ID, saved.getResourceId());
        assertEquals(DETAILS, saved.getDetails());
        assertEquals("SUCCESS", saved.getResult());
    }

    @Test
    @DisplayName("uses the authenticated user's name as the actor")
    void record_usesAuthenticatedUserAsActor() {
        authenticateAs("alice");

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertEquals("alice", saved.getActor());
    }

    @Test
    @DisplayName("falls back to 'system' when there is no authentication")
    void record_usesSystemActorWhenNoAuthentication() {
        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertEquals("system", saved.getActor());
    }

    @Test
    @DisplayName("reads the correlation id from the X-Correlation-ID header")
    void record_readsCorrelationIdFromHeader() {
        bindRequestWithCorrelationId("corr-abc-123");

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertEquals("corr-abc-123", saved.getCorrelationId());
    }

    @Test
    @DisplayName("correlation id is null when the header is missing")
    void record_correlationIdNullWhenHeaderMissing() {
        bindRequestWithCorrelationId(null);

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertNull(saved.getCorrelationId());
    }

    @Test
    @DisplayName("correlation id is null when there is no current request")
    void record_correlationIdNullWhenNoRequest() {
        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertNull(saved.getCorrelationId());
    }

    @Test
    @DisplayName("correlation id is null when request attributes are not servlet based")
    void record_correlationIdNullWhenNonServletAttributes() {
        RequestContextHolder.setRequestAttributes(org.mockito.Mockito.mock(RequestAttributes.class));

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        assertNull(saved.getCorrelationId());
    }

    @Test
    @DisplayName("computes the duration in milliseconds from the start time")
    void record_computesDurationInMillis() {
        long fiveSecondsAgo = System.nanoTime() - 5_000_000_000L;

        ControlPlaneAuditRecord saved = recordAndCapture(fiveSecondsAgo);

        assertTrue(saved.getDurationMs() >= 5000,
                "duration should be at least 5000 ms but was " + saved.getDurationMs());
        assertTrue(saved.getDurationMs() < 10000,
                "duration should be well under 10000 ms but was " + saved.getDurationMs());
    }

    @Test
    @DisplayName("sets createdAt to the current time")
    void record_setsCreatedAt() {
        LocalDateTime before = LocalDateTime.now();

        ControlPlaneAuditRecord saved = recordAndCapture(System.nanoTime());

        LocalDateTime after = LocalDateTime.now();
        assertNotNull(saved.getCreatedAt());
        assertTrue(!saved.getCreatedAt().isBefore(before), "createdAt should not be before the call");
        assertTrue(!saved.getCreatedAt().isAfter(after), "createdAt should not be after the call");
    }

    @Test
    @DisplayName("propagates repository failures to the caller")
    void record_propagatesRepositoryException() {
        when(auditRecordRepository.save(any(ControlPlaneAuditRecord.class)))
                .thenThrow(new IllegalStateException("db down"));

        long startedAt = System.nanoTime();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                auditService.record(organization, ACTION, RESOURCE_TYPE, RESOURCE_ID, startedAt, DETAILS));

        assertEquals("db down", ex.getMessage());
    }
}