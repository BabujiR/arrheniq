package com.arrheniq.kernel.security;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * Spring Session JDBC stores attributes through the conversion service with this bean name; ours writes JSON
 * ({@link SessionJson}) instead of Java serialization.
 */
@Configuration(proxyBeanMethods = false)
class SessionJsonConfig implements BeanClassLoaderAware {

    private ClassLoader classLoader = SessionJsonConfig.class.getClassLoader();

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean("springSessionConversionService")
    GenericConversionService springSessionConversionService() {
        SessionJson json = new SessionJson(classLoader);
        GenericConversionService conversion = new GenericConversionService();
        conversion.addConverter(Object.class, byte[].class, json::write);
        conversion.addConverter(byte[].class, Object.class, json::read);
        return conversion;
    }
}
