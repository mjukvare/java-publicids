package com.mjukvare.publicids.sample.web;

import com.jayway.jsonpath.JsonPath;
import com.mjukvare.publicids.PublicIdCodec;
import com.mjukvare.publicids.sample.domain.Customer;
import com.mjukvare.publicids.sample.repository.CustomerRepository;
import com.mjukvare.publicids.sample.repository.InvoiceRepository;
import com.mjukvare.publicids.sample.thirdparty.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PublicIdCodec publicIds;

    @Autowired
    CustomerRepository customers;

    @Autowired
    InvoiceRepository invoices;

    @Test
    void createReturnsPublicIdWithCustomerPrefix() {
        // Act
        var result = mvc.post().uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Grace Hopper\"}")
                .exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.id").asString().startsWith("cus_");
    }

    @Test
    void createdCustomerCanBeFetchedByItsPublicId() throws Exception {
        // Arrange
        var created = mvc.post().uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Grace Hopper\"}")
                .exchange();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Act
        var result = mvc.get().uri("/customers/{id}", id).exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Grace Hopper");
    }

    @Test
    void invoicesUseTheRegisteredPrefix() {
        // Arrange
        var customer = customers.save(new Customer(UUID.randomUUID(), "Ada Lovelace"));
        invoices.save(new Invoice(UUID.randomUUID(), customer.id(), BigDecimal.TEN));

        // Act
        var result = mvc.get().uri("/customers/{id}/invoices", customer.publicId()).exchange();

        // Assert
        assertThat(result).hasStatusOk()
                .bodyJson().extractingPath("$[0].id").asString().startsWith("inv_");
    }

    @Test
    void idOfAnotherTypeIsBadRequest() {
        // Arrange
        var customer = customers.save(new Customer(UUID.randomUUID(), "Ada Lovelace"));
        // Same UUID, but an invoice id: it must not reach the customer
        var invoiceId = publicIds.of(Invoice.class, customer.id());

        // Act
        var result = mvc.get().uri("/customers/{id}", invoiceId).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownCustomerIsNotFound() {
        // Arrange
        var unknownId = publicIds.of(Customer.class, UUID.randomUUID());

        // Act
        var result = mvc.get().uri("/customers/{id}", unknownId).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void malformedIdIsBadRequest() {
        // Act
        var result = mvc.get().uri("/customers/{id}", "cus_not-an-id").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
