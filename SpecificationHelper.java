package com.app.helper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SpecificationHelper {

  private static final String PG_FUNCTION_CONCAT_WS = "concat_ws";

  @SuppressWarnings("unchecked")
  private static <T, Y> Join<T, Y> getJoin(Root<T> root, String column) {
    return (Join<T, Y>) root.getJoins().stream()
        .filter(
            r -> r.getAttribute().getName().equals(column) && r.getJoinType().equals(JoinType.LEFT))
        .findFirst().orElseGet(() -> root.join(column, JoinType.LEFT));
  }

  @SuppressWarnings("unchecked")
  private static <T, Y> Join<T, Y> getJoin(Join<T, Y> root, String column) {
    return (Join<T, Y>) root.getJoins().stream()
        .filter(
            r -> r.getAttribute().getName().equals(column) && r.getJoinType().equals(JoinType.LEFT))
        .findFirst().orElseGet(() -> root.join(column, JoinType.LEFT));
  }

  public static <T> Path<T> getRootPath(String column, Root<T> root) {
    if (!column.contains(".")) {
      return root.get(column);
    }
    String joinColumn = StringUtils.substringBefore(column, ".");
    column = StringUtils.substringAfter(column, ".");
    Join<T, ?> path = getJoin(root, joinColumn);
    while (true) {
      if (column.contains(".")) {
        joinColumn = StringUtils.substringBefore(column, ".");
        column = StringUtils.substringAfter(column, ".");
        path = getJoin(path, joinColumn);
      } else {
        return path.get(column);
      }
    }
  }

  public static <T, Y> Expression<T> getRootExpression(String column, Root<Y> root) {
    if (!column.contains(".")) {
      return root.<T>get(column);
    }
    String joinColumn = StringUtils.substringBefore(column, ".");
    column = StringUtils.substringAfter(column, ".");
    Join<Y, ?> path = getJoin(root, joinColumn);
    while (true) {
      if (column.contains(".")) {
        joinColumn = StringUtils.substringBefore(column, ".");
        column = StringUtils.substringAfter(column, ".");
        path = getJoin(path, joinColumn);
      } else {
        return path.<T>get(column);
      }
    }
  }

  @Deprecated(forRemoval = false, since = "")
  public static <T> Specification<T> inquiryFilterOld(List<String> columns, String inquiry) {
    return ((root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      for (String column : columns) {
        Path<T> path = getRootPath(column, root);
        predicates.add(cb.and(
            cb.like(cb.lower(path.as(String.class)), "%|" + inquiry.toLowerCase() + "%", '|')));
      }
      return cb.or(predicates.toArray(new Predicate[0]));
    });
  }
  
  public static <T> Specification<T> inquiryFilter(List<String> columns, String inquiry) {
    return ((root, query, cb) -> {
      List<Expression<String>> concatColumns = new ArrayList<>();
      concatColumns.add(cb.<String>literal(""));
      for (String column : columns) {
        Path<T> path = getRootPath(column, root);
        concatColumns.add(path.as(String.class));
      }
      Expression<String> newColumn = cb.function(
          PG_FUNCTION_CONCAT_WS, String.class, concatColumns.toArray(new Expression[0]));
      return cb.like(cb.lower(newColumn), "%|" + inquiry.toLowerCase() + "%", '|');
    });
  }
  
  public static <T, Y> Specification<T> subqueryIn(String targetColumn, 
      String subqueryColumn, Class<Y> subqueryClass, Specification<Y> subquerySpec){
    
    return ((root, query, cb) -> {
      Path<T> path = SpecificationHelper.getRootPath(targetColumn, root);
      Subquery<String> subquery = query.subquery(String.class);
      Root<Y> subqueryRoot = subquery.from(subqueryClass);
      Predicate predicate = subquerySpec.toPredicate(subqueryRoot, query, cb);
      subquery
        .select(SpecificationHelper.getRootExpression(subqueryColumn, subqueryRoot))
        .distinct(true)
        .where(predicate);
      return path.in(subquery);
    });
  }
  
  public static <T, Y> Specification<T> subqueryNotIn(String targetColumn, 
      String subqueryColumn, Class<Y> subqueryClass, Specification<Y> subquerySpec){
    
    return ((root, query, cb) -> {
      Path<T> path = SpecificationHelper.getRootPath(targetColumn, root);
      Subquery<String> subquery = query.subquery(String.class);
      Root<Y> subqueryRoot = subquery.from(subqueryClass);
      Predicate predicate = subquerySpec.toPredicate(subqueryRoot, query, cb);
      subquery
        .select(SpecificationHelper.getRootExpression(subqueryColumn, subqueryRoot))
        .distinct(true)
        .where(predicate);
      return path.in(subquery).not();
    });
  }
  
  public static <T, Y, X> Specification<T> subquery(String targetColumn, String subqueryColumn, 
      Class<X> subqueryColumnClass, Class<Y> subqueryClass, Specification<Y> subquerySpec, 
      SpecificationOperation operator){
    
    return ((root, query, cb) -> {
      Path<T> path = SpecificationHelper.getRootPath(targetColumn, root);
      Subquery<X> subquery = query.subquery(subqueryColumnClass);
      Root<Y> subqueryRoot = subquery.from(subqueryClass);
      Predicate predicate = subquerySpec.toPredicate(subqueryRoot, query, cb);
      subquery
        .select(SpecificationHelper.getRootExpression(subqueryColumn, subqueryRoot))
        .distinct(true)
        .where(predicate);
      return createPredicate(cb, path, subquery, operator);
    });
  }
  
  public static <T> Specification<T> parameterFilter(String column, Object value) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return cb.equal(path, value);
    });
  }

  public static <T> Specification<T> parameterFilterIfConditionTrue(Boolean condition, String column, Object value) {
    return ((root, query, cb) -> {
      if(Boolean.TRUE.equals(condition)) {
        Path<T> path = getRootPath(column, root);
        return cb.equal(path, value);        
      }else {
        return cb.conjunction();
      }
    });
  }
  
  public static <T> Specification<T> parameterFilter(String column, Object value,
      SpecificationOperation operator) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return createPredicate(cb, path, value, operator);
    });
  }

  public static <T> Specification<T> parameterFilterColumnAndColumn(String column, String column2,
      SpecificationOperation operator) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      Path<T> path2 = getRootPath(column2, root);
      return createPredicate(cb, path, path2, operator);
    });
  }
  
  public static <T> Specification<T> parameterFilterIfNotNull(String column, Object value,
      SpecificationOperation operator) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      Expression<Boolean> expression = cb.<Boolean>selectCase()
          .when(path.isNotNull(), createPredicate(cb, path, value, operator)).otherwise(true);
      return cb.and(expression, cb.literal(true));

    });
  }

  public static <T> Specification<T> ifNotNullEqualTo(String column, Object value) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);

      Expression<Boolean> expression =
          cb.<Boolean>selectCase().when(path.isNotNull(), cb.equal(path, value)).otherwise(true);
      return cb.and(expression, cb.literal(true));
    });
  }

  public static <T> Specification<T> ifNotNullEqualToElse(String column1, String column2, Object value) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column1, root);

      Expression<Boolean> expression = cb.<Boolean>selectCase()
          .when(path.isNotNull(), cb.equal(path, value))
          .otherwise(cb.equal(getRootPath(column2, root), value));
      return cb.and(expression, cb.literal(true));
    });
  }
  
  public static <T> Specification<T> likeAny(String column, Object value) {
    return ((root, query, cb) -> {
      Expression<T> pathArray = getRootExpression(column, root);
      return cb.like(
          cb.lower(
              cb.function("array_to_string", String.class, pathArray, cb.<String>literal("|"))),
          "%|" + ((String) value).toLowerCase() + "%", '|');
    });
  }

  public static <T> Specification<T> parameterFilterIn(String column, List<?> values) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return path.in(values);
    });
  }

  public static <T> Specification<T> arrayStringIsContainedBy(String column, List<String> values) {
    return ((root, query, cb) -> {
      Expression<T> pathArray = getRootExpression(column, root);
      return cb.equal(cb.function("array_is_contained_by", Boolean.class, pathArray,
          cb.<Object[]>literal(values.toArray(new String[] {}))), true);
    });
  }

  public static <T> Specification<T> whereTrue() {
    return ((root, query, cb) -> cb.conjunction());
  }

  public static <T> Specification<T> whereFalse() {
    return ((root, query, cb) -> cb.disjunction());
  }

  public static <T> Specification<T> parameterFilterNotIn(String column, List<?> values) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return path.in(values).not();
    });
  }

  public static <T> Specification<T> isNull(String column) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return path.isNull();
    });
  }

  public static <T> Specification<T> isNotNull(String column) {
    return ((root, query, cb) -> {
      Path<T> path = getRootPath(column, root);
      return path.isNotNull();
    });
  }

  public static <T> Specification<T> inJobPositionChildrenByJobPositionId(String column,
      String jobPositionParentId) {
    if (jobPositionParentId == null || jobPositionParentId.isBlank()) {
      return ((root, query, cb) -> cb.disjunction());
    } else {
      return ((root, query, cb) -> {
        Path<T> path = getRootPath(column, root);
        return path.in(cb.function("SELECT get_job_position_children_direct_indirect", String.class,
            cb.<String>literal(jobPositionParentId)));
      });
    }
  }
  
  public static <T> Specification<T> ifNullIsNullOrElse(
      String column, Object value, SpecificationOperation operator) {
    if(value == null) {
      return isNull(column);
    }
    return parameterFilter(column, value, operator);
  }

  private static List<Field> getAllBaseField(Class<?> entity) {
    Class<?> base = entity;
    List<Field> list = new ArrayList<>();
    list.addAll(Arrays.asList(base.getDeclaredFields()));

    boolean isBreak = false;
    while (!isBreak) {
      try {
        base = base.getSuperclass();
        list.addAll(Arrays.asList(base.getDeclaredFields()));
      } catch (Exception e) {
        isBreak = true;
      }
    }
    return list;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <Y extends Comparable<? super Y>> Predicate createPredicate(CriteriaBuilder cb,
      Expression column, Object value, SpecificationOperation operator) {
    switch (operator) {
      case EQUAL:
        return cb.equal(column, value);
      case GREATER_THAN:
        return cb.greaterThan(column, (Y) value);
      case GREATER_THAN_EQUAL:
        return cb.greaterThanOrEqualTo(column, (Y) value);
      case LESS_THAN:
        return cb.lessThan(column, (Y) value);
      case LESS_THAN_EQUAL:
        return cb.lessThanOrEqualTo(column, (Y) value);
      case MATCH:
        return cb.like(column, "%" + (String) value + "%");
      case MATCH_END:
        return cb.like(cb.lower(column), "%" + ((String) value).toLowerCase() + "%");
      case NOT_EQUAL:
        return cb.notEqual(column, value);
      case IN:
        return column.in((List<?>) value);
      default:
        return cb.equal(column, value);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Predicate createPredicate(CriteriaBuilder cb,
      Expression column, Expression column2, SpecificationOperation operator) {
    switch (operator) {
      case EQUAL:
        return cb.equal(column, column2);
      case GREATER_THAN:
        return cb.greaterThan(column, column2);
      case GREATER_THAN_EQUAL:
        return cb.greaterThanOrEqualTo(column, column2);
      case LESS_THAN:
        return cb.lessThan(column,  column2);
      case LESS_THAN_EQUAL:
        return cb.lessThanOrEqualTo(column, column2);
      case MATCH:
        return cb.like(column, cb.function(PG_FUNCTION_CONCAT_WS, String.class, 
            cb.<String>literal(""), cb.<String>literal("%"), column2, cb.<String>literal("%")));
      case MATCH_END:
        return cb.like(cb.lower(column), cb.function(PG_FUNCTION_CONCAT_WS, String.class, 
            cb.<String>literal(""), cb.<String>literal("%"), cb.lower(column2), cb.<String>literal("%")));
      case NOT_EQUAL:
        return cb.notEqual(column, column2);
      case IN:
        return column.in((List<?>) column2);
      default:
        return cb.equal(column, column2);
    }
  }
  
  public enum SpecificationOperation {
    GREATER_THAN, LESS_THAN, GREATER_THAN_EQUAL, LESS_THAN_EQUAL, NOT_EQUAL, EQUAL, MATCH, MATCH_END, IN
  }

  public static Sort createSort(Map<String, String> properties, List<SortBy> sortBy) {
    List<Order> orders = new ArrayList<>();
    if (sortBy == null || sortBy.isEmpty()) {
      return Sort.by(orders);
    }
    sortBy.stream().forEach(s -> {
      if (properties.get(s.getPropertyName()) == null) {
        throw new Exception("sort property is not exist");
      }
      orders.add(new Order(Direction.fromString(s.getDirection().name()),
          properties.get(s.getPropertyName())));
    });
    return Sort.by(orders);
  }

  public static Sort createSort(List<SortBy> sortBy) {
    List<Order> orders = new ArrayList<>();
    if (sortBy == null || sortBy.isEmpty()) {
      return Sort.by(orders);
    }
    sortBy.stream().forEach(s -> orders
        .add(new Order(Direction.fromString(s.getDirection().name()), s.getPropertyName())));
    return Sort.by(orders);
  }

  public static Sort createSort(String property, SortByDirection direction) {
    return Sort.by(Arrays.asList(new Order(Direction.fromString(direction.name()), property)));
  }

  public static Sort createSort(Class<?> clazz, List<SortBy> sortBy) {
    List<Field> list = getAllBaseField(clazz);
    List<Order> orders = new ArrayList<>();
    sortBy.stream().forEach(s -> list.stream().filter(f -> f.getName().equals(s.getPropertyName()))
        .findFirst().ifPresentOrElse(f -> {
          SortProperty sortProperty = f.getDeclaredAnnotation(SortProperty.class);
          if (sortProperty == null) {
            orders
                .add(new Order(Direction.fromString(s.getDirection().name()), s.getPropertyName()));
          } else {
            orders.add(new Order(Direction.fromString(s.getDirection().name()),
                sortProperty.entityProperty()));
          }
        }, () -> {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "sort property " + ErrorMessageConstant.IS_NOT_EXISTS);
        }));
    return Sort.by(orders);
  }

  public static CompareToBuilder createBuilderSort() {
    return new CompareToBuilder();
  }

  public static <T> Comparator<T> createModelSort(Class<T> clazz, Sort sort) {
    return (val1, val2) -> {
      CompareToBuilder builder = new CompareToBuilder();
      List<Field> list = getAllBaseField(clazz);
      sort.forEach(s -> list.stream().filter(f -> f.getName().equals(s.getProperty())).findFirst()
          .ifPresent(f -> {
            if (s.getDirection().equals(Direction.ASC)) {
              try {
                builder.append(FieldUtils.readField(val1, f.getName(), true),
                    FieldUtils.readField(val2, f.getName(), true));
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              }
            } else if (s.getDirection().equals(Direction.DESC)) {
              try {
                builder.append(FieldUtils.readField(val2, f.getName(), true),
                    FieldUtils.readField(val1, f.getName(), true));
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              }
            }
          }));
      return builder.toComparison();
    };
  }

  public static Comparator<LinkedHashMap<String, Object>> createSort(Sort sort) {
    return (val1, val2) -> {
      CompareToBuilder builder = new CompareToBuilder();
      sort.forEach(s -> {
        if (s.getDirection().equals(Direction.ASC)) {
          builder.append(val1.get(s.getProperty()), val2.get(s.getProperty()));
        } else if (s.getDirection().equals(Direction.DESC)) {
          builder.append(val2.get(s.getProperty()), val1.get(s.getProperty()));
        }
      });
      return builder.toComparison();
    };
  }
  
  public static Order createOrder(String property, SortByDirection direction) {
    return new Order(Direction.fromString(direction.name()), property);
  }

  public static <T> Specification<T> inquiryFilter(List<String> columns, List<String> inquiries) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            String inquiry = inquiries.get(i);
            if (inquiry != null && !inquiry.isEmpty()) {
                String[] inquiryTerms = inquiry.split(","); // Split if there are multiple values

                CriteriaBuilder.In<String> inClause = cb.in(root.get(column));
                for (String term : inquiryTerms) {
                    inClause.value(term.trim());
                }
                predicates.add(inClause);
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}

}
