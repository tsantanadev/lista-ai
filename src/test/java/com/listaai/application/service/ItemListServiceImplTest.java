package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.domain.model.ItemList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemListServiceImplTest {

    @Mock
    private ItemListRepository repository;

    @InjectMocks
    private ItemListServiceImpl service;

    @Test
    void getItemsList_returnsMappedItems() {
        var items = List.of(
                new ItemList(1L, "Milk", false),
                new ItemList(2L, "Eggs", true)
        );
        when(repository.getItemsList(1L)).thenReturn(items);

        List<ItemList> result = service.getItemsList(1L);

        assertThat(result).hasSize(2).isEqualTo(items);
    }

    @Test
    void getItemsList_returnsEmptyList() {
        when(repository.getItemsList(1L)).thenReturn(List.of());

        List<ItemList> result = service.getItemsList(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void save_delegatesToRepository() {
        var command = new CreateItemListCommand("Milk", 1L);

        service.save(command);

        verify(repository).save(new ItemList(null, "Milk", false), 1L);
    }

    @Test
    void save_returnsNothing() {
        var command = new CreateItemListCommand("Milk", 1L);

        assertThatNoException().isThrownBy(() -> service.save(command));
    }

    @Test
    void update_delegatesToRepository() {
        var command = new UpdateItemListCommand(3L, "Butter", 1L, true);
        var updated = new ItemList(3L, "Butter", true);
        when(repository.update(updated, 1L)).thenReturn(updated);

        service.update(command);

        verify(repository).update(new ItemList(3L, "Butter", true), 1L);
    }

    @Test
    void update_returnsUpdatedDomain() {
        var command = new UpdateItemListCommand(3L, "Butter", 1L, true);
        var updated = new ItemList(3L, "Butter", true);
        when(repository.update(updated, 1L)).thenReturn(updated);

        ItemList result = service.update(command);

        assertThat(result).isEqualTo(updated);
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete(1L, 2L);

        verify(repository).delete(1L, 2L);
    }
}
