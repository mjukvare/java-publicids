package com.mjukvare.publicids.sample.startup;

import com.mjukvare.publicids.sample.domain.Customer;
import com.mjukvare.publicids.sample.repository.CustomerRepository;
import com.mjukvare.publicids.sample.repository.InvoiceRepository;
import com.mjukvare.publicids.sample.thirdparty.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// Seeds a customer with an invoice and logs ready-to-use URLs. ApplicationRunners run once the context has started,
// so the codec is installed and customer.publicId() can use the static facade.
@Component
class SampleData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleData.class);

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;

    SampleData(CustomerRepository customers, InvoiceRepository invoices) {
        this.customers = customers;
        this.invoices = invoices;
    }

    @Override
    public void run(ApplicationArguments args) {
        var customer = customers.save(new Customer(UUID.randomUUID(), "Ada Lovelace"));
        invoices.save(new Invoice(UUID.randomUUID(), customer.id(), new BigDecimal("42.00")));

        log.info("Try GET /customers/{} and GET /customers/{}/invoices", customer.publicId(), customer.publicId());
    }
}
