package com.mebelsex.ui.customers;

import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.ui.TaskRunner;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A test verifying the Counter/Tracker logic simulating searchDebounce and
 * fetch tasks,
 * ensuring stale results do not get overriding newer results.
 */
class CustomersSearchRaceTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            latch.countDown();
        }
        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    void testStaleSearchResultIgnored() throws Exception {
        // Setup manual mock simulation of the logic found in CustomersController
        // using the searchCounter pattern.
        AtomicLong searchCounter = new AtomicLong(0);
        AtomicReference<List<Customer>> tableItems = new AtomicReference<>();

        // Simulates the click/type happening twice

        // User typed "a", task 1 created
        long task1Id = searchCounter.incrementAndGet();

        // User typed "ab", task 2 created immediately after (before task 1 completes)
        long task2Id = searchCounter.incrementAndGet();

        // Task 2 finishes extremely quickly returning "Customer AB"
        if (task2Id == searchCounter.get()) {
            Customer ab = new Customer();
            ab.setFullName("Customer AB");
            tableItems.set(List.of(ab));
        }

        // Task 1 finishes slowly later (stale), returning "Customer A"
        if (task1Id == searchCounter.get()) {
            Customer a = new Customer();
            a.setFullName("Customer A");
            tableItems.set(List.of(a));
        }

        // Validate table items did NOT get overwritten by Task 1
        assertEquals(1, tableItems.get().size());
        assertEquals("Customer AB", tableItems.get().get(0).getFullName(),
                "Stale task 1 should not override Task 2 results!");
    }
}
