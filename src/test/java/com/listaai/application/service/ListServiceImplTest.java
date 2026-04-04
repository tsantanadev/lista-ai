package com.listaai.application.service;

import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListServiceImplTest {

    @Mock
    private ListRepository listRepository;

    @InjectMocks
    private ListServiceImpl listService;

    @Test
    void getAllLists_returnsMappedLists() {
        Long userId = 1L;
        List<ShoppingList> expected = List.of(
                new ShoppingList(1L, "Groceries"),
                new ShoppingList(2L, "Hardware")
        );
        when(listRepository.findAllByUserId(userId)).thenReturn(expected);

        List<ShoppingList> result = listService.getAllLists(userId);

        assertThat(result).hasSize(2).isEqualTo(expected);
    }

    @Test
    void getAllLists_returnsEmptyList() {
        Long userId = 1L;
        when(listRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<ShoppingList> result = listService.getAllLists(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void createList_delegatesToRepository() {
        Long userId = 1L;
        CreateListCommand command = new CreateListCommand("Groceries", userId);
        ShoppingList expected = new ShoppingList(null, "Groceries");
        when(listRepository.save(expected, userId)).thenReturn(new ShoppingList(1L, "Groceries"));

        listService.createList(command);

        verify(listRepository).save(new ShoppingList(null, "Groceries"), userId);
    }

    @Test
    void createList_returnsPersistedDomain() {
        Long userId = 1L;
        CreateListCommand command = new CreateListCommand("Groceries", userId);
        ShoppingList persisted = new ShoppingList(1L, "Groceries");
        when(listRepository.save(new ShoppingList(null, "Groceries"), userId)).thenReturn(persisted);

        ShoppingList result = listService.createList(command);

        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void deleteList_delegatesToRepository() {
        Long listId = 1L;
        Long userId = 1L;
        when(listRepository.existsByIdAndUserId(listId, userId)).thenReturn(true);

        listService.deleteList(listId, userId);

        verify(listRepository).deleteById(listId);
    }

    @Test
    void deleteList_throwsAccessDenied_whenNotOwner() {
        Long listId = 1L;
        Long userId = 1L;
        when(listRepository.existsByIdAndUserId(listId, userId)).thenReturn(false);

        assertThatThrownBy(() -> listService.deleteList(listId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
