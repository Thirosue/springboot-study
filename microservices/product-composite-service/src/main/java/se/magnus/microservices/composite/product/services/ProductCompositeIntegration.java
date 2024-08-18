package se.magnus.microservices.composite.product.services;

import static org.springframework.http.HttpMethod.GET;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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

  private final RestTemplate restTemplate;
  private final ObjectMapper mapper;
  private final String productServiceUrl;
  private final String recommendationServiceUrl;
  private final String reviewServiceUrl;

  @Autowired
  public ProductCompositeIntegration(
      RestTemplate restTemplate,
      ObjectMapper mapper,
      @Value("${app.product-service.host}") String productServiceHost,
      @Value("${app.product-service.port}") int productServicePort,
      @Value("${app.recommendation-service.host}") String recommendationServiceHost,
      @Value("${app.recommendation-service.port}") int recommendationServicePort,
      @Value("${app.review-service.host}") String reviewServiceHost,
      @Value("${app.review-service.port}") int reviewServicePort) {
    this.restTemplate = restTemplate;
    this.mapper = mapper;
    productServiceUrl = "http://" + productServiceHost + ":" + productServicePort + "/product";
    recommendationServiceUrl =
        "http://" + recommendationServiceHost + ":" + recommendationServicePort + "/recommendation";
    reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort + "/review";
  }

  @Override
  public Product createProduct(Product body) {
    try {
      return restTemplate.postForObject(productServiceUrl, body, Product.class);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public Product getProduct(int productId) {
    try {
      String url = productServiceUrl + "/" + productId;
      LOG.debug("Will call getProduct API on URL: {}", url);

      Product product = restTemplate.getForObject(url, Product.class);
      assert product != null;
      LOG.debug("Found a product with id: {}", product.getProductId());

      return product;

    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public void deleteProduct(int productId) {
    try {
      restTemplate.delete(productServiceUrl + "/" + productId);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public Recommendation createRecommendation(Recommendation body) {
    try {
      return restTemplate.postForObject(recommendationServiceUrl, body, Recommendation.class);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public List<Recommendation> getRecommendations(int productId) {
    try {
      String url = recommendationServiceUrl + "?productId=" + productId;

      LOG.debug("Will call getRecommendations API on URL: {}", url);
      List<Recommendation> recommendations =
          restTemplate
              .exchange(url, GET, null, new ParameterizedTypeReference<List<Recommendation>>() {})
              .getBody();

      LOG.debug(
          "Found {} recommendations for a product with id: {}", recommendations.size(), productId);
      return recommendations;

    } catch (Exception ex) {
      LOG.warn(
          "Got an exception while requesting recommendations, return zero recommendations: {}",
          ex.getMessage());
      return new ArrayList<>();
    }
  }

  @Override
  public void deleteRecommendations(int productId) {
    try {
      restTemplate.delete(recommendationServiceUrl + "/" + productId);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public Review createReview(Review body) {
    try {
      return restTemplate.postForObject(reviewServiceUrl, body, Review.class);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  @Override
  public List<Review> getReviews(int productId) {
    try {
      String url = reviewServiceUrl + "?productId=" + productId;

      LOG.debug("Will call getReviews API on URL: {}", url);
      List<Review> reviews =
          restTemplate
              .exchange(url, GET, null, new ParameterizedTypeReference<List<Review>>() {})
              .getBody();

      LOG.debug("Found {} reviews for a product with id: {}", reviews.size(), productId);
      return reviews;
    } catch (Exception ex) {
      LOG.warn(
          "Got an exception while requesting reviews, return zero reviews: {}", ex.getMessage());
      return new ArrayList<>();
    }
  }

  @Override
  public void deleteReviews(int productId) {
    try {
      restTemplate.delete(reviewServiceUrl + "/" + productId);
    } catch (HttpClientErrorException ex) {
      throw handleHttpClientException(ex);
    }
  }

  private RuntimeException handleHttpClientException(HttpClientErrorException ex) {
    switch (Objects.requireNonNull(HttpStatus.resolve(ex.getStatusCode().value()))) {
      case NOT_FOUND:
        return new NotFoundException(getErrorMessage(ex));

      case UNPROCESSABLE_ENTITY:
        return new InvalidInputException(getErrorMessage(ex));

      default:
        LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
        LOG.warn("Error body: {}", ex.getResponseBodyAsString());
        return ex;
    }
  }

  private String getErrorMessage(HttpClientErrorException ex) {
    try {
      return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
    } catch (IOException ioex) {
      return ex.getMessage();
    }
  }
}
