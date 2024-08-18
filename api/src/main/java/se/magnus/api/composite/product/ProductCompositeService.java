package se.magnus.api.composite.product;

import org.springframework.web.bind.annotation.*;

public interface ProductCompositeService {

  @PostMapping(value = "/product-composite", consumes = "application/json")
  void createProduct(@RequestBody ProductAggregate body);

  /**
   * Sample usage: "curl $HOST:$PORT/product-composite/1".
   *
   * @param productId Id of the product
   * @return the composite product info, if found, else null
   */
  @GetMapping(value = "/product-composite/{productId}", produces = "application/json")
  ProductAggregate getProduct(@PathVariable int productId);

  @DeleteMapping(value = "/product-composite/{productId}")
  void deleteProduct(@PathVariable int productId);
}
