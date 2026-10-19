# PublicIds

[![Maven Central](https://img.shields.io/maven-central/v/com.mjukvare.publicids/publicids-core)](https://central.sonatype.com/namespace/com.mjukvare.publicids)

Typed, URL-safe public identifiers for entities that use UUIDs. Your application stores and queries by UUID. Outside
the application, a customer is known only as `cus_AZL4pDt8fR6fKmxbjk0aNw` and an invoice as `inv_SUTPHPemSKyjCtnEqRhttw`.

```java
@PublicIdPrefix("cus")
public record Customer(UUID id, String name) {}

String publicId = codec.of(Customer.class, customer.id());     // "cus_AZL4pDt8fR6fKmxbjk0aNw"
UUID id = codec.parseOrThrow(Customer.class, request.id());     // the UUID back, or InvalidPublicIdException
```

- **Typed.** The prefix says what an id refers to, in logs, support tickets and URLs. Parsing checks it, so an invoice id
  is rejected where a customer id is expected, even when the same UUID is behind both.
- **Compact and URL-safe.** 22 characters of URL-safe Base64 after the prefix, instead of the 36 of a UUID. No escaping
  in paths or query strings.
- **Strict.** Each UUID has exactly one public id, and parsing accepts only that one. There are no aliases that differ in
  case, padding or unused bits.
- **Fails early.** Invalid or clashing prefixes fail when the codec is built, or with the Spring Boot starter, when the
  application starts.
- **Small.** The core library depends only on [JSpecify](https://jspecify.dev) annotations. The starter adds only what
  Spring Boot auto-configuration needs.

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start with Spring Boot](#quick-start-with-spring-boot)
- [Quick start without Spring](#quick-start-without-spring)
- [The public id format](#the-public-id-format)
- [Public ids are not secret](#public-ids-are-not-secret)
- [Giving types a prefix](#giving-types-a-prefix)
- [Encoding and parsing](#encoding-and-parsing)
- [The static `PublicIds` facade](#the-static-publicids-facade)
- [Spring Boot starter](#spring-boot-starter)
- [Testing](#testing)
- [Custom encoders](#custom-encoders)
- [Null safety, thread safety and modules](#null-safety-thread-safety-and-modules)
- [Sample application](#sample-application)
- [Contributing](#contributing)
- [License](#license)

## Requirements

- Java 21 or newer.
- For the starter, Spring Boot 4. It is built and tested against Spring Boot 4.1.

## Installation

Both artifacts are on Maven Central under the group `com.mjukvare.publicids`. The badge above shows the latest version.

| Artifact                        | Use it when                                                                  |
|---------------------------------|------------------------------------------------------------------------------|
| `publicids-spring-boot-starter` | You use Spring Boot. It includes `publicids-core`.                           |
| `publicids-core`                | You don't use Spring Boot, or you want to build and manage the codec yourself. |

Maven:

```xml
<dependency>
    <groupId>com.mjukvare.publicids</groupId>
    <artifactId>publicids-spring-boot-starter</artifactId>
    <version>${publicids.version}</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.mjukvare.publicids:publicids-spring-boot-starter:$publicidsVersion")
```

For the core library alone, use the artifact id `publicids-core` instead.

## Quick start with Spring Boot

**1. Give your types a prefix.**

```java
@PublicIdPrefix("cus")
public record Customer(UUID id, String name) {}
```

That's all the setup there is. At startup the starter scans your application's packages for `@PublicIdPrefix` types,
builds a `PublicIdCodec` bean from them, and logs the mapping:

```
PublicIds maps 1 type to prefixes. With annotation fallback, other @PublicIdPrefix types resolve on first use.
cus -> com.example.shop.domain.Customer (@PublicIdPrefix)
```

**2. Convert at the edge of your application.** Inject `PublicIdCodec`, take public ids in as `String`s, and turn them
into UUIDs before they go any further. Turn UUIDs back into public ids when they go out.

```java
@RestController
@RequestMapping("/customers")
class CustomerController {

    private final PublicIdCodec publicIds;
    private final CustomerRepository customers;

    CustomerController(PublicIdCodec publicIds, CustomerRepository customers) {
        this.publicIds = publicIds;
        this.customers = customers;
    }

    record CustomerResponse(String id, String name) {}

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable String id) {
        var customerId = publicIds.parseOrThrow(Customer.class, id);
        var customer = customers.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return new CustomerResponse(publicIds.of(Customer.class, customer.id()), customer.name());
    }
}
```

**3. Answer bad ids with 400 Bad Request.** The library leaves status codes to you. Without a handler, an
`InvalidPublicIdException` becomes a 500.

```java
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidPublicIdException.class)
    ProblemDetail invalidPublicId(InvalidPublicIdException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Not a valid " + exception.type().getSimpleName().toLowerCase() + " id");
    }
}
```

The handler deliberately doesn't echo the rejected text back to the client. It is whatever the client sent.

**4. Where you can't inject, use the static facade.** Entities and value objects aren't Spring beans. The starter
installs its codec behind `PublicIds` when the application starts, so they can use that instead:

```java
@PublicIdPrefix("cus")
public record Customer(UUID id, String name) {

    public String publicId() {
        return PublicIds.of(Customer.class, id);
    }
}
```

## Quick start without Spring

Build one codec at startup and share it. It is immutable and thread-safe.

```java
var codec = PublicIdCodec.builder()
        .annotated(Customer.class, Order.class)   // reads @PublicIdPrefix now, so mistakes fail here
        .register(Invoice.class, "inv")           // for types you can't or don't want to annotate
        .build();

String publicId = codec.of(Customer.class, customerId);           // "cus_AZL4pDt8fR6fKmxbjk0aNw"
Optional<UUID> parsed = codec.parse(Customer.class, publicId);     // empty for bad input
UUID id = codec.parseOrThrow(Customer.class, publicId);            // InvalidPublicIdException for bad input
```

To use the codec from code that can't be handed it, install it behind the static facade before anything uses the facade,
and close the installation on shutdown:

```java
try (var installation = PublicIds.install(codec)) {
    runApplication();
}
```

See [The static `PublicIds` facade](#the-static-publicids-facade) for the rules.

## The public id format

A public id is the type's prefix, an underscore, and the UUID's 16 bytes in URL-safe Base64 without padding:

```
cus_AZL4pDt8fR6fKmxbjk0aNw
└┬┘│└─────────┬──────────┘
 │ │          └ the UUID 0192f8a4-3b7c-7d1e-9f2a-6c5b8e4d1a37, 22 characters
 │ └ separator
 └ prefix
```

- A prefix matches `[a-z0-9]+`. Short prefixes of two to four characters keep ids readable.
- With the default encoder, a public id is always the prefix length plus 23 characters long.
- The encoded part uses `A–Z`, `a–z`, `0–9`, `-` and `_`. It can itself contain `_` and `-`, and can even start with
  them: the UUID with every bit set is `cus______________________w`.
- Public ids are case-sensitive. `cus_AZL4...` and `cus_azl4...` are different ids, and only one of them is valid.

So treat public ids as opaque. Compare them exactly, store them as they are, and use `parse` rather than splitting them
on `_` or validating them with your own regex.

### The format is a contract

Public ids end up in your customers' bookmarks, databases, logs and emails, so they have to keep working. The format,
including the canonical encoding rule, is part of this library's public API: it won't change within a major version,
and the same type and UUID will always give the same public id.

What can break your ids is your own configuration. A public id stays valid only as long as its type keeps its prefix
and the codec keeps its encoder:

- **Don't rename a prefix** once ids with it have been handed out. Ids with the old prefix stop parsing. A type has
  exactly one prefix, so there is no way to accept both.
- **Don't change the encoder** of a codec in use. Every id it has handed out stops parsing.
- **Store UUIDs, not public ids.** Public ids are a presentation of the UUID. Keep them out of your database and convert
  at the edge.

## Public ids are not secret

A public id is an encoding, not encryption. Anyone can decode the UUID from it, the same way they can decode Base64.
That has two consequences:

- **It doesn't hide your internal ids.** If your UUIDs must not be exposed, PublicIds is not the tool for that.
- **It can reveal when a record was created.** UUIDv7, a common choice for database keys, starts with a millisecond
  timestamp, and so does its public id. `cus_AZL4pDt8fR6fKmxbjk0aNw` decodes to a UUIDv7 created on 2024-11-04 at 19:26:05 UTC.
  Random UUIDv4s carry no such information.

A public id is also not a credential. Knowing a valid id must never be enough to access the record: check
authorization the same way you would for a raw UUID.

## Giving types a prefix

There are several ways to give a type its prefix. Pick by whether you can annotate the type and whether you use the
starter:

| Way                                  | Where                   | Use it for                                          |
|--------------------------------------|-------------------------|-----------------------------------------------------|
| `@PublicIdPrefix("cus")` on the type | core                    | Your own types. The usual way.                      |
| `@PublicIdRegistration` declaration  | starter                 | Types you can't annotate, such as a library's.      |
| `builder.register(Type.class, "inv")`| core                    | Types you can't annotate, without the starter.      |
| `PublicIdCodecCustomizer` bean       | starter                 | Registrations that need code to work out.           |

### Inheritance and proxies

A type's prefix is found by walking from the type up its superclass chain. Interfaces are not consulted. At each class,
an explicit registration is checked first, then `@PublicIdPrefix`, and the first match wins.

So a subclass shares its superclass's prefix unless it declares its own. That includes proxies generated at runtime,
such as Hibernate's lazy-loading proxies, so `codec.of(entity.getClass(), entity.getId())` works on a proxied entity.
Types that share a prefix this way accept each other's ids when parsing.

```java
@PublicIdPrefix("acc")
abstract class Account { }

class SavingsAccount extends Account { }      // "acc", from Account

@PublicIdPrefix("chk")
class CheckingAccount extends Account { }     // "chk", its own
```

### Each prefix belongs to one type

Two types can't have the same prefix, or a public id couldn't say which type it belongs to. Clashes, invalid prefixes,
and registrations that contradict a type's annotation fail with `IllegalArgumentException` when the codec is built.
With the starter, that means the application fails to start, and the message names both types and where each prefix
was declared.

### Annotation fallback

By default, a `@PublicIdPrefix` type that wasn't registered or scanned up front still resolves the first time it is
used. That keeps the core library usable with no setup at all, but it also means a clash with such a type is only
found when that type is first used, with an `IllegalStateException`.

To find every problem up front, list your types with `annotated(...)` (or let the starter scan them) and disable the
fallback:

```java
var codec = PublicIdCodec.builder()
        .annotated(Customer.class, Order.class)
        .register(Invoice.class, "inv")
        .annotationFallback(false)
        .build();
```

Every other type then fails with `IllegalArgumentException`, annotated or not. With the starter, set
`publicids.annotation-fallback=false`.

## Encoding and parsing

| Method                           | Returns                                                   | For bad input                      |
|----------------------------------|-----------------------------------------------------------|------------------------------------|
| `of(type, uuid)`                 | the public id                                             | —                                  |
| `parse(type, publicId)`          | `Optional<UUID>`                                          | empty                              |
| `parseOrThrow(type, publicId)`   | `UUID`                                                    | throws `InvalidPublicIdException`  |
| `prefixes()`                     | the prefixes known up front, as an unmodifiable map       | —                                  |

Use `parse` where bad input is expected and you handle it yourself, and `parseOrThrow` where bad input means the
request is wrong, such as in a web endpoint.

Parsing is strict. It accepts exactly what `of` writes for the type, and rejects:

- an id of another type: `inv_...` where `cus_...` is expected,
- an id that differs in case, or has extra or missing characters,
- an encoding that decodes to the right bytes but isn't the one `of` writes. The 22 Base64 characters hold 132 bits for
  128, so several strings decode to the same UUID. Only one of them is accepted.

### Errors

Text that isn't a public id of the expected type is bad input, not a bug. Everything else points at a mistake in the
program and shouldn't be handled as bad input:

| Exception                   | Thrown when                                                                       |
|-----------------------------|-----------------------------------------------------------------------------------|
| `InvalidPublicIdException`  | `parseOrThrow` gets text that isn't a public id of the type. Bad input.           |
| `IllegalArgumentException`  | A type has no prefix, or a prefix is invalid or clashes while building the codec. |
| `IllegalStateException`     | A prefix found through annotation fallback is already used by another type.       |
| `NullPointerException`      | An argument is `null`.                                                            |

`InvalidPublicIdException` deliberately isn't an `IllegalArgumentException`, so a handler that maps it to 400 Bad
Request doesn't also catch programming errors. It carries the expected `type()` and the rejected `publicId()`.

## The static `PublicIds` facade

`PublicIds` gives static access to one JVM-wide codec, for code that can't have one injected: entities, value objects,
serializers. It has the same `of`, `parse` and `parseOrThrow` methods as the codec.

Where you *can* inject, inject `PublicIdCodec` instead. The dependency is then visible, and it can't depend on what
has or hasn't been installed yet.

### Which codec it uses

- If a codec is installed, the facade uses it.
- If none is installed, the **first use locks in a default codec**, the same as `PublicIdCodec.builder().build()`. It
  resolves `@PublicIdPrefix` only: no registrations, no custom encoder.
- Once the default codec is locked in, installing a codec fails with `IllegalStateException`, because earlier calls would
  have used different rules than later ones. The exception's cause is a stack trace of that first use, so you can find
  it.

So install your codec before anything uses the facade. The starter does that for you while the application context
starts. Code that calls the facade during startup, such as in a bean's constructor, can still run before the
installation and then fail it. Inject `PublicIdCodec` in beans to avoid that.

### Installations

`install` returns an `Installation`. Closing it uninstalls the codec. Installations are counted, so several owners can
share one codec, such as several Spring contexts cached by the test framework:

- Installing a codec *equal* to the installed one succeeds. The codec stays installed until every installation is
  closed.
- Installing a *different* codec while one is installed fails with `IllegalStateException`.
- Once the last installation is closed, the facade is back where it started.

Two codecs are equal when they were built with the same registrations, the same annotation fallback setting and equal
encoders, so they turn every UUID into the same public id.

## Spring Boot starter

The starter auto-configures a `PublicIdCodec` bean and installs it behind `PublicIds`. It brings no web code: it
doesn't register converters, argument resolvers or exception handlers. Conversion stays explicit in your code.

### What it does at startup

1. Scans the auto-configuration packages for types directly annotated with `@PublicIdPrefix` and for
   `@PublicIdRegistration` declarations. The auto-configuration packages are normally the package of your
   `@SpringBootApplication` class and its subpackages. Abstract and nested classes are included.
2. Applies every `PublicIdCodecCustomizer` bean, in `@Order`.
3. Builds the codec and logs the prefix mapping at `INFO`, with where each prefix came from.
4. Installs the codec behind `PublicIds`, and closes the installation when the context closes. This happens even with
   lazy initialization enabled.

An invalid or clashing prefix anywhere in the scanned packages stops the application from starting, rather than failing
the first request that uses it.

### `@PublicIdRegistration`

For types you can't annotate, such as one from another library, declare their prefixes on any class in your
application's packages. The class is never a bean and needs no other annotation:

```java
@PublicIdRegistration(type = Invoice.class, prefix = "inv")
@PublicIdRegistration(type = Receipt.class, prefix = "rct")
final class ThirdPartyPublicIds {}
```

Declarations are only read while scanning is enabled.

### `PublicIdCodecCustomizer`

For registrations that need code, or to set an encoder, declare a customizer bean:

```java
@Bean
PublicIdCodecCustomizer publicIdCustomizer() {
    return builder -> builder.encoder(new HexIdEncoder());
}
```

Prefer `@PublicIdRegistration` for fixed prefixes. A customizer is a bean, and test slices such as `@WebMvcTest` don't
load the configuration that declares it, so the slice builds a different codec unless it `@Import`s that configuration.

Conflicting registrations fail at startup, so the order customizers run in can't change the result.

### Your own codec

Define a `PublicIdCodec` bean and the starter uses it instead of building one. It doesn't scan or apply customizers
then, but it still installs your codec behind `PublicIds` unless `publicids.install=false`.

### Properties

| Property                        | Default | Description                                                                                                                                         |
|---------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `publicids.install`             | `true`  | Install the codec behind the static `PublicIds` facade. Disable it when a context needs a codec that differs from one already installed in the JVM. |
| `publicids.scan`                | `true`  | Scan the auto-configuration packages at startup. With scanning disabled, `@PublicIdRegistration` declarations are not read.                         |
| `publicids.annotation-fallback` | `true`  | Let `@PublicIdPrefix` types that were neither scanned nor registered resolve on first use.                                                          |

The starter ships configuration metadata, so your IDE completes and documents these properties.

### Logging

The starter logs through Commons Logging, like Spring itself, so your logging setup decides where it goes. To hide the
startup mapping:

```properties
logging.level.com.mjukvare.publicids.autoconfigure=warn
```

## Testing

`@SpringBootTest` and `@WebMvcTest` get the same codec as the application, with no setup: the starter registers its
auto-configuration for `@WebMvcTest`, and the scan doesn't depend on which beans a slice loads.

Other slices, such as `@DataJpaTest` or `@JsonTest`, don't include the starter. Import it if the code under test needs
the codec:

```java
@DataJpaTest
@ImportAutoConfiguration(PublicIdsAutoConfiguration.class)
class CustomerRepositoryTest { }
```

### Unit tests

In plain unit tests, build a real codec rather than mocking `PublicIdCodec`. It does no I/O and takes a line to
build, and the test then checks the public ids your code actually produces instead of strings a stub made up. Bad input
needs no stubbing either: pass malformed text or another type's id, and `parse` and `parseOrThrow` reject it just as
they do in production.

`@PublicIdPrefix` types resolve with no setup, so `PublicIdCodec.builder().build()` is enough for them. Register only
the types the application registers, so that the ids in your assertions are the ones clients see:

```java
class CustomerServiceTest {

    private final PublicIdCodec codec = PublicIdCodec.builder().register(Invoice.class, "inv").build();

    @Test
    void rejectsAnInvoiceIdWhereACustomerIdIsExpected() {
        // Arrange
        var service = new CustomerService(codec, new InMemoryCustomerRepository());
        String invoiceId = codec.of(Invoice.class, UUID.randomUUID());

        // Act
        Throwable result = catchThrowable(() -> service.find(invoiceId));

        // Assert
        assertThat(result).isInstanceOf(InvalidPublicIdException.class);
    }
}
```

This works best for code that takes the codec through its constructor. Code that uses the static facade has the
caveat below.

### Unit tests and the static facade

Be careful with plain unit tests that reach the static facade, for example through `customer.publicId()`. If such a
test runs before any Spring context in the same JVM, it locks in the default codec, and every Spring context started
afterwards in that JVM fails to install its codec. Whether that happens depends on test order.

To stay clear of it, either keep facade calls out of code that plain unit tests reach, or install the same codec the
application builds before those tests run, so the Spring contexts find an equal codec already installed.

### Several contexts in one JVM

The test framework caches contexts, so several can be open at once. Contexts that build equal codecs share the
installation. A context that builds a different codec, for example because of a test-only customizer, fails to start
while another one's codec is installed. Set `publicids.install=false` in that test and inject `PublicIdCodec` instead
of using the facade.

## Custom encoders

The default `Base64IdEncoder` suits most applications. To write the UUID differently, implement `IdEncoder`:

```java
record HexIdEncoder() implements IdEncoder {
    private static final HexFormat HEX = HexFormat.of();

    @Override
    public String encode(byte[] id) {
        return HEX.formatHex(id);
    }

    @Override
    public byte[] decode(String encodedId) {
        return HEX.parseHex(encodedId);
    }
}

var codec = PublicIdCodec.builder().encoder(new HexIdEncoder()).build();
codec.of(Customer.class, id);   // "cus_0192f8a43b7c7d1e9f2a6c5b8e4d1a37"
```

An encoder must:

- **throw `IllegalArgumentException`** from `decode` for text it can't decode. The codec turns that into an empty
  `parse` result.
- **be deterministic.** The codec only accepts text that `encode` gives back unchanged, so non-canonical input is
  rejected for free. Above, `parseHex` accepts upper case, but the codec rejects `cus_0192F8A4...` because `encode`
  writes lower case.
- **be thread-safe**, since one codec is shared across threads.
- **implement `equals`**, or be a record as above, if codecs using it should compare equal. The static facade and the
  starter rely on codec equality.

Remember that changing the encoder of a codec in use invalidates every public id it has handed out.

## Null safety, thread safety and modules

- **Null safety.** Both packages are `@NullMarked` with [JSpecify](https://jspecify.dev): nothing accepts or returns
  `null` unless it is annotated `@Nullable`. Null checkers such as NullAway, and Kotlin, understand this. A `null`
  argument throws `NullPointerException`.
- **Thread safety.** `PublicIdCodec` is immutable and thread-safe; build one and share it. `PublicIds` is thread-safe.
  `PublicIdCodec.Builder` is not.
- **Modules.** The jars declare the automatic module names `com.mjukvare.publicids` and
  `com.mjukvare.publicids.autoconfigure`.

## Sample application

[`samples/spring-boot-sample`](samples/spring-boot-sample) is a small shop API that uses the starter the way an
application would: annotated and registered prefixes, conversion in a controller, a 400 handler, the facade in a domain
type, and both `@SpringBootTest` and `@WebMvcTest` tests. Its README shows how to run it.

## Contributing

Building, testing and releasing are described in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

PublicIds is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
