package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ItemList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemListServiceImplTest {

    private static final long LIST_ID = 1L;
    private static final long USER_ID = 10L;
    private static final long OTHER_USER_ID = 99L;

    @Mock
    private ItemListRepository repository;

    @Mock
    private ListRepository listRepository;

    @InjectMocks
    private ItemListServiceImpl service;

    // --- getItemsList ---

    @Test
    void getItemsList_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getItemsList(LIST_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getItemsList_returnsMappedItems() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var items = List.of(
                new ItemList(1L, "Milk", false, null, null),
                new ItemList(2L, "Eggs", true, 2.0, "pcs")
        );
        when(repository.getItemsList(LIST_ID)).thenReturn(items);

        List<ItemList> result = service.getItemsList(LIST_ID, USER_ID);

        assertThat(result).hasSize(2).isEqualTo(items);
    }

    @Test
    void getItemsList_returnsEmptyList() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        when(repository.getItemsList(LIST_ID)).thenReturn(List.of());

        List<ItemList> result = service.getItemsList(LIST_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    // --- save ---

    @Test
    void save_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        assertThatThrownBy(() -> service.save(command, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void save_delegatesToRepository_withQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, 1.5, "liters");

        service.save(command, USER_ID);

        verify(repository).save(new ItemList(null, "Milk", false, 1.5, "liters"), LIST_ID);
    }

    @Test
    void save_delegatesToRepository_withNullQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        service.save(command, USER_ID);

        verify(repository).save(new ItemList(null, "Milk", false, null, null), LIST_ID);
    }

    @Test
    void save_returnsNothing() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new CreateItemListCommand("Milk", LIST_ID, null, null);

        assertThatNoException().isThrownBy(() -> service.save(command, USER_ID));
    }

    // --- update ---

    @Test
    void update_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, null, null);

        assertThatThrownBy(() -> service.update(command, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_delegatesToRepository_withQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        service.update(command, USER_ID);

        verify(repository).update(new ItemList(3L, "Butter", true, 0.5, "kg"), LIST_ID);
    }

    @Test
    void update_delegatesToRepository_withNullQuantityAndUom() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, null, null);
        var updated = new ItemList(3L, "Butter", true, null, null);
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        service.update(command, USER_ID);

        verify(repository).update(new ItemList(3L, "Butter", true, null, null), LIST_ID);
    }

    @Test
    void update_returnsUpdatedDomain() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);
        var command = new UpdateItemListCommand(3L, "Butter", LIST_ID, true, 0.5, "kg");
        var updated = new ItemList(3L, "Butter", true, 0.5, "kg");
        when(repository.update(updated, LIST_ID)).thenReturn(updated);

        ItemList result = service.update(command, USER_ID);

        assertThat(result).isEqualTo(updated);
    }

    // --- delete ---

    @Test
    void delete_throwsAccessDenied_whenNotOwner() {
        when(listRepository.existsByIdAndUserId(LIST_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(LIST_ID, 2L, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_delegatesToRepository() {
        when(listRepository.existsByIdAndUserId(LIST_ID, USER_ID)).thenReturn(true);

        service.delete(LIST_ID, 2L, USER_ID);

        verify(repository).delete(LIST_ID, 2L);
    }
}
