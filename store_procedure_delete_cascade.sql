
CREATE OR REPLACE FUNCTION delete_cascade(table_name_ text, id_ text)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
  DECLARE
  
    foreigns_ record;
    exists_ bool;
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
      
      q := NULL;  
    
      EXECUTE 
        'SELECT EXISTS(
          SELECT 1 FROM '|| foreigns_.referencing_table ||
        ' WHERE ' || foreigns_.referencing_column || ' = ' || quote_literal(id_) ||
        ' );' 
      INTO exists_;
    
      IF exists_ = TRUE THEN 
        q := 'SELECT delete_cascade('|| quote_literal(foreigns_.referencing_table) ||' , id) ' 
          'FROM ' || foreigns_.referencing_table || ' ' ||
          'WHERE ' || foreigns_.referencing_column || ' = ' || quote_literal(id_) || ';';
        
        EXECUTE q;
        RAISE NOTICE '%', q;
        
      END IF;
          
    END LOOP; 
  
    q := 'DELETE FROM ' || table_name_ || 
          ' WHERE id IN(
            SELECT id 
            FROM ' || table_name_ ||
            ' WHERE id = ' || quote_literal(id_)||
          ');'
        ; 
    RAISE NOTICE '%', q;
    EXECUTE q;
  
    RETURN;
  END
$function$
;
