CREATE OR REPLACE FUNCTION id_used_on_table(table_name_ text, id_ text)
 RETURNS TABLE(table_name text, id TEXT, sort integer)
 LANGUAGE plpgsql
AS $function$
  DECLARE
  
    foreigns_ record;
    data_ jsonb;
    final_data jsonb := '[]';
    q TEXT;
  
  BEGIN
    
    FOR foreigns_ IN
      SELECT 
        la.attrelid::regclass AS referencing_table,
        la.attname AS referencing_column
      FROM pg_constraint AS c
      JOIN pg_index AS i ON i.indexrelid = c.conindid
      JOIN pg_attribute AS la ON la.attrelid = c.conrelid
        AND la.attnum = c.conkey[1]
      JOIN pg_attribute AS ra ON ra.attrelid = c.confrelid
        AND ra.attnum = c.confkey[1]
      WHERE c.confrelid = table_name_::regclass
        AND c.contype = 'f'
        AND ra.attname = 'id'
        AND cardinality(c.confkey) = 1  
    LOOP
      data_ := NULL;
      q := NULL;
      
      q := concat(
      $$SELECT jsonb_agg(json_build_object('table', $$,
      quote_literal(foreigns_.referencing_table),
      $$, 'id', $$, 
      $$id, 'column', $$, 
      quote_literal(foreigns_.referencing_column),
      $$, 'sort', 1 $$,
      $$)) FROM $$,
      foreigns_.referencing_table,
      $$ WHERE $$,
      foreigns_.referencing_column, 
      $$ = $$, 
      quote_literal(id_),
      $$ ;$$);
    
      RAISE NOTICE '%', q;
      EXECUTE q INTO data_; 
      final_data := final_data || COALESCE(data_, '[]'::jsonb);
    
      SELECT jsonb_agg(
          jsonb_build_object(
            'table', res.table_name, 
            'id', res.id,
            'sort', res.sort+1
          ) 
        ) 
      FROM( 
        SELECT
          (a.datas ->> 'table')::TEXT AS table_name,
          (a.datas ->> 'id')::TEXT AS id
        FROM jsonb_array_elements(data_)AS a(datas)
      )base 
      JOIN id_used_on_table(base.table_name, base.id) AS res ON TRUE 
      INTO data_;
    
      final_data := final_data || COALESCE(data_, '[]'::jsonb);
    
    END LOOP; 
  
    RAISE NOTICE '%', data_;
    RAISE NOTICE '%', final_data;
    
    RETURN query 
    SELECT
      (a.data_ ->> 'table')::TEXT,
      (a.data_ ->> 'id')::TEXT,
      (a.data_ ->> 'sort')::integer 
    FROM jsonb_array_elements(final_data)AS a(data_)
    ORDER BY (a.data_ ->> 'sort')::integer  ASC 
    ;
    
  END
$function$
;

