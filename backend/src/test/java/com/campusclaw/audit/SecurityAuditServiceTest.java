package com.campusclaw.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class SecurityAuditServiceTest {
    @Test
    void commitsDenialInAnIndependentTransactionWithMetadataOnly() {
        var events = mock(SecurityAuditEventRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        var service = new SecurityAuditService(events, transactions);

        service.record(1L, 7L, AuditAction.CONFIG_WRITE, AuditObjectType.SKILL,
                12L, 12L, null, AuditStatus.DENIED, 403, "FORBIDDEN",
                null, null, null, null, "request-1");

        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactions).commit(status);
        var event = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().getClassId()).isEqualTo(1L);
        assertThat(event.getValue().getUserId()).isEqualTo(7L);
        assertThat(event.getValue().getHttpStatus()).isEqualTo(403);
        assertThat(event.getValue().getTotalTokens()).isNull();
    }

    @Test
    void repositoryFailureRollsBackOnlyTheAuditAndDoesNotEscapeToTheCaller() {
        var events = mock(SecurityAuditEventRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        when(events.save(any())).thenThrow(new IllegalStateException("synthetic database failure"));
        var service = new SecurityAuditService(events, transactions);

        assertThatCode(() -> service.recordSkillFailure(
                1L, 7L, 11L, 12L, "计算", "MODEL_STREAM_FAILED", "request-2"))
                .doesNotThrowAnyException();

        verify(transactions).rollback(status);
    }

    @Test
    void commitFailureDoesNotEscapeToTheCaller() {
        var events = mock(SecurityAuditEventRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("synthetic commit failure")).when(transactions).commit(status);
        var service = new SecurityAuditService(events, transactions);

        assertThatCode(() -> service.recordSkillFailure(
                1L, 7L, 11L, null, null, "MODEL_STREAM_FAILED", "request-3"))
                .doesNotThrowAnyException();
    }
}
