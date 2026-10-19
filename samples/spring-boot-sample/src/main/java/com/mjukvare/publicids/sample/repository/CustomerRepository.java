package com.mjukvare.publicids.sample.repository;

import com.mjukvare.publicids.sample.domain.Customer;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// In memory to keep the sample small. A real repository stores and queries by UUID the same way: public ids never
// reach the database.
@Repository
public class CustomerRepository {

    private final Map<UUID, Customer> customers = new ConcurrentHashMap<>();

    public Customer save(Customer customer) {
        customers.put(customer.id(), customer);
        return customer;
    }

    public Optional<Customer> findById(UUID id) {
        return Optional.ofNullable(customers.get(id));
    }
}
