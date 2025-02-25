package com.app.persistence.repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.app.AggregateFunction;

import jakarta.persistence.Tuple;

public interface CustomRepository<T> {

  List<Tuple> selectTuple(Class<T> entityClass, 
      List<String> columns, Specification<T> spec);
  Page<Tuple> selectTuple(Class<T> entityClass, 
      List<String> columns, Specification<T> spec, Pageable pageable);
  List<Tuple> selectTupleCountGroupBy(Class<T> entityClass, 
      List<String> columns, List<String> countColumn, Specification<T> spec);
  List<Tuple> selectTupleAvgGroupBy(Class<T> entityClass, List<String> columns, 
      String avgColumn, Specification<T> spec);
  List<Tuple> selectTupleSumGroupBy(Class<T> entityClass, List<String> columns, 
      String sumColumn, Specification<T> spec);
  Page<Tuple> selectTupleAvgGroupBy(Class<T> entityClass, List<String> columns, String avgColumn, Specification<T> spec,
		Pageable pageable);
  Page<Tuple> selectTupleAggregateGroupBy(Class<T> entityClass, List<String> columns,
		Map<AggregateFunction, String> aggregates, Specification<T> spec, Pageable pageable);

}
