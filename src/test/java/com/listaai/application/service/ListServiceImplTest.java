package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListServiceImplTest {

    @Mock
    private ListRepository listRepository;

    @InjectMocks
    private ListServiceImpl listService;

    @Test
    void getAll_returnsMappedLists() {
        List<ShoppingList> expected = List.of(
                new ShoppingList(1L, "Groceries"),
                new ShoppingList(2L, "Hardware")
        );
        when(listRepository.findAll()).thenReturn(expected);

        List<ShoppingList> result = listService.getAll();

        assertThat(result).hasSize(2).isEqualTo(expected);
    }

    @Test
    void getAll_returnsEmptyList() {
        when(listRepository.findAll()).thenReturn(List.of());

        List<ShoppingList> result = listService.getAll();

        assertThat(result).isEmpty();
    }

    @Test
    void save_delegatesToRepository() {
        CreateListCommand command = new CreateListCommand("Groceries");
        ShoppingList expected = new ShoppingList(null, "Groceries");
        when(listRepository.save(expected)).thenReturn(new ShoppingList(1L, "Groceries"));

        listService.save(command);

        verify(listRepository).save(new ShoppingList(null, "Groceries"));
    }

    @Test
    void save_returnsPersistedDomain() {
        CreateListCommand command = new CreateListCommand("Groceries");
        ShoppingList persisted = new ShoppingList(1L, "Groceries");
        when(listRepository.save(new ShoppingList(null, "Groceries"))).thenReturn(persisted);

        ShoppingList result = listService.save(command);

        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void delete_delegatesToRepository() {
        listService.delete(1L);

        verify(listRepository).delete(1L);
    }
}
