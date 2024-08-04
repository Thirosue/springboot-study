package se.magnus.api.composite.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewSummary {
  private int reviewId;
  private String author;
  private String subject;
  private String content;
}
