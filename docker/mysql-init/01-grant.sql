-- Ensure hr database is ready for apply/diff (empty target schema)
GRANT ALL PRIVILEGES ON hr.* TO 'root'@'%';
FLUSH PRIVILEGES;
