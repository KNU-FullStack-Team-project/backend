SET PAGESIZE 100
SET LINESIZE 200
COLUMN table_name FORMAT A20
COLUMN constraint_name FORMAT A30
COLUMN column_name FORMAT A20
COLUMN r_constraint_name FORMAT A30

SELECT a.table_name, a.constraint_name, b.column_name, a.r_constraint_name
FROM all_constraints a
JOIN all_cons_columns b ON a.constraint_name = b.constraint_name
WHERE a.constraint_name = 'FK4JG39JA0RJVJYYDIBGTBP8B6B';

SELECT table_name, constraint_name, constraint_type, r_constraint_name
FROM all_constraints
WHERE constraint_name = 'FK4JG39JA0RJVJYYDIBGTBP8B6B';
