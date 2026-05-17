-- Sample HR schema for oracle2mysql smoke tests (runs as SYS on first boot)
CREATE TABLE hr.departments (
    department_id   NUMBER(4)       NOT NULL,
    department_name VARCHAR2(30)    NOT NULL,
    CONSTRAINT dept_pk PRIMARY KEY (department_id)
);

CREATE TABLE hr.employees (
    employee_id    NUMBER(6)       NOT NULL,
    first_name     VARCHAR2(20),
    last_name      VARCHAR2(25)    NOT NULL,
    email          VARCHAR2(25),
    hire_date      DATE            DEFAULT SYSDATE NOT NULL,
    salary         NUMBER(8, 2),
    department_id  NUMBER(4),
    CONSTRAINT emp_pk PRIMARY KEY (employee_id),
    CONSTRAINT emp_email_uk UNIQUE (email),
    CONSTRAINT emp_dept_fk FOREIGN KEY (department_id) REFERENCES hr.departments (department_id)
);

COMMENT ON TABLE hr.employees IS 'Employee master data';
COMMENT ON COLUMN hr.employees.salary IS 'Monthly salary';

CREATE INDEX hr.emp_dept_ix ON hr.employees (department_id);

INSERT INTO hr.departments VALUES (10, 'Administration');
INSERT INTO hr.departments VALUES (20, 'Marketing');
INSERT INTO hr.employees VALUES (100, 'Steven', 'King', 'SKING', DATE '2003-06-17', 24000, 10);
INSERT INTO hr.employees VALUES (101, 'Neena', 'Kochhar', 'NKOCHHAR', DATE '2005-01-21', 17000, 20);

COMMIT;
