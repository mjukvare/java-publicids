package com.mjukvare.publicids.sample.web;

import com.mjukvare.publicids.sample.domain.Customer;
import com.mjukvare.publicids.sample.repository.CustomerRepository;
import com.mjukvare.publicids.sample.repository.InvoiceRepository;
import com.mjukvare.publicids.sample.thirdparty.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// The starter registers its auto-configuration for @WebMvcTest, and the codec is built from the scanned @PublicIdPrefix
// types and @PublicIdRegistration declarations rather than from beans. The slice therefore gets the same codec as the
// application without importing anything.
@WebMvcTest(CustomerController.class)
class CustomerControllerWebMvcTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CustomerRepository customers;

    @MockitoBean
    InvoiceRepository invoices;

    @Test
    void findsCustomerByPublicId() {
        // Arrange
        var customer = new Customer(UUID.randomUUID(), "Ada Lovelace");
        given(customers.findById(customer.id())).willReturn(Optional.of(customer));

        // Act
        var result = mvc.get().uri("/customers/{id}", customer.publicId()).exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$.id").isEqualTo(customer.publicId());
    }

    @Test
    void invoicesUseTheRegisteredPrefix() {
        // Arrange
        var customer = new Customer(UUID.randomUUID(), "Ada Lovelace");
        given(customers.findById(customer.id())).willReturn(Optional.of(customer));
        given(invoices.findByCustomerId(customer.id()))
                .willReturn(List.of(new Invoice(UUID.randomUUID(), customer.id(), BigDecimal.TEN)));

        // Act
        var result = mvc.get().uri("/customers/{id}/invoices", customer.publicId()).exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$[0].id").asString().startsWith("inv_");
    }

    @Test
    void malformedIdIsBadRequest() {
        // Act
        var result = mvc.get().uri("/customers/{id}", "cus_not-an-id").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
