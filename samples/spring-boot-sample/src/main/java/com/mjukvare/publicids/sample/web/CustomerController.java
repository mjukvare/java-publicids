package com.mjukvare.publicids.sample.web;

import com.mjukvare.publicids.PublicIdCodec;
import com.mjukvare.publicids.sample.domain.Customer;
import com.mjukvare.publicids.sample.repository.CustomerRepository;
import com.mjukvare.publicids.sample.repository.InvoiceRepository;
import com.mjukvare.publicids.sample.thirdparty.Invoice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
class CustomerController {

    // Spring-managed code injects the codec: the dependency is explicit, and it is guaranteed to be ready
    private final PublicIdCodec publicIds;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;

    CustomerController(PublicIdCodec publicIds, CustomerRepository customers, InvoiceRepository invoices) {
        this.publicIds = publicIds;
        this.customers = customers;
        this.invoices = invoices;
    }

    record CreateCustomerRequest(String name) {}

    record CustomerResponse(String id, String name) {}

    record InvoiceResponse(String id, BigDecimal amount) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse create(@RequestBody CreateCustomerRequest request) {
        var customer = customers.save(new Customer(UUID.randomUUID(), request.name()));
        return toResponse(customer);
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable String id) {
        return toResponse(find(id));
    }

    @GetMapping("/{id}/invoices")
    List<InvoiceResponse> invoices(@PathVariable String id) {
        var customer = find(id);
        return invoices.findByCustomerId(customer.id()).stream()
                .map(invoice -> new InvoiceResponse(publicIds.of(Invoice.class, invoice.getId()), invoice.getAmount()))
                .toList();
    }

    // Public id in, UUID out, at the edge of the application. A malformed id, or an id of another type such as inv_...,
    // throws InvalidPublicIdException, which ApiExceptionHandler answers with 400 Bad Request. A well-formed id that
    // matches no customer is 404 Not Found.
    private Customer find(String id) {
        var customerId = publicIds.parseOrThrow(Customer.class, id);
        return customers.findById(customerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.publicId(), customer.name());
    }
}
