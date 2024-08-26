package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.FINE;
import static reactor.core.publisher.Flux.empty;
import static reactor.core.publisher.Mono.just;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.recommendation.RecommendationService;
import se.magnus.api.core.review.Review;
import se.magnus.api.core.review.ReviewService;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.util.http.HttpErrorInfo;

@Component
public class ProductCompositeIntegration
    implements ProductService, ReviewService, RecommendationService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

  private final WebClient webClient;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper;
  private final String productServiceUrl;
  private final String recommendationServiceUrl;
  private final String reviewServiceUrl;

  @Autowired
  public ProductCompositeIntegration(
      WebClient.Builder webClient,
      RestTemplate restTemplate,
      ObjectMapper mapper,
      @Value("${app.product-service.host}") String productServiceHost,
      @Value("${app.product-service.port}") int productServicePort,
      @Value("${app.recommendation-service.host}") String recommendationServiceHost,
      @Value("${app.recommendation-service.port}") int recommendationServicePort,
      @Value("${app.review-service.host}") String reviewServiceHost,
      @Value("${app.review-service.port}") int reviewServicePort) {
    this.webClient = webClient.build();
    this.restTemplate = restTemplate;
    this.mapper = mapper;
    productServiceUrl = "http://" + productServiceHost + ":" + productServicePort + "/product";
    recommendationServiceUrl =
        "http://" + recommendationServiceHost + ":" + recommendationServicePort + "/recommendation";
    reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort + "/review";
  }

  @Override
  public Mono<Product> createProduct(Product body) {

    return webClient
        .post()
        .uri(productServiceUrl)
        .body(just(body), Product.class)
        .retrieve()
        .bodyToMono(Product.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Mono<Product> getProduct(int productId) {
    String url = productServiceUrl + "/" + productId;
    LOG.debug("Will call the getProduct API on URL: {}", url);

    return webClient
        .get()
        .uri(url)
        .retrieve()
        .bodyToMono(Product.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Mono<Void> deleteProduct(int productId) {
    return webClient
        .delete()
        .uri(productServiceUrl + "/" + productId)
        .retrieve()
        .bodyToMono(Void.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Mono<Recommendation> createRecommendation(Recommendation body) {
    return webClient
        .post()
        .uri(recommendationServiceUrl)
        .body(just(body), Recommendation.class)
        .retrieve()
        .bodyToMono(Recommendation.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Flux<Recommendation> getRecommendations(int productId) {
    String url = recommendationServiceUrl + "?productId=" + productId;
    LOG.debug("Will call the getRecommendations API on URL: {}", url);

    return webClient
        .get()
        .uri(url)
        .retrieve()
        .bodyToFlux(Recommendation.class)
        .log(LOG.getName(), FINE)
        .onErrorResume(error -> empty());
  }

  @Override
  public Mono<Void> deleteRecommendations(int productId) {
    return webClient
        .delete()
        .uri(recommendationServiceUrl + "?productId=" + productId)
        .retrieve()
        .bodyToMono(Void.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Mono<Review> createReview(Review body) {
    return webClient
        .post()
        .uri(reviewServiceUrl)
        .body(just(body), Review.class)
        .retrieve()
        .bodyToMono(Review.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  @Override
  public Flux<Review> getReviews(int productId) {
    String url = reviewServiceUrl + "?productId=" + productId;
    LOG.debug("Will call the getReviews API on URL: {}", url);

    return webClient
        .get()
        .uri(url)
        .retrieve()
        .bodyToFlux(Review.class)
        .log(LOG.getName(), FINE)
        .onErrorResume(error -> empty());
  }

  @Override
  public Mono<Void> deleteReviews(int productId) {
    return webClient
        .delete()
        .uri(reviewServiceUrl + "?productId=" + productId)
        .retrieve()
        .bodyToMono(Void.class)
        .log(LOG.getName(), FINE)
        .onErrorMap(WebClientResponseException.class, this::handleException);
  }

  private Throwable handleException(Throwable ex) {
    if (!(ex instanceof WebClientResponseException wcre)) {
      LOG.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
      return ex;
    }

    switch (Objects.requireNonNull(HttpStatus.resolve(wcre.getStatusCode().value()))) {
      case NOT_FOUND:
        return new NotFoundException(getErrorMessage(wcre));

      case UNPROCESSABLE_ENTITY:
        return new InvalidInputException(getErrorMessage(wcre));

      default:
        LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
        LOG.warn("Error body: {}", wcre.getResponseBodyAsString());
        return ex;
    }
  }

  private String getErrorMessage(WebClientResponseException ex) {
    try {
      return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
    } catch (IOException ioex) {
      return ex.getMessage();
    }
  }
}
