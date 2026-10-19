package com.mjukvare.publicids.sample.repository;

import com.mjukvare.publicids.sample.thirdparty.Invoice;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InvoiceRepository {

    private final List<Invoice> invoices = new CopyOnWriteArrayList<>();

    public Invoice save(Invoice invoice) {
        invoices.add(invoice);
        return invoice;
    }

    public List<Invoice> findByCustomerId(UUID customerId) {
        return invoices.stream().filter(invoice -> invoice.getCustomerId().equals(customerId)).toList();
    }
}
