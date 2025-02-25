package com.app.persistence.repository;

import static org.springframework.data.jpa.repository.query.QueryUtils.toOrders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.data.support.PageableExecutionUtils;
import com.app.helper.SpecificationHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class CustomRepositoryImpl<T> implements CustomRepository<T> {

  @PersistenceContext
  private EntityManager entityManager;

  private List<Expression<?>> getExpresions(List<String> columns, Root<T> root) {
    List<Expression<?>> exps = new ArrayList<>();
    for (String column : columns) {
      Expression<?> exp = SpecificationHelper.getRootExpression(column, root);
      exp.alias(column);
      exps.add(exp);
    }
    return exps;
  }

  @Override
  public List<Tuple> selectTuple(Class<T> entityClass, List<String> columns,
      Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);
    cq.multiselect(exps.toArray(new Expression[0])).distinct(true).where(predicate);
    return entityManager.createQuery(cq).getResultList();
  }

  @Override
  public Page<Tuple> selectTuple(Class<T> entityClass, List<String> columns, Specification<T> spec,
      Pageable pageable) {
    Sort sort = pageable.isPaged() ? pageable.getSort() : Sort.unsorted();
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);
    cq.multiselect(exps.toArray(new Expression[0])).distinct(true).where(predicate);
    if (sort.isSorted()) {
      cq.orderBy(toOrders(sort, root, cb));
    }
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    if (pageable.isPaged()) {
      query.setFirstResult(PageableUtils.getOffsetAsInteger(pageable));
      query.setMaxResults(pageable.getPageSize());
    }
    return pageable.isUnpaged() ? new PageImpl<>(query.getResultList())
        : PageableExecutionUtils.getPage(query.getResultList(), pageable,
            () -> executeCountQuery(getCountQuery(entityClass, columns, spec)));
  }

  @Override
  public List<Tuple> selectTupleCountGroupBy(Class<T> entityClass, List<String> columns,
      List<String> countColumns, Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    List<Expression<?>> countExps = getExpresions(countColumns, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);

    List<Expression<?>> selectColumns = new ArrayList<>();
    selectColumns.addAll(exps);
    countExps.add(cb.<String>literal(""));
    Expression<String> newColumn =
        cb.function("concat_ws", String.class, countExps.toArray(new Expression[0]));
    Expression<Long> countexp = cb.count(newColumn);
    countexp.alias("count");
    selectColumns.add(countexp);

    cq.multiselect(selectColumns.toArray(new Expression[0])).where(predicate)
        .groupBy(exps.toArray(new Expression[0]));
    return entityManager.createQuery(cq).getResultList();
  }

  @Override
  public List<Tuple> selectTupleAvgGroupBy(Class<T> entityClass, List<String> columns,
      String avgColumn, Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    Expression<? extends Number> avgExp = SpecificationHelper.getRootExpression(avgColumn, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);

    List<Expression<?>> selectColumns = new ArrayList<>();
    selectColumns.addAll(exps);
    Expression<? extends Number> countexp = cb.avg(avgExp);
    countexp.alias("avg");
    selectColumns.add(countexp);

    cq.multiselect(selectColumns.toArray(new Expression[0])).where(predicate)
        .groupBy(exps.toArray(new Expression[0]));
    return entityManager.createQuery(cq).getResultList();
  }

  @Override
  public Page<Tuple> selectTupleAvgGroupBy(Class<T> entityClass, List<String> columns,
      String avgColumn, Specification<T> spec, Pageable pageable) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    List<Expression<?>> exps = getExpresions(columns, root);
    Expression<? extends Number> avgExp = SpecificationHelper.getRootExpression(avgColumn, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);

    List<Expression<?>> selectColumns = new ArrayList<>();
    selectColumns.addAll(exps);
    Expression<? extends Number> avgExpr = cb.avg(avgExp);
    avgExpr.alias("avg");
    selectColumns.add(avgExpr);

    cq.multiselect(selectColumns.toArray(new Expression[0])).where(predicate)
        .groupBy(exps.toArray(new Expression[0]));

    if (pageable.getSort() != null && pageable.getSort().isSorted()) {
      List<Order> orders = new ArrayList<>();
      for (Sort.Order order : pageable.getSort()) {
        if ("avg".equals(order.getProperty())) {
          orders.add(order.isAscending() ? cb.asc(avgExpr) : cb.desc(avgExpr));
        } else {
          Expression<?> orderExpression =
              SpecificationHelper.getRootExpression(order.getProperty(), root);
          orders.add(order.isAscending() ? cb.asc(orderExpression) : cb.desc(orderExpression));
        }
      }
      cq.orderBy(orders);
    }

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    if (pageable.isPaged()) {
      query.setFirstResult((int) pageable.getOffset());
      query.setMaxResults(pageable.getPageSize());
    }

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    countQuery.select(cb.countDistinct(countRoot))
        .where(spec.toPredicate(countRoot, countQuery, cb));
    Long total = entityManager.createQuery(countQuery).getSingleResult();

    return new PageImpl<>(query.getResultList(), pageable, total);
  }


  @Override
  public List<Tuple> selectTupleSumGroupBy(Class<T> entityClass, List<String> columns,
      String sumColumn, Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    Expression<? extends Number> avgExp = SpecificationHelper.getRootExpression(sumColumn, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);

    List<Expression<?>> selectColumns = new ArrayList<>();
    selectColumns.addAll(exps);
    Expression<? extends Number> countexp = cb.sum(avgExp);
    countexp.alias("sum");
    selectColumns.add(countexp);

    cq.multiselect(selectColumns.toArray(new Expression[0])).where(predicate)
        .groupBy(exps.toArray(new Expression[0]));
    return entityManager.createQuery(cq).getResultList();
  }

  private static long executeCountQuery(TypedQuery<Long> query) {
    List<Long> totals = query.getResultList();
    long total = 0L;
    for (Long element : totals) {
      total += element == null ? 0 : element;
    }
    return total;
  }

  private TypedQuery<Long> getCountQuery(Class<T> entityClass, List<String> columns,
      Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    Root<T> root = cq.from(entityClass);
    List<Expression<?>> exps = getExpresions(columns, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);
    Expression<String> newColumn =
        cb.function("concat_ws", String.class, exps.toArray(new Expression[0]));
    if (cq.isDistinct()) {
      cq.select(cb.countDistinct(newColumn));
    } else {
      cq.select(cb.count(newColumn));
    }
    cq.where(predicate);
    cq.orderBy(Collections.emptyList());
    return entityManager.createQuery(cq);
  }

  @Override
  public Page<Tuple> selectTupleAggregateGroupBy(Class<T> entityClass, List<String> columns,
      Map<AggregateFunction, String> aggregates, Specification<T> spec, Pageable pageable) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    List<Expression<?>> exps = getExpresions(columns, root);
    Predicate predicate = spec.toPredicate(root, cq, cb);

    List<Expression<?>> selectColumns = new ArrayList<>();
    selectColumns.addAll(exps);

    for (AggregateFunction function : aggregates.keySet()) {
      String column = aggregates.get(function);
      Expression<? extends Number> columnExpression =
          SpecificationHelper.getRootExpression(column, root);
      Expression<?> aggregateExpr =
          AggregateFunction.applyAggregate(cb, function, columnExpression);
      aggregateExpr.alias(function.name().toLowerCase() + "_" + column);
      selectColumns.add(aggregateExpr);
    }

    cq.multiselect(selectColumns.toArray(new Expression[0])).where(predicate)
        .groupBy(exps.toArray(new Expression[0]));

    if (pageable.getSort() != null && pageable.getSort().isSorted()) {
      List<Order> orders = new ArrayList<>();
      for (Sort.Order order : pageable.getSort()) {
        String property = order.getProperty();
        boolean handled = false;

        for (AggregateFunction function : aggregates.keySet()) {
          String column = aggregates.get(function);
          if (property.equals(function.name().toLowerCase() + "_" + column)) {
            Expression<?> aggregateExpr = AggregateFunction.applyAggregate(cb, function,
                SpecificationHelper.getRootExpression(column, root));
            orders.add(order.isAscending() ? cb.asc(aggregateExpr) : cb.desc(aggregateExpr));
            handled = true;
            break;
          }
        }

        if (!handled) {
          Expression<?> orderExpression = SpecificationHelper.getRootExpression(property, root);
          orders.add(order.isAscending() ? cb.asc(orderExpression) : cb.desc(orderExpression));
        }
      }
      cq.orderBy(orders);
    }

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    if (pageable.isPaged()) {
      query.setFirstResult((int) pageable.getOffset());
      query.setMaxResults(pageable.getPageSize());
    }

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    countQuery.select(cb.countDistinct(countRoot))
        .where(spec.toPredicate(countRoot, countQuery, cb));
    Long total = entityManager.createQuery(countQuery).getSingleResult();

    return new PageImpl<>(query.getResultList(), pageable, total);
  }

  public enum AggregateFunction {
    AVG, SUM;

    public static Expression<?> applyAggregate(CriteriaBuilder cb, AggregateFunction function,
        Expression<? extends Number> expression) {
      switch (function) {
        case AVG:
          return cb.avg(expression);
        case SUM:
          return cb.sum(expression);
        default:
          throw new IllegalArgumentException("Unsupported aggregate function: " + function);
      }
    }
  }

}
