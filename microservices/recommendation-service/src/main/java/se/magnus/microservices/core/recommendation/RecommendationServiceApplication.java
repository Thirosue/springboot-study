package se.magnus.microservices.core.recommendation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("se.magnus")
public class RecommendationServiceApplication {
  private static final Logger LOG = LoggerFactory.getLogger(RecommendationServiceApplication.class);

  @Bean
  public OpenAPI getOpenApiDocumentation() {
    return new OpenAPI().info(new Info().title("recommendation").version("v1"));
  }

  public static void main(String[] args) {
    ConfigurableApplicationContext ctx =
        SpringApplication.run(RecommendationServiceApplication.class, args);

    String mongodDbHost = ctx.getEnvironment().getProperty("spring.data.mongodb.host");
    String mongodDbPort = ctx.getEnvironment().getProperty("spring.data.mongodb.port");
    LOG.info("Connected to MongoDb: " + mongodDbHost + ":" + mongodDbPort);
  }
}
