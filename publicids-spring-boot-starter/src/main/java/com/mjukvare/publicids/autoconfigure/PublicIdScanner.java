package com.mjukvare.publicids.autoconfigure;

import com.mjukvare.publicids.PublicIdCodec;
import com.mjukvare.publicids.PublicIdPrefix;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Finds the types in the given packages that are directly annotated with {@link PublicIdPrefix}, and the
 * {@link PublicIdRegistration} declarations made there.
 */
final class PublicIdScanner {

    record Result(List<Class<?>> annotatedTypes, List<Registration> registrations) {}

    record Registration(Class<?> type, String prefix, Class<?> declaredOn) {
        void applyTo(PublicIdCodec.Builder builder) {
            try {
                builder.register(type, prefix);
            } catch (IllegalArgumentException e) {
                // Not chained: startup failures report the root cause, which must be the message naming the class
                throw new IllegalArgumentException(
                        e.getMessage() + " (declared by @PublicIdRegistration on " + declaredOn.getName() + ")");
            }
        }
    }

    private final ClassPathScanningCandidateComponentProvider provider;
    private final ResourceLoader resourceLoader;

    PublicIdScanner(Environment environment, ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.provider = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // The default accepts only concrete, top-level classes; annotated abstract base classes count too
                return true;
            }
        };
        provider.setResourceLoader(resourceLoader);
        // Directly present only. Subclasses of annotated types resolve through their superclass at runtime.
        provider.addIncludeFilter(directlyAnnotatedWith(PublicIdPrefix.class));
        provider.addIncludeFilter(directlyAnnotatedWith(PublicIdRegistration.class));
        provider.addIncludeFilter(directlyAnnotatedWith(PublicIdRegistration.List.class));
    }

    Result scan(Collection<String> packages) {
        List<Class<?>> types = packages.stream()
                .flatMap(basePackage -> provider.findCandidateComponents(basePackage).stream())
                .map(candidate -> Objects.requireNonNull(candidate.getBeanClassName()))
                .distinct()
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, resourceLoader.getClassLoader()))
                .toList();

        var annotatedTypes = types.stream()
                .filter(type -> type.getDeclaredAnnotation(PublicIdPrefix.class) != null)
                .toList();
        var registrations = types.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredAnnotationsByType(PublicIdRegistration.class))
                        .map(registration -> new Registration(registration.type(), registration.prefix(), type)))
                .toList();

        return new Result(annotatedTypes, registrations);
    }

    private static TypeFilter directlyAnnotatedWith(Class<? extends Annotation> annotation) {
        return (reader, factory) -> reader.getAnnotationMetadata().hasAnnotation(annotation.getName());
    }
}
