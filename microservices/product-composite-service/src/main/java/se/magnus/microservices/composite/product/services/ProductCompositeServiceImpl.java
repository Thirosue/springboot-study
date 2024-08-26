package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.FINE;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import se.magnus.api.composite.product.*;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.review.Review;
import se.magnus.util.http.ServiceUtil;

@RestController
public class ProductCompositeServiceImpl implements ProductCompositeService {
  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeServiceImpl.class);

  private final ServiceUtil serviceUtil;
  private ProductCompositeIntegration integration;

  @Autowired
  public ProductCompositeServiceImpl(
      ServiceUtil serviceUtil, ProductCompositeIntegration integration) {
    this.serviceUtil = serviceUtil;
    this.integration = integration;
  }

  // 共通化されたエンティティの作成とMonoリストへの追加メソッド
  private <T, U> Mono<Void> createEntitiesMono(
      List<T> entities, Function<T, U> mapper, Function<U, Mono<Void>> monoCreator) {
    if (entities == null || entities.isEmpty()) {
      return Mono.empty();
    }

    List<Mono<Void>> monoList =
        entities.stream()
            .map(entity -> monoCreator.apply(mapper.apply(entity)))
            .collect(Collectors.toList());

    return Mono.when(monoList);
  }

  @Override
  public Mono<Void> createProduct(ProductAggregate body) {
    try {
      LOG.info("Will create a new composite entity for product.id: {}", body.getProductId());

      Mono<Void> productMono =
          integration
              .createProduct(
                  new Product(body.getProductId(), body.getName(), body.getWeight(), null))
              .then();

      Mono<Void> recommendationsMono =
          createEntitiesMono(
              body.getRecommendations(),
              r ->
                  new Recommendation(
                      body.getProductId(),
                      r.getRecommendationId(),
                      r.getAuthor(),
                      r.getRate(),
                      r.getContent(),
                      null),
              recommendation -> integration.createRecommendation(recommendation).then());

      Mono<Void> reviewsMono =
          createEntitiesMono(
              body.getReviews(),
              r ->
                  new Review(
                      body.getProductId(),
                      r.getReviewId(),
                      r.getAuthor(),
                      r.getSubject(),
                      r.getContent(),
                      null),
              review -> integration.createReview(review).then());

      return Mono.zip(productMono, recommendationsMono, reviewsMono)
          .doOnError(ex -> LOG.warn("product create failed: {}", ex.toString()))
          .then();

    } catch (RuntimeException re) {
      LOG.warn("createCompositeProduct failed: {}", re.toString());
      throw re;
    }
  }

  @Override
  public Mono<ProductAggregate> getProduct(int productId) {
    return Mono.zip(
            integration.getProduct(productId),
            integration.getRecommendations(productId).collectList(),
            integration.getReviews(productId).collectList())
        .map(
            tuple ->
                createProductAggregate(
                    tuple.getT1(), tuple.getT2(), tuple.getT3(), serviceUtil.getServiceAddress()))
        .doOnError(ex -> LOG.warn("product get failed: {}", ex.toString()))
        .log(LOG.getName(), FINE);
  }

  @Override
  public Mono<Void> deleteProduct(int productId) {
    try {
      LOG.info("Will delete a product aggregate for product.id: {}", productId);

      return Mono.zip(
              integration.deleteProduct(productId),
              integration.deleteRecommendations(productId),
              integration.deleteReviews(productId))
          .doOnError(ex -> LOG.warn("product delete failed: {}", ex.toString()))
          .log(LOG.getName(), FINE)
          .then();
    } catch (RuntimeException re) {
      LOG.warn("deleteCompositeProduct failed: {}", re.toString());
      throw re;
    }
  }

  private ProductAggregate createProductAggregate(
      Product product,
      List<Recommendation> recommendations,
      List<Review> reviews,
      String serviceAddress) {

    // 1. Setup product info
    int productId = product.getProductId();
    String name = product.getName();
    int weight = product.getWeight();

    // 2. Copy summary recommendation info, if available
    List<RecommendationSummary> recommendationSummaries =
        (recommendations == null)
            ? null
            : recommendations.stream()
                .map(
                    r ->
                        new RecommendationSummary(
                            r.getRecommendationId(), r.getAuthor(), r.getRate(), r.getContent()))
                .collect(Collectors.toList());

    // 3. Copy summary review info, if available
    List<ReviewSummary> reviewSummaries =
        (reviews == null)
            ? null
            : reviews.stream()
                .map(
                    r ->
                        new ReviewSummary(
                            r.getReviewId(), r.getAuthor(), r.getSubject(), r.getContent()))
                .collect(Collectors.toList());

    // 4. Create info regarding the involved microservices addresses
    String productAddress = product.getServiceAddress();
    String reviewAddress =
        (reviews != null && !reviews.isEmpty()) ? reviews.get(0).getServiceAddress() : "";
    String recommendationAddress =
        (recommendations != null && !recommendations.isEmpty())
            ? recommendations.get(0).getServiceAddress()
            : "";
    ServiceAddresses serviceAddresses =
        new ServiceAddresses(serviceAddress, productAddress, reviewAddress, recommendationAddress);

    return new ProductAggregate(
        productId, name, weight, recommendationSummaries, reviewSummaries, serviceAddresses);
  }
}
