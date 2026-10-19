# PublicIds Spring Boot sample

A small shop API that shows how an application uses PublicIds. Customers are stored and looked up by UUID, but the
API only ever exposes public ids such as `cus_NCkXyXgbS9eGyNXeSI2hCA`.

## Run it

The sample is a standalone project that depends on the library like any consumer would, so install the library first.
Building requires JDK 21 or newer.

```shell
./mvnw install -DskipTests                         # from the repository root
./mvnw -f samples/spring-boot-sample/pom.xml spring-boot:run
```

The log prints a seeded customer's id to try:

```shell
curl localhost:8080/customers/cus_NCkXyXgbS9eGyNXeSI2hCA
# {"id":"cus_NCkXyXgbS9eGyNXeSI2hCA","name":"Ada Lovelace"}

curl localhost:8080/customers/cus_NCkXyXgbS9eGyNXeSI2hCA/invoices
# [{"id":"inv_SUTPHPemSKyjCtnEqRhttw","amount":42.00}]

curl -X POST -H 'Content-Type: application/json' -d '{"name":"Grace Hopper"}' localhost:8080/customers
# {"id":"cus_MlQNQqPYRQGKzZp0C27nAw","name":"Grace Hopper"}

curl -i localhost:8080/customers/inv_NCkXyXgbS9eGyNXeSI2hCA
# 400: an invoice id is never accepted as a customer id, even with the same UUID behind it

curl -i localhost:8080/customers/cus_AAAAAAAAAAAAAAAAAAAAAA
# 404: a well-formed customer id, but no such customer
```

## Where to look

Sources are in `com.mjukvare.publicids.sample`, organised by layer.

| What                                    | Where                                                                   |
|-----------------------------------------|-------------------------------------------------------------------------|
| Give your own type a prefix             | `@PublicIdPrefix("cus")` on `domain.Customer`                           |
| Give a type you can't annotate a prefix | `@PublicIdRegistration` on `configuration.ThirdPartyPublicIds`          |
| Parse and encode in Spring beans        | Inject `PublicIdCodec`, as `web.CustomerController` does                |
| Turn a public id into a UUID            | `publicIds.parseOrThrow(...)` in `web.CustomerController.find`          |
| Answer a bad id with 400 Bad Request    | `InvalidPublicIdException` handler in `web.ApiExceptionHandler`         |
| Encode where there is no injection      | `PublicIds.of(...)` in `domain.Customer.publicId()`                     |
| Settings, with their defaults           | `application.properties`                                                |
| Test a controller with `@WebMvcTest`    | `web.CustomerControllerWebMvcTest`, which needs no PublicIds setup      |

There is no code that builds or installs the codec: the starter builds it from the `@PublicIdPrefix` types and
`@PublicIdRegistration` declarations in the application's packages, and installs it behind `PublicIds` when the
application starts.

Conversion is explicit: controllers take public ids as `String`s and call `parseOrThrow`, and response records carry
`String` ids made with `of`. The library doesn't pick status codes; the application maps `InvalidPublicIdException`
once, in `ApiExceptionHandler`.

For registrations that need code, the starter also applies any `PublicIdCodecCustomizer` beans. Those are beans,
though, so a test slice such as `@WebMvcTest` only sees them if it `@Import`s the configuration declaring them;
otherwise the slice builds a different codec. Prefer `@PublicIdRegistration` for fixed prefixes.
