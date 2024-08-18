package se.magnus.microservices.composite.product;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan("se.magnus")
public class ProductCompositeServiceApplication {

  @Value("${api.common.version}")
  String apiVersion;

  @Value("${api.common.title}")
  String apiTitle;

  /**
   * Will exposed on $HOST:$PORT/swagger-ui.html
   *
   * @return the common OpenAPI documentation
   */
  @Bean
  public OpenAPI getOpenApiDocumentation() {
    return new OpenAPI().info(new Info().title(apiTitle).version(apiVersion));
  }

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplate();
  }

  public static void main(String[] args) {
    SpringApplication.run(ProductCompositeServiceApplication.class, args);
  }
}
