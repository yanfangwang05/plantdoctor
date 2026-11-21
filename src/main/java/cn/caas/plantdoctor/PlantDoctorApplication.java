package cn.caas.plantdoctor;

import cn.caas.plantdoctor.config.PlantDoctorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * PlantDoctor - knowledge-enhanced diagnostic tool for crop pests and diseases.
 *
 * <p>The application starts in {@code mock} recognition mode by default, so that
 * the complete diagnostic workflow can be reproduced without access to the
 * commercial recognition services. Set
 * {@code plantdoctor.recognition.mode=remote} (or the profile {@code prod}) to
 * call the real services.
 */
@SpringBootApplication
@EnableConfigurationProperties(PlantDoctorProperties.class)
public class PlantDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlantDoctorApplication.class, args);
    }
}
