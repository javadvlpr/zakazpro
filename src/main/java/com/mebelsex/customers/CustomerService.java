package com.mebelsex.customers;

import com.mebelsex.common.BusinessException;
import com.mebelsex.orders.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public CustomerService(CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Customer create(String fullName, String phone, String address, String notes) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new BusinessException("customer.error.fullname.required");
        }

        Customer customer = new Customer(fullName.trim(), phone, address, notes);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, String fullName, String phone, String address, String notes) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new BusinessException("customer.error.fullname.required");
        }

        Customer customer = findById(id)
                .orElseThrow(() -> new BusinessException("customer.error.notfound"));

        customer.setFullName(fullName.trim());
        customer.setPhone(phone);
        customer.setAddress(address);
        customer.setNotes(notes);

        return customerRepository.save(customer);
    }

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    public List<Customer> list() {
        return customerRepository.findAll();
    }

    public List<Customer> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return list();
        }
        return customerRepository.searchByNameOrPhone(query.trim());
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findById(id)
                .orElseThrow(() -> new BusinessException("customer.error.notfound"));

        if (orderRepository.existsByCustomerId(id)) {
            throw new BusinessException("customer.error.has.orders");
        }

        customerRepository.delete(customer);
    }
}
