-- SeatSure schema. Owned by Flyway; Hibernate only validates.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    venue       VARCHAR(150) NOT NULL,
    starts_at   DATETIME(6)  NOT NULL,
    description VARCHAR(1000),
    seat_rows   INT          NOT NULL,
    seats_per_row INT        NOT NULL,
    created_at  DATETIME(6)  NOT NULL
) ENGINE = InnoDB;

CREATE TABLE seat_holds (
    id         VARCHAR(36)  PRIMARY KEY,
    event_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    status     VARCHAR(20)  NOT NULL,   -- ACTIVE, CONFIRMED, EXPIRED, RELEASED
    expires_at DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_holds_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_holds_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    INDEX idx_holds_status_expires (status, expires_at),
    INDEX idx_holds_user_event (user_id, event_id, status)
) ENGINE = InnoDB;

CREATE TABLE seats (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id   BIGINT        NOT NULL,
    row_label  VARCHAR(3)    NOT NULL,
    seat_number INT          NOT NULL,
    tier       VARCHAR(20)   NOT NULL,   -- PREMIUM, STANDARD, ECONOMY
    price      DECIMAL(10,2) NOT NULL,
    status     VARCHAR(20)   NOT NULL,   -- AVAILABLE, HELD, BOOKED
    hold_id    VARCHAR(36),
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_seats_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT uk_seat_position UNIQUE (event_id, row_label, seat_number),
    INDEX idx_seats_event_status (event_id, status),
    INDEX idx_seats_hold (hold_id)
) ENGINE = InnoDB;

CREATE TABLE bookings (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference    VARCHAR(12)   NOT NULL,
    event_id     BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    hold_id      VARCHAR(36)   NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    seat_labels  VARCHAR(255)  NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uk_bookings_reference UNIQUE (reference),
    CONSTRAINT uk_bookings_hold UNIQUE (hold_id),       -- one booking per hold, enforced by the DB
    CONSTRAINT fk_bookings_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_bookings_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    INDEX idx_bookings_user (user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE idempotency_keys (
    idem_key   VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    booking_id BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id, idem_key),
    CONSTRAINT fk_idem_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE = InnoDB;

CREATE TABLE outbox_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(50)  NOT NULL,
    payload      JSON         NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    published_at DATETIME(6),
    INDEX idx_outbox_unpublished (published_at, id)
) ENGINE = InnoDB;
