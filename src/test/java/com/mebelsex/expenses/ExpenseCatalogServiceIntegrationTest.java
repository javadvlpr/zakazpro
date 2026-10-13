package com.mebelsex.expenses;

import com.mebelsex.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-cat-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class ExpenseCatalogServiceIntegrationTest {

    @Autowired
    private ExpenseCatalogService catalogService;

    private String unique(String base) {
        return base + "-" + System.nanoTime();
    }

    @Test
    void categoryNamesAreUniqueCaseInsensitively() {
        String name = unique("Material");
        catalogService.createCategory(name);
        assertThrows(BusinessException.class, () -> catalogService.createCategory(name.toUpperCase()));
    }

    @Test
    void categoryCanBeRenamedAndDuplicateRejected() {
        String a = unique("Material");
        String b = unique("Xizmat");
        CategoryRow rowA = catalogService.createCategory(a);
        catalogService.createCategory(b);

        CategoryRow renamed = catalogService.renameCategory(rowA.id(), unique("Yangi nom"));
        assertTrue(renamed.name().startsWith("Yangi nom"));

        assertThrows(BusinessException.class, () -> catalogService.renameCategory(rowA.id(), b));
    }

    @Test
    void categoryActivationFiltersTheList() {
        String name = unique("Vaqtinchalik");
        CategoryRow row = catalogService.createCategory(name);
        catalogService.setCategoryActive(row.id(), false);

        assertFalse(catalogService.listCategories(false).stream().anyMatch(c -> c.id().equals(row.id())),
                "inactive category hidden by default");
        assertTrue(catalogService.listCategories(true).stream().anyMatch(c -> c.id().equals(row.id())),
                "inactive category shown when requested");
    }

    @Test
    void itemNamesAreUniqueWithinCategoryButNotAcrossCategories() {
        CategoryRow catA = catalogService.createCategory(unique("A"));
        CategoryRow catB = catalogService.createCategory(unique("B"));
        String itemName = unique("Fanera");

        catalogService.createItem(catA.id(), itemName, "m²", new BigDecimal("85000"));
        assertThrows(BusinessException.class,
                () -> catalogService.createItem(catA.id(), itemName.toUpperCase(), "m²", BigDecimal.ZERO));
        // same name in another category is fine
        catalogService.createItem(catB.id(), itemName, "dona", BigDecimal.ZERO);
    }

    @Test
    void itemActivationFiltersActiveItems() {
        CategoryRow cat = catalogService.createCategory(unique("C"));
        ItemRow item = catalogService.createItem(cat.id(), unique("Lak"), "litr", new BigDecimal("100000"));
        long count = catalogService.listCategories(true).stream()
                .filter(c -> c.id().equals(cat.id())).findFirst().orElseThrow().itemCount();
        assertTrue(count >= 1, "itemCount includes the new item");

        catalogService.setItemActive(item.id(), false);
        assertTrue(catalogService.listActiveItems(cat.id()).isEmpty(), "inactive item hidden from pickers");
        List<ItemRow> all = catalogService.listItems(cat.id(), null, true);
        assertTrue(all.stream().anyMatch(i -> i.id().equals(item.id())), "inactive item still listed when requested");
    }

    @Test
    void negativeDefaultPriceRejected() {
        CategoryRow cat = catalogService.createCategory(unique("D"));
        assertThrows(BusinessException.class,
                () -> catalogService.createItem(cat.id(), unique("X"), "dona", new BigDecimal("-1")));
    }
}
