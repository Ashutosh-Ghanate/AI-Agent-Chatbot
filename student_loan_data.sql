-- ============================================================
-- student_loan_db : schema + sample data
-- Run against your PostgreSQL database, e.g.:
--   psql -U postgres -d student_loan -f student_loan_data.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS student_table (
    id              SERIAL PRIMARY KEY,
    applicant_name  VARCHAR(100)  NOT NULL,
    email           VARCHAR(120)  UNIQUE NOT NULL,
    phone           VARCHAR(15),
    university      VARCHAR(150)  NOT NULL,
    course          VARCHAR(100),
    loan_amount     NUMERIC(12,2) NOT NULL CHECK (loan_amount > 0),
    interest_rate   NUMERIC(5,2)  NOT NULL CHECK (interest_rate > 0),  -- % per annum
    tenure_months   INT           NOT NULL CHECK (tenure_months > 0),
    emi             NUMERIC(12,2),                                     -- monthly installment
    credit_score    INT           CHECK (credit_score BETWEEN 300 AND 900),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','APPROVED','REJECTED','DISBURSED','CLOSED')),
    applied_on      DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Sample rows (10 applicants with varied profiles)
-- ------------------------------------------------------------
INSERT INTO student_table
    (applicant_name, email, phone, university, course,
     loan_amount, interest_rate, tenure_months, emi,
     credit_score, status, applied_on)
VALUES
    ('Aarav Sharma',   'aarav.sharma@example.com',   '9876501234', 'IIT Bombay',          'B.Tech CSE',           500000.00,  9.50, 60, 10501.00, 780, 'APPROVED',  '2026-06-12'),
    ('Diya Patil',     'diya.patil@example.com',     '9823456781', 'DELHI University',     'MBA',                 1000000.00,  8.75, 84, 15959.00, 810, 'DISBURSED', '2026-05-03'),
    ('Rohan Deshmukh', 'rohan.deshmukh@example.com', '9765432109', 'VIT Vellore',          'B.Tech ECE',           300000.00, 10.50, 36,  9743.00, 690, 'PENDING',   '2026-09-01'),
    ('Ananya Iyer',    'ananya.iyer@example.com',    '9890123456', 'NIT Trichy',           'B.Tech Mech',          750000.00, 11.25, 48, 19477.00, 720, 'APPROVED',  '2026-07-21'),
    ('Kabir Singh',    'kabir.singh@example.com',    '9745612380', 'Chandigarh University','B.Sc Data Science',    200000.00,  7.50, 24,  9000.00, 640, 'REJECTED',  '2026-08-15'),
    ('Meera Nair',     'meera.nair@example.com',     '9988776655', 'IIM Bangalore',        'PGDM',                1500000.00,  9.00, 60, 31120.00, 845, 'DISBURSED', '2026-04-10'),
    ('Vivaan Gupta',   'vivaan.gupta@example.com',   '9812345670', 'Anna University',      'B.E. IT',              400000.00, 10.75, 48, 10261.00, 670, 'PENDING',   '2026-09-18'),
    ('Ishita Rao',     'ishita.rao@example.com',     '9900112233', 'BITS Pilani',          'B.Tech CS',            600000.00,  8.25, 60, 12246.00, 795, 'APPROVED',  '2026-06-30'),
    ('Arjun Verma',    'arjun.verma@example.com',    '9777665544', 'Symbiosis Pune',       'BBA',                  350000.00, 12.00, 36, 11620.00, 610, 'REJECTED',  '2026-07-05'),
    ('Sara Khan',      'sara.khan@example.com',      '9666554433', 'Jadavpur University',  'M.Tech CSE',           850000.00,  9.75, 84, 13890.00, 760, 'CLOSED',    '2025-11-22');

-- ------------------------------------------------------------
-- Quick verification queries
-- ------------------------------------------------------------
-- SELECT * FROM student_loan_db ORDER BY id;
-- SELECT status, COUNT(*) FROM student_loan_db GROUP BY status;
-- SELECT AVG(loan_amount), AVG(interest_rate) FROM student_loan_db;
</parameter>
</task_progress>
</write_to_file>