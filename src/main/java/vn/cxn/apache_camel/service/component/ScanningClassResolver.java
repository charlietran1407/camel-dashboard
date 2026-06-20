package vn.cxn.apache_camel.service.component;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom {@link ClassResolver} delegate wrapper used during component scanning to intercept
 * unresolved Camel components, dataformats, or EIP classes and collect their names, returning a
 * dummy class definition to avoid ClassNotFoundException failures and context startup crashes.
 */
public class ScanningClassResolver implements ClassResolver {

    private static final Logger log = LoggerFactory.getLogger(ScanningClassResolver.class);

    private final ClassResolver delegate;
    private final Set<String> schemes;
    private final Set<String> dataFormats;

    public ScanningClassResolver(
            ClassResolver delegate, Set<String> schemes, Set<String> dataFormats) {
        this.delegate = delegate;
        this.schemes = schemes;
        this.dataFormats = dataFormats;
    }

    /** Dummy {@link DataFormat} implementation returned when a dataformat class is missing. */
    public static class DummyDataFormat
            implements DataFormat, org.apache.camel.spi.PropertyConfigurerAware {
        @Override
        public void marshal(Exchange exchange, Object graph, java.io.OutputStream stream) {}

        @Override
        public Object unmarshal(Exchange exchange, java.io.InputStream stream) {
            return null;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public org.apache.camel.spi.PropertyConfigurer getPropertyConfigurer(Object instance) {
            return (org.apache.camel.CamelContext context,
                    Object target,
                    String name,
                    Object value,
                    boolean ignoreCase) -> true;
        }
    }

    /**
     * Dummy {@link org.apache.camel.Component} implementation returned when a component class is
     * missing.
     */
    public static class DummyComponent extends DefaultComponent {
        public DummyComponent() {
            super();
        }

        public DummyComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(
                String uri, String remaining, Map<String, Object> parameters) {
            return null;
        }
    }

    @Override
    public Class<?> resolveClass(String name) {
        interceptClassName(name);
        try {
            Class<?> resolved = delegate.resolveClass(name);
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception e) {
            log.trace("Delegate failed to resolve class '{}': {}", name, e.getMessage());
        }
        return handleMissingClass(name, Object.class);
    }

    @Override
    public <T> Class<T> resolveClass(String name, Class<T> type) {
        interceptClassName(name);
        try {
            Class<T> resolved = delegate.resolveClass(name, type);
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception e) {
            log.trace(
                    "Delegate failed to resolve class '{}' as '{}': {}",
                    name,
                    type.getName(),
                    e.getMessage());
        }
        return handleMissingClass(name, type);
    }

    @Override
    public Class<?> resolveClass(String name, ClassLoader classLoader) {
        interceptClassName(name);
        try {
            Class<?> resolved = delegate.resolveClass(name, classLoader);
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception e) {
            log.trace(
                    "Delegate failed to resolve class '{}' with classloader: {}",
                    name,
                    e.getMessage());
        }
        return handleMissingClass(name, Object.class);
    }

    @Override
    public <T> Class<T> resolveClass(String name, Class<T> type, ClassLoader classLoader) {
        interceptClassName(name);
        try {
            Class<T> resolved = delegate.resolveClass(name, type, classLoader);
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception e) {
            log.trace(
                    "Delegate failed to resolve class '{}' as '{}' with classloader: {}",
                    name,
                    type.getName(),
                    e.getMessage());
        }
        return handleMissingClass(name, type);
    }

    @Override
    public Class<?> resolveMandatoryClass(String name) throws ClassNotFoundException {
        interceptClassName(name);
        try {
            return delegate.resolveMandatoryClass(name);
        } catch (ClassNotFoundException e) {
            Class<?> resolved = handleMissingClass(name, Object.class);
            if (resolved != null) {
                return resolved;
            }
            throw e;
        }
    }

    @Override
    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type)
            throws ClassNotFoundException {
        interceptClassName(name);
        try {
            return delegate.resolveMandatoryClass(name, type);
        } catch (ClassNotFoundException e) {
            Class<T> resolved = handleMissingClass(name, type);
            if (resolved != null) {
                return resolved;
            }
            throw e;
        }
    }

    @Override
    public Class<?> resolveMandatoryClass(String name, ClassLoader classLoader)
            throws ClassNotFoundException {
        interceptClassName(name);
        try {
            return delegate.resolveMandatoryClass(name, classLoader);
        } catch (ClassNotFoundException e) {
            Class<?> resolved = handleMissingClass(name, Object.class);
            if (resolved != null) {
                return resolved;
            }
            throw e;
        }
    }

    @Override
    public <T> Class<T> resolveMandatoryClass(String name, Class<T> type, ClassLoader classLoader)
            throws ClassNotFoundException {
        interceptClassName(name);
        try {
            return delegate.resolveMandatoryClass(name, type, classLoader);
        } catch (ClassNotFoundException e) {
            Class<T> resolved = handleMissingClass(name, type);
            if (resolved != null) {
                return resolved;
            }
            throw e;
        }
    }

    private void interceptClassName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (name.startsWith("org.apache.camel.dataformat.")) {
            extractDataFormatName(name);
        } else if (name.startsWith("org.apache.camel.component.")) {
            extractComponentName(name);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> handleMissingClass(String name, Class<T> type) {
        if (name == null || name.isBlank()) {
            return null;
        }

        log.debug("ScanningClassResolver: Intercepting unresolved class: {}", name);

        // Intercept dataformats
        if (name.startsWith("org.apache.camel.dataformat.")) {
            log.info("ScanningClassResolver: Returning DummyDataFormat.class for: {}", name);
            return (Class<T>) DummyDataFormat.class;
        }

        // Intercept components
        if (name.startsWith("org.apache.camel.component.")) {
            log.info("ScanningClassResolver: Returning DummyComponent.class for: {}", name);
            return (Class<T>) DummyComponent.class;
        }

        // Fallback: If type specifies a known Camel SPI, we can still return a mock implementation
        // to satisfy the compiler/runtime cast.
        if (type != null && type != Object.class) {
            if (type.isAssignableFrom(DummyDataFormat.class)) {
                return (Class<T>) DummyDataFormat.class;
            }
            if (type.isAssignableFrom(DummyComponent.class)) {
                return (Class<T>) DummyComponent.class;
            }
        }

        return null;
    }

    private void extractDataFormatName(String className) {
        // e.g., org.apache.camel.dataformat.barcode.BarcodeDataFormat
        String relative = className.substring("org.apache.camel.dataformat.".length());
        int dotIdx = relative.indexOf('.');
        if (dotIdx > 0) {
            String segment = relative.substring(0, dotIdx).trim();
            if (!segment.isEmpty()) {
                dataFormats.add(segment);
                log.info(
                        "ScanningClassResolver: Extracted dataformat from package: {} -> {}",
                        className,
                        segment);
            }
        }

        // Also extract based on Camel naming conventions (class simple name suffix: "DataFormat")
        int lastDotIdx = className.lastIndexOf('.');
        if (lastDotIdx > 0) {
            String simpleName = className.substring(lastDotIdx + 1);
            if (simpleName.endsWith("DataFormat")) {
                String prefix =
                        simpleName
                                .substring(0, simpleName.length() - "DataFormat".length())
                                .toLowerCase();
                if (!prefix.isEmpty()) {
                    dataFormats.add(prefix);
                    log.info(
                            "ScanningClassResolver: Extracted dataformat from class prefix: {} ->"
                                    + " {}",
                            className,
                            prefix);
                }
            }
        }
    }

    private void extractComponentName(String className) {
        // e.g., org.apache.camel.component.caffeine.cache.CaffeineCacheComponent
        String relative = className.substring("org.apache.camel.component.".length());
        int dotIdx = relative.indexOf('.');
        if (dotIdx > 0) {
            String segment = relative.substring(0, dotIdx).trim();
            if (!segment.isEmpty()) {
                schemes.add(segment);
                log.info(
                        "ScanningClassResolver: Extracted component scheme from package: {} -> {}",
                        className,
                        segment);
            }
        }
    }

    @Override
    public void addClassLoader(ClassLoader classLoader) {
        delegate.addClassLoader(classLoader);
    }

    @Override
    public InputStream loadResourceAsStream(String name) {
        return delegate.loadResourceAsStream(name);
    }

    @Override
    public URL loadResourceAsURL(String name) {
        return delegate.loadResourceAsURL(name);
    }

    @Override
    public Enumeration<URL> loadAllResourcesAsURL(String name) {
        return delegate.loadAllResourcesAsURL(name);
    }

    @Override
    public Enumeration<URL> loadResourcesAsURL(String name) {
        return delegate.loadResourcesAsURL(name);
    }

    @Override
    public ClassLoader getClassLoader(String name) {
        return delegate.getClassLoader(name);
    }

    @Override
    public Set<ClassLoader> getClassLoaders() {
        return delegate.getClassLoaders();
    }
}
