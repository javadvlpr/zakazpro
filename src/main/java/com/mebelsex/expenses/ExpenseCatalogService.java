package com.mebelsex.expenses;

import com.mebelsex.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Catalog of expense categories and items. Nothing is hard-deleted; rows are deactivated. */
@Service
@Transactional(readOnly = true)
public class ExpenseCatalogService {

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseItemRepository itemRepository;

    public ExpenseCatalogService(ExpenseCategoryRepository categoryRepository, ExpenseItemRepository itemRepository) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    // ---------------- categories ----------------

    @Transactional
    public CategoryRow createCategory(String name) {
        String clean = requireName(name, "expense.category.name.required");
        ensureUniqueCategoryName(clean, null);
        ExpenseCategory category = categoryRepository.save(new ExpenseCategory(clean));
        return toRow(category);
    }

    @Transactional
    public CategoryRow renameCategory(Long id, String name) {
        String clean = requireName(name, "expense.category.name.required");
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("expense.category.notfound"));
        ensureUniqueCategoryName(clean, id);
        category.setName(clean);
        return toRow(categoryRepository.save(category));
    }

    @Transactional
    public void setCategoryActive(Long id, boolean active) {
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("expense.category.notfound"));
        category.setActive(active);
        categoryRepository.save(category);
    }

    public List<CategoryRow> listCategories(boolean includeInactive) {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> includeInactive || c.isActive())
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<CategoryRow> listActiveCategories() {
        return listCategories(false);
    }

    // ---------------- items ----------------

    @Transactional
    public ItemRow createItem(Long categoryId, String name, String unit, BigDecimal defaultPrice) {
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("expense.category.notfound"));
        String clean = requireName(name, "expense.item.name.required");
        ensureUniqueItemName(categoryId, clean, null);
        BigDecimal price = normalizePrice(defaultPrice);

        ExpenseItem item = new ExpenseItem();
        item.setCategory(category);
        item.setName(clean);
        item.setUnit(normalizeUnit(unit));
        item.setDefaultPrice(price);
        return toRow(itemRepository.save(item));
    }

    @Transactional
    public ItemRow updateItem(Long id, Long categoryId, String name, String unit, BigDecimal defaultPrice) {
        ExpenseItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("expense.item.notfound"));
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("expense.category.notfound"));
        String clean = requireName(name, "expense.item.name.required");
        ensureUniqueItemName(categoryId, clean, id);
        BigDecimal price = normalizePrice(defaultPrice);

        item.setCategory(category);
        item.setName(clean);
        item.setUnit(normalizeUnit(unit));
        item.setDefaultPrice(price);
        return toRow(itemRepository.save(item));
    }

    @Transactional
    public void setItemActive(Long id, boolean active) {
        ExpenseItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("expense.item.notfound"));
        item.setActive(active);
        itemRepository.save(item);
    }

    public List<ItemRow> listItems(Long categoryId, String search, boolean includeInactive) {
        String query = search == null ? "" : search.trim().toLowerCase();
        return itemRepository.findByCategoryIdOrderByNameAsc(categoryId).stream()
                .filter(i -> includeInactive || i.isActive())
                .filter(i -> query.isEmpty() || i.getName().toLowerCase().contains(query))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<ItemRow> listActiveItems(Long categoryId) {
        return itemRepository.findByCategoryIdOrderByNameAsc(categoryId).stream()
                .filter(ExpenseItem::isActive)
                .sorted(Comparator.comparing(ExpenseItem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    // ---------------- helpers ----------------

    private String requireName(String name, String key) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(key);
        }
        return name.trim();
    }

    private void ensureUniqueCategoryName(String name, Long selfId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new BusinessException("expense.category.duplicate");
                });
    }

    private void ensureUniqueItemName(Long categoryId, String name, Long selfId) {
        itemRepository.findByCategoryIdAndNameIgnoreCase(categoryId, name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new BusinessException("expense.item.duplicate");
                });
    }

    private BigDecimal normalizePrice(BigDecimal defaultPrice) {
        if (defaultPrice == null) {
            return BigDecimal.ZERO;
        }
        if (defaultPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("expense.item.price.invalid");
        }
        return defaultPrice;
    }

    private String normalizeUnit(String unit) {
        if (unit == null) {
            return null;
        }
        String clean = unit.trim();
        return clean.isEmpty() ? null : clean;
    }

    private CategoryRow toRow(ExpenseCategory category) {
        return new CategoryRow(category.getId(), category.getName(), category.isActive(),
                itemRepository.countByCategoryId(category.getId()));
    }

    private ItemRow toRow(ExpenseItem item) {
        return new ItemRow(
                item.getId(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getName(),
                item.getUnit(),
                item.getDefaultPrice(),
                item.isActive());
    }
}
