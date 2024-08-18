package se.magnus.microservices.core.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.test.StepVerifier;
import se.magnus.microservices.core.product.persistence.ProductEntity;
import se.magnus.microservices.core.product.persistence.ProductRepository;

@DataMongoTest
class PersistenceTests extends MongoDbTestBase {

  @Autowired private ProductRepository repository;

  private ProductEntity savedEntity;

  @BeforeEach
  void setupDb() {
    StepVerifier.create(repository.deleteAll()).verifyComplete();

    ProductEntity entity = new ProductEntity(1, "n", 1);
    StepVerifier.create(repository.save(entity))
        .expectNextMatches(
            createdEntity -> {
              savedEntity = createdEntity;
              return areProductEqual(entity, createdEntity);
            })
        .verifyComplete();
  }

  @Test
  void create() {

    ProductEntity newEntity = new ProductEntity(2, "n", 2);
    StepVerifier.create(repository.save(newEntity))
        .expectNextMatches(newEntity::equals)
        .verifyComplete();

    StepVerifier.create(repository.findById(newEntity.getId()))
        .expectNextMatches(foundEntity -> areProductEqual(newEntity, foundEntity))
        .verifyComplete();

    StepVerifier.create(repository.count()).expectNext(2L).verifyComplete();
  }

  @Test
  void update() {
    savedEntity.setName("n2");
    assert savedEntity.getVersion() == 0;
    StepVerifier.create(repository.save(savedEntity))
        .expectNextMatches(savedEntity::equals)
        .verifyComplete();

    StepVerifier.create(repository.findById(savedEntity.getId()))
        .expectNextMatches(
            foundEntity -> foundEntity.getName().equals("n2") && foundEntity.getVersion() == 1)
        .verifyComplete();
  }

  @Test
  void delete() {
    StepVerifier.create(repository.delete(savedEntity)).verifyComplete();
    StepVerifier.create(repository.count()).expectNext(0L).verifyComplete();
  }

  @Test
  void getByProductId() {
    StepVerifier.create(repository.findByProductId(savedEntity.getProductId()))
        .expectNextMatches(foundEntity -> areProductEqual(savedEntity, foundEntity))
        .verifyComplete();
  }

  @Test
  void duplicateError() {
    ProductEntity entity = new ProductEntity(savedEntity.getProductId(), "n", 1);
    StepVerifier.create(repository.save(entity)).expectError(DuplicateKeyException.class).verify();
  }

  @Test
  void optimisticLockError() {

    // Store the saved entity in two separate entity objects
    assert savedEntity.getVersion() == 0;
    ProductEntity entity1 = repository.findById(savedEntity.getId()).block();
    ProductEntity entity2 = repository.findById(savedEntity.getId()).block();
    assert entity1 != null;
    assert entity2 != null;

    // Update the entity using the first entity object
    entity1.setName("n1");
    StepVerifier.create(repository.save(entity1))
        .expectNextMatches(entity1::equals)
        .verifyComplete();

    // Update the entity using the second entity object.
    // This should fail since the second entity now holds an old version number, i.e. an Optimistic
    // Lock Error
    StepVerifier.create(repository.save(entity2))
        .expectError(OptimisticLockingFailureException.class)
        .verify();

    // Get the updated entity from the database and verify its new sate
    StepVerifier.create(repository.findById(savedEntity.getId()))
        .expectNextMatches(
            updatedEntity ->
                updatedEntity.getVersion() == 1 && updatedEntity.getName().equals("n1"))
        .verifyComplete();
  }

  private boolean areProductEqual(ProductEntity expectedEntity, ProductEntity actualEntity) {
    return (expectedEntity.getId().equals(actualEntity.getId()))
        && (expectedEntity.getVersion() == actualEntity.getVersion())
        && (expectedEntity.getProductId() == actualEntity.getProductId())
        && (expectedEntity.getName().equals(actualEntity.getName()))
        && (expectedEntity.getWeight() == actualEntity.getWeight());
  }
}
