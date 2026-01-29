package org.neurotecfinger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve files placed in src/main/resources/images via /images/** URL
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/images/");
    }
}

