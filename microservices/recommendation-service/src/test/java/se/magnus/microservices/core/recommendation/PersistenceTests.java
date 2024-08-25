package se.magnus.microservices.core.recommendation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import se.magnus.microservices.core.recommendation.persistence.RecommendationEntity;
import se.magnus.microservices.core.recommendation.persistence.RecommendationRepository;

@DataMongoTest
class PersistenceTests extends MongoDbTestBase {

  @Autowired private RecommendationRepository repository;

  private RecommendationEntity savedEntity;

  @BeforeEach
  void setupDb() {
    repository.deleteAll().block();

    RecommendationEntity entity = new RecommendationEntity(1, 2, "a", 3, "c");
    savedEntity = repository.save(entity).block();

    assert savedEntity != null;
    assertEqualsRecommendation(entity, savedEntity);
  }

  @Test
  void create() {

    RecommendationEntity newEntity = new RecommendationEntity(1, 3, "a", 3, "c");
    repository.save(newEntity).block();

    RecommendationEntity foundEntity = repository.findById(newEntity.getId()).block();

    assert foundEntity != null;
    assertEqualsRecommendation(newEntity, foundEntity);

    assertEquals(2, repository.count().block());
  }

  @Test
  void update() {
    savedEntity.setAuthor("a2");
    repository.save(savedEntity).block();

    RecommendationEntity foundEntity = repository.findById(savedEntity.getId()).block();
    assert foundEntity != null;
    assertEquals(1, (long) foundEntity.getVersion());
    assertEquals("a2", foundEntity.getAuthor());
  }

  @Test
  void delete() {
    repository.delete(savedEntity).block();
    assert Boolean.FALSE.equals(repository.existsById(savedEntity.getId()).block());
  }

  @Test
  void getByProductId() {
    List<RecommendationEntity> entityList =
        repository.findByProductId(savedEntity.getProductId()).toStream().toList();

    assertThat(entityList, hasSize(1));
    assertEqualsRecommendation(savedEntity, entityList.get(0));
  }

  @Test
  void duplicateError() {
    assertThrows(
        DuplicateKeyException.class,
        () -> {
          RecommendationEntity entity = new RecommendationEntity(1, 2, "a", 3, "c");
          repository.save(entity).block();
        });
  }

  @Test
  void optimisticLockError() {
    // Store the saved entity in two separate entity objects
    RecommendationEntity entity1 = repository.findById(savedEntity.getId()).block();
    RecommendationEntity entity2 = repository.findById(savedEntity.getId()).block();
    assert entity1 != null;
    assert entity2 != null;
    // because we are using the same entity object, the version number is the same
    assertEquals(0, (int) savedEntity.getVersion());
    assertEquals("a", savedEntity.getAuthor());

    // Update the entity using the first entity object
    entity1.setAuthor("a1");
    repository.save(entity1).block();

    //  Update the entity using the second entity object.
    // This should fail since the second entity now holds an old version number, i.e. an Optimistic
    // Lock Error
    assertThrows(
        OptimisticLockingFailureException.class,
        () -> {
          entity2.setAuthor("a2");
          repository.save(entity2).block();
        });

    // Get the updated entity from the database and verify its new sate
    RecommendationEntity updatedEntity = repository.findById(savedEntity.getId()).block();
    assert updatedEntity != null;
    assertEquals(1, (int) updatedEntity.getVersion());
    assertEquals("a1", updatedEntity.getAuthor());
  }

  private void assertEqualsRecommendation(
      RecommendationEntity expectedEntity, RecommendationEntity actualEntity) {
    assertEquals(expectedEntity.getId(), actualEntity.getId());
    assertEquals(expectedEntity.getVersion(), actualEntity.getVersion());
    assertEquals(expectedEntity.getProductId(), actualEntity.getProductId());
    assertEquals(expectedEntity.getRecommendationId(), actualEntity.getRecommendationId());
    assertEquals(expectedEntity.getAuthor(), actualEntity.getAuthor());
    assertEquals(expectedEntity.getRating(), actualEntity.getRating());
    assertEquals(expectedEntity.getContent(), actualEntity.getContent());
  }
}
