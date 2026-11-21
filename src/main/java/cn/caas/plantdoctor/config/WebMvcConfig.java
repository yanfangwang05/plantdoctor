package cn.caas.plantdoctor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves uploaded images back over {@code /uploads/**}.
 *
 * <p>Needed because the general recognition contract fetches the image by URL
 * rather than receiving the bytes. In a public deployment the whole application
 * sits behind a reverse proxy and {@code plantdoctor.public-base-url} points at
 * it; locally the same path is served by the embedded Tomcat.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PlantDoctorProperties properties;

    public WebMvcConfig(PlantDoctorProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.getUploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
