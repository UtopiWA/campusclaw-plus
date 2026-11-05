package com.campusclaw.grading;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.IndexStatus;
import com.campusclaw.persistence.KnowledgeEntry;
import com.campusclaw.persistence.KnowledgeEntryRepository;
import com.campusclaw.persistence.Material;
import com.campusclaw.persistence.MaterialRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;

class AssignmentCreationServiceTest {
    private final AssignmentService assignments = mock(AssignmentService.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final KnowledgeEntryRepository entries = mock(KnowledgeEntryRepository.class);
    private final AssignmentCreationService service = new AssignmentCreationService(
            assignments, materials, entries, new ObjectMapper());
    private final UserAccount teacher = new UserAccount("teacher", "synthetic", Role.TEACHER, 1L);
    private final CreateAssignmentRequest request = new CreateAssignmentRequest("Title", "Question", null, 100, List.of(2L));

    private void ready(String content) {
        Material material = mock(Material.class);
        when(material.getIndexStatus()).thenReturn(IndexStatus.READY);
        when(material.getStoredPath()).thenReturn("synthetic.md");
        when(material.getId()).thenReturn(2L);
        when(material.getTitle()).thenReturn("Rubric");
        when(materials.findByIdAndClassId(2L, 1L)).thenReturn(Optional.of(material));
        when(entries.findAllByMaterialIdAndClassIdOrderByChunkIndex(2L, 1L))
                .thenReturn(List.of(new KnowledgeEntry(2L, 1L, 0, content)));
    }

    @Test
    void studentAndCrossClassCannotReadReferences() {
        assertThrows(AccessDeniedException.class, () -> service.create(request,
                new UserAccount("student", "synthetic", Role.STUDENT, 1L)));
        assertThrows(NotFoundException.class, () -> service.create(request, teacher));
        verifyNoInteractions(assignments, entries);
    }

    @Test
    void nonReadyMaterialCannotCreateAssignment() {
        Material pending = mock(Material.class);
        when(pending.getIndexStatus()).thenReturn(IndexStatus.PENDING);
        when(materials.findByIdAndClassId(2L, 1L)).thenReturn(Optional.of(pending));
        assertThrows(BadRequestException.class, () -> service.create(request, teacher));
        verifyNoInteractions(assignments, entries);
    }

    @Test
    void duplicateFilesAreRejectedBeforeReads() {
        assertThrows(BadRequestException.class, () -> service.create(
                new CreateAssignmentRequest("Title", "Question", null, 100, List.of(2L, 2L)), teacher));
        verifyNoInteractions(materials, assignments);
    }

    @Test
    void emptyFileDoesNotSave() {
        ready(" \n ");
        assertThrows(BadRequestException.class, () -> service.create(request, teacher));
        verifyNoInteractions(assignments);
    }

    @Test
    void unicodeFileLimitIsEnforcedBeforeSaving() {
        ready("学".repeat(20001));
        assertThrows(BadRequestException.class, () -> service.create(request, teacher));
        verifyNoInteractions(assignments);
    }

    @Test
    void answerOnlyFileIsSavedWithoutCriterionExtraction() {
        ready("直角三角形中 a²+b²=c²，c 是斜边。");
        service.create(request, teacher);
        verify(assignments).createPrepared(eq(request), eq(teacher), contains("Rubric"), contains("a²+b²=c²"));
    }
}
